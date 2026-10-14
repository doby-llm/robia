"""Draw the synthetic benchmark garment and its reference IMAGE vector (TS§3.2 recipe).

Usage: make_benchmark_image.py OUT_DIR MODEL [--vision-tokens N]

Writes OUT_DIR/garment.png (512x512 RGBA, transparent background, drawn here, no personal data) and
OUT_DIR/garment_vector.b64 (base64 of the 768 float32 little-endian values of the normalized IMAGE vector).
The vector uses the app recipe: composite on white, longest edge <= 1024, PNG bytes, N vision tokens (default 140).
"""
import argparse
import base64
import io
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
import litert_lm as L

SIZE = 512
BODY = (178, 34, 34, 255)  # dress body
TRIM = (245, 240, 225, 255)  # waist band and neckline


def draw_garment():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(210, 60), (302, 60), (330, 150), (300, 210), (212, 210), (182, 150)], fill=BODY)  # bodice
    d.polygon([(212, 210), (300, 210), (380, 440), (132, 440)], fill=BODY)  # A-line skirt
    d.polygon([(212, 196), (300, 196), (304, 220), (208, 220)], fill=TRIM)  # waist band
    d.polygon([(214, 60), (298, 60), (256, 104)], fill=TRIM)  # neckline
    return img


def app_recipe_png(img):
    flat = Image.new("RGB", img.size, (255, 255, 255))
    flat.paste(img, mask=img.split()[3])
    flat.thumbnail((1024, 1024))
    buf = io.BytesIO()
    flat.save(buf, "PNG")
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out_dir", type=Path)
    ap.add_argument("model")
    ap.add_argument("--vision-tokens", type=int, default=140)
    a = ap.parse_args()
    a.out_dir.mkdir(parents=True, exist_ok=True)

    img = draw_garment()
    img.save(a.out_dir / "garment.png")

    engine = L.EmbeddingEngine(a.model, backend=L.Backend.CPU(), vision_backend=L.Backend.CPU(),
                               vision_tokens_per_image=a.vision_tokens)
    v = np.asarray(engine.compute_embedding(L.Content.ImageBytes(app_recipe_png(img)),
                                            L.EmbeddingOptions(normalize=True)).embedding, dtype=np.float32)
    v = v / np.linalg.norm(v)
    assert v.shape == (768,), v.shape
    (a.out_dir / "garment_vector.b64").write_text(base64.b64encode(v.astype("<f4").tobytes()).decode())
    print(f"wrote {a.out_dir / 'garment.png'} and {a.out_dir / 'garment_vector.b64'} "
          f"(768 floats, vision_tokens={a.vision_tokens})", file=sys.stderr)


if __name__ == "__main__":
    main()
