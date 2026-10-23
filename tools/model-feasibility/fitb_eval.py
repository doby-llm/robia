"""Zero-shot FITB on the Polyvore sample with EG2 vectors (no training).
Scorers: img = mean cos(candidate image, outfit item images); txt = same on title vectors; mix = 0.7 img + 0.3 txt.
Prints accuracy (chance 25%) and a bootstrap 95% interval."""
import json
import random
from pathlib import Path

import numpy as np

P = Path('${ROBIA_FEAS_DIR}/polyvore')
qs = json.load(open(P / 'fitb_sample.json'))
E = P / 'eg2'


def vec(i, t=False):
    f = E / (f'{i}_t.npy' if t else f'{i}.npy')
    return np.load(f) if f.exists() else None


res = {'img': [], 'txt': [], 'mix': []}
for q in qs:
    items = [i for i in q['items']]
    cands = q['candidates']
    vi = [vec(i) for i in items]
    vc = [vec(c) for c in cands]
    if any(v is None for v in vi + vc):
        continue
    ti = [vec(i, True) for i in items]
    tc = [vec(c, True) for c in cands]
    s_img = [np.mean([c @ v for v in vi]) for c in vc]
    s_txt = [np.mean([c @ v for v in ti]) for c in tc]
    s_mix = [0.7 * a + 0.3 * b for a, b in zip(s_img, s_txt)]
    for k, s in (('img', s_img), ('txt', s_txt), ('mix', s_mix)):
        res[k].append(int(np.argmax(s) == q['label']))
for k, v in res.items():
    if not v:
        continue
    random.seed(0)
    boots = sorted(np.mean(random.choices(v, k=len(v))) for _ in range(2000))
    print(f'{k}: n={len(v)} acc={np.mean(v):.3f} ci95=[{boots[50]:.3f},{boots[1950]:.3f}]')
