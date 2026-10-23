"""Cut the demo video: cinematic intro (Veo), the real app on the Fire TV emulator with the phone pages beside it,
narration, music, and an outro. Everything is rendered with PIL + ffmpeg.

  python assets/demo/edit.py            ->  assets/demo/build/rosetta_demo.mp4  (1920x1080, 30 fps)

Inputs: footage/ (film.py + phone screenshots), vo/, music/, veo/ (make_demo.py).
"""
import json
import subprocess
import wave
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FT, VO, MU, VE = HERE / "footage", HERE / "vo", HERE / "music", HERE / "veo"
B = HERE / "build"
W, H, FPS = 1920, 1080, 30
FONTS = Path("C:/Windows/Fonts")
GOLD, CREAM, MIST, NIGHT = (244, 197, 106), (246, 234, 215), (169, 180, 204), (10, 16, 32)


def font(name: str, size: int):
    return ImageFont.truetype(str(FONTS / name), size)


SERIF, SERIF_B, SANS, SANS_SB, SANS_B, SYM = "georgia.ttf", "georgiab.ttf", "segoeui.ttf", "seguisb.ttf", "segoeuib.ttf", "seguisym.ttf"


def run(cmd: list):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(r.stderr[-2000:])


def dur(path: Path) -> float:
    if path.suffix == ".wav":
        with wave.open(str(path)) as w:
            return w.getnframes() / w.getframerate()
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)], capture_output=True, text=True).stdout
    return float(out)


# ------------------------------------------------------------------------------------------------ graphics
def background() -> Image.Image:
    """Deep night navy with soft gold and violet glows and a few stars: the stage behind every scene."""
    bg = Image.new("RGB", (W, H), (9, 13, 28))
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-300, -400, 1100, 700), fill=(70, 48, 120))
    d.ellipse((1100, 500, 2300, 1500), fill=(110, 78, 30))
    d.ellipse((600, 200, 1400, 900), fill=(30, 46, 100))
    glow = glow.filter(ImageFilter.GaussianBlur(220))
    bg = Image.blend(bg, glow, 0.55)
    d = ImageDraw.Draw(bg)
    import random
    rnd = random.Random(7)
    for _ in range(140):
        x, y, r = rnd.randrange(W), rnd.randrange(H), rnd.choice((1, 1, 1, 2))
        a = rnd.randrange(60, 200)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(a, a, int(a * 0.85)))
    return bg


BG = None


def text_center(d: ImageDraw.ImageDraw, cx: int, y: int, s: str, f, fill, spacing: int = 0):
    if spacing:
        widths = [d.textlength(ch, font=f) + spacing for ch in s]
        x = cx - (sum(widths) - spacing) / 2
        for ch, w in zip(s, widths):
            d.text((x, y), ch, font=f, fill=fill)
            x += w
    else:
        d.text((cx - d.textlength(s, font=f) / 2, y), s, font=f, fill=fill)


def caption(d: ImageDraw.ImageDraw, cx: int, y: int, eyebrow: str, title: str):
    sparkle = eyebrow.startswith("✦")
    eb = eyebrow.lstrip("✦ ").upper()
    f1 = font(SANS_SB, 25)
    widths = [d.textlength(ch, font=f1) + 5 for ch in eb]
    total = sum(widths) - 5 + (34 if sparkle else 0)
    x = cx - total / 2
    if sparkle:
        d.text((x, y - 4), "✦", font=font(SYM, 30), fill=GOLD)
        x += 34
    for ch, w in zip(eb, widths):
        d.text((x, y), ch, font=f1, fill=GOLD)
        x += w
    tf = font(SERIF, 50)
    # A soft shadow so the title reads over anything.
    d.text((cx - d.textlength(title, font=tf) / 2 + 2, y + 38), title, font=tf, fill=(0, 0, 0))
    text_center(d, cx, y + 36, title, tf, CREAM)


