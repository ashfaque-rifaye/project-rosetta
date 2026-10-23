"""Cut 3: smooth 30 fps footage (film3.py), a slow camera push into the TV, full-frame intro shots, rewards for both
children, the pocket-money story with live phone recordings, and the AWS architecture card.

  python assets/demo/edit3.py        ->  assets/demo/build/rosetta_demo_v3.mp4

Builds on edit2.py's room, phone and subtitle helpers.
"""
import json
import os
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

import arch
import edit
import edit2
from edit import B, CREAM, FPS, FT, H, MU, SERIF, VE, W, dur, end_title, end_title_card, font, rounded_mask, run, tighten_vo
from edit2 import FRAMES, ROOM, SCREEN, chip, footage_box, room_layer

HERE = Path(__file__).resolve().parent
FORCE = set(filter(None, os.environ.get("ROSETTA_FORCE", "*").split(",")))   # pieces to re-render ("*" = all)


def cached(name: str) -> Path | None:
    out = B / f"r3_{name}.mp4"
    return out if out.exists() and "*" not in FORCE and name not in FORCE else None
FRAMES["left"] = (740, 36, 100)        # a smaller TV on the left, so the phone beside it never covers the picture
edit2.PHONE_AT = (1405, 34)


def src(name: str) -> Path:
    """Cut 3 footage is .webm (host recorder); phone recordings and the parent's own video are .mp4."""
    w = FT / f"{name}.webm"
    return w if w.exists() else FT / f"{name}.mp4"


def cfr(path: Path) -> Path:
    out = B / "cfr3" / (path.stem + ".mp4")
    if not out.exists():
        out.parent.mkdir(exist_ok=True)
        run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(path), "-vf", "setpts=PTS-STARTPTS,fps=30", "-fps_mode", "cfr", "-an",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "14", "-pix_fmt", "yuv420p", str(out)])
    return out


def rpiece(name, d, frame, clip, start, speed=1.0, eyebrow="", title="", shot=None, crop_y=0, phone=None, fade_in=False, fade_out=False):
    """One scene on the TV; `phone` = (clip, start, speed) plays a phone recording beside it, `shot` a phone still."""
    if cached(name):
        return cached(name)
    s = cfr(src(clip))
    layer, (fx, fy, fw, fh), prect = room_layer(name, frame, eyebrow, title, shot, crop_y, phone_video=phone is not None)
    out = B / f"r3_{name}.mp4"
    ins = ["-ss", f"{start:.3f}", "-t", f"{d * speed + 0.5:.3f}", "-i", str(s), "-loop", "1", "-t", f"{d:.3f}", "-i", str(layer)]
    ch = [f"color=c=black:s={W}x{H}:r={FPS}:d={d:.3f}[base]",
          f"[0:v]setpts=(PTS-STARTPTS)/{speed},fps={FPS},tpad=stop_mode=clone:stop_duration=60,scale={fw}:{fh}:flags=lanczos,setsar=1[f]",
          f"[base][f]overlay={fx}:{fy}:shortest=1[a]"]
    last = "a"
    if phone is not None:
        pclip, ps, pspeed = phone
        ins += ["-ss", f"{ps:.3f}", "-t", f"{d * pspeed + 0.5:.3f}", "-i", str(cfr(src(pclip)))]
        px, py, pw, ph = prect
        ch += [f"[2:v]setpts=(PTS-STARTPTS)/{pspeed},fps={FPS},tpad=stop_mode=clone:stop_duration=60,scale={pw}:{ph}:flags=lanczos,setsar=1[m]",
               f"[a][m]overlay={px}:{py}[a2]"]
        last = "a2"
    ch.append(f"[{last}][1:v]overlay=0:0[b]")
    fades = (["fade=t=in:st=0:d=0.35"] if fade_in else []) + ([f"fade=t=out:st={d - 0.35:.3f}:d=0.35"] if fade_out else [])
    ch.append(f"[b]{','.join(fades) + ',' if fades else ''}format=yuv420p[v]")
    run(["ffmpeg", "-y", "-loglevel", "error", *ins, "-filter_complex", ";".join(ch), "-map", "[v]", "-t", f"{d:.3f}", "-r", str(FPS),
         "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p", str(out)])
    return out


