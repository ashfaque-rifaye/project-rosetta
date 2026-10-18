"""Feasibility test 3: Polly makes a Hindi clip -> S3 -> Transcribe with automatic language ID + WebVTT.

Usage: python pipeline/spikes/test3_transcribe.py [path/to/audio]   (no arg = synthesize with Polly)
"""
import json, sys, time, urllib.request, uuid
from pathlib import Path
import boto3

REGION = "us-east-1"
LANGS = ["ta-IN", "hi-IN", "ml-IN", "en-IN"]  # candidate family languages for language ID
OUT = Path(__file__).parent / "out"
OUT.mkdir(exist_ok=True)

HINDI_STORY = (
    "उस साल बारिश कुछ अलग ही थी। नदी मंदिर की सीढ़ियों तक आ गई थी, और हम बच्चे छत पर बैठकर पानी देखते रहते थे। "
    "हर सुबह अम्मा फिर भी दरवाज़े पर कोलम बनाती थीं, चावल के आटे से, ताकि चींटियों को भी खाना मिले। "
    "नौ दिन तक पूरी गली ने एक ही बड़े बर्तन में खाना पकाया। किसी के घर में चावल था, किसी के घर में दाल, "
    "और पड़ोस वाली मामी अपनी इमली की चटनी लेकर आती थीं। "
    "तभी तुम्हारे थाथा पहली बार हमारे घर आए थे, भीगे हुए, हाथ में एक छतरी और एक चिट्ठी लेकर।"
)

session = boto3.session.Session(region_name=REGION)
account = session.client("sts").get_caller_identity()["Account"]
bucket = f"rosetta-dev-{account}-use1"
s3 = session.client("s3")

def ensure_bucket():
    try:
        s3.head_bucket(Bucket=bucket)
    except s3.exceptions.ClientError:
        s3.create_bucket(Bucket=bucket)
        s3.put_public_access_block(Bucket=bucket, PublicAccessBlockConfiguration={
            "BlockPublicAcls": True, "IgnorePublicAcls": True, "BlockPublicPolicy": True, "RestrictPublicBuckets": True})
        print("created private bucket", bucket)

def synthesize() -> Path:
    polly = session.client("polly")
    r = polly.synthesize_speech(Text=HINDI_STORY, VoiceId="Kajal", Engine="neural",
                                LanguageCode="hi-IN", OutputFormat="mp3")
    path = OUT / "story_hi_polly.mp3"
    path.write_bytes(r["AudioStream"].read())
    print(f"polly: {path.name} {path.stat().st_size//1024} KB")
    return path

def main():
    ensure_bucket()
    audio = Path(sys.argv[1]) if len(sys.argv) > 1 else synthesize()
    key = f"spikes/test3/{audio.name}"
    s3.upload_file(str(audio), bucket, key)

    tr = session.client("transcribe")
    job = f"rosetta-test3-{uuid.uuid4().hex[:8]}"
    t0 = time.time()
    tr.start_transcription_job(
        TranscriptionJobName=job,
        Media={"MediaFileUri": f"s3://{bucket}/{key}"},
        IdentifyLanguage=True,
        LanguageOptions=LANGS,
        OutputBucketName=bucket,
        OutputKey=f"spikes/test3/{job}/",
        Subtitles={"Formats": ["vtt"], "OutputStartIndex": 1},
    )
    while True:
        j = tr.get_transcription_job(TranscriptionJobName=job)["TranscriptionJob"]
        if j["TranscriptionJobStatus"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(5)
    print(f"transcribe: {j['TranscriptionJobStatus']} in {time.time()-t0:.0f}s")
    if j["TranscriptionJobStatus"] == "FAILED":
        print("reason:", j.get("FailureReason")); sys.exit(1)

    print("identified:", j.get("IdentifiedLanguageScore") and f"{j['LanguageCode']} (score {j['IdentifiedLanguageScore']:.2f})")
    for name, uri in [("transcript.json", j["Transcript"]["TranscriptFileUri"]),
                      ("subtitles.vtt", j["Subtitles"]["SubtitleFileUris"][0])]:
        k = uri.split(f"/{bucket}/", 1)[1]
        s3.download_file(bucket, k, str(OUT / name))
    data = json.loads((OUT / "transcript.json").read_text(encoding="utf-8"))
    print("transcript:", data["results"]["transcripts"][0]["transcript"])
    print("segments:", len(data["results"].get("audio_segments", [])), "| words:",
          sum(1 for i in data["results"]["items"] if i["type"] == "pronunciation"))
    print("--- vtt head ---"); print("\n".join((OUT / "subtitles.vtt").read_text(encoding="utf-8").splitlines()[:12]))

if __name__ == "__main__":
    main()
