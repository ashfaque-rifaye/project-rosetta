"""Cut 2: the whole demo plays on a real TV in a living room, Mom's phone recording pops up beside it, Netflix-style
subtitles carry the narration, and the intro gets two cinematic Veo shots.

  python assets/demo/edit2.py        ->  assets/demo/build/rosetta_demo_v2.mp4

Uses edit.py's drawing helpers. Footage: film.py / film2.py; mom_phone.mp4 is the parent's own screen recording.
"""
import json
import re
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

import edit
from edit import (B, CREAM, FPS, FT, GOLD, H, MIST, MU, SANS, SANS_SB, SERIF, SYM, VE, W, card, dur, end_title, end_title_card, font,
                  rounded_mask, run, text_center, tighten_vo)

HERE = Path(__file__).resolve().parent
ROOM = Image.open(HERE / "tv_room.png").convert("RGB")
SCREEN = (1400, 431, 4105, 1917)        # the TV's picture area in tv_room.png (measured, inside the bezel)
PHONE_W, PHONE_H = 452, 985             # phone screen inside the mockup
_cache: dict = {}


def framing(name: str, screen_h: int, left: int, top: int):
    """Scale the room so the TV picture is screen_h tall, placed at (left, top); returns the 1920x1080 crop and the
    picture rect on it."""
    if name in _cache:
        return _cache[name]
    k = screen_h / (SCREEN[3] - SCREEN[1])
    big = ROOM.resize((round(ROOM.width * k), round(ROOM.height * k)), Image.LANCZOS)
    sx, sy = SCREEN[0] * k, SCREEN[1] * k
    ox, oy = round(sx - left), round(sy - top)
    crop = big.crop((ox, oy, ox + W, oy + H))
    sw = round((SCREEN[2] - SCREEN[0]) * k)
    _cache[name] = (crop, (left, top, sw, screen_h))
    return _cache[name]


FRAMES = {
    "wide": (560, 482, 150),    # the whole room
    "close": (860, 179, 30),    # the TV fills the frame
    "left": (800, 40, 70),      # the TV to the left, a phone in front on the right
}


def footage_box(rect):
    """16:9 footage fitted inside the TV's picture (the panel is a touch wider than 16:9: thin dark bars)."""
    x, y, w, h = rect
    fh = h
    fw = round(fh * 16 / 9)
    if fw > w:
        fw, fh = w, round(w * 9 / 16)
    return x + (w - fw) // 2, y + (h - fh) // 2, fw, fh


def chip(img: Image.Image, eyebrow: str, title: str):
    """A small frosted-glass label, bottom-left: what this scene shows."""
    d = ImageDraw.Draw(img)
    f1, f2 = font(SANS_SB, 22), font(SERIF, 30)
    eb = eyebrow.lstrip("✦ ").upper()
    w = max(d.textlength(eb, font=f1) + (30 if eyebrow.startswith("✦") else 0) + 4 * len(eb), d.textlength(title, font=f2)) + 44
    x, y = 36, 906
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.rounded_rectangle((x, y, x + w, y + 86), radius=20, fill=(14, 20, 44, 190), outline=(255, 255, 255, 90), width=2)
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    cx = x + 22
    if eyebrow.startswith("✦"):
        d.text((cx, y + 9), "✦", font=font(SYM, 24), fill=GOLD)
        cx += 30
    for ch in eb:
        d.text((cx, y + 12), ch, font=f1, fill=GOLD)
        cx += d.textlength(ch, font=f1) + 4
    d.text((x + 22, y + 40), title, font=f2, fill=CREAM)


