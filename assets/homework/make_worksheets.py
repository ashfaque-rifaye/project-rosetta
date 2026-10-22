"""Render the demo household's homework as phone-style photos (printed sheet, pencil answers, on a desk).

  python assets/homework/make_worksheets.py
Writes assets/homework/{arjun_fractions,meera_numbers}.jpg plus *.truth.json with the real answers, so the Tutor
agent's marking can be checked. Fonts: Patrick Hand (handwriting) and Nunito (print), both SIL OFL, in fonts/.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FONTS = HERE / "fonts"
W, H = 1240, 1650  # A4-ish paper at ~150 dpi

SHEETS = {
    "arjun_fractions": {
        "title": "Fractions Practice", "name": "Arjun", "class": "Class 3B", "subject": "Maths",
        "instructions": "Solve each problem. Write your answer on the line.",
        "items": [
            ("1/2 + 1/4 =", "3/4", "3/4"),
            ("Which is bigger: 2/3 or 3/5 ?", "2/3", "2/3"),
            ("3/8 + 2/8 =", "5/16", "5/8"),            # added the denominators too
            ("Half of 12 is", "6", "6"),
            ("Write 4/8 in its simplest form.", "2/4", "1/2"),  # not fully simplified
            ("2/5 + 1/5 =", "3/5", "3/5"),
        ],
    },
    "meera_numbers": {
        "title": "Adding and Taking Away", "name": "Meera", "class": "Class 1A", "subject": "Maths",
        "instructions": "Work out each sum.",
        "items": [
            ("7 + 5 =", "12", "12"),
            ("15 - 6 =", "8", "9"),                    # off by one when counting back
            ("9 + 9 =", "18", "18"),
            ("20 - 11 =", "9", "9"),
            ("6 + 8 =", "14", "14"),
        ],
    },
}


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def paper(sheet, rnd):
    im = Image.new("RGB", (W, H), (250, 248, 242))
    d = ImageDraw.Draw(im)
    # faint print grain
    for _ in range(9000):
        x, y = rnd.randrange(W), rnd.randrange(H)
        g = rnd.randrange(232, 246)
        d.point((x, y), fill=(g, g, g - 4))
    bold, reg, hand = font("Nunito.ttf", 58), font("Nunito.ttf", 30), font("PatrickHand.ttf", 64)
    bold.set_variation_by_axes([800])
    reg.set_variation_by_axes([500])
    ink = (40, 44, 52)
    d.text((90, 90), sheet["title"], font=bold, fill=ink)
    d.text((90, 175), f"Name: ", font=reg, fill=ink)
    d.text((W - 380, 175), sheet["class"], font=reg, fill=ink)
    d.line((185, 212, 560, 212), fill=(120, 120, 120), width=2)
    d.text((200, 150), sheet["name"], font=hand, fill=(70, 78, 96))
    d.text((90, 250), sheet["instructions"], font=reg, fill=(90, 90, 96))
    truth = []
    y = 350
    step = (H - 450) // len(sheet["items"])
    for i, (q, kid, correct) in enumerate(sheet["items"], 1):
        d.text((90, y), f"{i}.", font=reg, fill=ink)
        d.text((150, y), q, font=reg, fill=ink)
        qw = d.textlength(q, font=reg)
        x0 = int(150 + qw + 40)
        if "?" in q or "form" in q:
            x0, y_ans = 150, y + 70
        else:
            y_ans = y - 22
        d.line((x0, y_ans + 80, x0 + 260, y_ans + 80), fill=(150, 150, 150), width=2)
        # pencil answer, slightly rotated and jittered
        tw = int(d.textlength(kid, font=hand)) + 40
        layer = Image.new("RGBA", (tw, 110), (0, 0, 0, 0))
        ImageDraw.Draw(layer).text((20, 10), kid, font=hand, fill=(64, 72, 90, 235))
        layer = layer.rotate(rnd.uniform(-5, 5), resample=Image.BICUBIC, expand=True)
        ax, ay = x0 + 20 + rnd.randint(-6, 6), y_ans + rnd.randint(-8, 4)
        im.paste(layer, (ax, ay), layer)
        top = y - 20
        bottom = max(y_ans + 100, y + 60)
        truth.append({"n": i, "question": q, "kid_answer": kid, "correct_answer": correct, "is_correct": kid == correct,
                      "box_0_1": [round(80 / W, 3), round(top / H, 3), round(min(W - 60, max(x0 + 300, 150 + qw + 60)) / W, 3), round(bottom / H, 3)]})
        y += step
    return im, truth


def desk(rnd, size=(1600, 1200)):
    w, h = size
    im = Image.new("RGB", size)
    px = im.load()
    for yy in range(h):
        for xx in range(0, w):
            grain = math.sin(yy * 0.045 + math.sin(xx * 0.004) * 3.0) * 10 + math.sin(yy * 0.31) * 3
            light = 1.0 - 0.35 * (((xx - w * 0.45) / w) ** 2 + ((yy - h * 0.4) / h) ** 2)
            r, g, b = (150 + grain) * light, (104 + grain * 0.8) * light, (66 + grain * 0.5) * light
            px[xx, yy] = (int(r), int(g), int(b))
    return im.filter(ImageFilter.GaussianBlur(0.6))


def photo(sheet_im, rnd):
    bg = desk(rnd)
    s = 0.62
    sheet_small = sheet_im.resize((int(W * s), int(H * s)), Image.LANCZOS).convert("RGBA")
    angle = rnd.uniform(-4, 4)
    rot = sheet_small.rotate(angle, resample=Image.BICUBIC, expand=True)
    shadow = Image.new("RGBA", rot.size, (0, 0, 0, 0))
    shadow.putalpha(rot.getchannel("A").point(lambda a: int(a * 0.45)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    ox, oy = (bg.width - rot.width) // 2 + 30, (bg.height - rot.height) // 2
    bg.paste((0, 0, 0), (ox + 18, oy + 24), shadow)
    bg.paste(rot, (ox, oy), rot)
    # warm desk-lamp falloff
    lamp = Image.new("L", bg.size)
    lp = lamp.load()
    for yy in range(0, bg.height, 4):
        for xx in range(0, bg.width, 4):
            v = int(255 * max(0.0, 1 - 0.55 * math.hypot((xx - 500) / bg.width, (yy - 300) / bg.height)))
            for dy in range(4):
                for dx in range(4):
                    if xx + dx < bg.width and yy + dy < bg.height:
                        lp[xx + dx, yy + dy] = v
    warm = Image.new("RGB", bg.size, (255, 236, 205))
    bg = Image.composite(bg, Image.blend(bg, warm, 0.0), lamp)
    dark = Image.new("RGB", bg.size, (20, 14, 10))
    bg = Image.composite(bg, Image.blend(bg, dark, 0.45), lamp)
    return bg, (ox, oy, angle, s, rot.size)


if __name__ == "__main__":
    for key, sheet in SHEETS.items():
        rnd = random.Random(key)
        im, truth = paper(sheet, rnd)
        ph, _ = photo(im, rnd)
        ph.save(HERE / f"{key}.jpg", quality=86)
        (HERE / f"{key}.truth.json").write_text(json.dumps(
            {"subject": sheet["subject"], "title": sheet["title"], "kid": sheet["name"], "items": truth}, indent=2), encoding="utf-8")
        print("wrote", key)
