"""Film the demo on the Fire TV emulator while playing the family's part through the real live API.

  python assets/demo/film.py <shot> [<shot> ...]     ->  assets/demo/footage/<shot>.mp4

The TV app is the DEMO build (-ProsettaCode=DEMO). The "phone" here is this script: it uploads Arjun's real worksheet
photo, a wrong photo, his voice note and Mom's TV unlock through the same endpoints the phone pages use, so every AI
result on screen is the live agent crew's real output.
"""
import io
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

import requests
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "assets/demo/footage"
API = "https://jlgtsqwtzl.execute-api.us-east-1.amazonaws.com/household/DEMO"
PIN = {"x-parent-pin": "2468"}  # the public demo family's PIN (README)
ADB = [os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"), "-s", "emulator-5554"]


def adb(*args, check=False):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, check=check, env={**os.environ, "MSYS_NO_PATHCONV": "1"})


def key(name: str, wait: float = 0.9, times: int = 1):
    for _ in range(times):
        adb("shell", "input", "keyevent", f"KEYCODE_{name}")
        time.sleep(wait)


def household():
    return requests.get(API, timeout=20).json()


def quest(title: str, kid: str):
    return next(q for q in household()["quests"] if q["title"] == title and q["kidId"] == kid)


def upload(qid: str, data: bytes, content_type: str = "image/jpeg", phase: str | None = None):
    body = {"questId": qid}
    if phase:
        body.update(phase=phase, contentType=content_type)
    r = requests.post(f"{API}/upload", json=body, timeout=20).json()
    requests.put(r["uploadUrl"], data=data, headers={"content-type": r.get("contentType", content_type)}, timeout=60).raise_for_status()


def jpg(path: Path, max_side: int = 1600) -> bytes:
    im = Image.open(path).convert("RGB")
    im.thumbnail((max_side, max_side))
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=88)
    return buf.getvalue()


def wait_stage(title: str, kid: str, stages: set, timeout: float = 90):
    t0 = time.time()
    while time.time() - t0 < timeout:
        q = quest(title, kid)
        if q["stage"] in stages:
            return q
        time.sleep(2)
    raise TimeoutError(f"{title} never reached {stages}")


class Rec:
    """adb screenrecord for one shot (no audio; the edit adds narration and music)."""
    def __init__(self, name: str, seconds: int):
        self.name, self.seconds = name, seconds

    def __enter__(self):
        self.t = threading.Thread(target=adb, args=("shell", "screenrecord", "--bit-rate", "20000000", "--time-limit", str(self.seconds), f"/sdcard/{self.name}.mp4"))
        self.t.start()
        time.sleep(1.2)
        return self

    def __exit__(self, *exc):
        self.t.join()
        time.sleep(1)
        OUT.mkdir(parents=True, exist_ok=True)
        adb("pull", f"/sdcard/{self.name}.mp4", str(OUT / f"{self.name}.mp4"))
        adb("shell", "rm", f"/sdcard/{self.name}.mp4")
        print("filmed", self.name, flush=True)


def restart():
    adb("shell", "am", "force-stop", "app.rosetta.tv")
    adb("shell", "am", "start", "-n", "app.rosetta.tv/.MainActivity")


# ------------------------------------------------------------------------------------------------- shots
def s01_launch():
    """Launch: the Veo intro, the wordmark, then Home comes alive."""
    adb("shell", "am", "force-stop", "app.rosetta.tv")
    with Rec("s01_launch", 26):
        adb("shell", "am", "start", "-n", "app.rosetta.tv/.MainActivity")
        time.sleep(24)


def s02_home():
    """Home: the camera glides room to room as quests are selected; TV time is locked."""
    with Rec("s02_home", 22):
        time.sleep(2)
        for _ in range(6):
            key("DPAD_DOWN", 2.2)
        key("DPAD_UP", 0.8, 6)
        time.sleep(1)


def s03_homework():
    """Arjun's worksheet: snapped, marked by the AI Tutor on his handwriting, then practice."""
    q = quest("Fractions homework", "arjun")
    with Rec("s03_homework", 75):
        time.sleep(1.5)
        key("DPAD_CENTER", 4)          # open the quest: QR for the phone
        upload(q["id"], jpg(ROOT / "assets/homework/arjun_fractions.jpg"))
        wait_stage("Fractions homework", "arjun", {"Checked", "Practising", "Done"}, 60)
        time.sleep(10)                 # the marks land one by one; the step list fills in
        for _ in range(5):             # practice with the remote
            key("DPAD_CENTER", 3.2)
        time.sleep(3)


