"""Rosetta's intro and home music, and the parents' portraits, made with Google models on Vertex AI.

  python assets/intro/make_intro.py veo [takes]     8 s intro clips with sound (Veo)      -> assets/intro/takes/
  python assets/intro/make_intro.py lyria            intro sting + home theme (Lyria)      -> assets/intro/music/
  python assets/intro/make_intro.py avatars          Mom and Dad in the kids' avatar style -> assets/flow/avatars/
  python assets/intro/make_intro.py mix <take.mp4>   final intro.mp4 + music_home.ogg into the TV app (ffmpeg)
  python assets/intro/make_intro.py world [takes]   the Home dollhouse brought to life (Veo, image to video)
  python assets/intro/make_intro.py loop <take.mp4>  seamless home_world.mp4 loop into the TV app (ffmpeg)

Vertex AI only (project tesselle-demandfoundry, Application Default Credentials), never an API key.
The video has no text: the TV app draws the ROSETTA wordmark over its last seconds.
"""
from __future__ import annotations

import base64
import subprocess
import sys
import time
from pathlib import Path

import google.auth
import google.auth.transport.requests
import requests
from google import genai
from google.genai import types

PROJECT = "tesselle-demandfoundry"
ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "assets/intro"
LABELS = {"app": "rosetta", "purpose": "intro-media"}

VEO_PROMPT = (
    "Cinematic, magical, cosy night. Slow dolly-in through a miniature dollhouse living room at night, warm lamps, "
    "handcrafted stop-motion look, shallow depth of field. On a low table stands an ancient carved stone tablet with "
    "nine small empty star-shaped sockets. One by one the sockets ignite with soft golden starlight, tiny sparks "
    "drift upward, then a warm golden light sweeps across the room and blooms into a soft glow in the centre of the "
    "frame, leaving calm dark space in the middle. Colour palette: deep navy night, warm gold, cream. "
    "Sound: gentle magical chimes and a soft rising whoosh, warm and kid-friendly. "
    "No text, no letters, no words, no logos, no people, no watermark."
)
VEO_NEGATIVE = "text, letters, words, captions, logo, watermark, people, faces, scary, horror, dark creepy"

STING_PROMPT = (
    "A 10-second magical cinematic intro sting for a family TV app: warm orchestral strings and a soft synth pad "
    "swell from silence, celesta and harp sparkles rise, building to a bright, joyful golden chime and a warm "
    "sustained chord around 6 seconds, then a gentle shimmering tail. Kid-friendly, wondrous, premium, like a "
    "studio logo reveal. Instrumental."
)
HOME_PROMPT = (
    "Calm, cosy, loopable background music for a family TV home screen at night: soft marimba and celesta melody, "
    "warm felt piano, light pizzicato, gentle and playful, 90 bpm, quiet and unobtrusive, kid-friendly. Instrumental."
)
MUSIC_NEGATIVE = "vocals, singing, drums, heavy bass, distortion, dark, scary"

AVATAR_PROMPT = (
    "Create a new square portrait in exactly the same 3D animated style, lighting, framing, background and colour "
    "grading as these two reference portraits of the children Arjun and Meera: their {who}. A warm, friendly South "
    "Indian {desc}, smiling kindly at the camera, head and shoulders, same soft studio light and same plain "
    "background. One person only, no text."
)
AVATARS = {
    "mom": ("mother", "woman in her mid-thirties with long dark hair in a loose braid, small gold earrings, a soft teal kurta"),
    "dad": ("father", "man in his late thirties with short dark hair, a neat short beard, rectangular glasses, a navy polo shirt"),
}


def client(location: str = "global") -> genai.Client:
    return genai.Client(vertexai=True, project=PROJECT, location=location)


