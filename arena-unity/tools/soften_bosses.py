"""Soften the painted backdrop around each boss so it melts into the Unity arena instead of reading as a square.

  python arena-unity/tools/soften_bosses.py [--preview OUT_DIR]
Input: assets/flow/bosses/{kraken,golem,tornado}.webp (feather-blended Flow art).
Output: arena-unity/Assets/Resources/Art/boss_*.png.
Two soft masks, multiplied: a radial fade (full inside r0, gone at r1) and, only away from the centre, a fade of
dark backdrop pixels. No hard colour key: the dark seams in the patchwork must not turn into holes.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "assets" / "flow" / "bosses"
OUT = ROOT / "arena-unity" / "Assets" / "Resources" / "Art"
R0, R1 = 0.50, 0.98          # radial fade, in units of half-size (1 = middle of an edge)
DARK_FROM = 0.28             # darkness fade only applies beyond this radius
LUM_LO, LUM_HI = 0.10, 0.30  # luminance at which a pixel is fully backdrop / fully boss


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def soften(im: Image.Image) -> Image.Image:
    a = np.asarray(im.convert("RGBA"), dtype=np.float32) / 255
    h, w = a.shape[:2]
    y, x = np.mgrid[0:h, 0:w]
    r = np.hypot((x + 0.5) / w * 2 - 1, (y + 0.5) / h * 2 - 1)
    radial = 1 - smooth(R0, R1, r)
    lum = a[..., :3] @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
    sat = a[..., :3].max(-1) - a[..., :3].min(-1)
    # The Flow backdrops are dark navy: blue that beats red and green while the pixel is dim counts as backdrop.
    navy = np.clip(a[..., 2] - np.maximum(a[..., 0], a[..., 1]), 0, 1) * (1 - smooth(0.09, 0.2, lum))
    boss_like = smooth(LUM_LO, LUM_HI, lum + 0.6 * sat - 1.2 * navy)
    away = smooth(DARK_FROM, DARK_FROM + 0.25, r)
    dark_mask = 1 - away * (1 - boss_like)
    a[..., 3] *= radial * dark_mask
    return Image.fromarray((a * 255 + 0.5).astype(np.uint8))


if __name__ == "__main__":
    preview = Path(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None
    arena = Image.open(SRC / "arena.webp").convert("RGBA")
    for name in ("kraken", "golem", "tornado"):
        out = soften(Image.open(SRC / f"{name}.webp")).resize((512, 512), Image.LANCZOS)
        out.save(OUT / f"boss_{name}.png", optimize=True)
        if preview:
            bg = arena.resize((1024, 1024)).crop((256, 256, 768, 768))
            bg.alpha_composite(out)
            bg.save(preview / f"boss_{name}_preview.png")
    print("wrote", OUT)
