"""Sharpen the big background paintings for 1080p/4K TVs: Imagen upscaling on Vertex AI (x2), then a high-quality
WebP at 2560 px wide. Falls back to Lanczos when the upscaler isn't available. Vertex AI only, no API keys.

  python assets/upscale_art.py      ->  tv-app/app/src/main/assets/rosetta/{home_base,stone,movie_night}.webp
"""
import io
import sys
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
FLOW = ROOT / "assets/flow"
OUT = ROOT / "tv-app/app/src/main/assets/rosetta"
ART = {"home_base.webp": "production/rosetta_home_base_b.webp", "stone.webp": "production/stone.webp",
       "movie_night.webp": "concepts/concept_stone_2.webp"}
WIDTH = 2560


def imagen_x2(img: Image.Image) -> Image.Image | None:
    try:
        from google import genai
        from google.genai import types
        c = genai.Client(vertexai=True, project="tesselle-demandfoundry", location="us-central1")
        buf = io.BytesIO()
        img.save(buf, "PNG")
        r = c.models.upscale_image(model="imagen-3.0-generate-002", image=types.Image(image_bytes=buf.getvalue(), mime_type="image/png"),
                                   upscale_factor="x2", config=types.UpscaleImageConfig(output_mime_type="image/png"))
        return Image.open(io.BytesIO(r.generated_images[0].image.image_bytes)).convert("RGB")
    except Exception as e:  # noqa: BLE001 - fall back to Lanczos
        print("  imagen upscale unavailable:", str(e)[:160], file=sys.stderr)
        return None


if __name__ == "__main__":
    for out, src in ART.items():
        img = Image.open(FLOW / src).convert("RGB")
        big = imagen_x2(img)
        how = "imagen x2"
        if big is None:
            big = img.resize((img.width * 2, img.height * 2), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1.6, percent=60, threshold=2))
            how = "lanczos"
        h = round(WIDTH * img.height / img.width)
        big.resize((WIDTH, h), Image.LANCZOS).save(OUT / out, "WEBP", quality=90, method=6)
        print(out, how, (OUT / out).stat().st_size // 1024, "KB")