def s04_reward():
    """Claim: XP, coins, a 3D star, then the star lands on the Family Goal."""
    with Rec("s04_reward", 24):
        key("DPAD_CENTER", 9)          # claim rewards → the reveal
        key("DPAD_CENTER", 9)          # add the star → Family Goal (3D star)
        key("BACK", 3)


def s05_honest():
    """Meera sends the wrong photo: Rosetta says what it actually sees."""
    with Rec("s05_honest", 50):
        time.sleep(1.5)
        key("DPAD_CENTER", 4)          # first card is now Meera's homework
        q = quest("Adding and taking away", "meera")
        upload(q["id"], jpg(ROOT / "assets/flow/production/room_before.webp"))
        t0 = time.time()
        while time.time() - t0 < 40:
            if quest("Adding and taking away", "meera").get("whatISee"):
                break
            time.sleep(2)
        time.sleep(8)
        key("BACK", 2)


def s06_voice():
    """Arjun's voice note for Read 10 pages: Gemini listens, sums it up, keeps it as a memory."""
    with Rec("s06_voice", 60):
        time.sleep(1.5)
        key("DPAD_DOWN", 0.9, 2)       # Read 10 pages (Arjun) after the two homework cards… see film notes
        key("DPAD_CENTER", 5)
        q = quest("Read 10 pages", "arjun")
        upload(q["id"], (ROOT / "assets/demo/kid_voice.wav").read_bytes(), "audio/wav", phase="voice")
        wait_stage("Read 10 pages", "arjun", {"Done", "Checking"}, 45)
        time.sleep(7)
        key("DPAD_CENTER", 9)          # claim: coins and ₹5 pocket money
        key("DPAD_CENTER", 6)
        key("BACK", 2)


def s07_unlock():
    """Mom unlocks TV time from her phone: the portal bursts open on the TV."""
    requests.post(f"{API}/tvtime/lock", headers=PIN, timeout=20)  # start from locked, so the burst is on camera
    restart()
    time.sleep(6)
    key("DPAD_CENTER", 6)              # skip the intro, settle on Home
    with Rec("s07_unlock", 22):
        time.sleep(4)
        requests.post(f"{API}/tvtime/grant", headers=PIN, timeout=20)
        time.sleep(16)


def s08_quiz():
    """Family Quiz Night: the loading screen, then the 3D game."""
    with Rec("s08_quiz", 70):
        time.sleep(1)
        key("DPAD_DOWN", 0.3, 14)      # past the quest board onto the bottom row…
        key("DPAD_LEFT", 0.3, 6)       # …and along it to Quiz Night
        key("DPAD_CENTER", 32)         # the AI writes 8 questions; the 3D game opens
        for _ in range(3):
            key("DPAD_CENTER", 7)
        time.sleep(2)


def s09_progress():
    """Arjun's Progress and Coach's notes; then the Parents screen."""
    with Rec("s09_progress", 30):
        time.sleep(1)
        key("DPAD_UP", 1.2)
        key("DPAD_CENTER", 9)
        key("BACK", 2.5)
        key("DPAD_UP", 1)
        key("DPAD_RIGHT", 0.8, 2)
        key("DPAD_CENTER", 9)
        key("BACK", 2)


def s10_treasure():
    """Treasure: the 3D chest, pocket money and the wish list."""
    with Rec("s10_treasure", 18):
        time.sleep(1)
        key("DPAD_DOWN", 0.3, 14)
        key("DPAD_LEFT", 0.3, 6)
        key("DPAD_RIGHT", 0.8, 2)
        key("DPAD_CENTER", 12)
        key("BACK", 2)


SHOTS = {f.__name__: f for f in (s01_launch, s02_home, s03_homework, s04_reward, s05_honest, s06_voice, s07_unlock, s08_quiz, s09_progress, s10_treasure)}

if __name__ == "__main__":
    for name in sys.argv[1:]:
        SHOTS[name]()
