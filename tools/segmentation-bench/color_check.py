"""Port of Robia's colour extraction (fc45cec core/color + ClothingImageStore.paletteMatches) for offline analysis.
Usage: python -I color_check.py CUT_DIR OUT_JSONL [variant]  (CUT_DIR = RGBA cutouts named NNN.png)
variants: app (today) | gray (adds a Gray swatch, low-chroma restricted to neutrals incl. gray)
"""
import json, sys, time
from pathlib import Path
import numpy as np
from PIL import Image

PALETTE = [("black", "#1F1F1F"), ("white", "#F8F9FA"), ("gray-charcoal", "#D8C3A5"), ("brown", "#8B6848"),
           ("navy-blue", "#315F8E"), ("green", "#5F6F48"), ("red", "#9E3D35"), ("pink", "#D4879A"),
           ("purple", "#765A91"), ("yellow-mustard", "#D6B84C"), ("orange", "#C56F33")]
NEUTRAL = {"black", "white", "gray-charcoal", "brown"}


def hex2rgb(h): return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], float)


def lab(rgb):
    c = rgb / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    M = np.array([[0.4124564, 0.3575761, 0.1804375], [0.2126729, 0.7151522, 0.0721750], [0.0193339, 0.1191920, 0.9503041]])
    xyz = lin @ M.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (24389 / 27 * xyz + 16) / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)


def run(img, palette, neutral):
    a = np.asarray(img.convert("RGBA"))
    h, w = a.shape[:2]
    step = max(max(w, h) // 96, 1)
    s = a[step // 2::step, step // 2::step].reshape(-1, 4).astype(float)
    alpha = s[:, 3]
    wgt = np.where(alpha < 192, 0, np.where(alpha < 240, 0.35, 1.0))
    keep = wgt > 0
    s, wgt = s[keep, :3], wgt[keep]
    if wgt.sum() < 12: return []
    pl = lab(np.array([hex2rgb(hx) for _, hx in palette]))
    sl = lab(s)
    d = np.linalg.norm(sl[:, None, :] - pl[None, :, :], axis=2)
    mx, mn = s.max(1), s.min(1)
    sat = np.where(mx == 0, 0, (mx - mn) / np.maximum(mx, 1))
    nmask = np.array([pid in neutral for pid, _ in palette])
    d_low = np.where(nmask[None, :], d, np.inf)
    d = np.where((sat <= 0.12)[:, None], d_low, d)
    idx = d.argmin(1)
    counts = np.bincount(idx, weights=wgt, minlength=len(palette))
    tot = wgt.sum()
    order = sorted(range(len(palette)), key=lambda i: (-round(counts[i]), -counts[i]))
    prim = order[0]
    if counts[prim] / tot < 0.28: return []
    out = [(palette[prim][0], round(counts[prim] / tot, 2))]
    for i in order[1:]:
        if counts[i] / tot >= 0.18 and np.linalg.norm(pl[prim] - pl[i]) >= 16:
            out.append((palette[i][0], round(counts[i] / tot, 2))); break
    return out


if __name__ == "__main__":
    cut, outp = Path(sys.argv[1]), Path(sys.argv[2])
    variant = sys.argv[3] if len(sys.argv) > 3 else "app"
    pal, neu = list(PALETTE), set(NEUTRAL)
    if variant == "gray":
        pal.append(("gray", "#8A8A8A")); neu.add("gray")
    with open(outp, "w") as f:
        for p in sorted(cut.glob("*.png")):
            t = time.time(); r = run(Image.open(p), pal, neu)
            f.write(json.dumps({"i": int(p.stem), "colors": r, "ms": round((time.time() - t) * 1000)}) + "\n")
