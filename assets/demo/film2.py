"""Second filming pass (cut 2): both children, the coding class, the room scan and more of the app.

  python assets/demo/film2.py <shot> ...     ->  assets/demo/footage/<shot>.mp4

Same rules as film.py: the TV is the DEMO build; this script plays the phones through the live API, so every AI
result on screen is real. Quests are opened by working out their place on the Quest Board from the live household.
"""
import sys
import time

import requests

from film import API, PIN, ROOT, Rec, adb, household, jpg, key, quest, restart, upload, wait_stage

ORDER = ("Done",)


def board(h):
    """The Quest Board's order on the TV (HomeScreen): not done first, must-do first, then non-habits, then by child."""
    qs = h["quests"]
    return sorted(qs, key=lambda q: (q["stage"] == "Done", not q.get("mustDo"), q.get("kind") == "habit", q["kidId"]))


def on_screen() -> bool:
    out = adb("shell", "dumpsys", "activity", "activities").stdout
    return any("ResumedActivity" in ln and "app.rosetta.tv" in ln for ln in out.splitlines())


def home(skip_intro=False):
    """Fresh start, then let the 8 s intro end by itself (a key press could land on Home and open a quest)."""
    adb("shell", "am", "force-stop", "app.rosetta.arena")  # the 3D quiz game can still be in front
    restart()
    for _ in range(30):
        if on_screen():
            break
        time.sleep(1)
    time.sleep(14)
    if not on_screen():
        raise RuntimeError("Rosetta is not on screen")


def setup():
    """A fresh DEMO family with profiles and wishes (the live reset keeps nothing else)."""
    h = {"x-parent-pin": "2468"}
    requests.post(f"{API}/reset", headers=h, timeout=30)
    requests.post(f"{API}/kids/arjun/profile", headers=h, timeout=30, json={"grade": "Grade 3", "subjects": ["Maths", "English", "Science", "Coding"],
        "upcomingTests": [{"subject": "Coding", "topics": "loops and variables", "date": "2026-10-27"}, {"subject": "Maths", "topics": "fractions", "date": "2026-10-29"}]})
    requests.post(f"{API}/kids/meera/profile", headers=h, timeout=30, json={"grade": "Grade 1", "subjects": ["Maths", "English"],
        "upcomingTests": [{"subject": "Maths", "topics": "adding and taking away", "date": "2026-10-28"}]})
    requests.post(f"{API}/wishlist", headers=h, timeout=30, json={"kidId": "arjun", "title": "LEGO Classic box", "price": 40, "link": "https://www.amazon.in/"})
    requests.post(f"{API}/wishlist", headers=h, timeout=30, json={"kidId": "meera", "title": "Art set with 48 colours", "price": 25, "link": "https://www.amazon.in/"})
    print("DEMO family ready")


def open_quest(title, kid, wait=4.0):
    idx = next(i for i, q in enumerate(board(household())) if q["title"] == title and q["kidId"] == kid)
    key("DPAD_DOWN", 0.6, idx)
    time.sleep(0.8)
    key("DPAD_CENTER", wait)


def t01_launch():
    adb("shell", "am", "force-stop", "app.rosetta.tv")
    with Rec("t01_launch", 28):
        adb("shell", "am", "start", "-n", "app.rosetta.tv/.MainActivity")
        time.sleep(17)
        for _ in range(4):
            key("DPAD_DOWN", 2.0)


def t02_meera_homework():
    """Meera's real workbook page (the photo you shared), marked by the AI Tutor."""
    home()
    with Rec("t02_meera_hw", 70):
        open_quest("Adding and taking away", "meera")
        upload(quest("Adding and taking away", "meera")["id"], jpg(ROOT / "assets/demo/user_math_homework.webp"))
        wait_stage("Adding and taking away", "meera", {"Checked", "Practising", "Done"}, 70)
        time.sleep(12)
        for _ in range(3):
            key("DPAD_CENTER", 3.0)


def t03_arjun_coding():
    """Arjun's coding class worksheet: the AI Tutor traces each program."""
    home()
    with Rec("t03_arjun_code", 75):
        open_quest("Coding: loops and variables", "arjun")
        upload(quest("Coding: loops and variables", "arjun")["id"], jpg(ROOT / "assets/demo/coding_homework.jpg"))
        wait_stage("Coding: loops and variables", "arjun", {"Checked", "Practising", "Done"}, 70)
        time.sleep(12)
        for _ in range(4):
            key("DPAD_CENTER", 3.2)


def t04_reward():
    """Claim Arjun's coding reward: XP, 3D coin, a star onto the Family Goal."""
    with Rec("t04_reward", 22):
        time.sleep(1)
        key("DPAD_CENTER", 9)
        key("DPAD_CENTER", 9)


