"""v12 = round-3 combinations (R21). Builds on v11 (hanger detection + MobileSAM hanger mask + "hanger first" band re-selection).
Env:
  COARSE = u2netp (default, = v11) | isnet | ben2 | birefnet_lite_fp16   coarse saliency model used before magic_touch selection
  FILL   = median (default, = v11 AMB) | lama                            how the hanger is painted out before re-selection
  NOSEL  = 1 -> skip magic_touch selection, use the coarse matte directly (tests the matting model alone inside the pipeline)
Usage: python -I pipeline_v12.py OUT_DIR IDS...  -> OUT_DIR/v12_<COARSE>_<FILL>[_nosel]/NNN.png + times.jsonl
Run only through r3bench/scripts/heavy.sh (systemd MemoryMax), never in parallel with another model job.
"""
import json, os, resource, sys, time
from pathlib import Path
import numpy as np
import cv2
import onnxruntime as ort
from PIL import Image, ImageOps
from ai_edge_litert.interpreter import Interpreter

sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).parent / "r3bench"))
from bench_v2 import magic  # noqa: E402
from bench_v3 import third_points  # noqa: E402
from bench_v6 import MobileSam  # noqa: E402
from pipeline_v11 import RAW, MD, finish, save, hanger_mask  # noqa: E402
import run_benchmark as rb  # noqa: E402

COARSE = os.environ.get("COARSE", "u2netp")
FILL = os.environ.get("FILL", "median")
NOSEL = bool(os.environ.get("NOSEL"))


def sess(path, threads=3):
    so = ort.SessionOptions(); so.intra_op_num_threads = threads; so.log_severity_level = 3; so.enable_cpu_mem_arena = False
    return ort.InferenceSession(str(path), so, providers=["CPUExecutionProvider"])


def select(coarse_sess, mt, im, rgb):
    coarse = rb.mask(coarse_sess, rb.SPECS[COARSE], im)
    if NOSEL:
        return coarse
    hard = coarse > 0.5
    sel = np.zeros(hard.shape, np.float32)
    for p in third_points(hard):
        mm, _ = magic(mt, rgb, p); mb = mm > 0.5
        if mb.sum() and (mb & hard).sum() / mb.sum() >= 0.6:
            sel = np.maximum(sel, mm)
    return sel if sel.max() > 0 else coarse


def fill_median(rgb, km):
    kb = km.astype(bool)
    ring = cv2.dilate(km, np.ones((25, 25), np.uint8)).astype(bool) & ~kb
    med = np.median(rgb[ring], 0) if ring.any() else np.array([235, 235, 235])
    out = rgb.copy(); out[kb] = med.astype(np.uint8)
    bl = cv2.blur(out, (9, 9)); out[kb] = bl[kb]
    return out


def fill_lama(lama, rgb, km, box):
    """LaMa on a square crop around the hanger box (512 input), pasted back only under the mask."""
    h, w = km.shape
    x0, y0, x1, y1 = box
    ys, xs = np.where(km > 0)
    x0, y0, x1, y1 = min(x0, xs.min()), min(y0, ys.min()), max(x1, xs.max()), max(y1, ys.max())
    side = int(max(x1 - x0, y1 - y0) * 1.6) + 32
    side = min(max(side, 256), max(h, w))
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    cx0 = int(np.clip(cx - side // 2, 0, max(w - side, 0))); cy0 = int(np.clip(cy - side // 2, 0, max(h - side, 0)))
    crop = rgb[cy0:cy0 + side, cx0:cx0 + side]; mcrop = km[cy0:cy0 + side, cx0:cx0 + side]
    ch, cw = mcrop.shape
    x = cv2.resize(crop, (512, 512), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
    m = (cv2.resize(mcrop, (512, 512), interpolation=cv2.INTER_NEAREST) > 0).astype(np.float32)
    o = lama.run(None, {"image": x.transpose(2, 0, 1)[None], "mask": m[None, None]})[0][0].transpose(1, 2, 0)
    if o.max() <= 1.5:
        o = o * 255.0
    o = cv2.resize(np.clip(o, 0, 255).astype(np.uint8), (cw, ch), interpolation=cv2.INTER_CUBIC)
    out = rgb.copy(); region = out[cy0:cy0 + ch, cx0:cx0 + cw]
    mb = mcrop > 0; region[mb] = o[mb]
    return out


def main():
    out = Path(sys.argv[1]); idx = [int(a) for a in sys.argv[2:]]
    name = f"v12_{COARSE}_{FILL}" + ("_nosel" if NOSEL else "")
    od = out / name; od.mkdir(parents=True, exist_ok=True)
    coarse_sess = sess(MD / rb.SPECS[COARSE]["file"])
    det = sess(MD / "dfine_s_obj365.onnx")
    mt = Interpreter(model_path=str(MD / "magic_touch.tflite"), num_threads=3); mt.allocate_tensors()
    sam = MobileSam()
    lama = sess(MD / "lama_fp32.onnx") if FILL == "lama" else None
    done = {int(json.loads(l)["i"]) for l in (od / "times.jsonl").read_text().splitlines()} if (od / "times.jsonl").exists() else set()
    log = open(od / "times.jsonl", "a")
    for i in idx:
        if i in done and (od / f"{i:03d}.png").exists():
            continue
        im = ImageOps.exif_transpose(Image.open(RAW[i])).convert("RGB"); im.thumbnail((1024, 1024), Image.LANCZOS)
        rgb = np.ascontiguousarray(np.asarray(im))
        t0 = time.perf_counter()
        sel = select(coarse_sess, mt, im, rgb)
        kill, box = hanger_mask(det, sam, rgb, sel)
        hanger = kill is not None
        if hanger:
            km = cv2.dilate(kill.astype(np.uint8), np.ones((9, 9), np.uint8))
            rgbA = fill_lama(lama, rgb, km, box) if FILL == "lama" else fill_median(rgb, km)
            selA = select(coarse_sess, mt, Image.fromarray(rgbA), rgbA)
            bx0, by0, bx1, by1 = box; ys_, xs_ = np.where(sel > 0.5); ggh = ys_.max() - ys_.min()
            band_bottom = int(min(by1 + max(0.6 * (by1 - by0), 0.08 * ggh), rgb.shape[0]))
            band = np.zeros(sel.shape, bool); band[:band_bottom, :] = True
            sel = np.where(band, np.where(km.astype(bool), 0, selA), np.where(km.astype(bool), 0, sel))
        save(rgb, finish(rgb, sel), od / f"{i:03d}.png")
        rec = {"i": i, "hanger": hanger, "ms": round((time.perf_counter() - t0) * 1000),
               "peak_rss_mb": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024)}
        log.write(json.dumps(rec) + "\n"); log.flush(); print(name, rec, flush=True)


if __name__ == "__main__":
    main()
