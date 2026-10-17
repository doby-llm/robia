"""Robia segmentation bench v2 (autonomous refinement). Usage: python -I bench_v2.py OUT_DIR IDX...
Reuses OUT_DIR/{i}_src.jpg and {i}_isnet.png from bench.py.
Pipelines written as {i}_{name}.png alpha masks + v2_times.json:
  sam_box      SAM2.1-tiny, box prompt = ISNet bbox, best-iou mask
  sam_pts      SAM2.1-tiny, box + 3 positive points inside ISNet core + negative point on hanger hook (auto)
  magic        MediaPipe magic_touch (interactive segmenter) with an automatic point at the garment core
  fused        region from sam_pts (dilated) x ISNet soft alpha, then guided-filter edge refinement on the RGB
"""
import sys, json, time
from pathlib import Path
import numpy as np
import cv2
import onnxruntime as ort
from PIL import Image

MD = Path(__file__).parent / "models"


def guided(I, p, r=6, eps=1e-3):
    """Grey guided filter (He et al.). I guide in [0,1], p input in [0,1]."""
    k = (2 * r + 1, 2 * r + 1)
    mI = cv2.boxFilter(I, -1, k); mp = cv2.boxFilter(p, -1, k)
    cov = cv2.boxFilter(I * p, -1, k) - mI * mp
    var = cv2.boxFilter(I * I, -1, k) - mI * mI
    a = cov / (var + eps); b = mp - a * mI
    return cv2.boxFilter(a, -1, k) * I + cv2.boxFilter(b, -1, k)


def core_points(mask, n=3):
    """Positive points: maxima of the distance transform of the hard mask (deep inside the garment)."""
    hard = (mask > 0.5).astype(np.uint8)
    dt = cv2.distanceTransform(hard, cv2.DIST_L2, 5)
    pts = []
    d = dt.copy()
    for _ in range(n):
        y, x = np.unravel_index(np.argmax(d), d.shape)
        if d[y, x] <= 0:
            break
        pts.append((x, y))
        cv2.circle(d, (int(x), int(y)), int(max(dt[y, x] * 1.5, 10)), 0, -1)
    return pts


def hook_point(mask):
    """Hanger hook heuristic: topmost mask pixel when the top band of the mask is much narrower than the body.
    Returns (x, y) or None."""
    hard = mask > 0.5
    rows = np.where(hard.any(1))[0]
    if len(rows) < 10:
        return None
    top, bot = rows[0], rows[-1]
    h = bot - top
    widths = hard[top:bot].sum(1)
    body = np.percentile(widths, 75)
    band = widths[: max(3, int(h * 0.06))]
    if band.max() < 0.12 * body:  # thin structure on top = hook / wire
        xs = np.where(hard[top + 1])[0]
        return (int(xs.mean()), int(top + 2))
    return None


class Sam:
    def __init__(self):
        so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.log_severity_level = 3
        self.enc = ort.InferenceSession(str(MD / "sam2t_vision_encoder.onnx"), so, providers=["CPUExecutionProvider"])
        self.dec = ort.InferenceSession(str(MD / "sam2t_prompt_encoder_mask_decoder.onnx"), so, providers=["CPUExecutionProvider"])

    def embed(self, rgb):
        x = cv2.resize(rgb, (1024, 1024), interpolation=cv2.INTER_LINEAR).astype(np.float32) / 255.0
        x = (x - np.array([0.485, 0.456, 0.406], np.float32)) / np.array([0.229, 0.224, 0.225], np.float32)
        t = time.perf_counter()
        e = self.enc.run(None, {"pixel_values": x.transpose(2, 0, 1)[None]})
        return e, time.perf_counter() - t

    def predict(self, emb, shape, box, pos=(), neg=()):
        h, w = shape
        sx, sy = 1024 / w, 1024 / h
        pts = [(x * sx, y * sy) for x, y in list(pos) + list(neg)]
        labels = [1] * len(pos) + [0] * len(neg)
        feeds = {
            "image_embeddings.0": emb[0], "image_embeddings.1": emb[1], "image_embeddings.2": emb[2],
            "input_boxes": np.array([[[box[0] * sx, box[1] * sy, box[2] * sx, box[3] * sy]]], np.float32),
        }
        if pts:
            feeds["input_points"] = np.array([[pts]], np.float32)
            feeds["input_labels"] = np.array([[labels]], np.int64)
        else:
            feeds["input_points"] = np.zeros((1, 1, 0, 2), np.float32)
            feeds["input_labels"] = np.zeros((1, 1, 0), np.int64)
        t = time.perf_counter()
        iou, masks, _ = self.dec.run(None, feeds)
        dt = time.perf_counter() - t
        k = int(np.argmax(iou[0, 0]))
        logit = masks[0, 0, k]
        prob = 1 / (1 + np.exp(-cv2.resize(logit, (w, h), interpolation=cv2.INTER_LINEAR)))
        return prob, dt, iou[0, 0].tolist()


