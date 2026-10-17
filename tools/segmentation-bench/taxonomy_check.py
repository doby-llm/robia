"""R15 regression check: do new default categories steal top-1 from Manu's 142 labelled garments?
Usage: <embedding-precision venv python> -I taxonomy_check.py MODEL
Uses stored IMAGE vectors precision-data/emb/*_raw_vt140.npy and provisional labels.
"""
import sys, json
from pathlib import Path
import numpy as np
import litert_lm as L

sys.path.insert(0, __import__("os").path.join(__import__("os").path.dirname(__file__), "..", "embedding-precision"))
from labels import CATEGORIES, CLASSIFICATION_PREFIX  # noqa: E402

BASE = Path(__import__("os").environ.get("ROBIA_DATA", "data"))
import os
VARIANT = os.environ.get("V", "a")
NEW_ALL = {
    "category-socks": "socks or tights: hosiery worn on the feet and legs",
    "category-underwear": "underwear: bra, briefs, boxers or lingerie worn under clothes",
    "category-swimwear": "swimwear: swimsuit, bikini or swim trunks for the beach or pool",
    "category-sleepwear": "sleepwear: pajamas, nightgown or loungewear for sleeping at home",
}
NEW = {
    "a": {k: NEW_ALL[k] for k in ("category-socks", "category-underwear", "category-swimwear")},
    "b": {"category-socks": "socks: a pair of short knitted socks for the feet",
          "category-underwear": "underwear: a bra, panties, briefs or boxers",
          "category-swimwear": "swimwear: a swimsuit or a bikini"},
    "c": {"category-socks": "socks: a pair of short knitted socks for the feet",
          "category-underwear": "underwear: a bra, panties, briefs or boxers"},
}[VARIANT]
eng = L.EmbeddingEngine(sys.argv[1], backend=L.Backend.CPU(), vision_backend=L.Backend.CPU())
opt = L.EmbeddingOptions(normalize=True)


def emb(t):
    v = np.asarray(eng.compute_embedding(t, opt).embedding, np.float32); return v / np.linalg.norm(v)


old_ids = list(CATEGORIES); old = np.stack([emb(CLASSIFICATION_PREFIX + d) for _, d in CATEGORIES.values()])
new_ids = list(NEW); new = np.stack([emb(CLASSIFICATION_PREFIX + d) for d in NEW.values()])
labels = json.loads((BASE / "provisional_labels.json").read_text())
before_ok = after_ok = stolen = 0; steal = {}
for stem, lab in labels.items():
    p = BASE / "emb" / f"{stem}_raw_vt140.npy"
    if not p.exists():
        continue
    v = np.load(p); v = v / np.linalg.norm(v)
    s_old = old @ v; s_all = np.concatenate([s_old, new @ v]); ids = old_ids + new_ids
    b = old_ids[int(np.argmax(s_old))]; a = ids[int(np.argmax(s_all))]
    before_ok += b == lab["category"]; after_ok += a == lab["category"]
    if a in NEW:
        stolen += 1; steal[a] = steal.get(a, 0) + 1
print(json.dumps({"n": len(labels), "top1_before": before_ok, "top1_after": after_ok, "stolen_by_new": stolen, "by": steal}))
# margin effect: how many items lose their marked prediction (margin < 0.010) because a new label is 2nd