def push(name, d, a, b, clip, start, hold=(0.8, 0.8), eyebrow="", title="", fade_in=False):
    """A slow camera move from framing `a` to framing `b` (eased), the app playing on the TV the whole time.
    Rendered frame by frame with sub-pixel crops, so the move is smooth, not stepped."""
    if cached(name):
        return cached(name)
    kb = FRAMES["close"][0] / (SCREEN[3] - SCREEN[1]) * 1.25          # canvas scale: the close-up still downsamples
    big = ROOM.resize((round(ROOM.width * kb), round(ROOM.height * kb)), Image.LANCZOS).convert("RGBA")
    sx, sy = SCREEN[0] * kb, SCREEN[1] * kb
    sw, sh = (SCREEN[2] - SCREEN[0]) * kb, (SCREEN[3] - SCREEN[1]) * kb
    hole = Image.new("L", big.size, 255)
    hole.paste(Image.new("L", (round(sw), round(sh)), 0), (round(sx), round(sy)))
    big.putalpha(hole)
    fx, fy, fw, fh = footage_box((round(sx), round(sy), round(sw), round(sh)))
    label = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if title:
        chip(label, eyebrow, title)

    def crop_box(fr):
        screen_h, left, top = fr
        z = screen_h / sh                                              # output px per canvas px
        return (sx - left / z, sy - top / z, sx - left / z + W / z, sy - top / z + H / z)

    ca, cb = crop_box(FRAMES[a]), crop_box(FRAMES[b])
    n = round(d * FPS)
    s = cfr(src(clip))
    raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{start:.3f}", "-i", str(s), "-frames:v", str(n), "-vf", f"fps={FPS},scale={fw}:{fh}:flags=lanczos",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
    fsz = fw * fh * 3
    out = B / f"r3_{name}.mp4"
    enc = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                            "-vf", "fade=t=in:st=0:d=0.35" if fade_in else "null", "-c:v", "libx264", "-preset", "medium", "-crf", "17",
                            "-pix_fmt", "yuv420p", str(out)], stdin=subprocess.PIPE)
    canvas = Image.new("RGBA", big.size, (0, 0, 0, 255))
    frames = max(1, len(raw) // fsz)
    for i in range(n):
        t = i / FPS
        u = min(1.0, max(0.0, (t - hold[0]) / max(0.01, d - hold[0] - hold[1])))
        e = u * u * (3 - 2 * u)                                        # ease in and out
        box = tuple(ca[k] + (cb[k] - ca[k]) * e for k in range(4))
        j = min(i, frames - 1)
        canvas.paste(Image.frombytes("RGB", (fw, fh), raw[j * fsz:(j + 1) * fsz]), (fx, fy))
        comp = Image.alpha_composite(canvas, big)
        frame = comp.transform((W, H), Image.EXTENT, box, Image.BICUBIC)
        frame.alpha_composite(label)
        enc.stdin.write(frame.convert("RGB").tobytes())
    enc.stdin.close()
    enc.wait()
    return out


def cinematic(name, d, clip, start, line, fade_in=False, fade_out=False):
    """A full-frame Veo shot (nothing cropped) with its question as a film title over a soft shade."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shade).rectangle((0, 820, W, H), fill=(0, 0, 0, 120))
    img.alpha_composite(shade.filter(ImageFilter.GaussianBlur(60)))
    dr = ImageDraw.Draw(img)
    f = font(SERIF, 64)
    tw = dr.textlength(line, font=f)
    dr.text((W / 2 - tw / 2 + 2, 902), line, font=f, fill=(0, 0, 0, 200))
    dr.text((W / 2 - tw / 2, 900), line, font=f, fill=(*CREAM, 255))
    ov = B / f"cine3_{name}.png"
    img.save(ov)
    return edit.piece(name, d, "full", clip, start, overlay=ov, fade_in=fade_in, fade_out=fade_out)


def arch_piece(d: float) -> Path:
    """The architecture card, revealed column by column, then held."""
    imgs = arch.build()
    step = 0.7
    ins, ch = [], []
    for i, p in enumerate(imgs):
        last = i == len(imgs) - 1
        ins += ["-loop", "1", "-t", f"{d if last else step + 0.4:.2f}", "-i", str(p)]
    prev = "0:v"
    off = 0.0
    for i in range(1, len(imgs)):
        off += step
        ch.append(f"[{prev}][{i}:v]xfade=transition=fade:duration=0.4:offset={off:.2f}[x{i}]")
        prev = f"x{i}"
    ch.append(f"[{prev}]fade=t=in:st=0:d=0.3,fade=t=out:st={d - 0.35:.2f}:d=0.35,format=yuv420p[v]")
    out = B / "r3_arch.mp4"
    run(["ffmpeg", "-y", "-loglevel", "error", *ins, "-filter_complex", ";".join(ch), "-map", "[v]", "-t", f"{d:.2f}", "-r", str(FPS),
         "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p", str(out)])
    return out


def build():
    B.mkdir(parents=True, exist_ok=True)
    edit.BG = edit.background()
    vo_len = tighten_vo()
    import make_demo  # the narration lines (the generator stays local, see .gitignore)
    vo_text = make_demo.VO
    tl = []

    def add(p, d, key=None, off=0.4):
        tl.append((p, d, key, off))

    # ---------- intro ----------
    add(edit.piece("i1", 5.6, "full", VE / "intro_city.mp4", 0.0, fade_in=True), 5.6, "01_hook", 0.5)
    add(cinematic("i2", 3.0, VE / "intro_q1w.mp4", 0.6, "“Did you finish your homework?”"), 3.0, "02_q", 0.4)
    add(cinematic("i3", 3.0, VE / "intro_q2w.mp4", 1.2, "“Can I watch TV now?”"), 3.0)
    add(edit.piece("i4", 3.6, "full", VE / "intro_tv.mp4", 1.2), 3.6, "03_what_if", 0.3)
    add(edit.piece("i5", 7.2, "full", cfr(src("x_launch")), 4.4, fade_out=True), 7.2, "04_title", 1.6)

    # ---------- the app on the family TV: the camera walks up to it ----------
    add(push("h1", 6.5, "wide", "close", "t13_home", 1.0, hold=(1.0, 0.6), fade_in=True), 6.5, "05_home", 0.4)
    add(rpiece("h2", 8.2, "close", "t13_home", 7.5, eyebrow="Tonight, on Fire TV", title="A living home, full of quests", fade_out=True), 8.2)

    add(rpiece("m1", 4.5, "left", "t02_meera_hw", 6.0, eyebrow="✦ AI Tutor", title="Meera snaps her workbook", shot="phone_meera.png", crop_y=0, fade_in=True), 4.5, "06_homework", 0.4)
    add(rpiece("m2", 4.0, "close", "t02_meera_hw", 17.0, eyebrow="✦ AI Tutor", title="Reading every answer"), 4.0)
    add(rpiece("m3", 5.0, "close", "t02_meera_hw", 29.6, eyebrow="✦ AI Tutor", title="Marked on her own handwriting"), 5.0)
    add(rpiece("m4", 3.0, "close", "t02_meera_hw", 33.0, eyebrow="✦ AI Tutor", title="A hint, never the answer"), 3.0)
    add(rpiece("c1", 4.0, "close", "t03_arjun_code", 21.6, eyebrow="✦ Coding class", title="Arjun's programs, traced line by line"), 4.0, "06b_coding", 0.3)
    add(rpiece("c2", 4.0, "close", "x_arjun_practice", 0.4, eyebrow="✦ Coding class", title="Practice built on his own mistakes"), 4.0)
    add(rpiece("p1", 5.0, "close", "t02_meera_hw", 39.0, eyebrow="✦ Practice", title="Questions that make them think", fade_out=True), 5.0, "07_practice", 0.3)

    add(rpiece("o1", 6.5, "close", "t05_honest", 15.0, eyebrow="✦ Honest AI", title="Wrong photo? It says what it sees", fade_in=True), 6.5, "08_honest", 0.4)
    add(rpiece("t1", 4.0, "close", "t06_room", 23.5, eyebrow="✦ Room check", title="Lost Things, found in Meera's room"), 4.0, "08b_room", 0.3)
    add(rpiece("t2", 4.5, "close", "t06_room", 40.5, eyebrow="✦ Room check", title="Checked again: 5 of 6 back home", fade_out=True), 4.5)

    add(rpiece("v1", 6.0, "left", "t07_voice", 35.0, eyebrow="✦ Voice notes", title="Arjun tells Rosetta what he read", shot="phone_kid_voice.png", crop_y=500, fade_in=True), 6.0, "09_voice", 0.4)
    add(rpiece("v2", 5.0, "left", "t07_voice", 39.5, eyebrow="✦ Memories", title="Kept for Mom and Dad", shot="phone_parent.png", crop_y=2170, fade_out=True), 5.0)

    add(rpiece("w1", 3.6, "close", "x_meera_reward", 3.0, eyebrow="Rewards", title="Well done, Meera!", fade_in=True), 3.6, "10_rewards", 0.3)
    add(rpiece("w2", 3.2, "close", "x_arjun_reward", 3.4, eyebrow="Rewards", title="Well done, Arjun!"), 3.2)
    add(rpiece("w3", 2.2, "close", "x_meera_reward", 12.4, eyebrow="Family Goal", title="Every star counts toward Movie Night", fade_out=True), 2.2)

    add(rpiece("n1", 4.0, "left", "x_treasure_live", 15.0, eyebrow="Pocket money", title="Meera asks for her colour pencils", phone=("p_kid_ask", 1.2, 1.0), fade_in=True), 4.0, "10b_money", 0.3)
    add(rpiece("n2", 4.2, "left", "x_treasure_live", 29.6, eyebrow="Pocket money", title="Mom says yes with one tap", phone=("p_mom_approve", 1.6, 1.0)), 4.2)
    add(rpiece("n3", 3.2, "close", "x_treasure_live", 32.6, eyebrow="Pocket money", title="Her balance updates on the TV", fade_out=True), 3.2)

    add(rpiece("a1", 4.0, "left", "t08_unlock", 2.0, eyebrow="For parents", title="A ping, and one tap", phone=("mom_phone", 0.0, 1.0), fade_in=True), 4.0, "11_parents", 0.4)
    add(rpiece("a2", 6.0, "left", "t08_unlock", 4.5, eyebrow="TV time", title="Unlocked from the phone, live", phone=("mom_phone", 40.5, 1.0)), 6.0)
    add(rpiece("a3", 4.5, "left", "t13_home", 10.0, eyebrow="Mom's phone", title="Daily tasks, set in seconds", phone=("mom_phone", 10.0, 2.2)), 4.5, "11b_mom", 0.3)
    add(rpiece("a4", 5.0, "left", "x_arjun", 0.0, eyebrow="Mom's phone", title="Each child's class and next test", phone=("mom_phone", 34.0, 1.2), fade_out=True), 5.0)

    add(rpiece("q1", 3.5, "close", "t09_quiz", 13.0, eyebrow="Family Quiz Night", title="A 3D game for the family", fade_in=True), 3.5, "12_quiz", 0.4)
    add(rpiece("q2", 4.0, "close", "t09_quiz", 42.5, eyebrow="Family Quiz Night", title="Code tracing for Arjun"), 4.0)
    add(rpiece("q3", 3.5, "close", "t09_quiz", 51.0, eyebrow="Family Quiz Night", title="Word problems for Meera", fade_out=True), 3.5)

    add(rpiece("k1", 3.2, "close", "x_arjun", 0.0, eyebrow="✦ Coach's notes", title="Arjun's week", fade_in=True), 3.2, "13_progress", 0.3)
    add(rpiece("k2", 3.2, "close", "x_meera", 0.0, eyebrow="✦ Coach's notes", title="Meera's week", fade_out=True), 3.2)

    add(arch_piece(6.0), 6.0, "14_tech", 0.6)

    add(rpiece("z1", 2.6, "close", "x_squad", 0.0, eyebrow="Squad", title="Lost Things to collect", fade_in=True), 2.6, "14b_more", 0.3)
    add(rpiece("z2", 2.6, "close", "x_streaks", 0.0, eyebrow="Streaks", title="Stamps and day streaks"), 2.6)
    add(rpiece("z3", 2.6, "close", "x_movie", 0.0, eyebrow="Movie Night", title="Nine stars unlock it for the family"), 2.6)
    add(rpiece("z4", 2.6, "close", "x_parents", 0.0, eyebrow="For parents", title="Scan the TV to open the parent page", fade_out=True), 2.6)

    outro_start = sum(t[1] for t in tl)
    add(edit.piece("e1", 7.0, "full", VE / "outro_home.mp4", 0.0, overlay=end_title(footer=False), fade_in=True), 7.0, "15_outro", 1.0)
    add(edit.piece("e2", 2.5, "image", image=end_title_card(), fade_out=True), 2.5)
    total = sum(t[1] for t in tl)
    intro_end = sum(t[1] for t in tl[:5])

    # narration must never run into the next line
    t, cues = 0.0, []
    for p, d, key, off in tl:
        if key:
            cues.append((key, t + off))
        t += d
    for (k1, a1), (k2, a2) in zip(cues, cues[1:]):
        if a1 + vo_len[k1] > a2 - 0.15:
            print(f"OVERLAP {k1} ends {a1 + vo_len[k1]:.1f} > {k2} starts {a2:.1f}")

    lst = B / "pieces_v3.txt"
    lst.write_text("".join(f"file '{p.as_posix()}'\n" for p, *_ in tl), encoding="utf-8")
    run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", str(B / "video_v3.mp4")])

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
    run(["ffmpeg", "-y", "-loglevel", "error", *ins, "-filter_complex", ";".join(fl), "-map", "[a]", "-c:a", "aac", "-b:a", "192k", str(B / "audio_v3.m4a")])

    edit2.write_subs(cues, vo_text, vo_len, skip={"02_q"})
    final = B / "rosetta_demo_v3.mp4"
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", "video_v3.mp4", "-i", "audio_v3.m4a", "-vf", "ass=subs.ass:fontsdir=fonts",
                        "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p", "-c:a", "copy",
                        "-shortest", "-movflags", "+faststart", final.name], cwd=B, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(r.stderr[-1500:])
    (B / "timeline_v3.json").write_text(json.dumps([{"piece": p.name, "dur": round(d, 2), "vo": k} for p, d, k, _ in tl], indent=1))
    print("done", final, round(total, 1), "s")


if __name__ == "__main__":
    build()