def phone_frame(img: Image.Image, at, shot=None, crop_y=0):
    """Phone mockup; with shot=None its screen is left clear for a video underneath."""
    x, y = at
    pw, ph = PHONE_W + 30, PHONE_H + 30
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((x + 12, y + 26, x + pw + 12, y + ph + 26), radius=70, fill=(0, 0, 0, 170))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(26)))
    body = Image.new("RGBA", img.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    bd.rounded_rectangle((x, y, x + pw, y + ph), radius=68, fill=(12, 15, 26, 255), outline=(200, 205, 220, 255), width=3)
    hole = Image.new("L", img.size, 255)
    hole.paste(Image.new("L", (PHONE_W, PHONE_H), 0), (x + 15, y + 15), rounded_mask((PHONE_W, PHONE_H), 54))
    body.putalpha(Image.composite(body.getchannel("A"), Image.new("L", img.size, 0), hole))
    img.alpha_composite(body)
    if not shot:
        a = img.getchannel("A")
        a.paste(Image.new("L", (PHONE_W, PHONE_H), 0), (x + 15, y + 15), rounded_mask((PHONE_W, PHONE_H), 54))
        img.putalpha(a)
    if shot:
        src = Image.open(FT / shot).convert("RGB")
        scr = src.crop((0, crop_y, 1000, crop_y + 2180)).resize((PHONE_W, PHONE_H), Image.LANCZOS)
        img.paste(scr, (x + 15, y + 15), rounded_mask((PHONE_W, PHONE_H), 54))
    ImageDraw.Draw(img).rounded_rectangle((x + pw // 2 - 62, y + 27, x + pw // 2 + 62, y + 51), radius=12, fill=(12, 15, 26, 255))
    return (x + 15, y + 15, PHONE_W, PHONE_H)


PHONE_AT = (1388, 34)


def room_layer(name: str, frame: str, eyebrow="", title="", shot=None, crop_y=0, phone_video=False) -> tuple:
    """The room around the TV (its picture cut out, with a faint glass glare), an optional phone, and the label."""
    crop, rect = framing(frame, *FRAMES[frame])
    img = crop.convert("RGBA")
    x, y, w, h = rect
    hole = Image.new("L", img.size, 255)
    hole.paste(Image.new("L", (w, h), 0), (x, y))
    img.putalpha(hole)
    glare = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glare)
    gd.polygon([(x + w * 0.55, y), (x + w * 0.78, y), (x + w * 0.42, y + h), (x + w * 0.19, y + h)], fill=(255, 255, 255, 9))
    img.alpha_composite(glare.filter(ImageFilter.GaussianBlur(30)))
    prect = None
    if shot or phone_video:
        prect = phone_frame(img, PHONE_AT, shot, crop_y)
    if title:
        chip(img, eyebrow, title)
    out = B / f"room_{name}.png"
    img.save(out)
    return out, footage_box(rect), prect


def cfr(src: Path) -> Path:
    out = B / "cfr" / src.name
    if not out.exists():
        out.parent.mkdir(exist_ok=True)
        run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-vf", "setpts=PTS-STARTPTS,fps=30", "-fps_mode", "cfr", "-an",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "14", str(out)])
        try:
            ok = dur(out) > 0.5
        except Exception:  # noqa: BLE001 - no video stream at all
            ok = False
        if not ok:  # a still screen: the recorder wrote a single frame, so hold it for 12 s
            still = out.with_suffix(".png")
            run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-frames:v", "1", "-update", "1", str(still)])
            run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "30", "-loop", "1", "-t", "12", "-i", str(still),
                 "-c:v", "libx264", "-preset", "veryfast", "-crf", "14", "-pix_fmt", "yuv420p", str(out)])
    return out


