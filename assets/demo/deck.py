"""The project deck, in the demo's look (night navy, gold, serif titles): 16 slides as a PDF and a PowerPoint.

  python assets/demo/deck.py      ->  submission/Project-Rosetta-Deck.pdf and .pptx

Every screenshot is a still from the filmed app (film3.py) running against the live AWS stack.
"""
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

from edit import CREAM, GOLD, MIST, SANS, SANS_SB, SERIF, SYM, font

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
FT = HERE / "footage"
OUT = ROOT / "submission"
W, H = 1920, 1080
NAVY = (9, 14, 34)
ORANGE = (255, 153, 0)
LIME = (190, 240, 90)


def still(src: str, t: float) -> Image.Image:
    p = HERE / src if "/" in src else (FT / f"{src}.webm" if (FT / f"{src}.webm").exists() else FT / f"{src}.mp4")
    raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{t:.2f}", "-i", str(p), "-frames:v", "1", "-vf", "scale=1920:1080",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
    return Image.frombytes("RGB", (1920, 1080), raw)


def base(n: int) -> Image.Image:
    img = Image.new("RGB", (W, H), NAVY)
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((W * 0.55, -H * 0.45, W * 1.25, H * 0.45), fill=(80, 55, 15))
    gd.ellipse((-W * 0.25, H * 0.5, W * 0.45, H * 1.4), fill=(25, 40, 100))
    img = Image.blend(img, glow.filter(ImageFilter.GaussianBlur(200)), 0.6)
    d = ImageDraw.Draw(img)
    d.text((W - 90, H - 52), f"{n:02d}", font=font(SANS_SB, 20), fill=MIST)
    d.text((80, H - 52), "PROJECT ROSETTA", font=font(SANS_SB, 18), fill=(*GOLD,))
    return img


def header(img, eyebrow: str, title: str, sub: str = ""):
    d = ImageDraw.Draw(img)
    d.text((80, 64), eyebrow.upper(), font=font(SANS_SB, 24), fill=GOLD)
    d.text((80, 100), title, font=font(SERIF, 64), fill=CREAM)
    if sub:
        d.text((80, 186), sub, font=font(SANS, 28), fill=MIST)


def pic(img, im: Image.Image, box, radius=22):
    x, y, w, h = box
    r = im.copy()
    r.thumbnail((w, h), Image.LANCZOS)
    if r.size != (w, h):
        r = im.resize((w, h), Image.LANCZOS) if abs(im.width / im.height - w / h) < 0.02 else r
    w, h = r.size
    sh = Image.new("L", img.size, 0)
    ImageDraw.Draw(sh).rounded_rectangle((x + 8, y + 16, x + w + 8, y + h + 16), radius=radius, fill=160)
    img.paste((0, 0, 0), (0, 0), sh.filter(ImageFilter.GaussianBlur(20)))
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    img.paste(r, (x, y), m)
    ImageDraw.Draw(img).rounded_rectangle((x, y, x + w, y + h), radius=radius, outline=(255, 255, 255), width=2)
    return w, h


def bullets(img, items, x, y, width, size=30, gap=22, mark="•", color=CREAM):
    d = ImageDraw.Draw(img)
    f = font(SANS, size)
    fb = font(SANS_SB, size)
    for it in items:
        head, _, rest = it.partition("|")
        d.text((x, y), mark, font=fb, fill=GOLD)
        cx = x + 34
        words = ([(w, fb) for w in head.split()] if rest else []) + [(w, f) for w in (rest or head).split()]
        line_y = y
        for w, ff in words:
            ww = d.textlength(w + " ", font=ff)
            if cx + ww > x + width:
                cx = x + 34
                line_y += size + 10
            d.text((cx, line_y), w, font=ff, fill=color if ff is f else CREAM)
            cx += ww
        y = line_y + size + gap
    return y


