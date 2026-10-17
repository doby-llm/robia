"""Robia seg bench v6 = v5 with MobileSAM (MIT, 28 MB encoder + 16 MB decoder) instead of SAM2.1-tiny.
Usage: python -I bench_v6.py OUT_DIR BASE IDX...  (BASE: v3_isnet | v3_u2netp = garment selection file suffix)
Writes {i}_v6_{BASE}.png
"""
import sys, json, time
from pathlib import Path
import numpy as np
import cv2
import onnxruntime as ort
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from bench_v2 import core_points  # noqa: E402

MD = Path(__file__).parent / "models"


class MobileSam:
    def __init__(self):
        so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.log_severity_level = 3
        self.enc = ort.InferenceSession(str(MD / "msam_mobile_sam_image_encoder.onnx"), so, providers=["CPUExecutionProvider"])
        self.dec = ort.InferenceSession(str(MD / "msam_sam_mask_decoder_multi.onnx"), so, providers=["CPUExecutionProvider"])

    def embed(self, rgb):
        h, w = rgb.shape[:2]
        self.scale = 1024 / max(h, w); self.shape = (h, w)
        x = cv2.resize(rgb, (int(round(w * self.scale)), int(round(h * self.scale))), interpolation=cv2.INTER_LINEAR).astype(np.float32)
        t = time.perf_counter(); e = self.enc.run(None, {"input_image": x})[0]
        return e, time.perf_counter() - t

    def predict(self, emb, box, pos=(), neg=()):
        s = self.scale
        pts = [(x * s, y * s) for x, y in list(pos) + list(neg)] + [(box[0] * s, box[1] * s), (box[2] * s, box[3] * s)]
        lab = [1] * len(pos) + [0] * len(neg) + [2, 3]
        feeds = {"image_embeddings": emb, "point_coords": np.array([pts], np.float32), "point_labels": np.array([lab], np.float32),
                 "mask_input": np.zeros((1, 1, 256, 256), np.float32), "has_mask_input": np.zeros(1, np.float32),
                 "orig_im_size": np.array(self.shape, np.float32)}
        t = time.perf_counter(); masks, iou, _ = self.dec.run(None, feeds)
        k = int(np.argmax(iou[0]))
        return 1 / (1 + np.exp(-masks[0, k])), time.perf_counter() - t


def main():
    out = Path(sys.argv[1]); base = sys.argv[2]; idx = [int(a) for a in sys.argv[3:]]
    sam = MobileSam(); encs = []
    for i in idx:
        rgb = np.asarray(Image.open(out / f"{i:03d}_src.jpg").convert("RGB"))
        h, w = rgb.shape[:2]
        sel = np.asarray(Image.open(out / f"{i:03d}_{base}.png")).astype(np.float32) / 255.0
        dbg = json.loads((out / f"{i:03d}_v4_dbg.json").read_text())
        boxes = [b for b in dbg["hangers"] if b["score"] >= 0.45]
        rec = {}
        if boxes:
            hb = max(boxes, key=lambda b: b["score"])["box"]
            x0, y0, x1, y1 = max(hb[0], 0), max(hb[1], 0), min(hb[2], w - 1), min(hb[3], h - 1)
            hard = sel > 0.5
            inbox = np.argwhere(hard[y0:y1, x0:x1])
            if len(inbox):
                yy, xx = inbox[np.argmin(inbox[:, 0])]
                hook = (int(xx + x0), int(yy + y0 + 2))
                core = core_points(sel, 3)
                emb, te = sam.embed(rgb); encs.append(te)
                hp, _ = sam.predict(emb, (x0, y0, x1, y1), [hook], core)
                ys, xs = np.where(hard)
                gp, td = sam.predict(emb, (xs.min(), ys.min(), xs.max(), ys.max()), core, [hook])
                boxm = np.zeros((h, w), bool); boxm[y0:y1, x0:x1] = True
                kill = (hp > 0.5) & (gp < 0.5) & boxm
                frac = kill.sum() / max(hard.sum(), 1)
                if frac <= 0.15:
                    kill = cv2.dilate(kill.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
                    sel = np.where(kill, 0, sel)
                rec = {"frac": round(float(frac), 3), "enc": round(te, 2), "dec": round(td, 3)}
        hb2 = (sel > 0.5).astype(np.uint8)
        n, lab, st, _ = cv2.connectedComponentsWithStats(hb2, 8)
        if n > 2:
            big = st[1:, cv2.CC_STAT_AREA].max()
            keep = np.isin(lab, [j for j in range(1, n) if st[j, cv2.CC_STAT_AREA] >= 0.03 * big])
            sel = sel * cv2.dilate(keep.astype(np.uint8), np.ones((5, 5), np.uint8))
        Image.fromarray((sel * 255).astype(np.uint8)).save(out / f"{i:03d}_v6_{base}.png")
        print(i, rec, flush=True)
    if encs:
        print("median enc", sorted(encs)[len(encs) // 2])


if __name__ == "__main__":
    main()
