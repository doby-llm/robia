"""Compare an app debug dump with the reference dump, stage by stage (REQ-CU-01/02/03).

  python -I compare.py APP_DUMP_DIR REF_DUMP_DIR
Both dirs contain one sub-dir per photo stem with the stage PNGs. Masks are binarised at 0.5 (alpha at 127) and resized
to the reference size (nearest) before IoU. Also prints the hanger-band edge width ratio of s5_alpha (REQ-CU-03):
mean width (px) of the 0.05..0.95 alpha transition in the band rows vs in the rows below it.
Exit code 1 when any stage IoU < 0.95 or the final alpha IoU < 0.97.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

STAGES = ['s1_saliency', 's2_selection', 's4_kill', 's4b_reselect', 's5_alpha']


def load(path, size=None):
    im = Image.open(path).convert('L')
    if size and im.size != size:
        im = im.resize(size, Image.NEAREST)
    return np.asarray(im).astype(np.float32) / 255.0


def iou(a, b):
    a, b = a > 0.5, b > 0.5
    return float((a & b).sum() / max((a | b).sum(), 1))


def transition_width(alpha, rows):
    widths = []
    for y in rows:
        r = alpha[y]
        soft = (r > 0.05) & (r < 0.95)
        if r.max() > 0.95:
            widths.append(soft.sum() / 2.0)  # two edges per row
    return float(np.mean(widths)) if widths else float('nan')


def main():
    app, ref = Path(sys.argv[1]), Path(sys.argv[2])
    bad = False
    for rd in sorted(p for p in ref.iterdir() if p.is_dir()):
        ad = app / rd.name
        row = {'photo': rd.name}
        if not ad.exists():
            print(json.dumps({**row, 'error': 'missing in app dump'}))
            bad = True
            continue
        for st in STAGES:
            rp, ap = rd / f'{st}.png', ad / f'{st}.png'
            if rp.exists() and ap.exists():
                r = load(rp)
                row[st] = round(iou(load(ap, r.shape[::-1]), r), 3)
                limit = 0.97 if st == 's5_alpha' else 0.95
                bad |= row[st] < limit
            elif rp.exists() != ap.exists():
                row[st] = 'only in ' + ('reference' if rp.exists() else 'app')
                bad = True
        rep = json.loads((rd / 'report.json').read_text()) if (rd / 'report.json').exists() else {}
        if rep.get('band_bottom') and (ad / 's5_alpha.png').exists():
            a = load(ad / 's5_alpha.png')
            bb = int(rep['band_bottom'] * a.shape[0] / rep['work'][1])
            ys = np.where(a.max(1) > 0.95)[0]
            if len(ys):
                band_rows = range(ys.min(), min(bb, a.shape[0]))
                body_rows = range(min(bb, a.shape[0]), ys.max())
                wb, wo = transition_width(a, band_rows), transition_width(a, body_rows)
                row['band_edge_ratio'] = round(wb / wo, 2) if wo == wo and wo > 0 else None
        print(json.dumps(row))
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