def rpiece(name, d, frame, src, start, speed=1.0, eyebrow="", title="", shot=None, crop_y=0, mom=None, fade_in=False, fade_out=False):
    """One scene on the TV: footage in the picture, the room over it, optionally Mom's phone video (mom=(start, speed))."""
    src = cfr(src)
    layer, (fx, fy, fw, fh), prect = room_layer(name, frame, eyebrow, title, shot, crop_y, phone_video=mom is not None)
    out = B / f"r_{name}.mp4"
    ins = ["-ss", f"{start:.3f}", "-t", f"{d * speed + 0.5:.3f}", "-i", str(src), "-loop", "1", "-t", f"{d:.3f}", "-i", str(layer)]
    ch = [f"color=c=black:s={W}x{H}:r={FPS}:d={d:.3f}[base]",
          f"[0:v]setpts=(PTS-STARTPTS)/{speed},fps={FPS},tpad=stop_mode=clone:stop_duration=60,scale={fw}:{fh}:flags=lanczos,setsar=1[f]",  # hold the last frame (the recorder skips still frames)
          f"[base][f]overlay={fx}:{fy}:shortest=1[a]"]
    last = "a"
    if mom is not None:
        ms, mspeed = mom
        ins += ["-ss", f"{ms:.3f}", "-t", f"{d * mspeed + 0.5:.3f}", "-i", str(FT / "mom_phone.mp4")]
        px, py, pw, ph = prect
        ch += [f"[2:v]setpts=(PTS-STARTPTS)/{mspeed},fps={FPS},scale={pw}:{ph}:flags=lanczos,setsar=1[m]", f"[a][m]overlay={px}:{py}:eof_action=repeat[a2]"]
        last = "a2"
    ch.append(f"[{last}][1:v]overlay=0:0[b]")
    fades = (["fade=t=in:st=0:d=0.35"] if fade_in else []) + ([f"fade=t=out:st={d - 0.35:.3f}:d=0.35"] if fade_out else [])
    ch.append(f"[b]{','.join(fades) + ',' if fades else ''}format=yuv420p[v]")
    run(["ffmpeg", "-y", "-loglevel", "error", *ins, "-filter_complex", ";".join(ch), "-map", "[v]", "-t", f"{d:.3f}", "-r", str(FPS),
         "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", str(out)])
    return out


