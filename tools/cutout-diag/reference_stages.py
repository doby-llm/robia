"""Reference stage dump for the precise cutout (same stages and file names as the app's debug dump, REQ-CU-01).

Runs tools/segmentation-bench/pipeline_v11.py logic (chosen variant: BAND=1, FILL=median, NOENC=1) on every JPEG in IN_DIR
and writes OUT_DIR/<stem>/{s1_saliency,s2_selection,s4_kill,s4b_filled,s4b_reselect,s5_alpha}.png, s3_hanger.json,
final.png (RGBA, cropped) and report.json.

Usage (models in tools/segmentation-bench/models, see its README):
  python -I reference_stages.py IN_DIR OUT_DIR
Personal photos must never be committed; IN_DIR/OUT_DIR live outside the repository.
"""
import json
import sys
import time
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps

BENCH = Path(__file__).resolve().parent.parent / 'segmentation-bench'
sys.path.insert(0, str(BENCH))
import onnxruntime as ort  # noqa: E402
from ai_edge_litert.interpreter import Interpreter  # noqa: E402
from bench import MODELS, run  # noqa: E402
from bench_v2 import magic  # noqa: E402
from bench_v3 import third_points  # noqa: E402
from bench_v6 import MobileSam  # noqa: E402
from pipeline_v11 import finish, hanger_mask, save  # noqa: E402

MD = BENCH / 'models'


def png(a, path):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(path)


def select(u2, mt, im, rgb):
    m, _ = run(u2, MODELS['u2netp'], im)
    coarse = np.asarray(m).astype(np.float32) / 255.0
    hard = coarse > 0.5
    sel = np.zeros(coarse.shape, np.float32)
    pts = []
    for p in third_points(hard):
        mm, _ = magic(mt, rgb, p)
        mb = mm > 0.5
        ok = bool(mb.sum() and (mb & hard).sum() / mb.sum() >= 0.6)
        pts.append({'x': int(p[0]), 'y': int(p[1]), 'accepted': ok})
        if ok:
            sel = np.maximum(sel, mm)
    return (sel if sel.max() > 0 else coarse), coarse, pts


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    so.log_severity_level = 3
    u2 = ort.InferenceSession(str(MD / 'u2netp.onnx'), so, providers=['CPUExecutionProvider'])
    det = ort.InferenceSession(str(MD / 'dfine_s_obj365.onnx'), so, providers=['CPUExecutionProvider'])
    mt = Interpreter(model_path=str(MD / 'magic_touch.tflite'), num_threads=4)
    mt.allocate_tensors()
    sam = MobileSam()
    for photo in sorted(src.glob('*.jp*g')):
        d = out / photo.stem
        d.mkdir(parents=True, exist_ok=True)
        t0 = time.perf_counter()
        im = ImageOps.exif_transpose(Image.open(photo)).convert('RGB')
        im.thumbnail((1024, 1024), Image.LANCZOS)
        rgb = np.ascontiguousarray(np.asarray(im))
        sel, coarse, pts = select(u2, mt, im, rgb)
        png(coarse, d / 's1_saliency.png')
        png(sel, d / 's2_selection.png')
        kill, box = hanger_mask(det, sam, rgb, sel)
        rep = {'work': list(rgb.shape[1::-1]), 's2_points': pts, 'hanger_box': box, 'kill': kill is not None}
        (d / 's3_hanger.json').write_text(json.dumps({'box': box}))
        final_sel = sel
        if kill is not None:
            png(kill.astype(np.float32), d / 's4_kill.png')
            km = cv2.dilate(kill.astype(np.uint8), np.ones((9, 9), np.uint8))
            kb = km.astype(bool)
            ring = cv2.dilate(km, np.ones((25, 25), np.uint8)).astype(bool) & ~kb
            med = np.median(rgb[ring], 0) if ring.any() else np.array([235, 235, 235])
            filled = rgb.copy()
            filled[kb] = med.astype(np.uint8)
            bl = cv2.blur(filled, (9, 9))
            filled[kb] = bl[kb]
            Image.fromarray(filled).save(d / 's4b_filled.png')
            selA, _, _ = select(u2, mt, Image.fromarray(filled), filled)
            selA = np.where(kb, 0, selA)
            bx0, by0, bx1, by1 = box
            ys_, _ = np.where(sel > 0.5)
            band_bottom = int(min(by1 + max(0.6 * (by1 - by0), 0.08 * (ys_.max() - ys_.min())), rgb.shape[0]))
            band = np.zeros(sel.shape, bool)
            band[:band_bottom, :] = True
            final_sel = np.where(band, selA, np.where(kb, 0, sel))
            png(final_sel, d / 's4b_reselect.png')
            rep['band_bottom'] = band_bottom
        a8 = finish(rgb, final_sel)
        png(a8.astype(np.float32) / 255.0, d / 's5_alpha.png')
        save(rgb, a8, d / 'final.png')
        rep['seconds'] = round(time.perf_counter() - t0, 2)
        rep['opaque_fraction_of_s2'] = round(float((a8 > 127).sum() / max((sel > 0.5).sum(), 1)), 3)
        (d / 'report.json').write_text(json.dumps(rep, indent=1))
        print(photo.name, json.dumps(rep), flush=True)


if __name__ == '__main__':
    main()
