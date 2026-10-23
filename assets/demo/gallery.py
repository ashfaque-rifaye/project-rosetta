"""Devpost image gallery: 15 captioned 3:2 images (1800 x 1200), numbered in story order.

  python assets/demo/gallery.py      ->  submission/gallery/NN-name.png

Stills come straight from the filmed footage (film3.py) and the architecture card (arch.py).
"""
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

from edit import CREAM, GOLD, MIST, SANS, SANS_SB, SERIF, font

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
FT = HERE / "footage"
OUT = ROOT / "submission" / "gallery"
GW, GH = 1800, 1200

# (file name, source, time, eyebrow, title, subtitle)
SHOTS = [
    ("01-rosetta-on-the-family-tv", "build/video_v3.mp4", 29.0, "Project Rosetta · Fire TV", "The TV that works for the family",
     "Homework, chores and daily habits earn TV time and real pocket money"),
    ("02-home-living-house-quest-board", "t13_home", 3.0, "Home", "A living house, full of quests",
     "The camera glides to each child's room; TV time stays locked until the must-dos are done"),
    ("03-ai-tutor-marks-handwritten-workbook", "t02_meera_hw", 31.0, "✦ AI Tutor", "Marked on her own handwriting",
     "Every answer checked, maths double-checked in code, a hint for each mistake, never the answer"),
    ("04-practice-reasoning-questions", "t02_meera_hw", 42.5, "✦ Practice", "Questions that make them think",
     "A short round on exactly the skills that slipped, written for the child's grade"),
    ("05-coding-class-traced-line-by-line", "x_arjun_practice", 24.0, "✦ Coding class", "Programs traced line by line",
     "Arjun's worksheet marked 4 of 6; practice built on his own mistakes: 5 of 5"),
    ("06-honest-ai-wrong-photo", "t05_honest", 20.0, "✦ Honest AI", "Wrong photo? It says what it sees",
     "Every check reports what it actually saw: nothing on screen is made up"),
    ("07-room-check-lost-things", "t06_room", 42.0, "✦ Room check", "Lost Things, found and brought home",
     "Out-of-place items become collectibles; the after photo is checked again"),
    ("08-voice-note-daily-habit", "t07_voice", 40.0, "✦ Daily habits", "A voice note, kept as a memory",
     "Kids check in with a tick, a note, a photo or a voice note; parents replay it later"),
    ("09-rewards-for-every-child", "x_meera_reward", 8.0, "Rewards", "Every finished task pays off",
     "XP, coins, a stamp and a star toward Movie Night, for each child"),
    ("10-pocket-money-and-wish-list", "x_treasure_live", 40.0, "Pocket money", "Earned, saved, and spent with a yes",
     "Parents set what tasks pay; kids ask for a wish from their phone; the TV updates live"),
    ("11-tv-time-unlocked", "t08_unlock", 10.0, "TV time", "Unlocked when the work is done",
     "Or by a parent with one tap; Rosetta opens Prime Video or YouTube and counts the minutes"),
    ("12-family-quiz-night-unity-3d", "t09_quiz", 46.0, "Family Quiz Night", "A 3D game for the whole family",
     "Unity 6 on Fire OS: reasoning questions written for each child's class"),
    ("13-progress-and-coach-notes", "x_arjun", 5.0, "✦ Progress", "Coach's notes from real activity",
     "Habit calendar, homework accuracy, pocket money and stars, per child"),
    ("14-parents-scan-the-tv", "x_parents", 5.0, "For parents", "Rosetta lives on your phone too",
     "Scan the QR on the TV: alerts, approvals, daily tasks, pocket money, TV time"),
    ("15-architecture-serverless-on-aws", None, 0.0, "", "", ""),
]


def frame(src: str, t: float) -> Image.Image:
    p = HERE / src if "/" in src else (FT / f"{src}.webm")
    raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{t:.2f}", "-i", str(p), "-frames:v", "1", "-vf", "scale=1920:1080",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
    return Image.frombytes("RGB", (1920, 1080), raw)


def card(shot: Image.Image, eyebrow: str, title: str, sub: str) -> Image.Image:
    img = Image.new("RGB", (GW, GH), (9, 14, 34))
    glow = Image.new("RGB", (GW, GH), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((GW * 0.5, -GH * 0.4, GW * 1.3, GH * 0.5), fill=(70, 50, 20))
    gd.ellipse((-GW * 0.3, GH * 0.5, GW * 0.5, GH * 1.4), fill=(20, 35, 90))
    img = Image.blend(img, glow.filter(ImageFilter.GaussianBlur(180)), 0.55)
    d = ImageDraw.Draw(img)
    d.text((60, 44), eyebrow.replace("✦", "").strip().upper(), font=font(SANS_SB, 26), fill=GOLD)
    d.text((60, 80), title, font=font(SERIF, 62), fill=CREAM)
    d.text((60, 160), sub, font=font(SANS, 28), fill=MIST)
    w = GW - 120
    h = round(w * 9 / 16)
    pic = shot.resize((w, h), Image.LANCZOS)
    y = GH - h - 50
    sh = Image.new("L", (GW, GH), 0)
    ImageDraw.Draw(sh).rounded_rectangle((60 + 8, y + 18, 60 + w + 8, y + h + 18), radius=28, fill=170)
    img.paste((0, 0, 0), (0, 0), sh.filter(ImageFilter.GaussianBlur(24)))
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=24, fill=255)
    img.paste(pic, (60, y), m)
    ImageDraw.Draw(img).rounded_rectangle((60, y, 60 + w, y + h), radius=24, outline=(255, 255, 255), width=2)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, src, t, eb, title, sub in SHOTS:
        if src is None:   # the architecture card, letterboxed to 3:2
            a = Image.open(HERE / "build" / "arch_3.png").convert("RGB")
            img = Image.new("RGB", (GW, GH), (9, 14, 34))
            img.paste(a.resize((GW, round(GW * 9 / 16)), Image.LANCZOS), (0, (GH - round(GW * 9 / 16)) // 2))
        else:
            img = card(frame(src, t), eb, title, sub)
        img.save(OUT / f"{name}.png", optimize=True)
        print(name)


if __name__ == "__main__":
    main()
