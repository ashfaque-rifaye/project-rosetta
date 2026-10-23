"""The demo video's media, made with Google models on Vertex AI (project tesselle-demandfoundry, keyless ADC).

  python assets/demo/make_demo.py voice     narration, one WAV per beat (Gemini TTS)        -> assets/demo/vo/
  python assets/demo/make_demo.py kid       Arjun's voice note for the live demo (Gemini TTS) -> assets/demo/kid_voice.wav
  python assets/demo/make_demo.py music     intro sting, explainer bed, outro (Lyria)        -> assets/demo/music/
  python assets/demo/make_demo.py veo       cinematic intro and outro shots (Veo 3.1)        -> assets/demo/veo/

Screen footage comes from the Fire TV emulator and the phone pages; assets/demo/edit.py cuts it together.
"""
from __future__ import annotations

import base64
import io
import sys
import time
import wave
from pathlib import Path

import google.auth
import google.auth.transport.requests
import requests
from google import genai
from google.genai import types

PROJECT = "tesselle-demandfoundry"
HERE = Path(__file__).resolve().parent
LABELS = {"app": "rosetta", "purpose": "demo-video"}

NARRATOR = "Algieba"  # smooth
NARRATOR_STYLE = "Say in a calm, warm, confident voice, like a premium product film narrator"

# The narration, beat by beat (ids match the edit in edit.py). Every clip is checked by transcription (see verified()).
VO = {
    "01_hook": "Every evening, in millions of homes, the same two questions.",
    "02_q": "Did you finish your homework? ... Can I watch TV now?",
    "03_what_if": "What if the TV itself could help?",
    "04_title": "This is Rosetta. The Fire TV app where the whole family levels up.",
    "05_home": ("Tonight's quests live in a living dollhouse. Homework, chores, and the daily habits parents set. "
                "And TV time stays locked until the must-dos are done."),
    "06_homework": ("Arjun snaps his worksheet with any phone. Seconds later, the AI Tutor marks every answer on his own "
                    "handwriting. The maths is double-checked in code, and every mistake gets a hint, never the answer."),
    "07_practice": "Then, a practice round on exactly what slipped.",
    "08_honest": "And it's honest. Send the wrong photo, and Rosetta says what it actually sees.",
    "09_voice": ("Daily habits check in with a tick, a note, or a voice note. Gemini listens, sums it up, and saves it "
                 "as a memory for Mom and Dad."),
    "10_rewards": "Every task pays off: XP, coins, real pocket money for the wish list, and a star toward Movie Night.",
    "11_parents": "Parents get a ping, approve in one tap, and can even unlock TV time from their phone. The portal opens.",
    "12_quiz": "Then it's Family Quiz Night: a 3D game, with questions written for each child's class and next test.",
    "13_progress": "And Coach's notes turn real activity into a clear picture for parents.",
    "14_tech": ("Rosetta runs natively on Fire TV, with an agent crew on AWS: Strands Agents, Lambda, S3, DynamoDB and "
                "CloudFront. Gemini on Vertex AI powers vision and voice."),
    "15_outro": "Rosetta. The TV that works for the family, not against it.",
}

KID_LINE = ("I read eleven pages of Matilda! My favourite part was when Matilda made the chalk write on the board "
            "all by itself. It was so cool.")
KID_STYLE = "Say this like an excited, proud eight-year-old boy telling his mum about his day, quick and happy"

MUSIC = {
    "intro": ("A 14-second epic cinematic trailer opening for a magical family adventure: deep taiko and orchestral "
              "hits, soaring strings and brass, a choir swell, sparkling celesta, building to a huge heroic hit at "
              "around 11 seconds, then a shimmering tail. Big, awe-inspiring, Pixar trailer energy. Instrumental."),
    "bed": ("Upbeat, modern, optimistic product-demo music: bright plucked synths, light claps, warm piano chords, "
            "gentle four-on-the-floor kick, playful and confident, 112 bpm, steady energy for a 2-minute app "
            "walkthrough, leaves room for a voiceover. Instrumental."),
    "outro": ("A warm, emotional cinematic finale: soft piano motif rising into full orchestral strings and a gentle "
              "choir, hopeful and heartfelt, resolving to a final glowing chord. Instrumental."),
}
MUSIC_NEGATIVE = "vocals, singing, lyrics, distortion, harsh, scary, dark"

VEO = {
    "intro_city": ("Epic cinematic animated opening, Pixar-meets-anime style. Night over a cosy city of thousands of "
                   "small family homes with glowing windows, seen from high above. Camera swoops down fast between "
                   "rooftops as streams of golden starlight rise from every window and spiral up into the sky, forming "
                   "a giant glowing constellation. Volumetric light, sparks, sweeping camera, awe and wonder. "
                   "No text, no letters, no logos, no people's faces."),
    "intro_tv": ("Cinematic macro shot at night in a cosy living room: a modern TV on a wooden stand comes alive, its "
                 "dark screen fills with swirling golden starlight that bursts outward into the room, illuminating "
                 "toys and books, magical particles drifting, slow push-in, warm and wondrous. No text, no logos, "
                 "no people."),
    "outro_home": ("Warm cinematic animated shot, Pixar style: a tiny glowing miniature dollhouse on a table at night, "
                   "camera slowly pulls back as golden stars rise gently from its windows into a starry sky, cosy, "
                   "hopeful, peaceful ending. Leaves calm dark space in the upper middle. No text, no logos, no people."),
}
VEO_NEGATIVE = "text, letters, words, captions, logo, watermark, scary, horror, distorted faces, people"


