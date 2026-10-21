"""Copy the curated Flow originals in assets/flow/ into the TV app's bundled assets and web/.

Run from the repo root:  python assets/build_assets.py
Originals keep Flow's visible watermark (required in our region); cutouts are derived files.
"""
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "assets" / "flow"
DST = ROOT / "tv-app" / "app" / "src" / "main" / "assets" / "rosetta"

MAP = {
    "home_base.webp": "production/rosetta_home_base_b.webp",
    "stone.webp": "production/stone.webp",
    "movie_night.webp": "concepts/concept_stone_2.webp",
    "reference/room_before.webp": "production/room_before.webp",
    "reference/room_after_partial.webp": "production/room_after_partial.webp",
    "reference/room_after_done.webp": "production/room_after_done.webp",
    "ui/coin.webp": "ui/coin.webp",
}
for folder in ("things", "glyphs", "avatars", "shop", "stamps", "bosses"):
    for f in sorted((SRC / folder).glob("*.webp")):
        MAP[f"{folder}/{f.name}"] = f"{folder}/{f.name}"

shutil.rmtree(DST, ignore_errors=True)
for dst, src in MAP.items():
    d = DST / dst
    d.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SRC / src, d)

# Phone pages reuse portraits and avatars at web size.
from PIL import Image  # noqa: E402
for folder in ("things", "avatars", "stamps"):
    out = ROOT / "web" / folder
    out.mkdir(parents=True, exist_ok=True)
    for f in (SRC / folder).glob("*.webp"):
        Image.open(f).convert("RGB").resize((256, 256)).save(out / f.name, "WEBP", quality=85)
Image.open(SRC / "ui" / "coin.webp").resize((96, 96)).save(ROOT / "web" / "coin.webp", "WEBP")
print(f"copied {len(MAP)} assets to {DST.relative_to(ROOT)} (+ web/)")
