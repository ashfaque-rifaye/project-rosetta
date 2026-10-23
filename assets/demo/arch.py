"""The architecture card for cut 3: Rosetta on AWS, drawn crisp at 4K and revealed column by column.

  python assets/demo/arch.py      ->  assets/demo/build/arch_<n>.png (one image per reveal step)

Every box is a service the CDK stack (infra/lib) deploys or the agent crew calls.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

from edit import B, CREAM, GOLD, MIST, SANS, SANS_SB, SERIF, font

S = 2                                   # draw at 2x, then downscale for clean edges
W, H = 1920 * S, 1080 * S
NAVY = (9, 14, 34)
ORANGE = (255, 153, 0)                  # AWS orange, for the AWS services
SKY = (120, 190, 255)
LIME = (190, 240, 90)

# (column, row y, height, title, subtitle, accent, aws?)
BOXES = {
    "tv":     (0, 250, 200, "Fire TV app", "Kotlin · Compose for TV\nFilament 3D · Unity game", SKY, False),
    "phones": (0, 520, 200, "Phone pages", "Kids check in · parents\napprove, from any browser", SKY, False),
    "cf":     (1, 250, 200, "Amazon CloudFront", "+ Amazon S3 · the phone\npages, served worldwide", ORANGE, True),
    "api":    (1, 520, 200, "Amazon API Gateway", "One HTTP API for\nthe TV and the phones", ORANGE, True),
    "lambda": (2, 250, 300, "AWS Lambda", "Strands Agents crew:\nAI Tutor · Scout · Judge\nCoach · Keeper\nphoto checks, marking,\nquiz, notes, rewards", ORANGE, True),
    "events": (2, 600, 160, "Amazon EventBridge", "Streak nudges and the\nweekly family digest", ORANGE, True),
    "bedrock": (3, 210, 150, "Amazon Bedrock", "Model layer · Amazon Nova", ORANGE, True),
    "ddb":    (3, 385, 140, "Amazon DynamoDB", "The family's state", ORANGE, True),
    "s3":     (3, 550, 140, "Amazon S3", "Photos and voice notes\n→ event → the agents", ORANGE, True),
    "notify": (3, 715, 140, "Amazon SES · SNS", "Email and SMS alerts", ORANGE, True),
}
COLS = [(70, 380), (520, 400), (990, 420), (1480, 380)]
ARROWS = [("tv", "api"), ("phones", "api"), ("phones", "cf"), ("api", "lambda"), ("events", "lambda"),
          ("lambda", "bedrock"), ("lambda", "ddb"), ("lambda", "s3"), ("lambda", "notify")]
HEADS = ["ON THE TV AND PHONES", "EDGE", "SERVERLESS COMPUTE", "AI · DATA · ALERTS"]


def rect(key):
    c, y, h, *_ = BOXES[key]
    x, w = COLS[c]
    return x, y, w, h


def box(img, key):
    x, y, w, h = rect(key)
    _, _, _, title, sub, accent, aws = BOXES[key]
    x, y, w, h = x * S, y * S, w * S, h * S
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((x + 10, y + 22, x + w + 10, y + h + 22), radius=34 * S, fill=(0, 0, 0, 150))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(22 * S)))
    g = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    gd.rounded_rectangle((x, y, x + w, y + h), radius=26 * S, fill=(255, 255, 255, 20), outline=(*accent, 150), width=2 * S)
    gd.rounded_rectangle((x, y, x + 8 * S, y + h), radius=4 * S, fill=(*accent, 255))
    img.alpha_composite(g)
    d = ImageDraw.Draw(img)
    d.text((x + 30 * S, y + 22 * S), title, font=font(SANS_SB, 31 * S), fill=CREAM)
    d.multiline_text((x + 30 * S, y + 66 * S), sub, font=font(SANS, 22 * S), fill=MIST, spacing=6 * S)


def arrow(img, a, b):
    ax, ay, aw, ah = rect(a)
    bx, by, bw, bh = rect(b)
    if bx > ax:            # left to right
        p0 = (ax + aw, ay + ah / 2)
        p1 = (bx, min(max(ay + ah / 2, by + 30), by + bh - 30))
    else:                  # same column (EventBridge up into Lambda)
        p0 = (ax + aw / 2, ay)
        p1 = (bx + bw / 2, by + bh)
    d = ImageDraw.Draw(img)
    pts = [(p0[0] * S, p0[1] * S), ((p0[0] + p1[0]) / 2 * S, p0[1] * S), ((p0[0] + p1[0]) / 2 * S, p1[1] * S), (p1[0] * S, p1[1] * S)] \
        if bx > ax else [(p0[0] * S, p0[1] * S), (p1[0] * S, p1[1] * S)]
    d.line(pts, fill=(255, 200, 120, 210), width=3 * S, joint="curve")
    ex, ey = pts[-1]
    if bx > ax:
        d.polygon([(ex, ey), (ex - 16 * S, ey - 9 * S), (ex - 16 * S, ey + 9 * S)], fill=(255, 200, 120, 230))
    else:
        d.polygon([(ex, ey), (ex - 9 * S, ey + 16 * S), (ex + 9 * S, ey + 16 * S)], fill=(255, 200, 120, 230))


def base():
    img = Image.new("RGBA", (W, H), (*NAVY, 255))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((W * 0.45, -H * 0.3, W * 1.2, H * 0.6), fill=(255, 153, 0, 40))
    gd.ellipse((-W * 0.2, H * 0.4, W * 0.5, H * 1.3), fill=(80, 120, 255, 45))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(160 * S)))
    d = ImageDraw.Draw(img)
    d.text((70 * S, 58 * S), "HOW IT'S BUILT", font=font(SANS_SB, 24 * S), fill=GOLD)
    d.text((70 * S, 92 * S), "Serverless on AWS, end to end", font=font(SERIF, 58 * S), fill=CREAM)
    for (x, w), head in zip(COLS, HEADS):
        d.text((x * S, 180 * S), head, font=font(SANS_SB, 17 * S), fill=(*MIST, 255))
    return img


def footer(img):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    f = font(SANS_SB, 24 * S)
    items = ["AWS CDK · one command deploys it all", "Pay-per-use · scales to zero", "Open source · MIT"]
    x = 70 * S
    for it in items:
        tw = d.textlength(it, font=f)
        d.rounded_rectangle((x, 905 * S, x + tw + 44 * S, 961 * S), radius=28 * S, fill=(255, 153, 0, 28), outline=(*ORANGE, 140), width=S)
        d.text((x + 22 * S, 917 * S), it, font=f, fill=(*CREAM, 255))
        x += tw + 64 * S
    img.alpha_composite(layer)


def build() -> list:
    """Four steps: devices, + edge, + compute, + AI and data with the footer."""
    steps = [["tv", "phones"], ["cf", "api"], ["lambda", "events"], ["bedrock", "ddb", "s3", "notify"]]
    shown, outs = [], []
    for i, st in enumerate(steps):
        shown += st
        img = base()
        for a, b in ARROWS:
            if a in shown and b in shown:
                arrow(img, a, b)
        for k in shown:
            box(img, k)
        if i == len(steps) - 1:
            footer(img)
        out = B / f"arch_{i}.png"
        img.convert("RGB").resize((1920, 1080), Image.LANCZOS).save(out)
        outs.append(out)
    return outs


if __name__ == "__main__":
    print(build())