_clients: dict = {}


def client(location: str = "global") -> genai.Client:
    """One client per location, kept alive: a throwaway client can be closed before its request is sent."""
    if location not in _clients:
        _clients[location] = genai.Client(vertexai=True, project=PROJECT, location=location)
    return _clients[location]


def tts(text: str, voice: str, style: str, model: str) -> bytes:
    resp = client().models.generate_content(
        model=model, contents=f"{style}: {text}",
        config=types.GenerateContentConfig(
            response_modalities=["AUDIO"], labels=LABELS,
            speech_config=types.SpeechConfig(voice_config=types.VoiceConfig(prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=voice))),
        ),
    )
    pcm = resp.candidates[0].content.parts[0].inline_data.data
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(24000); w.writeframes(pcm)
    return buf.getvalue()


def words(t: str) -> list:
    import re
    return re.findall(r"[a-z0-9]+", t.lower().replace("ai", "a i"))


def verified(wav: bytes, text: str) -> bool:
    """Transcribe the clip and accept it only if it says the script and nothing else (TTS can read its style cue aloud)."""
    import difflib
    heard = client().models.generate_content(model="gemini-2.5-flash", contents=[
        types.Part.from_bytes(data=wav, mime_type="audio/wav"), "Transcribe this speech exactly, word for word. Output only the words."]).text
    a, b = words(text), words(heard)
    ratio = difflib.SequenceMatcher(None, a, b).ratio()
    ok = ratio >= 0.86 and len(b) <= len(a) + 3
    print(f"  heard ({ratio:.2f}): {heard.strip()[:110]}", flush=True)
    return ok


def speak(text: str, voice: str, style: str, out: Path) -> None:
    for attempt in range(10):  # small per-minute TTS quota: wait it out; prefer the Pro voice, then Flash
        model = "gemini-2.5-pro-tts" if attempt < 6 else "gemini-2.5-flash-tts"
        try:
            wav = tts(text, voice, style, model)
            if verified(wav, text):
                out.write_bytes(wav)
                print("ok", out.name, model, flush=True)
                return
        except Exception as e:  # noqa: BLE001
            print("  retry", out.name, model, str(e)[:90], flush=True)
            time.sleep(15)
    print("FAIL", out.name)


def voice() -> None:
    out = HERE / "vo"
    out.mkdir(parents=True, exist_ok=True)
    for key, line in VO.items():
        if not (out / f"{key}.wav").exists():
            speak(line, NARRATOR, NARRATOR_STYLE, out / f"{key}.wav")


def kid() -> None:
    speak(KID_LINE, "Puck", KID_STYLE, HERE / "kid_voice.wav")


def music() -> None:
    out = HERE / "music"
    out.mkdir(parents=True, exist_ok=True)
    creds, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
    creds.refresh(google.auth.transport.requests.Request())
    url = f"https://us-central1-aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/us-central1/publishers/google/models/lyria-002:predict"
    for key, prompt in MUSIC.items():
        r = requests.post(url, headers={"Authorization": f"Bearer {creds.token}"}, timeout=300, json={
            "instances": [{"prompt": prompt, "negative_prompt": MUSIC_NEGATIVE}], "parameters": {"sample_count": 1}})
        r.raise_for_status()
        p = r.json()["predictions"][0]
        (out / f"{key}.wav").write_bytes(base64.b64decode(p.get("bytesBase64Encoded") or p["audioContent"]))
        print("ok", key, flush=True)


def veo() -> None:
    out = HERE / "veo"
    out.mkdir(parents=True, exist_ok=True)
    c = client("us-central1")
    pending = [k for k in VEO if not (out / f"{k}.mp4").exists()]
    ops = {k: c.models.generate_videos(
        model="veo-3.1-generate-001", prompt=p,
        config=types.GenerateVideosConfig(aspect_ratio="16:9", duration_seconds=8, number_of_videos=1, generate_audio=True,
                                          negative_prompt=VEO_NEGATIVE, resolution="1080p", labels=LABELS))
        for k, p in VEO.items() if k in pending}
    for k, op in ops.items():
        while not op.done:
            time.sleep(15)
            op = c.operations.get(op)
        if op.error:
            print("veo failed", k, op.error, flush=True)
            continue
        (out / f"{k}.mp4").write_bytes(op.response.generated_videos[0].video.video_bytes)
        print("ok", k, flush=True)


if __name__ == "__main__":
    {"voice": voice, "kid": kid, "music": music, "veo": veo}[sys.argv[1]]()
