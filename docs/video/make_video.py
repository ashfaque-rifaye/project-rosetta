"""Assemble the demo video draft from the raw emulator recording + generated cards (no AWS needed).

  python docs/video/make_video.py   ->  docs/video/rosetta_demo_draft.mp4  (1080p, captions burned in, < 3 min)

Every product shot is the real app recorded on the Android TV (Fire OS 8 base) emulator.
Narration: captions only in this draft; add a voice track later (Polly or human).
"""
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
VID = ROOT / "docs" / "video"
RAW = VID / "rosetta_tv_flow_raw.mp4"
OUT = VID / "rosetta_demo_draft.mp4"
W, H = 1920, 1080
NIGHT, GOLD, CREAM, MIST = (10, 16, 32), (244, 197, 106), (246, 234, 215), (169, 180, 204)
SERIF = "C:/Windows/Fonts/georgia.ttf"
SANS = "C:/Windows/Fonts/segoeui.ttf"
SANS_B = "C:/Windows/Fonts/segoeuib.ttf"


def font(path, size):
    return ImageFont.truetype(path, size)


def card(path: Path, eyebrow: str, title: str, body: str = "", bg: Path | None = None, side: Path | None = None):
    im = Image.new("RGB", (W, H), NIGHT)
    if bg:
        b = Image.open(bg).convert("RGB").resize((W, H)).filter(ImageFilter.GaussianBlur(2))
        im = Image.blend(im, b, 0.45)
        shade = Image.new("L", (W, H))
        ImageDraw.Draw(shade).rectangle([0, 0, W, H], fill=0)
        for x in range(W):
            a = int(235 * max(0, min(1, (1250 - x) / 900)))
            ImageDraw.Draw(shade).line([(x, 0), (x, H)], fill=a)
        im = Image.composite(Image.new("RGB", (W, H), NIGHT), im, shade)
    if side:
        s = Image.open(side).convert("RGB")
        s.thumbnail((620, 980))
        im.paste(s, (W - s.width - 140, (H - s.height) // 2))
    d = ImageDraw.Draw(im)
    x, y = 140, 300
    d.text((x, y), eyebrow.upper(), font=font(SANS_B, 30), fill=GOLD)
    y += 60
    for line in title.split("\n"):
        d.text((x, y), line, font=font(SERIF, 86), fill=CREAM)
        y += 104
    y += 20
    for line in body.split("\n"):
        d.text((x, y), line, font=font(SANS, 38), fill=MIST)
        y += 56
    im.save(path)


def caption_filter(lines):
    """lines: [(start_s, end_s, text)] → ffmpeg drawtext chain (bottom band)."""
    parts = []
    for s, e, t in lines:
        t = t.replace("'", "\u2019").replace(":", "\\:")
        parts.append(
            f"drawtext=fontfile='{SANS_B.replace(':', '\\:')}':text='{t}':fontsize=40:fontcolor=0xF6EAD7:"
            f"box=0:x=(w-text_w)/2:y=h-82:enable='between(t,{s},{e})'"
        )
    return ",".join(parts)


def run(args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def still(png: Path, seconds: float, out: Path):
    run(["-loop", "1", "-t", str(seconds), "-i", str(png), "-vf", f"scale={W}:{H},fade=t=in:d=0.4,fade=t=out:st={seconds - 0.4}:d=0.4,format=yuv420p",
         "-r", "30", "-c:v", "libx264", "-preset", "medium", "-crf", "20", str(out)])


def main():
    tmp = Path(tempfile.mkdtemp())
    flow = ROOT / "assets" / "flow"
    shots = ROOT / "docs" / "screens"

    card(tmp / "c1.png", "Every evening, in 55% of families", "“Clean your room.”\n“I did!”",
         "Screen time is the nightly argument.\nThe TV is the prize, never part of the solution.",
         bg=flow / "concepts" / "concept_lostthings_2.webp")
    card(tmp / "c2.png", "Project Rosetta · Fire TV", "The TV that translates\n“clean your room”\ninto an adventure.",
         "An AI agent crew + the family's biggest screen.", bg=flow / "production" / "rosetta_home_base_b.webp")
    card(tmp / "c3.png", "The kid's phone is the camera", "Scan the QR.\nSnap the room.",
         "No app install. Photos go straight to the cloud\nand are deleted after a day.", side=shots / "rosetta-phone-kid.jpg")
    card(tmp / "c4.png", "Parents: Mission Control", "Only the unsure\ncases reach you.",
         "Post quests from your phone.\nThe Judge's low-confidence verdicts\nwait for a one-tap approval.", side=shots / "rosetta-phone-parent.jpg")
    card(tmp / "c5.png", "How it's built", "Native Fire TV + an\nagent crew on AWS",
         "Kotlin · Jetpack Compose for TV · Fire OS\n"
         "Strands Agents: Quest Master → Scout · Coach · Judge · Keeper\n"
         "Amazon Bedrock (Nova Pro vision) · Lambda · S3 · DynamoDB\n"
         "API Gateway · CloudFront · CDK  ·  open-source vision-overlay")
    card(tmp / "c6.png", "Project Rosetta", "Chores in.\nAdventures out.",
         "github repo · MIT · demo household runs with just a remote", bg=flow / "production" / "stone.webp")

    segs = []
    for i, (png, sec) in enumerate([("c1", 7), ("c2", 6), ("c3", 7)]):
        still(tmp / f"{png}.png", sec, tmp / f"s{i}.mp4"); segs.append(tmp / f"s{i}.mp4")

    # Real app footage: keep the meaningful parts of the raw recording (seconds), drop dead time.
    keep = [(0, 8), (14, 19), (20, 31), (35, 46), (53, 61), (62, 70), (71, 73.4)]
    caps_raw = [  # (raw_start, raw_end, caption)
        (0, 8, "Home Base: your house as a lit miniature. Rooms with a quest glow."),
        (14, 19, "Arjun opens his quest. His phone becomes the camera."),
        (20, 23, "The Scout and Coach agents read the photo…"),
        (23, 31, "…and the mess becomes Lost Things, each pinned to the real object."),
        (35, 38, "Arjun tidies. The Judge agent compares before and after."),
        (38, 46, "4 of 5 home. The Keeper names exactly what's left, kindly."),
        (53, 61, "All home. Time to carve a glyph."),
        (62, 70, "The Family Stone is complete: 9 glyphs, carved together."),
        (71, 74, "Movie night, earned. The TV hands off to your streaming app."),
    ]
    # Map raw times to the trimmed timeline.
    def to_cut(t):
        acc = 0.0
        for a, b in keep:
            if a <= t <= b:
                return acc + (t - a)
            acc += b - a
        return acc
    caps = [(round(to_cut(a), 2), round(to_cut(min(b, keep[-1][1])), 2), txt) for a, b, txt in caps_raw]
    caps[-1] = (caps[-1][0], caps[-1][1] + 3, caps[-1][2])  # covers the frozen last frame

    select = "+".join(f"between(t,{a},{b})" for a, b in keep)
    fw, fh = 1728, 972  # footage at 90%; captions get their own band below it
    vf = (f"select='{select}',setpts=N/FRAME_RATE/TB,scale={fw}:{fh},"
          f"pad={W}:{H}:{(W - fw) // 2}:0:color=0x0A1020,tpad=stop_mode=clone:stop_duration=3,"
          f"{caption_filter(caps)},format=yuv420p")
    run(["-i", str(RAW), "-vf", vf, "-r", "30", "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "20", str(tmp / "app.mp4")])
    segs.append(tmp / "app.mp4")

    for i, (png, sec) in enumerate([("c4", 7), ("c5", 10), ("c6", 6)], start=3):
        still(tmp / f"{png}.png", sec, tmp / f"s{i}.mp4"); segs.append(tmp / f"s{i}.mp4")

    lst = tmp / "list.txt"
    lst.write_text("".join(f"file '{p.as_posix()}'\n" for p in segs))
    # Silent audio track so YouTube treats it as a normal video.
    run(["-f", "concat", "-safe", "0", "-i", str(lst), "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo",
         "-shortest", "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", str(OUT)])
    for png in ["c1", "c2", "c3", "c4", "c5", "c6"]:
        shutil.copyfile(tmp / f"{png}.png", VID / f"card_{png}.png")
    shutil.rmtree(tmp)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
