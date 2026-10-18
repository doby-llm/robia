"""Generates the synthetic benchmark scenes of the precise cutout dev benchmark (SG2a).

Usage: python3 -I make_cutout_benchmark.py OUT_DIR
Draws three garment-on-hanger scenes with PIL primitives (no photos are used):
  dress_wire_hanger_door.png  dress on a wire hanger against a light door
  white_top_white_wall.png    white top on a white wall (the ML Kit white-on-white case)
  skirt_clip_hanger.png       skirt on a clip hanger
Writes 768x1024 RGB PNGs into OUT_DIR (app/src/main/assets/benchmark/cutout/).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H = 768, 1024


def door_background(color=(236, 232, 224), frame=(214, 208, 198), handle=(150, 140, 120)):
    img = Image.new("RGB", (W, H), color)
    d = ImageDraw.Draw(img)
    d.rectangle([60, 40, W - 60, H - 20], outline=frame, width=18)
    d.ellipse([W - 170, H // 2 - 30, W - 130, H // 2 + 10], fill=handle)
    return img


def wire_hanger(d, cx, top, color=(120, 120, 124)):
    d.arc([cx - 18, top - 40, cx + 18, top + 4], start=180, end=360, fill=color, width=6)
    d.line([(cx, top + 4), (cx - 330, top + 190)], fill=color, width=6)
    d.line([(cx, top + 4), (cx + 330, top + 190)], fill=color, width=6)
    d.line([(cx - 330, top + 190), (cx + 330, top + 190)], fill=color, width=6)


def clip_hanger(d, cx, top, color=(92, 92, 96)):
    d.rectangle([cx - 230, top, cx + 230, top + 26], fill=color)
    d.rectangle([cx - 14, top - 40, cx + 14, top], fill=color)


def dress_scene():
    img = door_background()
    d = ImageDraw.Draw(img)
    cx = W // 2
    wire_hanger(d, cx, 120)
    d.polygon([(cx - 60, 250), (cx + 60, 250), (cx + 120, 620), (cx + 200, 960), (cx - 200, 960), (cx - 120, 620)], fill=(128, 52, 96))
    d.polygon([(cx - 60, 250), (cx + 60, 250), (cx + 40, 330), (cx - 40, 330)], fill=(108, 40, 80))
    return img


def white_top_scene():
    img = Image.new("RGB", (W, H), (246, 246, 244))
    d = ImageDraw.Draw(img)
    cx = W // 2
    d.polygon([(cx - 150, 220), (cx + 150, 220), (cx + 190, 300), (cx + 150, 700), (cx - 150, 700), (cx - 190, 300)], fill=(252, 252, 252))
    d.rectangle([cx - 190, 300, cx - 150, 340], fill=(252, 252, 252))
    d.arc([cx - 60, 180, cx + 60, 260], start=0, end=180, fill=(236, 236, 234), width=4)
    return img


def skirt_scene():
    img = door_background(color=(226, 224, 220))
    d = ImageDraw.Draw(img)
    cx = W // 2
    clip_hanger(d, cx, 170)
    d.polygon([(cx - 170, 200), (cx + 170, 200), (cx + 230, 960), (cx - 230, 960)], fill=(34, 42, 78))
    d.rectangle([cx - 170, 200, cx + 170, 250], fill=(26, 32, 62))
    return img


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    scenes = {
        "dress_wire_hanger_door.png": dress_scene,
        "white_top_white_wall.png": white_top_scene,
        "skirt_clip_hanger.png": skirt_scene,
    }
    for name, build in scenes.items():
        build().save(out / name, optimize=True)
        print(name)


if __name__ == "__main__":
    main()