def rounded_mask(size, radius) -> Image.Image:
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def tv_window(img: Image.Image, box, radius=26):
    """Cut a rounded window in the stage for the TV footage, with a soft glow and a fine rim around it."""
    x, y, w, h = box
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle((x - 8, y - 8, x + w + 8, y + h + 8), radius=radius + 8, fill=(244, 197, 106, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(26))
    img.alpha_composite(glow)
    hole = Image.new("L", (W, H), 255)
    hole.paste(Image.new("L", (w, h), 0), (x, y), rounded_mask((w, h), radius))
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (W, H), 0), hole))
    ImageDraw.Draw(img).rounded_rectangle((x - 2, y - 2, x + w + 1, y + h + 1), radius=radius + 2, outline=(255, 255, 255, 70), width=3)


def phone(img: Image.Image, shot: Path, crop_y: int, at):
    """A phone mockup holding a real screenshot of the phone page (1000 px wide, 2x)."""
    sw, sh = 452, 981
    src = Image.open(shot).convert("RGB")
    screen = src.crop((0, crop_y, 1000, crop_y + 2170)).resize((sw, sh), Image.LANCZOS)
    pw, ph = sw + 30, sh + 30
    x, y = at
    sh_img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh_img).rounded_rectangle((x + 10, y + 24, x + pw + 10, y + ph + 24), radius=70, fill=(0, 0, 0, 150))
    img.alpha_composite(sh_img.filter(ImageFilter.GaussianBlur(24)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((x, y, x + pw, y + ph), radius=68, fill=(14, 18, 32, 255), outline=(244, 197, 106, 160), width=3)
    img.paste(screen, (x + 15, y + 15), rounded_mask((sw, sh), 54))
    d.rounded_rectangle((x + pw // 2 - 60, y + 26, x + pw // 2 + 60, y + 50), radius=12, fill=(14, 18, 32, 255))


def frame(name: str, layout: str, eyebrow: str = "", title: str = "", phone_shot=None, crop_y: int = 0) -> Path:
    """The static layer drawn over the footage for one piece."""
    out = B / f"frame_{name}.png"
    if layout == "full":
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        if title:
            shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(shade).rectangle((0, 860, W, H), fill=(0, 0, 0, 150))
            img.alpha_composite(shade.filter(ImageFilter.GaussianBlur(40)))
            caption(ImageDraw.Draw(img), W // 2, 940, eyebrow, title)
    else:
        img = BG.convert("RGBA").copy()
        if layout == "tv":
            tv_window(img, TV_BOX)
            caption(ImageDraw.Draw(img), W // 2, 948, eyebrow, title)
        elif layout == "split":
            tv_window(img, SPLIT_TV, 22)
            phone(img, FT / phone_shot, crop_y, (86, 36))
            caption(ImageDraw.Draw(img), SPLIT_TV[0] + SPLIT_TV[2] // 2, 900, eyebrow, title)
    img.save(out)
    return out


TV_BOX = (176, 34, 1568, 882)       # 16:9 window for full TV scenes
SPLIT_TV = (636, 150, 1216, 684)    # TV beside a phone


def card(name: str, eyebrow: str, title: str, chips: list = (), sub: str = "", big: bool = False) -> Path:
    img = BG.convert("RGBA").copy()
    d = ImageDraw.Draw(img)
    top = 300 if chips else 430
    if eyebrow:
        caption_eb = eyebrow.upper()
        text_center(d, W // 2, top - 70, caption_eb, font(SANS_SB, 28), GOLD, spacing=6)
    tf = font(SERIF, 96 if big else 74)
    lines = title.split("\n")
    y = top
    for ln in lines:
        d.text((W // 2 - d.textlength(ln, font=tf) / 2 + 3, y + 3), ln, font=tf, fill=(0, 0, 0))
        text_center(d, W // 2, y, ln, tf, CREAM)
        y += int(tf.size * 1.25)
    if sub:
        text_center(d, W // 2, y + 10, sub, font(SANS, 34), MIST)
        y += 70
    if chips:
        cf = font(SANS_SB, 34)
        rows, row, rw = [], [], 0
        for c in chips:
            cw = d.textlength(c, font=cf) + 64
            if row and rw + cw > 1500:
                rows.append(row); row, rw = [], 0
            row.append((c, cw)); rw += cw + 22
        rows.append(row)
        y += 60
        for row in rows:
            total = sum(cw for _, cw in row) + 22 * (len(row) - 1)
            x = W // 2 - total / 2
            for c, cw in row:
                d.rounded_rectangle((x, y, x + cw, y + 74), radius=37, fill=(26, 37, 80, 255), outline=(244, 197, 106, 200), width=2)
                d.text((x + 32, y + 13), c, font=cf, fill=CREAM)
                x += cw + 22
            y += 96
    out = B / f"card_{name}.png"
    img.save(out)
    return out


def question_card(name: str, text: str) -> Path:
    img = Image.new("RGB", (W, H), (5, 7, 14))
    d = ImageDraw.Draw(img)
    tf = font(SERIF_B, 96)
    text_center(d, W // 2, H // 2 - 70, text, tf, CREAM)
    out = B / f"q_{name}.png"
    img.save(out)
    return out


def end_title(footer: bool = True) -> Path:
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shade).ellipse((260, 200, 1660, 820), fill=(0, 0, 0, 150))
    img.alpha_composite(shade.filter(ImageFilter.GaussianBlur(90)))
    d = ImageDraw.Draw(img)
    text_center(d, W // 2, 330, "ROSETTA", font(SERIF, 150), GOLD, spacing=34)
    d.line((W // 2 - 260, 520, W // 2 + 260, 520), fill=GOLD, width=3)
    text_center(d, W // 2, 548, "The TV that works for the family, not against it.", font(SERIF, 46), CREAM)
    if footer:
        text_center(d, W // 2, 900, "Amazon Build · Ship · Shape  ·  Fire TV track", font(SANS_SB, 30), MIST)
        text_center(d, W // 2, 945, "github.com/ashfaque-rifaye/project-rosetta", font(SANS, 30), MIST)
    out = B / ("end_title.png" if footer else "end_title_plain.png")
    img.save(out)
    return out


# ------------------------------------------------------------------------------------------------ pieces
def piece(name: str, dur_s: float, layout: str, src=None, start=0.0, speed=1.0, crop=None, overlay=None,
          image=None, fade_in=False, fade_out=False, zoom=0.0) -> Path:
    """Render one piece (no audio). layout: tv / split (footage in a window) · full (footage full screen) · image."""
    out = B / f"p_{name}.mp4"
    inputs, chains = [], []
    if layout in ("tv", "split", "full"):
        box = {"tv": TV_BOX, "split": SPLIT_TV, "full": (0, 0, W, H)}[layout]
        inputs += ["-ss", f"{start:.3f}", "-t", f"{dur_s * speed + 0.5:.3f}", "-i", str(src)]
        crop_f = f"crop={crop[2]}:{crop[3]}:{crop[0]}:{crop[1]}," if crop else ""
        chains.append(f"color=c=black:s={W}x{H}:r={FPS}:d={dur_s:.3f}[base]")
        chains.append(f"[0:v]setpts=(PTS-STARTPTS)/{speed},fps={FPS},{crop_f}scale={box[2]}:{box[3]}:flags=lanczos,setsar=1[f]")
        chains.append(f"[base][f]overlay={box[0]}:{box[1]}:shortest=1[a]")
        last = "a"
        if overlay:
            inputs += ["-loop", "1", "-t", f"{dur_s:.3f}", "-i", str(overlay)]
            chains.append(f"[a][1:v]overlay=0:0[b]")
            last = "b"
    else:
        inputs += ["-framerate", str(FPS), "-loop", "1", "-t", f"{dur_s:.3f}", "-i", str(image)]
        z = f",zoompan=z='1+{zoom}*on':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={W}x{H}:fps={FPS}" if zoom else ""
        chains.append(f"[0:v]format=rgb24,scale={W}:{H}{z},fps={FPS}[b]")
        last = "b"
    fades = []
    if fade_in:
        fades.append("fade=t=in:st=0:d=0.35")
    if fade_out:
        fades.append(f"fade=t=out:st={dur_s - 0.35:.3f}:d=0.35")
    chains.append(f"[{last}]{','.join(fades) + ',' if fades else ''}format=yuv420p[v]")
    run(["ffmpeg", "-y", "-loglevel", "error", *inputs, "-filter_complex", ";".join(chains), "-map", "[v]",
         "-t", f"{dur_s:.3f}", "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", str(out)])
    return out


def tighten_vo():
    """Shorten long pauses in the narration (TTS can be slow and thoughtful)."""
    out = B / "vo"
    out.mkdir(parents=True, exist_ok=True)
    for f in sorted(VO.glob("*.wav")):
        run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(f), "-af",
             "silenceremove=start_periods=1:start_threshold=-50dB:stop_periods=-1:stop_duration=0.45:stop_threshold=-45dB:stop_silence=0.3,"
             "areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse", str(out / f.name)])
    return {f.stem: dur(f) for f in sorted(out.glob("*.wav"))}


# ------------------------------------------------------------------------------------------------ the cut
def build():
    global BG
    B.mkdir(parents=True, exist_ok=True)
    BG = background()
    vo = tighten_vo()
    print("narration", round(sum(vo.values()), 1), "s")
    s = lambda n: FT / f"{n}.mp4"  # noqa: E731
    timeline = []   # (piece path, duration, vo key or None, vo offset)

    def add(p, d, key=None, off=0.4):
        timeline.append((p, d, key, off))

    # ---------- intro: cinematic ----------
    add(piece("i1", 6.6, "full", VE / "intro_city.mp4", 0.0, fade_in=True), 6.6, "01_hook", 0.9)
    add(piece("i2a", 2.4, "image", image=question_card("hw", "“Did you finish your homework?”"), zoom=0.0006), 2.4, "02_q", 0.2)
    add(piece("i2b", 2.6, "image", image=question_card("tv", "“Can I watch TV now?”"), zoom=0.0006), 2.6)
    add(piece("i3", 4.0, "full", VE / "intro_tv.mp4", 1.0), 4.0, "03_what_if", 0.4)
    add(piece("i4", 8.6, "full", s("s01_launch"), 4.0, fade_out=True), 8.6, "04_title", 2.8)
    intro_end = sum(t[1] for t in timeline)

    # ---------- the app ----------
    f = lambda n, eb, t, **kw: frame(n, kw.pop("layout", "tv"), eb, t, **kw)  # noqa: E731
    fr = f("home", "Tonight, on Fire TV", "A living home, full of quests")
    add(piece("h1", 5.0, "tv", s("s01_launch"), 15.0, overlay=fr, fade_in=True), 5.0, "05_home", 0.5)
    add(piece("h2", 10.5, "tv", s("s02_home"), 2.0, overlay=fr, fade_out=True), 10.5)

    fr1 = f("hw1", "✦ AI Tutor", "Snap the worksheet with any phone", layout="split", phone_shot="phone_kid.png")
    fr2 = f("hw2", "✦ AI Tutor", "Every answer, marked on his own handwriting")
    add(piece("w1", 5.0, "split", s("s03_homework"), 3.5, overlay=fr1, fade_in=True), 5.0, "06_homework", 0.4)
    add(piece("w2", 4.0, "tv", s("s03_homework"), 14.5, speed=1.5, overlay=fr2), 4.0)
    add(piece("w3", 6.5, "tv", s("s03_homework"), 22.5, overlay=fr2), 6.5)
    add(piece("w4", 3.5, "tv", s("s03_homework"), 29.0, crop=(24, 112, 920, 518), overlay=f("hw4", "✦ Checked in code", "A hint for each mistake, never the answer")), 3.5)
    add(piece("p1", 5.5, "tv", s("s03_homework"), 33.0, overlay=f("pr", "✦ Practice", "A round on exactly what slipped"), fade_out=True), 5.5, "07_practice", 0.3)

    add(piece("o1", 7.0, "tv", s("s05_honest"), 14.0, overlay=f("hon", "✦ Honest AI", "Wrong photo? It says what it really sees"), fade_in=True, fade_out=True), 7.0, "08_honest", 0.4)

    fv1 = f("v1", "✦ Voice notes", "Arjun tells Rosetta what he read", layout="split", phone_shot="phone_kid_voice.png", crop_y=500)
    fv2 = f("v2", "✦ Memories", "Saved for Mom and Dad to play back", layout="split", phone_shot="phone_parent.png", crop_y=2170)
    add(piece("v1", 6.0, "split", s("s06_voice"), 24.5, overlay=fv1, fade_in=True), 6.0, "09_voice", 0.4)
    add(piece("v2", 5.5, "split", s("s06_voice"), 26.0, overlay=fv2, fade_out=True), 5.5)

    fr = f("rw", "Rewards", "XP, coins, real pocket money, and stars")
    add(piece("r1", 5.0, "tv", s("s04_reward"), 1.0, overlay=fr, fade_in=True), 5.0, "10_rewards", 0.4)
    add(piece("r2", 4.0, "tv", s("s06_voice"), 33.5, overlay=fr), 4.0)
    add(piece("r3", 4.5, "tv", s("s10b_treasure"), 3.0, overlay=f("tr", "Treasure", "Saving up for the wish list"), fade_out=True), 4.5)

    fp1 = f("pa1", "For parents", "A ping, and one tap from the phone", layout="split", phone_shot="phone_parent.png", crop_y=0)
    fp2 = f("pa2", "TV time", "Unlocked from the phone, live on the TV", layout="split", phone_shot="phone_parent.png", crop_y=8900)
    add(piece("a1", 4.0, "split", s("s07_unlock"), 0.5, overlay=fp1, fade_in=True), 4.0, "11_parents", 0.4)
    add(piece("a2", 7.5, "split", s("s07_unlock"), 4.0, overlay=fp2), 7.5)
    add(piece("a3", 4.0, "tv", s("s09_progress"), 15.0, overlay=f("pa3", "For parents", "Scan the TV to open the parent page"), fade_out=True), 4.0)

    fq = f("qz", "Family Quiz Night", "A 3D game, written for each child")
    add(piece("q1", 4.0, "tv", s("s08_quiz"), 16.0, overlay=fq, fade_in=True), 4.0, "12_quiz", 0.4)
    add(piece("q2", 7.0, "tv", s("s08_quiz"), 38.5, overlay=fq), 7.0)
    add(piece("q3", 3.5, "tv", s("s08_quiz"), 47.5, overlay=fq, fade_out=True), 3.5)

    add(piece("c1", 7.0, "tv", s("s09b_progress"), 2.0, overlay=f("co", "✦ Coach's notes", "Real activity, in plain words"), fade_in=True, fade_out=True), 7.0, "13_progress", 0.4)

    t1 = card("tech1", "Native on Fire TV", "Built like a game,\nruns on every Fire TV",
              ["Kotlin · Compose for TV", "Unity 6 · 3D quiz", "Filament · real-time 3D", "32- and 64-bit", "Generated art and music"])
    t2 = card("tech2", "The agent crew, on AWS", "Strands Agents on AWS",
              ["Strands Agents SDK", "AWS Lambda", "Amazon S3", "Amazon DynamoDB", "API Gateway", "Amazon CloudFront",
               "Amazon EventBridge", "AWS CDK", "Amazon Bedrock · Polly · Transcribe"])
    tech_d = max(16.0, vo["14_tech"] + 1.2)
    add(piece("t1", tech_d * 0.45, "image", image=t1, zoom=0.0004, fade_in=True), tech_d * 0.45, "14_tech", 0.4)
    add(piece("t2", tech_d * 0.55, "image", image=t2, zoom=0.0004, fade_out=True), tech_d * 0.55)

    # ---------- outro ----------
    outro_start = sum(t[1] for t in timeline)
    add(piece("e1", 8.0, "full", VE / "outro_home.mp4", 0.0, overlay=end_title(footer=False), fade_in=True), 8.0, "15_outro", 1.2)
    add(piece("e2", 3.0, "image", image=end_title_card(), fade_out=True), 3.0)
    total = sum(t[1] for t in timeline)

    # ---------- video ----------
    lst = B / "pieces.txt"
    lst.write_text("".join(f"file '{p.as_posix()}'\n" for p, *_ in timeline), encoding="utf-8")
    run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", str(B / "video.mp4")])

    # ---------- sound: narration on cue, music under it ----------
    t, cues = 0.0, []
    for p, d, key, off in timeline:
        if key:
            cues.append((key, t + off))
        t += d
    ins, fl = [], []
    for i, (key, at) in enumerate(cues):
        ins += ["-i", str(B / "vo" / f"{key}.wav")]
        ms = int(at * 1000)
        fl.append(f"[{i}:a]aresample=48000,aformat=channel_layouts=stereo,volume=1.35,adelay={ms}|{ms}[v{i}]")
    n = len(cues)
    ins += ["-i", str(MU / "intro.wav"), "-i", str(MU / "bed.wav"), "-i", str(MU / "bed.wav"), "-i", str(MU / "outro.wav")]
    bed_len = outro_start - intro_end + 2
    fl.append(f"[{n}:a]aresample=48000,atrim=0:{intro_end + 1:.2f},afade=t=out:st={intro_end - 1.5:.2f}:d=2.5,volume=0.9[mi]")
    fl.append(f"[{n + 1}:a][{n + 2}:a]acrossfade=d=3,aresample=48000,atrim=0:{bed_len:.2f},afade=t=in:d=1.5,afade=t=out:st={bed_len - 2.5:.2f}:d=2.5,volume=0.32,adelay={int((intro_end - 0.5) * 1000)}|{int((intro_end - 0.5) * 1000)}[mb]")
    fl.append(f"[{n + 3}:a]aresample=48000,atrim=0:{total - outro_start + 1:.2f},afade=t=in:d=1,afade=t=out:st={total - outro_start - 2.5:.2f}:d=2.5,volume=0.75,adelay={int((outro_start - 0.8) * 1000)}|{int((outro_start - 0.8) * 1000)}[mo]")
    fl.append("".join(f"[v{i}]" for i in range(n)) + f"amix=inputs={n}:normalize=0,apad=whole_dur={total:.2f},atrim=0:{total:.2f},asplit=2[voa][vsc]")  # full length, so the ducked music runs to the end
    fl.append(f"[mi][mb][mo]amix=inputs=3:normalize=0,atrim=0:{total:.2f}[mus]")
    fl.append("[mus][vsc]sidechaincompress=threshold=0.02:ratio=6:attack=25:release=450:makeup=1[duck]")
    fl.append(f"[voa][duck]amix=inputs=2:normalize=0,loudnorm=I=-15:TP=-1.5:LRA=11[a]")
    run(["ffmpeg", "-y", "-loglevel", "error", *ins, "-filter_complex", ";".join(fl), "-map", "[a]", "-c:a", "aac", "-b:a", "192k", str(B / "audio.m4a")])

    final = B / "rosetta_demo.mp4"
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(B / "video.mp4"), "-i", str(B / "audio.m4a"), "-map", "0:v", "-map", "1:a",
         "-c:v", "copy", "-c:a", "copy", "-shortest", "-movflags", "+faststart", str(final)])
    (B / "timeline.json").write_text(json.dumps([{"piece": p.name, "dur": round(d, 2), "vo": k} for p, d, k, _ in timeline], indent=1))
    print("done", final, round(total, 1), "s")


def end_title_card() -> Path:
    img = Image.new("RGBA", (W, H), (5, 7, 14, 255))
    img.alpha_composite(Image.open(end_title()))
    out = B / "end_card.png"
    img.convert("RGB").save(out)
    return out


if __name__ == "__main__":
    build()
