"""v11 = "hanger first" experiments (Manu feedback 2026-10-17).
Variants, all starting from the v10 hanger detection + MobileSAM hanger mask:
  A  inpaint: paint the hanger out of the photo (cv2.inpaint Telea on the dilated hanger mask), then rerun
     u2netp + magic_touch x3 on the inpainted photo -> the area the hanger enclosed now touches the background.
  B  enclosed-background removal: after v10, remove opaque components whose colour matches the local background
     ring AND that touch the removed hanger area (dilated) -> the triangle under the hook / gap under a clip bar.
Writes out/v11{A,B}/{i:03d}.png and times.
"""
import sys, json, time, glob, os
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
from pipeline_v10 import drop_islands  # noqa: E402

RAW = sorted(glob.glob(os.environ.get("ROBIA_PHOTOS", "data/photos_raw") + "/*.jpg"))
MD = Path(__file__).parent / "models"
SUF = os.environ.get("SUF", "")


def coarse_and_select(u2, mt, im, rgb):
    h, w = rgb.shape[:2]
    m, _ = run(u2, MODELS["u2netp"], im); coarse = np.asarray(m).astype(np.float32) / 255.0
    hard = coarse > 0.5
    sel = np.zeros((h, w), np.float32)
    for p in third_points(hard):
        mm, _ = magic(mt, rgb, p); mb = mm > 0.5
        if mb.sum() and (mb & hard).sum() / mb.sum() >= 0.6:
            sel = np.maximum(sel, mm)
    return (sel if sel.max() > 0 else coarse), coarse


def finish(rgb, sel):
    sel = drop_islands(sel)
    grey = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
    a = np.clip(guided(grey, sel, r=3, eps=1e-3), 0, 1)
    a = np.where(sel > 0.97, 1.0, a) * cv2.dilate((sel > 0.05).astype(np.uint8), np.ones((5, 5), np.uint8))
    return (a * 255).astype(np.uint8)


def save(rgb, a8, path):
    h, w = a8.shape
    ys, xs = np.where(a8 > 24); pad = int(0.04 * max(h, w))
    bb = (max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad, w), min(ys.max() + pad, h))
    Image.fromarray(np.dstack([rgb, a8])[bb[1]:bb[3], bb[0]:bb[2]], "RGBA").save(path)


