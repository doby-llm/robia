"""RES-3 (d3): FITB on the Polyvore sample with a d3 decision model via llama-server.
One request per question, two images: (1) collage of the partial outfit, (2) the 4 candidates labelled A-D.
Question type 'choice' over A-D. Usage: d3_fitb.py MODEL MMPROJ OUT_JSONL [--limit 40]."""
import argparse
import base64
import io
import json
import os
import subprocess
import time
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw

P = Path('${ROBIA_FEAS_DIR}/polyvore')
SERVER = Path('${ROBIA_FEAS_DIR}/llama/build/bin/llama-server')
ap = argparse.ArgumentParser()
ap.add_argument('model')
ap.add_argument('mmproj')
ap.add_argument('out')
ap.add_argument('--limit', type=int, default=40)
ap.add_argument('--port', type=int, default=9932)
a = ap.parse_args()


def tile(ids, labels=None, cell=192):
    im = Image.new('RGB', (cell * 2, cell * ((len(ids) + 1) // 2)), 'white')
    d = ImageDraw.Draw(im)
    for n, i in enumerate(ids):
        x = Image.open(P / 'img' / f'{i}.jpg').convert('RGB')
        x.thumbnail((cell - 8, cell - 8))
        ox, oy = (n % 2) * cell, (n // 2) * cell
        im.paste(x, (ox + 4, oy + 4))
        if labels:
            d.rectangle([ox + 2, oy + 2, ox + 28, oy + 26], fill='black')
            d.text((ox + 9, oy + 6), labels[n], fill='white')
    buf = io.BytesIO()
    im.save(buf, 'JPEG', quality=90)
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode()


qs = json.load(open(P / 'fitb_sample.json'))[: a.limit]
done = set()
if os.path.exists(a.out):
    done = {json.loads(line).get('id') for line in open(a.out)}
proc = subprocess.Popen([str(SERVER), '-m', a.model, '--mmproj', a.mmproj, '-t', '4', '--port', str(a.port),
                         '-c', '8192', '-ub', '4096', '-b', '4096', '--no-warmup'],
                        stdout=open(a.out + '.server.log', 'a'), stderr=subprocess.STDOUT)
url = f'http://127.0.0.1:{a.port}'
for _ in range(600):
    try:
        if json.load(urllib.request.urlopen(url + '/health', timeout=2)).get('status') == 'ok':
            break
    except Exception:
        pass
    time.sleep(1)
log = open(a.out, 'a')
try:
    for q in qs:
        if q['example_id'] in done:
            continue
        body = {'state': 'Image 1 shows the pieces of an incomplete outfit. Image 2 shows four candidate items labelled A, B, C and D.',
                'images': [tile(q['items']), tile(q['candidates'], list('ABCD'))],
                'questions': {'pick': {'type': 'choice',
                                       'instructions': 'Which candidate item completes the outfit best, matching its style and colours?',
                                       'criteria': {'A': None, 'B': None, 'C': None, 'D': None}}}}
        t = time.time()
        req = urllib.request.Request(url + '/v1/systemone', json.dumps(body).encode(), {'Content-Type': 'application/json'})
        r = json.load(urllib.request.urlopen(req, timeout=900))
        ans = r['answers']['pick']
        rec = {'id': q['example_id'], 'pred': 'ABCD'.index(ans['choice']), 'label': q['label'],
               'probs': ans['probabilities'], 'seconds': round(time.time() - t, 2), 'tokens': r['usage']['input_tokens']}
        log.write(json.dumps(rec) + '\n')
        log.flush()
        print(rec, flush=True)
finally:
    proc.terminate()
    proc.wait(30)
