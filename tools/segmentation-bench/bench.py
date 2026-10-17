"""Segmentation benchmark for Robia (RPi CPU). Usage: python -I bench.py OUT_DIR IDX... 
Mirrors the app input: EXIF-rotate + resize long side 1024 (the app's processed size is 576x1024).
For each photo and model writes a mask PNG and timing; post-processing variants are applied later by compose.py.
"""
import sys, time, json, glob
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps
import onnxruntime as ort

RAW = sorted(glob.glob(__import__("os").environ.get("ROBIA_PHOTOS", "data/photos_raw") + "/*.jpg"))
MD = Path(__file__).parent / "models"

MODELS = {
    # name: (file, size, mean, std, needs_sigmoid, output_index)
    "u2netp": ("u2netp.onnx", 320, (0.485, 0.456, 0.406), (0.229, 0.224, 0.225), False, 0),
    "isnet": ("isnet-general-use.onnx", 1024, (0.5, 0.5, 0.5), (1.0, 1.0, 1.0), False, 0),
    "birefnet_lite": ("birefnet_lite.onnx", 1024, (0.485, 0.456, 0.406), (0.229, 0.224, 0.225), True, 0),
}


def load(i):
    im = ImageOps.exif_transpose(Image.open(RAW[i])).convert("RGB")
    im.thumbnail((1024, 1024), Image.LANCZOS)
    return im


def run(sess, spec, im):
    _, size, mean, std, sig, oi = spec
    x = np.asarray(im.resize((size, size), Image.BILINEAR), dtype=np.float32) / 255.0
    if spec[0].startswith("u2net"):
        x = x / max(x.max(), 1e-6)
    x = (x - np.array(mean, np.float32)) / np.array(std, np.float32)
    x = x.transpose(2, 0, 1)[None]
    t = time.perf_counter()
    out = sess.run(None, {sess.get_inputs()[0].name: x})[oi][0, 0]
    dt = time.perf_counter() - t
    if sig:
        out = 1 / (1 + np.exp(-out))
    else:
        lo, hi = out.min(), out.max()
        out = (out - lo) / max(hi - lo, 1e-6)
    m = Image.fromarray((out * 255).astype(np.uint8)).resize(im.size, Image.BILINEAR)
    return m, dt


def main():
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    idx = [int(a) for a in sys.argv[2:]]
    only = __import__("os").environ.get("ONLY")
    names = [n for n in MODELS if (MD / MODELS[n][0]).exists() and (not only or n in only.split(","))]
    tf = out / "times.json"
    times = __import__("json").loads(tf.read_text()) if tf.exists() and tf.stat().st_size else {}
    so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.log_severity_level = 3
    for n in names:
        sess = ort.InferenceSession(str(MD / MODELS[n][0]), so, providers=["CPUExecutionProvider"])
        times[n] = []
        for i in idx:
            im = load(i)
            if not (out / f"{i:03d}_src.jpg").exists():
                im.save(out / f"{i:03d}_src.jpg", quality=90)
            m, dt = run(sess, MODELS[n], im)
            m.save(out / f"{i:03d}_{n}.png")
            times[n].append(round(dt, 3))
            print(n, i, round(dt, 2), flush=True)
            tf.write_text(json.dumps(times, indent=1))
        del sess
    (out / "times.json").write_text(json.dumps(times, indent=1))


if __name__ == "__main__":
    main()
