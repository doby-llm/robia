"""Manu's idea, step 1: class-agnostic 'segment everything' (MobileSAM point grid), dedupe, export segment crops.
Usage: python -I seg_everything.py OUT_DIR IDX...
Writes OUT_DIR/segs/{i}/seg_{k}.png (segment on white, cropped to bbox), masks.npz, meta.json
"""
import sys, json, time
from pathlib import Path
import numpy as np
import cv2
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from bench_v6 import MobileSam  # noqa: E402

GX, GY = 6, 8  # 48 prompt points


def main():
    out = Path(sys.argv[1]); idx = [int(a) for a in sys.argv[2:]]
    sam = MobileSam()
    for i in idx:
        rgb = np.asarray(Image.open(out / f"{i:03d}_src.jpg").convert("RGB")); h, w = rgb.shape[:2]
        d = out / "segs" / f"{i:03d}"; d.mkdir(parents=True, exist_ok=True)
        t0 = time.perf_counter(); emb, te = sam.embed(rgb)
        cands = []
        for gy in range(GY):
            for gx in range(GX):
                p = (int((gx + 0.5) * w / GX), int((gy + 0.5) * h / GY))
                # point-only prompt: reuse predict() with a full-image box would bias; call decoder directly
                s = sam.scale
                feeds = {"image_embeddings": emb, "point_coords": np.array([[(p[0] * s, p[1] * s), (0, 0)]], np.float32),
                         "point_labels": np.array([[1, -1]], np.float32), "mask_input": np.zeros((1, 1, 256, 256), np.float32),
                         "has_mask_input": np.zeros(1, np.float32), "orig_im_size": np.array((h, w), np.float32)}
                masks, iou, _ = sam.dec.run(None, feeds)
                for k in range(1, 4):  # skip k=0 (single-mask token), take the 3 multimask outputs
                    m = masks[0, k] > 0
                    a = m.sum()
                    if iou[0, k] < 0.80 or a < 0.005 * h * w or a > 0.85 * h * w:
                        continue
                    cands.append((float(iou[0, k]), m))
        # NMS by IoU
        cands.sort(key=lambda c: -c[0]); keep = []
        for sc, m in cands:
            if all((m & k).sum() / (m | k).sum() < 0.7 for _, k in keep):
                keep.append((sc, m))
        keep = keep[:16]
        tsam = time.perf_counter() - t0
        meta = []
        for k, (sc, m) in enumerate(keep):
            ys, xs = np.where(m)
            crop = np.where(m[..., None], rgb, 255)[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
            Image.fromarray(crop.astype(np.uint8)).save(d / f"seg_{k}.png")
            meta.append({"k": k, "iou": round(sc, 3), "area": round(float(m.mean()), 4)})
        np.savez_compressed(d / "masks.npz", masks=np.stack([m for _, m in keep]) if keep else np.zeros((0, h, w), bool))
        (d / "meta.json").write_text(json.dumps({"segments": meta, "sam_s": round(tsam, 2), "enc_s": round(te, 2)}))
        print(i, len(cands), "->", len(keep), "segments", round(tsam, 1), "s", flush=True)


if __name__ == "__main__":
    main()