def hanger_mask(det, sam, rgb, sel):
    h, w = rgb.shape[:2]
    sh = sel > 0.5; ys_, xs_ = np.where(sh)
    gx0, gy0, gx1, gy1 = xs_.min(), ys_.min(), xs_.max(), ys_.max(); gw, gh = gx1 - gx0, gy1 - gy0

    def plausible(b):
        x0, y0, x1, y1 = b
        return (x1 - x0) >= 0.35 * gw and y0 < gy0 + 0.25 * gh and y1 < gy0 + 0.5 * gh
    hs, _, _ = detect(det, rgb, 0.25)
    hs = [hb for hb in hs if plausible(hb["box"])]
    if not hs:
        cy0 = max(gy0 - int(0.15 * gh), 0); cy1 = min(gy0 + int(0.35 * gh), h)
        cx0 = max(gx0 - int(0.1 * gw), 0); cx1 = min(gx1 + int(0.1 * gw), w)
        hb2, _, _ = detect(det, np.ascontiguousarray(rgb[cy0:cy1, cx0:cx1]), 0.25)
        for hb in hb2:
            bx = hb["box"]; hb["box"] = [bx[0] + cx0, bx[1] + cy0, bx[2] + cx0, bx[3] + cy0]
        hs = [hb for hb in hb2 if plausible(hb["box"])]
    if not hs:
        return None, None
    x0, y0, x1, y1 = [int(v) for v in hs[0]["box"]]
    x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, w - 1), min(y1, h - 1)
    emb, _ = sam.embed(rgb)
    hp, _ = sam.predict(emb, (x0, y0, x1, y1))
    gp, _ = sam.predict(emb, (gx0, gy0, gx1, gy1), core_points(sel, 3), [((x0 + x1) // 2, y0 + max(2, (y1 - y0) // 6))])
    boxm = np.zeros((h, w), bool); boxm[max(y0 - 4, 0):y1 + 4, max(x0 - 4, 0):x1 + 4] = True
    hm = hp > 0.5
    inside = (hm & boxm).sum() / max(hm.sum(), 1)
    kill = (hm & boxm) if inside >= 0.85 else (hm & (gp < 0.5) & boxm)
    if kill.sum() / max(sh.sum(), 1) > 0.15:
        return None, (x0, y0, x1, y1)
    return kill, (x0, y0, x1, y1)


def enclosed_bg(rgb, sel, kill, box):
    """Opaque components that look like the nearby background and touch the removed hanger."""
    h, w = sel.shape
    al = sel > 0.5
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB).astype(np.float32)
    ring = cv2.dilate(al.astype(np.uint8), np.ones((41, 41), np.uint8)).astype(bool) & ~al
    x0, y0, x1, y1 = box
    band = np.zeros((h, w), bool); band[max(y0 - 40, 0):min(y1 + int(0.6 * (y1 - y0)) + 60, h), :] = True
    ring_b = ring & band
    if ring_b.sum() < 200:
        ring_b = ring
    bg = lab[ring_b]; mu = np.median(bg, 0)
    tol = max(np.percentile(np.linalg.norm(bg - mu, axis=1), 90), 6.0) + 4.0
    d = np.linalg.norm(lab - mu, axis=2)
    cand = ((d < tol) & al).astype(np.uint8)
    cand = cv2.morphologyEx(cand, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, lb, st, _ = cv2.connectedComponentsWithStats(cand, 8)
    touch = cv2.dilate(kill.astype(np.uint8), np.ones((15, 15), np.uint8)).astype(bool)
    out = np.zeros((h, w), bool); area = al.sum()
    for j in range(1, n):
        comp = lb == j
        if st[j, 4] < 0.25 * area and (comp & touch).any():
            out |= comp
    return out


def main():
    out = Path(sys.argv[1]); idx = [int(a) for a in sys.argv[2:]]
    for v in "AB":
        (out / f"v11{v}{SUF}").mkdir(parents=True, exist_ok=True)
    so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.log_severity_level = 3
    u2 = ort.InferenceSession(str(MD / "u2netp.onnx"), so, providers=["CPUExecutionProvider"])
    det = ort.InferenceSession(str(MD / "dfine_s_obj365.onnx"), so, providers=["CPUExecutionProvider"])
    mt = Interpreter(model_path=str(MD / "magic_touch.tflite"), num_threads=4); mt.allocate_tensors()
    sam = MobileSam()
    log = open(out / f"v11{SUF}_times.jsonl", "a")
    for i in idx:
        im = ImageOps.exif_transpose(Image.open(RAW[i])).convert("RGB"); im.thumbnail((1024, 1024), Image.LANCZOS)
        rgb = np.ascontiguousarray(np.asarray(im)); t = {}
        t0 = time.perf_counter(); sel, _ = coarse_and_select(u2, mt, im, rgb); t["sel"] = time.perf_counter() - t0
        t0 = time.perf_counter(); kill, box = hanger_mask(det, sam, rgb, sel); t["hanger"] = time.perf_counter() - t0
        if kill is None:
            a8 = finish(rgb, sel)
            for v in "AB":
                save(rgb, a8, out / f"v11{v}{SUF}" / f"{i:03d}.png")
            log.write(json.dumps({"i": i, "hanger": False, **{k: round(x, 2) for k, x in t.items()}}) + "\n"); continue
        # B: v10 kill + enclosed background removal
        t0 = time.perf_counter()
        selB = np.where(cv2.dilate(kill.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool), 0, sel)
        enc = enclosed_bg(rgb, selB, kill, box)
        selB = np.where(cv2.dilate(enc.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool), 0, selB)
        save(rgb, finish(rgb, selB), out / f"v11B{SUF}" / f"{i:03d}.png"); t["B_extra"] = time.perf_counter() - t0
        # A: inpaint hanger, rerun selection on the inpainted photo, then also apply B's rule
        t0 = time.perf_counter()
        km = cv2.dilate(kill.astype(np.uint8), np.ones((9, 9), np.uint8))
        if os.environ.get("FILL") == "median":  # pure-Kotlin-friendly: fill with median colour of a ring around the hanger, then blur the seam
            kb = km.astype(bool)
            ring = cv2.dilate(km, np.ones((25, 25), np.uint8)).astype(bool) & ~kb
            med = np.median(rgb[ring], 0) if ring.any() else np.array([235, 235, 235])
            rgbA = rgb.copy(); rgbA[kb] = med.astype(np.uint8)
            bl = cv2.blur(rgbA, (9, 9)); rgbA[kb] = bl[kb]
        else:
            rgbA = cv2.inpaint(rgb, km, 7, cv2.INPAINT_TELEA)
        selA, _ = coarse_and_select(u2, mt, Image.fromarray(rgbA), rgbA)
        selA = np.where(km.astype(bool), 0, selA)
        ref = (sel > 0.5) & ~km.astype(bool)
        fellback = False
        if os.environ.get("BAND"):  # re-selection may only change the hanger band; below it keep the previous result
            bx0, by0, bx1, by1 = box; ys_, xs_ = np.where(sel > 0.5); ggh = ys_.max() - ys_.min()
            band_bottom = int(min(by1 + max(0.6 * (by1 - by0), 0.08 * ggh), rgb.shape[0]))
            band = np.zeros(sel.shape, bool); band[:band_bottom, :] = True
            selA = np.where(band, selA, np.where(km.astype(bool), 0, sel))
        elif (selA > 0.5).sum() < 0.85 * ref.sum():  # re-selection lost garment parts -> keep v10 result
            selA = np.where(km.astype(bool), 0, sel); fellback = True
        if os.environ.get("NOENC"):
            encA = np.zeros_like(kill)
        else:
            encA = enclosed_bg(rgb, selA, kill, box)
        selA = np.where(cv2.dilate(encA.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool), 0, selA)
        save(rgb, finish(rgb, selA), out / f"v11A{SUF}" / f"{i:03d}.png"); t["A_extra"] = time.perf_counter() - t0
        rec = {"i": i, "hanger": True, "fallback": fellback, "enc_B": int(enc.sum()), "enc_A": int(encA.sum()), **{k: round(x, 2) for k, x in t.items()}}
        log.write(json.dumps(rec) + "\n"); log.flush(); print(rec, flush=True)


if __name__ == "__main__":
    main()
