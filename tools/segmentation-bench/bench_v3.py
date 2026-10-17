"""Robia seg bench v3: autonomous "smart selection" with MediaPipe magic_touch.
Usage: python -I bench_v3.py OUT_DIR BASE IDX...   (BASE = isnet | u2netp: the coarse saliency model)
Pipeline per photo (all automatic):
 1. coarse = BASE soft mask (already computed by bench.py) -> hard = alpha > 0.5
 2. garment points: distance-transform maxima taken in the top/middle/bottom thirds of the hard mask
 3. magic_touch per point -> keep masks whose overlap with `hard` covers >= 60% of the selection
    (rejects a selection that jumped to the door/background); union = garment selection
 4. hanger: hook point (thin top band) -> magic_touch(hook) -> accept as hanger if small (<12% of garment area)
    and mostly in the top 30% of the garment bbox -> subtract (dilated 2 px) from the selection
 5. alpha = selection soft prob (magic_touch) refined with a guided filter on the RGB, interior clamped to 1
Writes {i}_v3_{BASE}.png and {i}_v3_{BASE}_dbg.json
"""
import sys, json, time
from pathlib import Path
import numpy as np
import cv2
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

sys.path.insert(0, str(Path(__file__).parent))
from bench_v2 import guided, hook_point, magic  # noqa: E402

MD = Path(__file__).parent / "models"


def third_points(hard):
    ys = np.where(hard.any(1))[0]
    if len(ys) == 0:
        return []
    top, bot = ys[0], ys[-1]
    dt = cv2.distanceTransform(hard.astype(np.uint8), cv2.DIST_L2, 5)
    pts = []
    for k in range(3):
        a = top + (bot - top) * k // 3; b = top + (bot - top) * (k + 1) // 3
        band = np.zeros_like(dt); band[a:b] = dt[a:b]
        y, x = np.unravel_index(np.argmax(band), band.shape)
        if band[y, x] > 3:
            pts.append((int(x), int(y)))
    return pts


def main():
    out = Path(sys.argv[1]); base = sys.argv[2]; idx = [int(a) for a in sys.argv[3:]]
    mt = Interpreter(model_path=str(MD / "magic_touch.tflite"), num_threads=4); mt.allocate_tensors()
    tot = []
    for i in idx:
        rgb = np.asarray(Image.open(out / f"{i:03d}_src.jpg").convert("RGB"))
        h, w = rgb.shape[:2]
        coarse = np.asarray(Image.open(out / f"{i:03d}_{base}.png")).astype(np.float32) / 255.0
        hard = coarse > 0.5
        t0 = time.perf_counter()
        sel = np.zeros((h, w), np.float32); used = []
        for p in third_points(hard):
            m, _ = magic(mt, rgb, p)
            mb = m > 0.5
            if mb.sum() == 0:
                continue
            ov = (mb & hard).sum() / mb.sum()
            used.append({"pt": p, "overlap": round(float(ov), 2)})
            if ov >= 0.6:
                sel = np.maximum(sel, m)
        if sel.max() == 0:  # fall back to coarse
            sel = coarse.copy()
        hook = hook_point(sel)
        hanger = None
        if hook:
            hm, _ = magic(mt, rgb, hook)
            hb = hm > 0.5
            ys = np.where(sel > 0.5)[0]
            g_top, g_bot = ys.min(), ys.max()
            area_ok = hb.sum() < 0.12 * (sel > 0.5).sum()
            in_top = hb[: int(g_top + 0.3 * (g_bot - g_top))].sum() >= 0.8 * max(hb.sum(), 1)
            hanger = {"pt": hook, "area_ratio": round(float(hb.sum() / max((sel > 0.5).sum(), 1)), 3), "accepted": bool(area_ok and in_top)}
            if area_ok and in_top:
                kill = cv2.dilate(hb.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
                sel[kill] = 0
        # keep components >= 3% of the largest (drop specks)
        hb2 = (sel > 0.5).astype(np.uint8)
        n, lab, st, _ = cv2.connectedComponentsWithStats(hb2, 8)
        if n > 2:
            big = st[1:, cv2.CC_STAT_AREA].max()
            keep = np.isin(lab, [j for j in range(1, n) if st[j, cv2.CC_STAT_AREA] >= 0.03 * big])
            sel = sel * cv2.dilate(keep.astype(np.uint8), np.ones((7, 7), np.uint8))
        grey = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
        ref = np.clip(guided(grey, sel, r=3, eps=1e-3), 0, 1)
        ref = np.where(sel > 0.97, 1.0, ref) * (cv2.dilate((sel > 0.05).astype(np.uint8), np.ones((5, 5), np.uint8)))
        dt = time.perf_counter() - t0
        tot.append(dt)
        Image.fromarray((ref * 255).astype(np.uint8)).save(out / f"{i:03d}_v3_{base}.png")
        (out / f"{i:03d}_v3_{base}_dbg.json").write_text(json.dumps({"points": used, "hanger": hanger, "sec": round(dt, 2)}))
        print(i, round(dt, 2), used, hanger, flush=True)
    print("median s", sorted(tot)[len(tot) // 2])


if __name__ == "__main__":
    main()
