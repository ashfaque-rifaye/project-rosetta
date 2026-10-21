"""Copy the curated Flow originals in assets/flow/ into the TV app's bundled assets.

Run from the repo root:  python assets/build_assets.py
Originals stay untouched (including Flow's visible watermark, which is required in our region).
"""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "assets" / "flow"
DST = ROOT / "tv-app" / "app" / "src" / "main" / "assets" / "rosetta"

# app path  ->  source path (relative to assets/flow)
MAP = {
    "home_base.webp": "production/rosetta_home_base_b.webp",
    "stone.webp": "production/stone.webp",
    "movie_night.webp": "concepts/concept_stone_2.webp",
    "demo/room_before.webp": "production/room_before.webp",
    "demo/room_after_partial.webp": "production/room_after_partial.webp",
    "demo/room_after_done.webp": "production/room_after_done.webp",
}
for thing in ["sock", "books", "dino", "bricks", "jacket", "sneakers", "mug", "teddy"]:
    MAP[f"things/{thing}.webp"] = f"things/{thing}.webp"
for glyph in ["bed", "books", "shirt", "plant", "broom", "dish", "package", "shoes", "sock"]:
    MAP[f"glyphs/{glyph}.webp"] = f"glyphs/{glyph}.webp"

missing = []
for dst, src in MAP.items():
    s = SRC / src
    if not s.exists():
        missing.append(src)
        continue
    d = DST / dst
    d.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(s, d)

# The phone pages show the same Lost Thing portraits (smaller copies).
web_things = ROOT / "web" / "things"
web_things.mkdir(parents=True, exist_ok=True)
try:
    from PIL import Image
    for f in (SRC / "things").glob("*.webp"):
        Image.open(f).convert("RGB").resize((256, 256)).save(web_things / f.name, "WEBP", quality=85)
except ImportError:
    for f in (SRC / "things").glob("*.webp"):
        shutil.copyfile(f, web_things / f.name)

print(f"copied {len(MAP) - len(missing)} assets to {DST.relative_to(ROOT)} (+ web/things)")
if missing:
    print("missing:", *missing, sep="\n  ")