def magic(interp, rgb, pt):
    h, w = rgb.shape[:2]
    x = cv2.resize(rgb, (512, 512), interpolation=cv2.INTER_LINEAR).astype(np.float32) / 255.0
    roi = np.zeros((512, 512), np.float32)
    cv2.circle(roi, (int(pt[0] * 512 / w), int(pt[1] * 512 / h)), 6, 1.0, -1)  # keypoint channel
    inp = np.concatenate([x, roi[..., None]], -1)[None]
    d = interp.get_input_details()[0]; o = interp.get_output_details()[0]
    interp.set_tensor(d["index"], inp.astype(np.float32))
    t = time.perf_counter(); interp.invoke(); dt = time.perf_counter() - t
    m = interp.get_tensor(o["index"])[0, :, :, 0]
    if m.min() < 0 or m.max() > 1:
        m = 1 / (1 + np.exp(-m))
    return cv2.resize(m, (w, h), interpolation=cv2.INTER_LINEAR), dt


def main():
    out = Path(sys.argv[1]); idx = [int(a) for a in sys.argv[2:]]
    sam = Sam()
    try:
        from ai_edge_litert.interpreter import Interpreter
        mt = Interpreter(model_path=str(MD / "magic_touch.tflite"), num_threads=4); mt.allocate_tensors()
    except Exception as e:  # noqa
        print("magic_touch unavailable", e); mt = None
    tf = out / "v2_times.json"
    times = json.loads(tf.read_text()) if tf.exists() else {}
    for i in idx:
        rgb = np.asarray(Image.open(out / f"{i:03d}_src.jpg").convert("RGB"))
        h, w = rgb.shape[:2]
        isn = np.asarray(Image.open(out / f"{i:03d}_isnet.png")).astype(np.float32) / 255.0
        ys, xs = np.where(isn > 0.5)
        pad = int(0.02 * max(h, w))
        box = (max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad, w - 1), min(ys.max() + pad, h - 1))
        pos = core_points(isn, 3)
        hook = hook_point(isn)
        emb, t_enc = sam.embed(rgb)
        p_box, t_d1, iou1 = sam.predict(emb, (h, w), box)
        p_pts, t_d2, iou2 = sam.predict(emb, (h, w), box, pos, [hook] if hook else [])
        rec = {"enc": round(t_enc, 2), "dec": round(t_d2, 3), "hook": hook, "iou_box": iou1, "iou_pts": iou2}
        Image.fromarray((p_box * 255).astype(np.uint8)).save(out / f"{i:03d}_sam_box.png")
        Image.fromarray((p_pts * 255).astype(np.uint8)).save(out / f"{i:03d}_sam_pts.png")
        # fused: SAM decides WHICH pixels belong to the garment, ISNet gives the soft edge, guided filter snaps to RGB
        region = cv2.dilate((p_pts > 0.5).astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
        n, lab, st, _ = cv2.connectedComponentsWithStats(region, 8)
        if n > 2:  # drop small islands, keep components >= 3% of the largest
            big = st[1:, cv2.CC_STAT_AREA].max()
            region = np.isin(lab, [j for j in range(1, n) if st[j, cv2.CC_STAT_AREA] >= 0.03 * big]).astype(np.uint8)
        soft = isn * region
        t = time.perf_counter()
        grey = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
        ref = np.clip(guided(grey, soft, r=4, eps=2e-3), 0, 1)
        ref = np.where(soft > 0.97, 1.0, ref) * region
        rec["guided"] = round(time.perf_counter() - t, 3)
        Image.fromarray((ref * 255).astype(np.uint8)).save(out / f"{i:03d}_fused.png")
        if mt is not None:
            mm, t_m = magic(mt, rgb, pos[0])
            rec["magic"] = round(t_m, 2)
            Image.fromarray((mm * 255).clip(0, 255).astype(np.uint8)).save(out / f"{i:03d}_magic.png")
        times[str(i)] = rec
        tf.write_text(json.dumps(times, indent=1))
        print(i, rec["enc"], rec["dec"], rec.get("magic"), "hook" if hook else "", flush=True)


if __name__ == "__main__":
    main()