def t05_honest():
    """Arjun sends the wrong photo for his fractions homework."""
    home()
    with Rec("t05_honest", 40):
        open_quest("Fractions homework", "arjun")
        upload(quest("Fractions homework", "arjun")["id"], jpg(ROOT / "assets/flow/production/room_before.webp"))
        t0 = time.time()
        while time.time() - t0 < 35 and not quest("Fractions homework", "arjun").get("whatISee"):
            time.sleep(2)
        time.sleep(8)


def t06_room():
    """Meera's room: Rosetta spots what's out of place; she tidies; the after photo is checked."""
    home()
    with Rec("t06_room", 80):
        open_quest("Tidy your room", "meera")
        qid = quest("Tidy your room", "meera")["id"]
        upload(qid, jpg(ROOT / "assets/flow/production/room_before.webp"))
        wait_stage("Tidy your room", "meera", {"Found", "AwaitingAfter", "Done"}, 60)
        time.sleep(9)
        upload(qid, jpg(ROOT / "assets/flow/production/room_after_done.webp"))
        wait_stage("Tidy your room", "meera", {"Done", "Partial", "Checking"}, 60)
        time.sleep(8)


def t07_voice():
    """Arjun's voice note for Read 10 pages."""
    home()
    with Rec("t07_voice", 50):
        open_quest("Read 10 pages", "arjun", 5)
        upload(quest("Read 10 pages", "arjun")["id"], (ROOT / "assets/demo/kid_voice.wav").read_bytes(), "audio/wav", phase="voice")
        wait_stage("Read 10 pages", "arjun", {"Done", "Checking"}, 45)
        time.sleep(7)
        key("DPAD_CENTER", 9)          # claim: coins and ₹5


def t08_unlock():
    requests.post(f"{API}/tvtime/lock", headers=PIN, timeout=20)
    home()
    with Rec("t08_unlock", 20):
        time.sleep(4)
        requests.post(f"{API}/tvtime/grant", headers=PIN, timeout=20)
        time.sleep(14)


def t09_quiz():
    home()
    with Rec("t09_quiz", 80):
        key("DPAD_DOWN", 0.3, 16)
        key("DPAD_LEFT", 0.3, 6)
        key("DPAD_CENTER", 34)
        for _ in range(4):
            key("DPAD_CENTER", 7.5)


def t10_kids():
    """Both children: Arjun's Progress, then Meera's."""
    home()
    key("DPAD_UP", 1.5)
    with Rec("t10_kids", 26):
        key("DPAD_CENTER", 10)
        key("DPAD_RIGHT", 1.5)
        key("DPAD_CENTER", 10)


def t11_places():
    """Treasure, Squad, Streaks and Family Goal, one after another."""
    home()
    with Rec("t11_places", 46):
        for right in (2, 3, 4, 1):
            key("DPAD_DOWN", 0.3, 16)
            key("DPAD_LEFT", 0.3, 6)
            key("DPAD_RIGHT", 0.6, right)
            key("DPAD_CENTER", 8.5)
            key("BACK", 2)


def t13_home():
    """Home, slowly: the camera glides from room to room as quests are selected."""
    home()
    with Rec("t13_home", 24):
        time.sleep(2)
        for _ in range(8):
            key("DPAD_DOWN", 2.4)


def t12_parents():
    home()
    key("DPAD_UP", 1.2)
    key("DPAD_RIGHT", 0.8, 2)
    with Rec("t12_parents", 10):
        key("DPAD_CENTER", 8)


SHOTS = {f.__name__: f for f in (setup, t01_launch, t02_meera_homework, t03_arjun_coding, t04_reward, t05_honest, t06_room, t07_voice,
                                 t08_unlock, t09_quiz, t10_kids, t11_places, t12_parents, t13_home)}

def u_shots():
    """Short separate shots (one per screen), each from a settled Home."""
    def settle():
        home()
        time.sleep(3)
    settle(); key("DPAD_UP", 1.5)
    with Rec("u_arjun", 11):
        key("DPAD_CENTER", 9.5)
    key("DPAD_RIGHT", 1.5)
    with Rec("u_meera", 10):
        key("DPAD_CENTER", 8.5)
    for name, right in (("u_goal", 1), ("u_treasure", 2), ("u_squad", 3), ("u_streaks", 4)):
        settle()
        key("DPAD_DOWN", 0.45, 15)
        key("DPAD_LEFT", 0.45, 6)
        key("DPAD_RIGHT", 0.7, right)
        with Rec(name, 9):
            key("DPAD_CENTER", 7.5)
    settle(); key("DPAD_UP", 1.2); key("DPAD_RIGHT", 0.8, 2)
    with Rec("u_parents", 8):
        key("DPAD_CENTER", 6.5)


SHOTS["u_shots"] = u_shots

if __name__ == "__main__":
    for name in sys.argv[1:]:
        SHOTS[name]()
