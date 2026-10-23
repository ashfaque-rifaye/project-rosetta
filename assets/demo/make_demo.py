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
    '01_hook': 'Every evening, in millions of homes, the same two questions.',
    '02_q': 'Did you finish your homework? ... Can I watch TV now?',
    '03_what_if': 'What if the TV itself could help?',
    '04_title': 'This is Rosetta. The Fire TV app where the whole family levels up.',
    '05_home': "Tonight's quests live in a living dollhouse. Homework, chores, and the daily habits parents set. And TV time stays locked until the must-dos are done.",
    '06_homework': 'Meera snaps her workbook with any phone. Seconds later, the AI Tutor marks every answer on her own handwriting, double-checks the maths in code, and gives a hint, never the answer.',
    '06b_coding': "Arjun's coding class works the same way. The Tutor traces each little program, line by line.",
    '07_practice': 'Then practice that makes them think, not just add.',
    '08_honest': "And it's honest. Send the wrong photo, and Rosetta says what it actually sees.",
    '08b_room': "Tidy-ups too. Rosetta spots what's out of place, and checks the room again when it's done.",
    '09_voice': 'Daily habits check in with a tick, a note, or a voice note. Gemini listens, sums it up, and saves it as a memory for Mom and Dad.',
    '10_rewards': 'Every task pays off: XP, coins, real pocket money for the wish list, and a star toward Movie Night.',
    '11_parents': 'Parents get a ping, approve in one tap, and can even unlock TV time from their phone. The portal opens.',
    '11b_mom': "Mom sets the day from her phone: daily tasks, each child's class and next test, pocket money, and TV time.",
    '12_quiz': "Then it's Family Quiz Night: a 3D game with reasoning questions written for each child's class, from fractions to code.",
    '13_progress': "And Coach's notes turn real activity into a clear picture of each child.",
    '14_tech': 'Built natively for Fire TV, with an agent crew on AWS, and Gemini on Vertex AI for vision and voice.',
    '14b_more': "And there's more. A squad of Lost Things to collect, streaks and stamps, a treasure shop with real wish lists, and a Family Goal that unlocks Movie Night.",
    '15_outro': 'Rosetta. The TV that works for the family, not against it.',
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
    "intro_q1": ("Cinematic animated film shot, Pixar style, evening: a child's desk by a window at dusk, an unfinished "
                 "maths worksheet under a warm desk lamp, a pencil slowly rolls across it, a wall clock ticks toward seven, "
                 "slow dramatic push-in, moody golden and blue light, dust motes in the lamp beam, shallow depth of field. "
                 "No text on screen, no letters, no people."),
    "intro_q2": ("Cinematic animated film shot, Pixar style, evening living room: a small child's hand slowly reaches for a "
                 "TV remote on a sofa cushion, the big dark TV ahead reflects the room, suspenseful, shallow depth of field, "
                 "warm lamp light and cool blue shadows, slow push-in. No text, no faces, no logos."),
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


def room() -> None:
    """A photoreal living room with a big wall TV seen straight on: the demo plays inside its screen."""
    import io as _io
    from PIL import Image
    prompt = ("Photorealistic, cinematic photo of a cosy modern family living room at night. A large wall-mounted 75-inch "
              "flat TV seen perfectly straight-on and level, centred, filling about 72 percent of the image width, its screen "
              "completely black and switched off with a clean thin bezel. Warm lamp light, a plant, shelves with books and "
              "toys, the soft edge of a sofa blurred in the foreground at the bottom. Rich detail, shallow depth of field, "
              "16:9, no text, no logos, no people.")
    for model in ("gemini-3-pro-image", "gemini-3.1-flash-image", "gemini-2.5-flash-image"):
        for size in ("4K", "2K", None):
            try:
                cfg = types.ImageConfig(aspect_ratio="16:9", **({"image_size": size} if size else {}))
                r = client().models.generate_content(model=model, contents=[prompt], config=types.GenerateContentConfig(
                    response_modalities=["IMAGE"], image_config=cfg, labels=LABELS))
                for part in r.candidates[0].content.parts:
                    if part.inline_data:
                        im = Image.open(_io.BytesIO(part.inline_data.data)).convert("RGB")
                        im.save(HERE / "tv_room.png")
                        print("ok room", model, size, im.size, flush=True)
                        return
            except Exception as e:  # noqa: BLE001
                print("  room", model, size, str(e)[:100], flush=True)


if __name__ == "__main__":
    {"voice": voice, "kid": kid, "music": music, "veo": veo, "room": lambda: room()}[sys.argv[1]]()
