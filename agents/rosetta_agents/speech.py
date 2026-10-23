"""Speech on AWS: the Keeper's voice with Amazon Polly, and kids' voice notes heard with Amazon Transcribe.

  speak(text)            -> WAV bytes (16 kHz mono 16-bit), Polly's generative voice (neural if generative is out)
  transcribe(key, ext)   -> what was said in a voice note already in the bucket (a Transcribe batch job; short notes
                            usually take ~10 s, occasionally over a minute). The words then go to the model (vision.listen) for the parents' summary.
"""
from __future__ import annotations

import io
import json
import logging
import time
import uuid
import wave

import boto3

from . import config

log = logging.getLogger(__name__)
_polly = boto3.client("polly", region_name=config.REGION)
_transcribe = boto3.client("transcribe", region_name=config.REGION)
_s3 = boto3.client("s3", region_name=config.REGION)


def speak(text: str) -> bytes:
    started = time.perf_counter()
    for engine in ("generative", "neural"):
        try:
            resp = _polly.synthesize_speech(Text=text[:1500], VoiceId=config.KEEPER_VOICE, Engine=engine,
                                            LanguageCode=config.KEEPER_LANGUAGE,
                                            OutputFormat="pcm", SampleRate="16000")
            break
        except _polly.exceptions.ClientError:
            if engine == "neural":
                raise
            log.warning("generative voice unavailable, using neural")
    pcm = resp["AudioStream"].read()
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(pcm)
    print(f"keeper voice: {len(pcm) // 32000}s of audio in {time.perf_counter() - started:.1f}s")
    return buf.getvalue()


# Transcribe reads these containers straight from S3 (Safari records m4a/mp4, Chrome webm, Firefox ogg).
MEDIA_FORMAT = {"webm": "webm", "ogg": "ogg", "m4a": "m4a", "mp4": "mp4", "mp3": "mp3", "wav": "wav"}


def transcribe(key: str, ext: str, timeout: float = 150.0) -> str:
    job = f"rosetta-{uuid.uuid4().hex[:20]}"
    out_key = f"transcripts/{job}.json"
    _transcribe.start_transcription_job(
        TranscriptionJobName=job, Media={"MediaFileUri": f"s3://{config.BUCKET}/{key}"},
        MediaFormat=MEDIA_FORMAT.get(ext, "webm"), IdentifyLanguage=True, LanguageOptions=["en-IN", "en-US", "en-GB", "hi-IN"],
        OutputBucketName=config.BUCKET, OutputKey=out_key,
    )
    t0 = time.time()
    while time.time() - t0 < timeout:
        status = _transcribe.get_transcription_job(TranscriptionJobName=job)["TranscriptionJob"]["TranscriptionJobStatus"]
        if status == "COMPLETED":
            body = json.loads(_s3.get_object(Bucket=config.BUCKET, Key=out_key)["Body"].read())
            return " ".join(t["transcript"] for t in body["results"]["transcripts"]).strip()
        if status == "FAILED":
            raise RuntimeError(f"transcription {job} failed")
        time.sleep(1.5)
    raise TimeoutError(f"transcription {job} took over {timeout:.0f}s")
