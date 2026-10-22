"""Bundle the TV's own Keeper lines in the Gemini voice (Vertex AI TTS, same voice as the live agents).

  python assets/voice/make_keeper_voice.py
Writes tv-app/app/src/main/assets/voice/<hash>.wav and index.json (exact line -> file), which KeeperVoice plays before
falling back to the device voice. Lines must match the TV code exactly: LocalRepository (offline household) and the
Family Stone lines in MainActivity. Needs Google ADC for project tesselle-demandfoundry. Re-running skips clips that exist.
"""
import hashlib
import json
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "agents"))
from rosetta_agents import gemini  # noqa: E402

OUT = ROOT / "tv-app/app/src/main/assets/voice"
KIDS = ["Arjun", "Meera"]
LINES = [
    # MainActivity: the Family Stone
    "The last glyph is carved. The Family Stone is complete. Movie night is yours.",
    "A new glyph is carved into the Family Stone.",
    # LocalRepository: the offline household
    *[f"Snap your homework, {k} 📸 Tutor will take a look." for k in KIDS],
    *[f"Snap your room, {k} 📸 Let's find the Lost Things." for k in KIDS],
    "Looking… 👀",
    *[f"5 Lost Things spotted! Bring them home, {k} 🏠" for k in KIDS],
    "Tutor is reading your work… ✏️",
    "Every answer right. Big brain energy 🧠🏆",
    "4 out of 6, Arjun! Two need another look. Quick practice round? 🎯",
    "4 out of 5, Meera! Two need another look. Quick practice round? 🎯",
    *[f"{right} of {total} in practice. That's how skills level up, {k} ⚡" for k in KIDS for total in (4, 5) for right in range(total + 1)],
    "Checking… ⚖️",
    "4 home! Only the jacket left. Hook it by the window 🧥",
    "ALL HOME. Absolute legend 🏆 Claim your glyph!",
]
EMOJI = re.compile(r"[\U0001F000-\U0001FAFF☀-➿️]")

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    index = {}
    for line in LINES:
        name = hashlib.sha1(line.encode()).hexdigest()[:16] + ".wav"
        index[line] = name
        if (OUT / name).exists():
            continue
        spoken = re.sub(r"\s+", " ", EMOJI.sub("", line)).strip()
        for attempt in range(6):  # Gemini TTS has a small per-minute quota: wait it out
            try:
                (OUT / name).write_bytes(gemini.speak(spoken, purpose="tv-bundled-voice"))
                break
            except Exception as e:
                print("  retry:", str(e)[:80])
                time.sleep(20)
        print("ok " if (OUT / name).exists() else "FAIL", line, flush=True)
    (OUT / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")
