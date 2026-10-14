"""Numbered contact sheets of the processed photos (for provisional labelling).

Usage: contact_sheets.py BASE   (writes BASE/sheets/sheet_NN.jpg and BASE/sheets/index.json)
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

BASE = Path(sys.argv[1])
stems = sorted({p.name.rsplit("_", 1)[0] for p in (BASE / "processed").glob("*_raw.jpg")})
out = BASE / "sheets"
out.mkdir(exist_ok=True)
COLS, ROWS, CELL = 6, 4, 220
font = ImageFont.load_default(size=22)
index = {}
for s0 in range(0, len(stems), COLS * ROWS):
    sheet = Image.new("RGB", (COLS * CELL, ROWS * CELL), "white")
    d = ImageDraw.Draw(sheet)
    for k, stem in enumerate(stems[s0:s0 + COLS * ROWS]):
        n = s0 + k
        index[n] = stem
        img = Image.open(BASE / "processed" / f"{stem}_raw.jpg").convert("RGB")
        img.thumbnail((CELL - 10, CELL - 34))
        x, y = (k % COLS) * CELL, (k // COLS) * CELL
        sheet.paste(img, (x + (CELL - img.width) // 2, y + 30))
        d.rectangle([x, y, x + CELL - 1, y + CELL - 1], outline=(200, 200, 200))
        d.text((x + 6, y + 4), f"#{n}", fill=(200, 0, 0), font=font)
    sheet.save(out / f"sheet_{s0 // (COLS * ROWS):02d}.jpg", quality=85)
(out / "index.json").write_text(json.dumps(index, indent=0))
print(len(stems), sorted(p.name for p in out.glob("*.jpg")))