def veo(takes: int = 2) -> None:
    out = HERE / "takes"
    out.mkdir(parents=True, exist_ok=True)
    c = client("us-central1")
    for model in ("veo-3.1-generate-001", "veo-3.0-generate-001"):
        try:
            ops = [c.models.generate_videos(
                model=model, prompt=VEO_PROMPT,
                config=types.GenerateVideosConfig(aspect_ratio="16:9", duration_seconds=8, number_of_videos=1,
                                                  generate_audio=True, negative_prompt=VEO_NEGATIVE, resolution="1080p",
                                                  labels=LABELS))
                   for _ in range(takes)]
        except Exception as e:  # noqa: BLE001 - try the next Veo version
            print(f"{model}: {e}", flush=True)
            continue
        print(f"{model}: {len(ops)} takes started", flush=True)
        for i, op in enumerate(ops, 1):
            while not op.done:
                time.sleep(15)
                op = c.operations.get(op)
            if op.error:
                print(f"take {i} failed: {op.error}", flush=True)
                continue
            for j, v in enumerate(op.response.generated_videos):
                path = out / f"take_{int(time.time())}_{i}{j}.mp4"
                path.write_bytes(v.video.video_bytes)
                print("saved", path, path.stat().st_size // 1024, "KB", flush=True)
        return


def lyria() -> None:
    out = HERE / "music"
    out.mkdir(parents=True, exist_ok=True)
    creds, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
    creds.refresh(google.auth.transport.requests.Request())
    url = (f"https://us-central1-aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/us-central1/"
           "publishers/google/models/lyria-002:predict")
    for name, prompt in (("sting", STING_PROMPT), ("home", HOME_PROMPT)):
        r = requests.post(url, headers={"Authorization": f"Bearer {creds.token}"}, timeout=180, json={
            "instances": [{"prompt": prompt, "negative_prompt": MUSIC_NEGATIVE}], "parameters": {"sample_count": 2}})
        r.raise_for_status()
        for i, p in enumerate(r.json()["predictions"]):
            path = out / f"{name}_{i}.wav"
            path.write_bytes(base64.b64decode(p["bytesBase64Encoded"] if "bytesBase64Encoded" in p else p["audioContent"]))
            print("saved", path, flush=True)


def avatars() -> None:
    c = client("global")
    refs = [types.Part.from_bytes(data=(ROOT / f"assets/flow/avatars/{k}.webp").read_bytes(), mime_type="image/webp")
            for k in ("arjun", "meera")]
    out = HERE / "avatars"
    out.mkdir(parents=True, exist_ok=True)
    for key, (who, desc) in AVATARS.items():
        for take in range(2):
            r = _image(c, [*refs, AVATAR_PROMPT.format(who=who, desc=desc)])
            img = next(p.inline_data for cand in r.candidates for p in cand.content.parts if p.inline_data)
            path = out / f"{key}_{take}.png"
            path.write_bytes(img.data)
            print("saved", path, flush=True)


def _image(c: genai.Client, contents: list):
    """Gemini image models in order of preference; wait out per-minute quota (429) and try the next one."""
    last = None
    for attempt in range(6):
        for model in ("gemini-3.1-flash-image", "gemini-2.5-flash-image", "gemini-3-pro-image"):
            try:
                return c.models.generate_content(
                    model=model, contents=contents,
                    config=types.GenerateContentConfig(response_modalities=["IMAGE"], labels=LABELS,
                                                       image_config=types.ImageConfig(aspect_ratio="1:1")))
            except Exception as e:  # noqa: BLE001
                last = e
                print(f"{model}: {str(e)[:90]}", flush=True)
        time.sleep(20)
    raise last


WORLD_PROMPT = (
    "The camera is completely still: a locked-off shot, no zoom, no pan, no dolly. This handcrafted miniature dollhouse "
    "at night comes gently alive: warm lamps glow and softly flicker, curtains sway slightly, a few fireflies drift "
    "outside, stars twinkle in the night sky, the television in the living room glows with soft changing colours, "
    "tiny dust motes float in the lamplight. Nothing else moves, no people, no new objects. Stop-motion miniature look, "
    "cosy and magical. No text."
)
WORLD_NEGATIVE = "camera movement, zoom, pan, people, hands, text, letters, watermark, new objects, morphing"


def world(takes: int = 2) -> None:
    """Image-to-video from the Home art, so every room stays exactly where the TV's clickable rooms expect it."""
    from PIL import Image
    import io
    out = HERE / "takes"
    out.mkdir(parents=True, exist_ok=True)
    src = Image.open(ROOT / "tv-app/app/src/main/assets/rosetta/home_base.webp").convert("RGB")
    w, h = src.size
    canvas = Image.new("RGB", (w, round(w * 9 / 16)), (8, 12, 26))  # pad to exact 16:9; the TV crops it back
    canvas.paste(src, (0, (canvas.height - h) // 2))
    buf = io.BytesIO()
    canvas.save(buf, "PNG")
    c = client("us-central1")
    ops = [c.models.generate_videos(
        model="veo-3.1-generate-001", prompt=WORLD_PROMPT, image=types.Image(image_bytes=buf.getvalue(), mime_type="image/png"),
        config=types.GenerateVideosConfig(aspect_ratio="16:9", duration_seconds=8, number_of_videos=1, generate_audio=False,
                                          negative_prompt=WORLD_NEGATIVE, resolution="1080p", labels=LABELS))
           for _ in range(takes)]
    for i, op in enumerate(ops, 1):
        while not op.done:
            time.sleep(15)
            op = c.operations.get(op)
        if op.error:
            print(f"world take {i} failed: {op.error}", flush=True)
            continue
        for j, v in enumerate(op.response.generated_videos):
            path = out / f"world_{int(time.time())}_{i}{j}.mp4"
            path.write_bytes(v.video.video_bytes)
            print("saved", path, path.stat().st_size // 1024, "KB", flush=True)


def loop(take: str) -> None:
    """A seamless loop: the clip forward then backward (Veo drifts the camera slightly), scaled for the TV, no sound."""
    raw = ROOT / "tv-app/app/src/main/res/raw"
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", take,
        "-filter_complex",
        "[0:v]scale=1920:-2:flags=lanczos,fps=24,split[a][b];[b]reverse[r];[a][r]concat=n=2:v=1:a=0,format=yuv420p[v]",
        "-map", "[v]", "-an", "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-movflags", "+faststart",
        str(raw / "home_world.mp4")], check=True)
    print("home_world.mp4", (raw / "home_world.mp4").stat().st_size // 1024, "KB")


def mix(take: str, sting: str | None = None, home: str | None = None) -> None:
    """intro.mp4: the Veo clip with its own sound under the Lyria sting; music_home.ogg: a quiet seamless loop."""
    raw = ROOT / "tv-app/app/src/main/res/raw"
    sting = sting or str(HERE / "music/sting_0.wav")
    home = home or str(HERE / "music/home_0.wav")
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", take, "-i", sting,
        "-filter_complex",
        "[0:a]volume=0.45[va];[1:a]atrim=0:8.6,afade=t=out:st=7.4:d=1.2,volume=0.95[sa];"
        "[va][sa]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95[a];"
        "[0:v]scale=1280:-2,fade=t=in:st=0:d=0.6,fade=t=out:st=7.5:d=0.5[v]",
        "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "slow", "-crf", "23", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", str(raw / "intro.mp4")], check=True)
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", home,
        "-af", "atrim=0:28,afade=t=in:st=0:d=1.5,afade=t=out:st=26:d=2,loudnorm=I=-22:TP=-2,aformat=channel_layouts=stereo",
        "-c:a", "libvorbis", "-q:a", "3", str(raw / "music_home.ogg")], check=True)
    for f in ("intro.mp4", "music_home.ogg"):
        print(f, (raw / f).stat().st_size // 1024, "KB")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "veo":
        veo(int(sys.argv[2]) if len(sys.argv) > 2 else 2)
    elif cmd == "lyria":
        lyria()
    elif cmd == "avatars":
        avatars()
    elif cmd == "mix":
        mix(*sys.argv[2:])
    elif cmd == "world":
        world(int(sys.argv[2]) if len(sys.argv) > 2 else 2)
    elif cmd == "loop":
        loop(sys.argv[2])
