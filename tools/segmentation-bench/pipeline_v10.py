"""Robia candidate cutout pipeline (reference implementation for the Android port). Fully automatic.
Usage: python -I pipeline.py OUT_DIR [IDX...]   (no IDX = all photos in photos_raw)
Stages (all models Apache-2.0/MIT):
  S1 coarse saliency   u2netp 320x320 (4.5 MB)              -> soft mask
  S2 garment select    MediaPipe magic_touch (6 MB), 3 auto points (distance-transform maxima per third),
                       keep selections with >= 60% overlap with S1, union  -> removes door/wall leaks
  S3 hanger detect     D-FINE-S Objects365 (42 MB), class 'Hanger' score >= 0.45
  S4 hanger remove     MobileSAM (28+16 MB) only when S3 fired: hanger = SAM(hanger box, +hook, -garment core),
                       garment = SAM(garment box, +core, -hook); remove hanger & ~garment inside the box, cap 15%
  S5 edges             guided filter on luminance (r=3, eps=1e-3), interior clamped to 1, islands < 3% dropped
Writes final/{stem}.png (RGBA cutout cropped to alpha bbox, 4% pad) and final/times.jsonl
"""
import sys, json, time, glob
from pathlib import Path
import numpy as np
import cv2
import onnxruntime as ort
from PIL import Image, ImageOps
from ai_edge_litert.interpreter import Interpreter

sys.path.insert(0, str(Path(__file__).parent))
from bench import MODELS, run  # noqa: E402
from bench_v2 import guided, magic, core_points  # noqa: E402
from bench_v3 import third_points  # noqa: E402
from bench_v4 import detect  # noqa: E402
from bench_v6 import MobileSam  # noqa: E402

RAW = sorted(glob.glob(__import__("os").environ.get("ROBIA_PHOTOS", "data/photos_raw") + "/*.jpg"))
MD = Path(__file__).parent / "models"


def drop_islands(sel):
    hb = (sel > 0.5).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(hb, 8)
    if n > 2:
        big = st[1:, cv2.CC_STAT_AREA].max()
        keep = np.isin(lab, [j for j in range(1, n) if st[j, cv2.CC_STAT_AREA] >= 0.10 * big])
        sel = sel * cv2.dilate(keep.astype(np.uint8), np.ones((5, 5), np.uint8))
    return sel


