"""Marqo-FashionSigLIP (ONNX, CPU 4 threads) on (a) Manu's 142 photos: category zero-shot + latency,
(b) Polyvore FITB sample: zero-shot compatibility by mean cosine. Writes siglip/out/*.npy and prints a summary.
Image prep = app-like: composite on white, resize 224 bicubic, mean/std 0.5."""
import json
import statistics
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps
from tokenizers import Tokenizer

R = Path('${ROBIA_PLANNING_DIR}')
S = R / 'r3/research/siglip'
sys.path.insert(0, str(R / 'workers/coord/tools/embedding-precision'))
from labels import CATEGORIES  # noqa: E402

variant = sys.argv[1] if len(sys.argv) > 1 else 'fp32'
vis_file = {'fp32': 'vision_model.onnx', 'int8': 'vision_model_int8.onnx'}[variant]
so = ort.SessionOptions()
so.intra_op_num_threads = 4
so.enable_cpu_mem_arena = False
t0 = time.time()
vis = ort.InferenceSession(str(S / 'onnx' / vis_file), so, providers=['CPUExecutionProvider'])
txt = ort.InferenceSession(str(S / 'onnx/text_model.onnx'), so, providers=['CPUExecutionProvider'])
LOG = open(S / f'result_{variant}.txt', 'a')
_print = print
def print(*a, **k):
    _print(*a, **k); LOG.write(' '.join(str(x) for x in a) + '\n'); LOG.flush()


print('load_s', round(time.time() - t0, 1), flush=True)
tok = Tokenizer.from_file(str(S / 'tokenizer.json'))


def norm(v):
    return v / np.linalg.norm(v, axis=-1, keepdims=True)


def img_vec(path):
    im = ImageOps.exif_transpose(Image.open(path)).convert('RGBA')
    bg = Image.new('RGBA', im.size, 'white')
    bg.alpha_composite(im)
    im = bg.convert('RGB').resize((224, 224), Image.BICUBIC)
    x = (np.asarray(im, dtype=np.float32) / 255.0 - 0.5) / 0.5
    x = x.transpose(2, 0, 1)[None]
    return norm(vis.run(None, {'pixel_values': x})[0][0])


def text_vec(s):
    ids = tok.encode(s.lower()).ids[:64]
    ids = ids + [1] * (64 - len(ids))  # SigLIP pads to 64 with pad id 1
    return norm(txt.run(None, {'input_ids': np.array([ids], dtype=np.int64)})[0][0])


out = S / 'out'
out.mkdir(exist_ok=True)
# (a) Manu's photos
labels = json.load(open(R / 'precision-data/provisional_labels.json'))
keys = list(CATEGORIES)
T = np.stack([text_vec('a photo of ' + CATEGORIES[k][1]) for k in keys])
times, ok, n = [], 0, 0
X = {}
for stem, lab in sorted(labels.items()):
    t = time.time()
    v = img_vec(R / f'precision-data/processed/{stem}_raw.jpg')
    times.append(time.time() - t)
    X[stem] = v
    if lab.get('ambiguous'):
        continue
    n += 1
    ok += int(keys[int(np.argmax(T @ v))] == lab['category'])
np.save(out / f'manu_{variant}.npy', np.stack([X[s] for s in sorted(X)]))
print(f'manu: n={n} cat_top1={ok / n:.3f} img_median_ms={1000 * statistics.median(times):.0f}', flush=True)
# (b) Polyvore FITB
P = R / 'r3/research/polyvore'
qs = json.load(open(P / 'fitb_sample.json'))[:60]
cache = {}


def pv(i):
    if i not in cache:
        cache[i] = img_vec(P / 'img' / f'{i}.jpg')
    return cache[i]


hits = []
for q in qs:
    vi = [pv(i) for i in q['items']]
    sc = [np.mean([pv(c) @ v for v in vi]) for c in q['candidates']]
    hits.append(int(np.argmax(sc) == q['label']))
print(f'polyvore FITB zero-shot: n={len(hits)} acc={np.mean(hits):.3f} (chance 0.25)')
