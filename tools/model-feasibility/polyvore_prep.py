"""Pick N FITB questions (nondisjoint valid, fixed seed) and extract their item images from the parquet shards.
Writes polyvore/fitb_sample.json and polyvore/img/<item_id>.jpg (+ items_meta.json with category/title)."""
import io
import json
import random
import sys
from pathlib import Path

import pyarrow.parquet as pq
from PIL import Image

P = Path('${ROBIA_FEAS_DIR}/polyvore')
N = int(sys.argv[1]) if len(sys.argv) > 1 else 150
qs = json.load(open(P / 'fitb_valid.json'))
random.seed(20261022)
sample = random.sample(qs, N)
s2i = json.load(open(P / 'setidx_to_item.json'))
for q in sample:
    q['items'] = [s2i[x] for x in q['items']]
    q['candidates'] = [s2i[x] for x in q['candidates']]
need = set()
for q in sample:
    need.update(q['items'])
    need.update(q['candidates'])
json.dump(sample, open(P / 'fitb_sample.json', 'w'))
(P / 'img').mkdir(exist_ok=True)
meta = {}
for shard in sorted((P / 'ds/data').glob('*.parquet')):
    pf = pq.ParquetFile(shard)
    for rg in range(pf.num_row_groups):
        ids = pf.read_row_group(rg, columns=['item_id']).column('item_id').to_pylist()
        hit = [i for i, x in enumerate(ids) if x in need and x not in meta]
        if not hit:
            continue
        t = pf.read_row_group(rg, columns=['item_id', 'image', 'title', 'category'])
        for i in hit:
            row = {k: t.column(k)[i].as_py() for k in ['item_id', 'image', 'title', 'category']}
            raw = row['image']['bytes'] if isinstance(row['image'], dict) else row['image']
            img = Image.open(io.BytesIO(raw)).convert('RGB')
            img.save(P / 'img' / f"{row['item_id']}.jpg", quality=92)
            meta[row['item_id']] = {'title': row['title'], 'category': row['category'], 'wh': img.size}
    print(shard.name, len(meta), '/', len(need), flush=True)
json.dump(meta, open(P / 'items_meta.json', 'w'))
print('missing', len(need - set(meta)))
