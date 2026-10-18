"""Hanger-sensitivity scan (cheap): best plausible D-FINE 'Hanger' score per photo, full image + top crop pass.
Garment bbox from out/final_v10 alpha (pre-hanger-removal selection proxy). Writes r3bench/out/hanger_scan.jsonl."""
import json, sys
from pathlib import Path
import numpy as np, onnxruntime as ort
from PIL import Image, ImageOps
S = Path(__file__).resolve().parent; sys.path.insert(0, str(S))
from pipeline_v11 import RAW, MD  # noqa
from bench_v4 import detect  # noqa
so = ort.SessionOptions(); so.intra_op_num_threads = 3; so.log_severity_level = 3; so.enable_cpu_mem_arena = False
det = ort.InferenceSession(str(MD / 'dfine_s_obj365.onnx'), so, providers=['CPUExecutionProvider'])
out = open(S / 'r3bench/out/hanger_scan.jsonl', 'w')
for i in range(len(RAW)):
    im = ImageOps.exif_transpose(Image.open(RAW[i])).convert('RGB'); im.thumbnail((1024, 1024), Image.LANCZOS)
    rgb = np.ascontiguousarray(np.asarray(im)); h, w = rgb.shape[:2]
    a = np.asarray(Image.open(S / f'out/final_v10/{i:03d}.png').convert('RGBA').resize((w, h)))[:, :, 3] > 127
    ys, xs = np.where(a)
    if not len(ys):
        continue
    gx0, gy0, gx1, gy1 = xs.min(), ys.min(), xs.max(), ys.max(); gw, gh = gx1 - gx0, gy1 - gy0
    pl = lambda b: (b[2] - b[0]) >= 0.35 * gw and b[1] < gy0 + 0.25 * gh and b[3] < gy0 + 0.5 * gh
    full = [d for d in detect(det, rgb, 0.03)[0] if pl(d['box'])]
    cy0, cy1 = max(gy0 - int(0.15 * gh), 0), min(gy0 + int(0.35 * gh), h); cx0, cx1 = max(gx0 - int(0.1 * gw), 0), min(gx1 + int(0.1 * gw), w)
    crop = []
    for d in detect(det, np.ascontiguousarray(rgb[cy0:cy1, cx0:cx1]), 0.03)[0]:
        b = d['box']; d['box'] = [b[0] + cx0, b[1] + cy0, b[2] + cx0, b[3] + cy0]
        if pl(d['box']): crop.append(d)
    rec = {'i': i, 'full': max([d['score'] for d in full], default=0), 'crop': max([d['score'] for d in crop], default=0)}
    out.write(json.dumps(rec) + '\n'); out.flush()
print('done')
