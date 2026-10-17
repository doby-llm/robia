"""Robia seg bench v4: saliency + hanger detector + interactive refinement, fully automatic.
Usage: python -I bench_v4.py OUT_DIR IDX...
 1. ISNet soft mask (from bench.py)                         -> coarse garment + leaks
 2. magic_touch multi-point union (v3 logic, file {i}_v3_isnet.png) -> fixes white-on-white door leaks
 3. D-FINE-S Objects365 detector (Apache-2.0): boxes for class 'Hanger' (101), score >= 0.35
 4. hanger pixels = magic_touch(point on the hanger box's thin top part) ∩ hanger box, else
    (box ∩ selection ∩ thin-structure) ; subtract from selection; garment area may not drop > 15%
Writes {i}_v4.png, {i}_v4_dbg.json, prints timings.
"""
import sys, json, time
from pathlib import Path
import numpy as np
import cv2
import onnxruntime as ort
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

sys.path.insert(0, str(Path(__file__).parent))
from bench_v2 import magic  # noqa: E402

MD = Path(__file__).parent / "models"
HANGER = 101


def detect(sess, rgb, thr=0.35):
    h, w = rgb.shape[:2]
    x = cv2.resize(rgb, (640, 640), interpolation=cv2.INTER_LINEAR).astype(np.float32) / 255.0
    t = time.perf_counter()
    logits, boxes = sess.run(None, {"pixel_values": x.transpose(2, 0, 1)[None]})
    dt = time.perf_counter() - t
    p = 1 / (1 + np.exp(-logits[0]))
    res = []
    for q in np.argsort(-p[:, HANGER])[:5]:
        s = float(p[q, HANGER])
        if s < thr:
            break
        cx, cy, bw, bh = boxes[0, q]
        res.append({"score": round(s, 3), "box": [int((cx - bw / 2) * w), int((cy - bh / 2) * h), int((cx + bw / 2) * w), int((cy + bh / 2) * h)]})
    top = [(int(c), round(float(p[q, c]), 2)) for q in np.argsort(-p.max(1))[:3] for c in [int(np.argmax(p[q]))]]
    return res, dt, top


def main():
    out = Path(sys.argv[1]); idx = [int(a) for a in sys.argv[2:]]
    so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.log_severity_level = 3
    det = ort.InferenceSession(str(MD / "dfine_s_obj365.onnx"), so, providers=["CPUExecutionProvider"])
    mt = Interpreter(model_path=str(MD / "magic_touch.tflite"), num_threads=4); mt.allocate_tensors()
    for i in idx:
        rgb = np.asarray(Image.open(out / f"{i:03d}_src.jpg").convert("RGB"))
        sel = np.asarray(Image.open(out / f"{i:03d}_v3_isnet.png")).astype(np.float32) / 255.0
        hangers, dt, top = detect(det, rgb)
        g_area = (sel > 0.5).sum()
        removed = 0
        for hg in hangers:
            x0, y0, x1, y1 = [max(v, 0) for v in hg["box"]]
            boxm = np.zeros(sel.shape, bool); boxm[y0:y1, x0:x1] = True
            # point on the hanger: the selected pixel in the box that is farthest from the garment body centre (top part)
            cand = np.argwhere(boxm & (sel > 0.5))
            hm = np.zeros(sel.shape, bool)
            if len(cand):
                y, x = cand[np.argmin(cand[:, 0])]
                m, _ = magic(mt, rgb, (int(x), int(y) + 3))
                hm = (m > 0.5) & boxm
            if hm.sum() == 0 or hm.sum() > 0.15 * g_area:
                # fallback: thin structures inside the box only (opening restricted to the box)
                k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15))
                inside = ((sel > 0.5) & boxm).astype(np.uint8)
                hm = (inside > 0) & ~cv2.morphologyEx(inside, cv2.MORPH_OPEN, k).astype(bool)
            if hm.sum() <= 0.15 * g_area:
                kill = cv2.dilate(hm.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
                removed += int((kill & (sel > 0.5)).sum())
                sel = np.where(kill, 0, sel)
        # drop islands created by the cut
        hb = (sel > 0.5).astype(np.uint8)
        n, lab, st, _ = cv2.connectedComponentsWithStats(hb, 8)
        if n > 2:
            big = st[1:, cv2.CC_STAT_AREA].max()
            keep = np.isin(lab, [j for j in range(1, n) if st[j, cv2.CC_STAT_AREA] >= 0.03 * big])
            sel = sel * cv2.dilate(keep.astype(np.uint8), np.ones((5, 5), np.uint8))
        Image.fromarray((sel * 255).astype(np.uint8)).save(out / f"{i:03d}_v4.png")
        dbg = {"det_s": round(dt, 2), "hangers": hangers, "removed_frac": round(removed / max(g_area, 1), 3), "top": top}
        (out / f"{i:03d}_v4_dbg.json").write_text(json.dumps(dbg))
        print(i, dbg, flush=True)


if __name__ == "__main__":
    main()
