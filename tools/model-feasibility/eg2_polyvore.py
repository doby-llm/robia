"""Embed Polyvore sample items with EmbeddingGemma 2 (same recipe as the app: composite on white, long edge <=1024,
PNG, 70 vision tokens, normalize). Also embeds item titles as TEXT ('title: none | text: ' + title).
Resumable. Output: polyvore/eg2/<item>.npy (image) and <item>_t.npy (text), timings in eg2_times.jsonl."""
import io
import json
import time
from pathlib import Path

import litert_lm as L
import numpy as np
from PIL import Image

R = Path('${ROBIA_PLANNING_DIR}')
P = R / 'r3/research/polyvore'
out = P / 'eg2'
out.mkdir(exist_ok=True)
meta = json.load(open(P / 'items_meta.json'))
# Pi budget: only the items of the first 60 FITB questions (~490 images, ~1 h at ~7 s/image)
keep = set()
for q in json.load(open(P / 'fitb_sample.json'))[:60]:
    keep.update(q['items'])
    keep.update(q['candidates'])
meta = {k: v for k, v in meta.items() if k in keep}
engine = L.EmbeddingEngine(str(R / 'model/embeddinggemma-2-text-vision-440m.litertlm'), backend=L.Backend.CPU(),
                           vision_backend=L.Backend.CPU(), vision_tokens_per_image=70)
opt = L.EmbeddingOptions(normalize=True)
log = open(P / 'eg2_times.jsonl', 'a')
tmp = P / 'eg2_tmp.png'
for n, (iid, m) in enumerate(sorted(meta.items()), 1):
    f = out / f'{iid}.npy'
    if not f.exists():
        im = Image.open(P / 'img' / f'{iid}.jpg').convert('RGBA')
        bg = Image.new('RGBA', im.size, 'white')
        bg.alpha_composite(im)
        bg = bg.convert('RGB')
        bg.thumbnail((1024, 1024))
        bg.save(tmp, 'PNG')
        t = time.time()
        v = np.asarray(engine.compute_embedding(L.Content.ImageFile(str(tmp)), opt).embedding, dtype=np.float32)
        log.write(json.dumps({'item': iid, 'kind': 'image', 's': round(time.time() - t, 3)}) + '\n')
        np.save(f, v / np.linalg.norm(v))
    ft = out / f'{iid}_t.npy'
    if not ft.exists():
        text = (m.get('title') or '').strip() or m.get('category') or 'garment'
        v = np.asarray(engine.compute_embedding('title: none | text: ' + text, opt).embedding, dtype=np.float32)
        np.save(ft, v / np.linalg.norm(v))
    if n % 50 == 0:
        print(n, len(meta), flush=True)
        log.flush()
print('done')
