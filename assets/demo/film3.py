"""Third filming pass (cut 3): smooth footage, rewards for both children, and the pocket-money story.

  python assets/demo/film3.py <shot> ...     ->  assets/demo/footage/<shot>.webm

adb screenrecord encodes inside the emulator and only managed 2-5 frames a second, so cut 2 stuttered. This pass uses
the emulator's own recorder (`adb emu screenrecord`), which captures on the host GPU at a steady 30 fps. Run the
emulator with 8 cores and 6 GB so the app itself draws at 30 fps too:

  emulator -avd FireTV_FireOS16 -gpu host -cores 8 -memory 6144

The phone parts are played through the live API (film.py) or recorded from the real phone pages (phone.py).
"""
import shutil
import sys
import time
from pathlib import Path

import requests

import film
import film2
from film import API, PIN, ROOT, adb, jpg, key, quest, upload, wait_stage
from film2 import home, open_quest

TMP = Path("E:/Temp/rec")


class HostRec:
    """The emulator's own screen recorder: 30 fps from the host GPU. Records for at least `seconds`."""
    def __init__(self, name: str, seconds: float):
        self.name, self.seconds = name, seconds

    def __enter__(self):
        TMP.mkdir(parents=True, exist_ok=True)
        self.tmp = TMP / f"{self.name}.webm"
        self.tmp.unlink(missing_ok=True)
        adb("emu", "screenrecord", "start", "--fps", "30", "--bit-rate", "16000000", "--time-limit", "180", self.tmp.as_posix())
        self.t0 = time.time()
        time.sleep(0.4)
        return self

    def __exit__(self, *exc):
        time.sleep(max(0.0, self.seconds - (time.time() - self.t0)))
        adb("emu", "screenrecord", "stop")
        time.sleep(3)
        film.OUT.mkdir(parents=True, exist_ok=True)
        shutil.copy(self.tmp, film.OUT / f"{self.name}.webm")
        print("filmed", self.name, flush=True)


film2.Rec = HostRec   # every film2 shot now records with the host recorder


def snap(name: str):
    """A still of the TV right now (to check a shot before moving on)."""
    import subprocess
    TMP.mkdir(parents=True, exist_ok=True)
    (TMP / f"{name}.png").write_bytes(subprocess.run(film.ADB + ["exec-out", "screencap", "-p"], capture_output=True).stdout)


def setup():
    """The DEMO family from film2, plus pocket money for every coin earned and a wish Arjun can reach today."""
    film2.setup()
    requests.post(f"{API}/settings", headers=PIN, timeout=30, json={"moneyPerCoin": 0.2})
    requests.post(f"{API}/wishlist", headers=PIN, timeout=30, json={"kidId": "arjun", "title": "Cricket ball", "price": 10, "link": "https://www.amazon.in/"})
    print("pocket money on")


def x_launch():
    """The app starting: recorded from the first frame of Rosetta's intro (the edit trims the launcher)."""
    adb("shell", "am", "force-stop", "app.rosetta.tv")
    time.sleep(1)
    with HostRec("x_launch", 24):
        adb("shell", "am", "start", "-n", "app.rosetta.tv/.MainActivity")
        time.sleep(17)
        for _ in range(3):
            key("DPAD_DOWN", 2.0)


def x_meera_reward():
    """Meera's homework is done: claim her reward (XP, coins, pocket money, a star)."""
    with HostRec("x_meera_reward", 20):
        time.sleep(1)
        key("DPAD_CENTER", 9)
        key("DPAD_CENTER", 9)


def x_arjun_reward():
    with HostRec("x_arjun_reward", 20):
        time.sleep(1)
        key("DPAD_CENTER", 9)
        key("DPAD_CENTER", 9)


def screen(name: str, shot: str, seconds: float = 10, settle: float = 12):
    """Open one screen directly (debug build) and film it once it has settled."""
    adb("shell", "am", "force-stop", "app.rosetta.arena")
    adb("shell", "am", "force-stop", "app.rosetta.tv")
    adb("shell", "am", "start", "-n", "app.rosetta.tv/.MainActivity", "--es", "screen", name)
    time.sleep(settle)
    with HostRec(shot, seconds):
        pass


def x_screens():
    for name, shot in (("arjun", "x_arjun"), ("meera", "x_meera"), ("goal", "x_goal"), ("squad", "x_squad"), ("streaks", "x_streaks"),
                       ("movie", "x_movie"), ("parents", "x_parents")):
        screen(name, shot)


def x_treasure_live():
    """Treasure on the TV while Arjun asks for his wish and Mom approves it from her phone (phone.py, same moment)."""
    screen("treasure", "x_treasure_before", 8)
    with HostRec("x_treasure_live", 30):
        pass


SHOTS = {f.__name__: f for f in (setup, x_launch, x_meera_reward, x_arjun_reward, x_screens, x_treasure_live)}
SHOTS = {**film2.SHOTS, **SHOTS}

if __name__ == "__main__":
    for name in sys.argv[1:]:
        if name.startswith("snap:"):
            snap(name[5:])
        else:
            SHOTS[name]()
