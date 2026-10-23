"""RES-1/RES-2: d3-edge (llama.cpp, CPU) latency + category accuracy on Manu's 142 photos.

Usage: python d3_bench.py MODEL_GGUF MMPROJ_GGUF OUT_JSONL --size 512 [--limit N] [--threads 4]
Starts llama-server as a child, asks one 21-way category choice per photo, logs latency and answer.
Resumable: skips stems already in OUT_JSONL for the same size.
"""
import argparse
import base64
import io
import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

R = Path('${ROBIA_PLANNING_DIR}')
sys.path.insert(0, str(R / 'workers/coord/tools/embedding-precision'))
from labels import CATEGORIES, OCCASIONS  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('model')
ap.add_argument('mmproj')
ap.add_argument('out')
ap.add_argument('--size', type=int, default=512)
ap.add_argument('--limit', type=int, default=0)
ap.add_argument('--threads', type=int, default=4)
ap.add_argument('--port', type=int, default=9931)
ap.add_argument('--occasion', action='store_true')
ap.add_argument('--min-img-tokens', type=int, default=0)
a = ap.parse_args()

server = R / 'r3/research/llama/build/bin/llama-server'
labels = json.load(open(R / 'precision-data/provisional_labels.json'))
stems = sorted(labels)
if a.limit:
    stems = stems[:: max(1, len(stems) // a.limit)][: a.limit]
done = set()
if os.path.exists(a.out):
    for line in open(a.out):
        r = json.loads(line)
        if r.get('size') == a.size and 'stem' in r:
            done.add(r['stem'])

t0 = time.time()
proc = subprocess.Popen([str(server), '-m', a.model, '--mmproj', a.mmproj, '-t', str(a.threads),
                         '--port', str(a.port), '-c', '8192', '-ub', '4096', '-b', '4096', '--no-warmup'] + (['--image-min-tokens', str(a.min_img_tokens)] if a.min_img_tokens else []),
                        stdout=open(a.out + '.server.log', 'a'), stderr=subprocess.STDOUT)
url = f'http://127.0.0.1:{a.port}'
for _ in range(600):
    try:
        if json.load(urllib.request.urlopen(url + '/health', timeout=2)).get('status') == 'ok':
            break
    except Exception:
        pass
    time.sleep(1)
load_s = time.time() - t0
log = open(a.out, 'a')
log.write(json.dumps({'event': 'load', 'size': a.size, 'load_s': round(load_s, 1)}) + '\n')

cat_criteria = {k: d for k, (n, d) in CATEGORIES.items()}
occ_criteria = {k: d for k, (n, d) in OCCASIONS.items()}


def img_url(stem):
    im = Image.open(R / f'precision-data/processed/{stem}_raw.jpg')
    im = ImageOps.exif_transpose(im).convert('RGB')
    im.thumbnail((a.size, a.size))
    buf = io.BytesIO()
    im.save(buf, 'JPEG', quality=90)
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode(), im.size


try:
    for stem in stems:
        if stem in done:
            continue
        data, wh = img_url(stem)
        qs = {'category': {'type': 'choice', 'instructions': 'What kind of garment is shown in the photo?',
                           'criteria': cat_criteria}}
        if a.occasion:
            qs['occasion'] = {'type': 'choice', 'instructions': 'For which occasion is this garment best suited?',
                              'criteria': occ_criteria}
        body = json.dumps({'state': 'A photo of one garment from a personal wardrobe.', 'images': [data],
                           'questions': qs}).encode()
        t = time.time()
        req = urllib.request.Request(url + '/v1/systemone', body, {'Content-Type': 'application/json'})
        resp = json.load(urllib.request.urlopen(req, timeout=900))
        dt = time.time() - t
        ans = resp['answers']['category']
        rec = {'stem': stem, 'size': a.size, 'wh': wh, 'seconds': round(dt, 2), 'pred': ans['choice'],
               'conf': round(ans['confidence'], 3), 'ref': labels[stem]['category'],
               'ambiguous': labels[stem].get('ambiguous'), 'tokens': resp.get('usage', {}).get('input_tokens')}
        if a.occasion:
            rec['occasion'] = resp['answers']['occasion']['choice']
        log.write(json.dumps(rec) + '\n')
        log.flush()
        print(rec, flush=True)
finally:
    proc.terminate()
    proc.wait(30)