def chip(img, x, y, text, fill=(255, 153, 0), size=24):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    f = font(SANS_SB, size)
    tw = d.textlength(text, font=f)
    d.rounded_rectangle((x, y, x + tw + 40, y + size + 24), radius=(size + 24) // 2, fill=(*fill, 40), outline=(*fill, 200), width=2)
    d.text((x + 20, y + 11), text, font=f, fill=(*CREAM, 255))
    base_rgba = img.convert("RGBA")
    base_rgba.alpha_composite(layer)
    img.paste(base_rgba.convert("RGB"))
    return x + tw + 56


# ------------------------------------------------------------------------------------------------ slides
def s_title(n):
    bg = still("build/video_v3.mp4", 23.2).filter(ImageFilter.GaussianBlur(3))
    img = Image.blend(bg, Image.new("RGB", (W, H), NAVY), 0.62)
    d = ImageDraw.Draw(img)
    f = font(SERIF, 150)
    t = "R O S E T T A"
    d.text(((W - d.textlength(t, font=f)) / 2, 300), t, font=f, fill=GOLD)
    for i, (line, ff, col) in enumerate([("The TV that works for the family, not against it", font(SERIF, 52), CREAM),
                                         ("Homework, chores and daily habits earn TV time and real pocket money,", font(SANS, 32), MIST),
                                         ("checked by an AI agent crew that runs serverless on AWS.", font(SANS, 32), MIST)]):
        d.text(((W - d.textlength(line, font=ff)) / 2, 520 + i * 70 + (14 if i else 0)), line, font=ff, fill=col)
    x = 640
    for c in ("Fire TV track", "AWS Builder", "Open Source"):
        x = chip(img, x, 800, c)
    d = ImageDraw.Draw(img)
    s = "Amazon Developer · Build, Ship, Shape hackathon 2026"
    d.text(((W - d.textlength(s, font=font(SANS_SB, 22))) / 2, 960), s, font=font(SANS_SB, 22), fill=MIST)
    return img


def s_problem(n):
    img = base(n)
    header(img, "The problem", "Every evening, the same two questions", "In front of the same screen, in millions of homes")
    a, b = still("veo/intro_q1w.mp4", 4.0), still("veo/intro_q2w.mp4", 4.0)
    pic(img, a, (80, 270, 860, 484))
    pic(img, b, (980, 270, 860, 484))
    d = ImageDraw.Draw(img)
    d.text((80, 776), "“Did you finish your homework?”", font=font(SERIF, 44), fill=CREAM)
    d.text((980, 776), "“Can I watch TV now?”", font=font(SERIF, 44), fill=CREAM)
    bullets(img, ["Parents nag and kids bargain; the TV, the biggest shared screen at home, is only ever the prize that gets switched off.",
                  "Homework help, chores and screen-time rules live in different apps, or in nobody's app at all."], 80, 860, 1760, size=26, gap=12)
    return img


def s_idea(n):
    img = base(n)
    header(img, "The idea", "What if the TV itself could help?", "Rosetta translates between parents and kids, like the Rosetta Stone")
    cols = [("Parents set the day", ["From any phone: homework, chores and daily habits", "What each task pays in pocket money",
                                     "Each child's class, subjects and next test"]),
            ("Kids check in", ["Snap a worksheet or a room", "A tick, a note or a voice note", "Ask for a wish when they've saved enough"]),
            ("The TV keeps it fair", ["✦ AI checks the work honestly", "XP, coins, stars and real pocket money",
                                      "TV time unlocks when the must-dos are done"])]
    for i, (t, items) in enumerate(cols):
        x = 80 + i * 600
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle((x, 290, x + 560, 860), radius=30, fill=(255, 255, 255, 14), outline=(244, 197, 106, 120), width=2)
        rgba = img.convert("RGBA"); rgba.alpha_composite(layer); img.paste(rgba.convert("RGB"))
        d = ImageDraw.Draw(img)
        d.text((x + 40, 330), f"0{i + 1}", font=font(SERIF, 70), fill=GOLD)
        d.text((x + 40, 430), t, font=font(SERIF, 44), fill=CREAM)
        bullets(img, [s.replace("✦ ", "") for s in items], x + 40, 520, 490, size=27, gap=18)
    return img


def s_loop(n):
    img = base(n)
    header(img, "How a day works", "One loop, from phone to TV, in about fifteen seconds")
    steps = [("Parent", "adds tasks and sets pocket money"), ("Kid", "snaps a worksheet or records a voice note"),
             ("S3 → Lambda", "the upload starts the Strands agent crew"), ("AI crew", "marks, checks honestly, writes practice"),
             ("Rewards", "XP, coins, a star, real pocket money"), ("TV time", "unlocks when the must-dos are done")]
    d = ImageDraw.Draw(img)
    for i, (t, s) in enumerate(steps):
        x = 80 + i * 300
        cx, cy = x + 120, 420
        d.ellipse((cx - 90, cy - 90, cx + 90, cy + 90), outline=GOLD, width=4, fill=(20, 28, 58))
        d.text((cx - d.textlength(str(i + 1), font=font(SERIF, 80)) / 2, cy - 52), str(i + 1), font=font(SERIF, 80), fill=GOLD)
        if i < len(steps) - 1:
            d.line((cx + 100, cy, cx + 200, cy), fill=GOLD, width=4)
            d.polygon([(cx + 200, cy), (cx + 186, cy - 9), (cx + 186, cy + 9)], fill=GOLD)
        d.text((cx - d.textlength(t, font=font(SANS_SB, 32)) / 2, 540), t, font=font(SANS_SB, 32), fill=CREAM)
        yy = 590
        for line in _wrap(d, s, font(SANS, 25), 250):
            d.text((cx - d.textlength(line, font=font(SANS, 25)) / 2, yy), line, font=font(SANS, 25), fill=MIST)
            yy += 34
    bullets(img, ["Nothing on screen is sample data:| every number comes from the family's real history on the server.",
                  "Co-op only:| siblings are never ranked; rewards are earned, never random."], 80, 800, 1760, size=28, gap=16)
    return img


def _wrap(d, text, f, width):
    out, cur = [], ""
    for w in text.split():
        if d.textlength((cur + " " + w).strip(), font=f) > width:
            out.append(cur); cur = w
        else:
            cur = (cur + " " + w).strip()
    return out + [cur]


def s_feature(n, eyebrow, title, sub, shot, points):
    img = base(n)
    header(img, eyebrow, title, sub)
    pic(img, shot, (80, 270, 1120, 630))
    bullets(img, points, 1250, 290, 600, size=27, gap=20)
    return img


def s_two(n, eyebrow, title, sub, a, b, ca, cb):
    img = base(n)
    header(img, eyebrow, title, sub)
    pic(img, a, (80, 270, 860, 484))
    pic(img, b, (980, 270, 860, 484))
    y = 790
    bullets(img, ca, 80, y, 840, size=26, gap=12)
    bullets(img, cb, 980, y, 840, size=26, gap=12)
    return img


def s_money(n):
    img = base(n)
    header(img, "Real pocket money", "Earned, saved, and spent with a yes", "Filmed live: Meera asks for colour pencils, Mom approves, the TV updates")
    pic(img, still("x_treasure_live", 40.0), (80, 270, 1100, 619))
    ph = [still("p_kid_ask", 6.0), still("p_mom_approve", 7.5)]
    for i, p in enumerate(ph):
        p = p.crop((0, 0, 1030, 2230)) if p.height >= 2230 else p
        pic(img, p.resize((290, 628)), (1230 + i * 320, 262, 290, 628), radius=30)
    bullets(img, ["Parents choose|what each daily task pays (₹ per task, or per coin earned).",
                  "Kids save toward a wish list|and ask from their phone when they have enough.",
                  "One tap from Mom or Dad|approves it; the balance updates on the TV."], 80, 920, 1760, size=25, gap=6)
    return img


def s_arch(n):
    return Image.open(HERE / "build" / "arch_3.png").convert("RGB")


def s_aws(n):
    img = base(n)
    header(img, "AWS Builder", "Why it's built this way", "Serverless, event-driven, one CDK stack, pay-per-use")
    rows = [("Strands Agents SDK", "Quest Master agent picks the specialist tools for each photo or voice note"),
            ("Amazon Bedrock", "Converse API tool specs with forced JSON; Amazon Nova Pro; Nova grounding boxes"),
            ("AWS Lambda", "API, agent crew (S3-triggered) and scheduled jobs: three functions"),
            ("Amazon S3 events", "Presigned phone uploads start the agent run; photos deleted after 1 day"),
            ("Amazon DynamoDB", "One household document per family; the TV polls it once a second"),
            ("API Gateway · CloudFront", "One HTTP API for TV and phones; HTTPS phone pages and TV downloads"),
            ("EventBridge · SES · SNS", "Streak nudges, weekly digest, email and SMS alerts to parents"),
            ("AWS CDK", "Everything in one stack: npx cdk deploy; scales to zero between homework sessions")]
    d = ImageDraw.Draw(img)
    y = 270
    for k, v in rows:
        d.rounded_rectangle((80, y, 92, y + 56), radius=4, fill=ORANGE)
        d.text((116, y + 8), k, font=font(SANS_SB, 30), fill=CREAM)
        d.text((560, y + 12), v, font=font(SANS, 26), fill=MIST)
        y += 76
    d.text((80, 900), "Trust layer: the model reads the handwriting, exact maths in code decides the verdict (11 of 11 on our test sheets).",
           font=font(SANS_SB, 26), fill=GOLD)
    return img


def s_crew(n):
    img = base(n)
    header(img, "The agent crew", "Specialists that must say what they saw", "Kids never see agent names: on screen it's a plain ✦ step list".replace("✦ ", ""))
    crew = [("Quest Master", "Strands agent: decides what to run and when to ask a parent"), ("AI Tutor", "Marks homework, traces code, writes practice and quizzes"),
            ("Scout · Judge", "Finds what's out of place; compares before and after"), ("Listener", "Transcribes and summarises voice notes"),
            ("Proof check", "Says honestly whether a photo shows the task done"), ("Router", "Sends low-confidence verdicts to a parent"),
            ("Coach", "Writes notes from real activity only"), ("Keeper", "The friendly voice that talks to the child")]
    d = ImageDraw.Draw(img)
    for i, (k, v) in enumerate(crew):
        x, y = 80 + (i % 2) * 900, 280 + (i // 2) * 150
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle((x, y, x + 860, y + 124), radius=24, fill=(255, 255, 255, 14), outline=(255, 153, 0, 110), width=2)
        rgba = img.convert("RGBA"); rgba.alpha_composite(layer); img.paste(rgba.convert("RGB"))
        d = ImageDraw.Draw(img)
        d.text((x + 30, y + 18), k, font=font(SANS_SB, 32), fill=CREAM)
        d.text((x + 30, y + 66), v, font=font(SANS, 25), fill=MIST)
    d.text((80, 900), "Every specialist is one multimodal call with a forced JSON schema, including a 'what I see' field.", font=font(SANS_SB, 26), fill=GOLD)
    return img


def s_firetv(n):
    img = base(n)
    header(img, "Built for Fire TV", "Native, D-pad first, 10-foot design", "Kotlin · Jetpack Compose for TV · Filament 3D · Media3 · Unity 6")
    pic(img, still("t13_home", 6.0), (80, 270, 1000, 563))
    bullets(img, ["One 1440 × 810 canvas|scaled to the panel: 720p, 1080p (960 × 540 dp) and 4K look identical.",
                  "Real-time 3D rewards|(coins, chest, padlock, trophy) with one shared Filament engine.",
                  "Unity 6 Quiz Night|as a companion Fire OS app: ARMv7, ARM64 and x86_64.",
                  "Earned TV time|launches Prime Video, YouTube or Netflix and counts the minutes.",
                  "Runs on Fire OS 16|(Android TV emulator, API 36) and a 4K Android TV."], 1130, 280, 720, size=26, gap=18)
    bullets(img, ["Offline mode:| the whole flow also runs on-device with just a remote."], 80, 880, 1700, size=26)
    return img


def s_oss(n):
    img = base(n)
    header(img, "Open Source", "compose-vision-overlay", "A new MIT library: AI vision results as a 10-foot overlay for Android TV and Fire TV")
    pic(img, still("t06_room", 26.0), (80, 270, 1000, 563))
    bullets(img, ["Draws model boxes|on the image as it's fitted on screen.",
                  "Staggered spring entrances,|attention pulses and 'resolved' exits.",
                  "Never steals D-pad focus;|helpers for both 0-1000 box conventions.",
                  "Unit tests and a sample TV app.",
                  "Rosetta itself is open source too|(MIT): TV app, agent crew, CDK stack."], 1130, 280, 720, size=26, gap=18)
    d = ImageDraw.Draw(img)
    d.text((80, 880), "github.com/ashfaque-rifaye/compose-vision-overlay  ·  github.com/ashfaque-rifaye/project-rosetta", font=font(SANS_SB, 26), fill=GOLD)
    return img


def s_next(n):
    img = base(n)
    header(img, "What's next", "From honoured to enforced", "")
    bullets(img, ["Amazon Kids 'earn time' API|so earned TV time is enforced, not honoured (our #1 feature request).",
                  "Alexa voice answers|during Family Quiz Night.",
                  "Reading-aloud fluency checks,|more subjects and grades.",
                  "A Vega OS build|once the Vega SDK runs on Windows.",
                  "Fire TV Appstore launch|for families in India first: pocket money in ₹, Hindi voice."], 80, 240, 1760, size=32, gap=24)
    d = ImageDraw.Draw(img)
    links = [("Try the TV app", "dr8w3l96syzm7.cloudfront.net/tv.html"), ("Parent page", "dr8w3l96syzm7.cloudfront.net/parent.html?code=DEMO  (PIN 2468)"),
             ("Code (MIT)", "github.com/ashfaque-rifaye/project-rosetta"), ("Friction log", "dr8w3l96syzm7.cloudfront.net/friction-log.html")]
    y = 690
    for k, v in links:
        d.text((80, y), k, font=font(SANS_SB, 28), fill=GOLD)
        d.text((380, y), v, font=font(SANS, 28), fill=CREAM)
        y += 56
    return img


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    slides = [
        s_title, s_problem, s_idea, s_loop,
        lambda n: s_feature(n, "✦ AI Tutor".replace("✦ ", ""), "Homework, marked on their own handwriting", "Snap with any phone; marked on the TV in about fifteen seconds",
                            still("t02_meera_hw", 31.0), ["Every answer|ticked or crossed on the child's writing, with a score ring.",
                                                          "Exact maths in code|double-checks every arithmetic verdict.",
                                                          "A hint, never the answer,|for each mistake.",
                                                          "Real result:| Meera's workbook, 9 of 12."]),
        lambda n: s_two(n, "Coding class and practice", "From worksheets to code", "The Tutor traces little programs line by line",
                        still("x_arjun_practice", 24.0), still("t02_meera_hw", 42.5),
                        ["Arjun's loops-and-variables sheet:| 4 of 6, with a hint on each slip."],
                        ["Practice on exactly what slipped:| easy reasoning, not '1 + 1'."]),
        lambda n: s_two(n, "Honest AI", "It always says what it actually saw", "Wrong photos are called out; tidy-ups are checked twice",
                        still("t05_honest", 20.0), still("t06_room", 42.0),
                        ["'That's a lovely photo of your room, but I need your Fractions worksheet.'"],
                        ["Lost Things found in Meera's room; after photo: 5 of 6 back home."]),
        lambda n: s_two(n, "Daily habits", "Voice notes become memories", "Make your bed · Read 10 pages · Prayer time",
                        still("t07_voice", 40.0), still("x_meera_reward", 8.0),
                        ["A tick, a note, a photo or a voice note;| transcribed and summarised, kept 90 days."],
                        ["Rewards for every child:| XP, coins, stamps and a star for Movie Night."]),
        s_money,
        lambda n: s_two(n, "Parents in control", "TV time, unlocked when it's earned", "Or by a parent with one tap from their phone",
                        still("t08_unlock", 10.0), still("x_parents", 5.0),
                        ["The portal opens when the must-dos are done;| Rosetta opens the streaming app and counts minutes."],
                        ["Scan the QR on the TV:| alerts, approvals, daily tasks, pocket money. No app to install."]),
        lambda n: s_two(n, "Family Quiz Night and Progress", "Play together, see the whole picture", "Unity 6 in 3D · ✦ Coach's notes from real activity".replace("✦ ", ""),
                        still("t09_quiz", 46.0), still("x_arjun", 5.0),
                        ["Reasoning questions for each child's class:| code tracing for Arjun, word problems for Meera."],
                        ["Habit calendar, homework accuracy, pocket money and stars,| per child."]),
        s_arch, s_aws, s_crew, s_firetv, s_oss, s_next,
    ]
    pages = [f(i + 1) for i, f in enumerate(slides)]
    pdf = OUT / "Project-Rosetta-Deck.pdf"
    pages[0].save(pdf, save_all=True, append_images=pages[1:], resolution=144, quality=90)
    from pptx import Presentation
    from pptx.util import Emu
    prs = Presentation()
    prs.slide_width, prs.slide_height = Emu(12192000), Emu(6858000)
    tmp = OUT / "_slides"
    tmp.mkdir(exist_ok=True)
    for i, p in enumerate(pages):
        f = tmp / f"{i + 1:02d}.jpg"
        p.save(f, quality=92)
        prs.slides.add_slide(prs.slide_layouts[6]).shapes.add_picture(str(f), 0, 0, prs.slide_width, prs.slide_height)
    prs.save(OUT / "Project-Rosetta-Deck.pptx")
    print(pdf, len(pages), "slides")


if __name__ == "__main__":
    build()