def main():
    out = Path(sys.argv[1]); fin = out / "final_v10"; fin.mkdir(parents=True, exist_ok=True)
    idx = [int(a) for a in sys.argv[2:]] or list(range(len(RAW)))
    so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.log_severity_level = 3
    u2 = ort.InferenceSession(str(MD / "u2netp.onnx"), so, providers=["CPUExecutionProvider"])
    det = ort.InferenceSession(str(MD / "dfine_s_obj365.onnx"), so, providers=["CPUExecutionProvider"])
    mt = Interpreter(model_path=str(MD / "magic_touch.tflite"), num_threads=4); mt.allocate_tensors()
    sam = MobileSam()
    log = open(fin / "times.jsonl", "a")
    for i in idx:
        stem = Path(RAW[i]).stem
        if (fin / f"{i:03d}.png").exists():
            continue
        im = ImageOps.exif_transpose(Image.open(RAW[i])).convert("RGB"); im.thumbnail((1024, 1024), Image.LANCZOS)
        rgb = np.asarray(im); h, w = rgb.shape[:2]
        t = {}
        t0 = time.perf_counter()
        m, _ = run(u2, MODELS["u2netp"], im); coarse = np.asarray(m).astype(np.float32) / 255.0
        t["s1"] = time.perf_counter() - t0
        hard = coarse > 0.5
        if hard.sum() < 0.01 * h * w:
            log.write(json.dumps({"i": i, "stem": stem, "fallback": "empty"}) + "\n"); continue
        t1 = time.perf_counter(); sel = np.zeros((h, w), np.float32)
        for p in third_points(hard):
            mm, _ = magic(mt, rgb, p); mb = mm > 0.5
            if mb.sum() and (mb & hard).sum() / mb.sum() >= 0.6:
                sel = np.maximum(sel, mm)
        if sel.max() == 0:
            sel = coarse.copy()
        t["s2"] = time.perf_counter() - t1
        t2 = time.perf_counter()
        sh = sel > 0.5; ys_, xs_ = np.where(sh)
        gx0, gy0, gx1, gy1 = xs_.min(), ys_.min(), xs_.max(), ys_.max(); gw, gh = gx1 - gx0, gy1 - gy0
        def plausible(b):  # a hanger spans a good part of the garment width and sits at its top
            x0, y0, x1, y1 = b
            return (x1 - x0) >= 0.35 * gw and y0 < gy0 + 0.25 * gh and y1 < gy0 + 0.5 * gh
        hangers, _, _ = detect(det, rgb, 0.25)
        hangers = [hb for hb in hangers if plausible(hb["box"])]
        if not hangers:  # second pass on the top band of the garment, upscaled
            cy0 = max(gy0 - int(0.15 * gh), 0); cy1 = min(gy0 + int(0.35 * gh), h)
            cx0 = max(gx0 - int(0.1 * gw), 0); cx1 = min(gx1 + int(0.1 * gw), w)
            hb2, _, _ = detect(det, np.ascontiguousarray(rgb[cy0:cy1, cx0:cx1]), 0.25)
            for hb in hb2:
                bx = hb["box"]; hb["box"] = [bx[0] + cx0, bx[1] + cy0, bx[2] + cx0, bx[3] + cy0]; hb["pass"] = 2
            hangers = [hb for hb in hb2 if plausible(hb["box"])]
        t["s3"] = time.perf_counter() - t2
        hanger_frac = 0.0
        if hangers:
            t3 = time.perf_counter()
            x0, y0, x1, y1 = [int(v) for v in hangers[0]["box"]]
            x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, w - 1), min(y1, h - 1)
            core = core_points(sel, 3)
            emb, _ = sam.embed(rgb)
            hp, _ = sam.predict(emb, (x0, y0, x1, y1))  # box-only prompt isolates the hanger
            gp, _ = sam.predict(emb, (gx0, gy0, gx1, gy1), core, [((x0 + x1) // 2, y0 + max(2, (y1 - y0) // 6))])
            boxm = np.zeros((h, w), bool); boxm[max(y0 - 4, 0):y1 + 4, max(x0 - 4, 0):x1 + 4] = True
            hm = hp > 0.5
            inside = (hm & boxm).sum() / max(hm.sum(), 1)
            kill = (hm & boxm) if inside >= 0.85 else (hm & (gp < 0.5) & boxm)
            hanger_frac = float(kill.sum() / max(sh.sum(), 1))
            if hanger_frac <= 0.15:
                sel = np.where(cv2.dilate(kill.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool), 0, sel)
            t["s4"] = time.perf_counter() - t3
        t4 = time.perf_counter()
        sel = drop_islands(sel)
        grey = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
        a = np.clip(guided(grey, sel, r=3, eps=1e-3), 0, 1)
        a = np.where(sel > 0.97, 1.0, a) * cv2.dilate((sel > 0.05).astype(np.uint8), np.ones((5, 5), np.uint8))
        t["s5"] = time.perf_counter() - t4
        a8 = (a * 255).astype(np.uint8)
        ys, xs = np.where(a8 > 24); pad = int(0.04 * max(h, w))
        bb = (max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad, w), min(ys.max() + pad, h))
        rgba = np.dstack([rgb, a8])[bb[1]:bb[3], bb[0]:bb[2]]
        Image.fromarray(rgba, "RGBA").save(fin / f"{i:03d}.png")
        rec = {"i": i, "stem": stem, "hanger": hangers[0]["score"] if hangers else None, "hanger_frac": round(hanger_frac, 3),
               **{k: round(v, 2) for k, v in t.items()}, "total": round(sum(t.values()), 2)}
        log.write(json.dumps(rec) + "\n"); log.flush()
        print(rec, flush=True)


if __name__ == "__main__":
    main()
