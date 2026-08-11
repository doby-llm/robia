#!/usr/bin/env python3
"""Create deterministic, non-personal image fixtures for the Android CI baseline."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw

FIXTURE_COUNT = 60


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output
    output.mkdir(parents=True, exist_ok=True)

    manifest = []
    for index in range(FIXTURE_COUNT):
        width, height = ((1600, 1200) if index % 3 else (1200, 1600))
        image = Image.new("RGB", (width, height), (24 + index * 3 % 180, 46 + index * 5 % 160, 72 + index * 7 % 140))
        draw = ImageDraw.Draw(image)
        draw.rectangle((width // 6, height // 6, width * 5 // 6, height * 5 // 6), outline=(245, 235, 220), width=24)
        draw.ellipse((width // 3, height // 3, width * 2 // 3, height * 2 // 3), fill=(220, 130 + index % 80, 80))
        path = output / f"fixture-{index:02d}.jpg"
        image.save(path, "JPEG", quality=90, optimize=True)
        manifest.append({
            "id": f"fixture-{index:02d}",
            "path": path.name,
            "width": width,
            "height": height,
            "bytes": path.stat().st_size,
            "sha256": hashlib.file_digest(path.open("rb"), "sha256").hexdigest(),
        })

    alpha = Image.new("RGBA", (1600, 1200), (0, 0, 0, 0))
    ImageDraw.Draw(alpha).rounded_rectangle((260, 160, 1340, 1040), radius=160, fill=(75, 125, 185, 255))
    alpha.save(output / "transparent-fixture.png")
    (output / "manifest.json").write_text(json.dumps({"synthetic": True, "fixtures": manifest}, indent=2) + "\n")


if __name__ == "__main__":
    main()