def cinematic(name, d, src, start, line, fade_in=False, fade_out=False):
    """A Veo shot with letterbox bars and a film-style title line."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.rectangle((0, 0, W, 118), fill=(0, 0, 0, 255))
    dr.rectangle((0, H - 118, W, H), fill=(0, 0, 0, 255))
    if line:
        f = font(SERIF, 64)
        tw = dr.textlength(line, font=f)
        shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(shade).rounded_rectangle((W / 2 - tw / 2 - 60, 760, W / 2 + tw / 2 + 60, 900), radius=60, fill=(0, 0, 0, 120))
        img.alpha_composite(shade.filter(ImageFilter.GaussianBlur(30)))
        dr = ImageDraw.Draw(img)
        dr.text((W / 2 - tw / 2 + 2, 792), line, font=f, fill=(0, 0, 0, 200))
        dr.text((W / 2 - tw / 2, 790), line, font=f, fill=(*CREAM, 255))
    ov = B / f"cine_{name}.png"
    img.save(ov)
    return edit.piece(name, d, "full", src, start, overlay=ov, fade_in=fade_in, fade_out=fade_out)


# ------------------------------------------------------------------------------------------------ subtitles
def chunks(text: str, limit: int = 58) -> list:
    """Split narration into subtitle lines at sentence and phrase breaks, each short enough for one line."""
    parts = re.split(r"(?<=[.!?])\s+|(?<=[,:;])\s+", text.replace("...", "").strip())
    out, cur = [], ""
    for p in parts:
        if not p:
            continue
        if cur and len(cur) + 1 + len(p) > limit:
            out.append(cur); cur = p
        else:
            cur = f"{cur} {p}".strip()
    if cur:
        out.append(cur)
    res = []
    for c in out:  # very long phrases: split at a word boundary near the middle
        while len(c) > limit + 12:
            cut = c.rfind(" ", 0, limit)
            res.append(c[:cut]); c = c[cut + 1:]
        res.append(c)
    return res


def ass_time(t: float) -> str:
    h, m = int(t // 3600), int(t % 3600 // 60)
    return f"{h}:{m:02d}:{t % 60:05.2f}"


def write_subs(cues: list, vo_text: dict, vo_len: dict, skip=()) -> Path:
    head = ("[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\nWrapStyle: 2\n\n[V4+ Styles]\n"
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
            "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
            "Style: Net,Segoe UI Semibold,44,&H00FFFFFF,&H00FFFFFF,&H00000000,&H64000000,0,0,0,0,100,100,0.5,0,1,2.2,1.2,2,80,80,46,1\n\n"
            "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
    lines = []
    for key, at in cues:
        if key in skip:
            continue
        parts = chunks(vo_text[key])
        total = sum(len(p) for p in parts)
        t = at
        for p in parts:
            d = vo_len[key] * len(p) / total
            lines.append(f"Dialogue: 0,{ass_time(t)},{ass_time(t + d - 0.05)},Net,,0,0,0,,{p}")
            t += d
    path = B / "subs.ass"
    path.write_text(head + "\n".join(lines) + "\n", encoding="utf-8")
    return path


# ------------------------------------------------------------------------------------------------ the cut
def build():
    B.mkdir(parents=True, exist_ok=True)
    edit.BG = edit.background()
    vo_len = tighten_vo()
    import make_demo  # the narration lines (the generator stays local, see .gitignore)
    vo_text = make_demo.VO
    s = lambda n: FT / f"{n}.mp4"  # noqa: E731
    tl = []

    def add(p, d, key=None, off=0.4):
        tl.append((p, d, key, off))

    # ---------- intro ----------
    add(edit.piece("i1", 5.6, "full", VE / "intro_city.mp4", 0.0, fade_in=True), 5.6, "01_hook", 0.5)
    add(cinematic("i2", 3.0, VE / "intro_q1.mp4", 1.5, "“Did you finish your homework?”"), 3.0, "02_q", 0.4)
    add(cinematic("i3", 3.0, VE / "intro_q2.mp4", 2.5, "“Can I watch TV now?”"), 3.0)
    add(edit.piece("i4", 3.6, "full", VE / "intro_tv.mp4", 1.2), 3.6, "03_what_if", 0.3)
    add(edit.piece("i5", 8.6, "full", s("t01_launch"), T["launch_intro"], fade_out=True), 8.6, "04_title", 2.8)
    intro_end = sum(t[1] for t in tl)

    # ---------- the app, on the family TV ----------
    add(rpiece("h1", 4.2, "wide", s("t06_room"), 4.0, fade_in=True), 4.2, "05_home", 0.4)
    add(rpiece("h2", 10.5, "close", s("t03_arjun_code"), 4.0, eyebrow="Tonight, on Fire TV", title="A living home, full of quests", fade_out=True), 10.5)

    add(rpiece("m1", 4.5, "left", s("t02_meera_hw"), T["meera_qr"], eyebrow="✦ AI Tutor", title="Meera snaps her workbook", shot="phone_meera.png", crop_y=0, fade_in=True), 4.5, "06_homework", 0.4)
    add(rpiece("m2", 4.0, "close", s("t02_meera_hw"), T["meera_scan"], speed=1.4, eyebrow="✦ AI Tutor", title="Reading every answer"), 4.0)
    add(rpiece("m3", 5.0, "close", s("t02_meera_hw"), T["meera_marked"], eyebrow="✦ AI Tutor", title="Marked on her own handwriting"), 5.0)
    add(rpiece("m4", 3.0, "left", s("t02_meera_hw"), T["meera_marked"] + 3, eyebrow="✦ AI Tutor", title="A hint, never the answer", shot="phone_meera.png", crop_y=1080), 3.0)
    add(rpiece("c1", 4.0, "close", s("t03_arjun_code"), T["code_marked"], eyebrow="✦ Coding class", title="Arjun's programs, traced line by line"), 4.0, "06b_coding", 0.3)
    add(rpiece("c2", 4.0, "close", s("t03_arjun_code"), T["code_q"], eyebrow="✦ Coding class", title="Practice: what does this code print?"), 4.0)
    add(rpiece("p1", 5.0, "close", s("t02_meera_hw"), T["meera_practice"], eyebrow="✦ Practice", title="Questions that make them think", fade_out=True), 5.0, "07_practice", 0.3)

    add(rpiece("o1", 6.5, "close", s("t05_honest"), T["honest"], eyebrow="✦ Honest AI", title="Wrong photo? It says what it sees", fade_in=True), 6.5, "08_honest", 0.4)
    add(rpiece("t1", 4.0, "close", s("t06_room"), T["room_found"], eyebrow="✦ Room check", title="Lost Things, found in Meera's room"), 4.0, "08b_room", 0.3)
    add(rpiece("t2", 4.5, "close", s("t06_room"), T["room_done"], eyebrow="✦ Room check", title="Checked again: all tidy", fade_out=True), 4.5)

    add(rpiece("v1", 6.0, "left", s("t07_voice"), T["voice_done"], eyebrow="✦ Voice notes", title="Arjun tells Rosetta what he read", shot="phone_kid_voice.png", crop_y=500, fade_in=True), 6.0, "09_voice", 0.4)
    add(rpiece("v2", 5.0, "left", s("t07_voice"), T["voice_done"] + 2, eyebrow="✦ Memories", title="Kept for Mom and Dad", shot="phone_parent.png", crop_y=2170, fade_out=True), 5.0)

    add(rpiece("w1", 4.5, "close", s("t04_reward"), T["reward"], eyebrow="Rewards", title="XP, coins and a star", fade_in=True), 4.5, "10_rewards", 0.4)
    add(rpiece("w2", 3.5, "close", s("s06_voice"), 33.5, eyebrow="Rewards", title="Real pocket money"), 3.5)
    add(rpiece("w3", 3.5, "close", s("v_goal"), 0.0, eyebrow="Family Goal", title="Every star counts toward Movie Night", fade_out=True), 3.5)

    add(rpiece("a1", 4.0, "left", s("t08_unlock"), 4.0, eyebrow="For parents", title="A ping, and one tap", mom=(0.0, 1.0), fade_in=True), 4.0, "11_parents", 0.4)
    add(rpiece("a2", 6.5, "left", s("t08_unlock"), 7.0, eyebrow="TV time", title="Unlocked from the phone, live", mom=(40.5, 1.0)), 6.5)
    add(rpiece("a3", 4.5, "left", s("t13_home"), 10.0, eyebrow="Mom's phone", title="Daily tasks, set in seconds", mom=(10.0, 2.2)), 4.5, "11b_mom", 0.3)
    add(rpiece("a4", 5.0, "left", s("v_arjun"), 0.0, eyebrow="Mom's phone", title="Each child's class and next test", mom=(34.0, 1.2), fade_out=True), 5.0)

    add(rpiece("q1", 3.5, "close", s("t09_quiz"), T["quiz_load"], eyebrow="Family Quiz Night", title="A 3D game for the family", fade_in=True), 3.5, "12_quiz", 0.4)
    add(rpiece("q2", 4.0, "close", s("t09_quiz"), T["quiz_q1"], eyebrow="Family Quiz Night", title="Reasoning questions for each child"), 4.0)
    add(rpiece("q3", 3.5, "close", s("t09_quiz"), T["quiz_q2"], eyebrow="Family Quiz Night", title="From fractions to code", fade_out=True), 3.5)

    add(rpiece("k1", 3.2, "close", s("v_arjun"), 0.0, eyebrow="✦ Coach's notes", title="Arjun's week", fade_in=True), 3.2, "13_progress", 0.3)
    add(rpiece("k2", 3.2, "close", s("v_meera"), 8.2, eyebrow="✦ Coach's notes", title="Meera's week", fade_out=True), 3.2)

    t1 = card("tech_v2", "How it's built", "Native on Fire TV · agents on AWS",
              ["Kotlin · Compose for TV", "Unity 6 · Filament 3D", "Strands Agents", "AWS Lambda · S3 · DynamoDB", "API Gateway · CloudFront",
               "Amazon Bedrock (Nova)"])
    td = max(6.0, vo_len["14_tech"] + 0.8)
    add(edit.piece("x1", td, "image", image=t1, zoom=0.0005, fade_in=True, fade_out=True), td, "14_tech", 0.3)

    add(rpiece("z1", 2.8, "close", s("v_squad"), 0.0, eyebrow="Squad", title="Lost Things to collect", fade_in=True), 2.8, "14b_more", 0.3)
    add(rpiece("z2", 2.8, "close", s("v_streaks"), 0.0, eyebrow="Streaks", title="Stamps and day streaks"), 2.8)
    add(rpiece("z3", 2.8, "close", s("v_treasure"), 0.0, eyebrow="Treasure", title="Real wish lists"), 2.8)
    add(rpiece("z4", 2.6, "close", s("v_movie"), 0.0, eyebrow="Movie Night", title="Nine stars unlock it for the family"), 2.6)
    add(rpiece("z5", 2.6, "close", s("v_parents"), 0.0, eyebrow="For parents", title="Scan the TV to open the parent page", fade_out=True), 2.6)

    outro_start = sum(t[1] for t in tl)
    add(edit.piece("e1", 7.0, "full", VE / "outro_home.mp4", 0.0, overlay=end_title(footer=False), fade_in=True), 7.0, "15_outro", 1.0)
    add(edit.piece("e2", 2.5, "image", image=end_title_card(), fade_out=True), 2.5)
    total = sum(t[1] for t in tl)

    lst = B / "pieces_v2.txt"
    lst.write_text("".join(f"file '{p.as_posix()}'\n" for p, *_ in tl), encoding="utf-8")
    run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", str(B / "video_v2.mp4")])

    # narration on cue, music ducked under it
    t, cues = 0.0, []
    for p, d, key, off in tl:
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
    fl.append("".join(f"[v{i}]" for i in range(n)) + f"amix=inputs={n}:normalize=0,apad=whole_dur={total:.2f},atrim=0:{total:.2f},asplit=2[voa][vsc]")
    fl.append(f"[mi][mb][mo]amix=inputs=3:normalize=0,atrim=0:{total:.2f}[mus]")
    fl.append("[mus][vsc]sidechaincompress=threshold=0.02:ratio=6:attack=25:release=450:makeup=1[duck]")
    fl.append("[voa][duck]amix=inputs=2:normalize=0,loudnorm=I=-15:TP=-1.5:LRA=11[a]")
    run(["ffmpeg", "-y", "-loglevel", "error", *ins, "-filter_complex", ";".join(fl), "-map", "[a]", "-c:a", "aac", "-b:a", "192k", str(B / "audio_v2.m4a")])

    # Netflix-style subtitles, burned in (the intro's questions are already on screen as titles)
    write_subs(cues, vo_text, vo_len, skip={"02_q"})
    fonts = B / "fonts"
    fonts.mkdir(exist_ok=True)
    shutil.copy("C:/Windows/Fonts/seguisb.ttf", fonts / "seguisb.ttf")
    final = B / "rosetta_demo_v2.mp4"
    import subprocess
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", "video_v2.mp4", "-i", "audio_v2.m4a", "-vf", "ass=subs.ass:fontsdir=fonts",
                        "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "copy",
                        "-shortest", "-movflags", "+faststart", final.name], cwd=B, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(r.stderr[-1500:])
    (B / "timeline_v2.json").write_text(json.dumps([{"piece": p.name, "dur": round(d, 2), "vo": k} for p, d, k, _ in tl], indent=1))
    print("done", final, round(total, 1), "s")


T = {}   # source times in the footage, filled in from the contact sheets (edit2_times.json)

if __name__ == "__main__":
    T.update(json.loads((HERE / "edit2_times.json").read_text()))
    build()
