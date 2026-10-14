"""Probes on the 142 photos: occasion gap rules (DEC-009), text-query search prefixes (DEC-011) and the
garment TEXT prefix check ("title: none | text: " vs none) against the benchmark garment vector.

Usage: probe_search.py BASE MODEL [--garment-vector PATH] [--vision-tokens N]
Reads BASE/provisional_labels.json, BASE/sheets/index.json, BASE/emb. Writes nothing.
"""
import argparse
import base64
import json
import sys
from collections import Counter
from pathlib import Path

import numpy as np
import litert_lm as L

sys.path.insert(0, str(Path(__file__).parent))
from labels import CATEGORIES, OCCASIONS  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument("base", type=Path)
ap.add_argument("model")
ap.add_argument("--garment-vector", type=Path, default=None)
ap.add_argument("--vision-tokens", type=int, default=140)
a = ap.parse_args()
B = a.base

ref = json.loads((B / "provisional_labels.json").read_text())
stems = sorted(ref)
V = np.stack([np.load(B / "emb" / f"{s}_raw_vt{a.vision_tokens}.npy") for s in stems])
lab = np.load(B / "emb" / "labels.npz")
O = V @ lab["desc__occasion"].T
occ_names = [v[0] for v in OCCASIONS.values()]
srt = -np.sort(-O, 1)
g12, g23, g13 = srt[:, 0] - srt[:, 1], srt[:, 1] - srt[:, 2], srt[:, 0] - srt[:, 2]
print("occasion gap percentiles (10/50/90): top1-top2", np.round(np.percentile(g12, [10, 50, 90]), 4),
      "top2-top3", np.round(np.percentile(g23, [10, 50, 90]), 4))
is_acc = np.array([ref[s]["category"] == "category-accessories" for s in stems])
for thr in (0.010, 0.0075, 0.005):
    out = []
    for i in range(len(stems)):
        if is_acc[i]:
            out.append("skip_accessory")
        elif g12[i] > thr:
            out.append("top1")
        elif g23[i] > thr:
            out.append("top1+top2")
        else:
            out.append("none")
    print(f"rule thr={thr}: top2-top3 variant", Counter(out))
    out2 = ["skip_accessory" if is_acc[i] else "top1" if g12[i] > thr else "top1+top2" if g13[i] > thr else "none"
            for i in range(len(stems))]
    print(f"rule thr={thr}: top1-top3 variant", Counter(out2))

engine = L.EmbeddingEngine(a.model, backend=L.Backend.CPU(), vision_backend=L.Backend.CPU(),
                           vision_tokens_per_image=a.vision_tokens)
opt = L.EmbeddingOptions(normalize=True)


def embed_text(text):
    qv = np.asarray(engine.compute_embedding(text, opt).embedding, dtype=np.float32)
    return qv / np.linalg.norm(qv)


queries = ["vestido rojo", "red dress", "vaqueros cortos", "denim shorts", "cinturón", "falda negra",
           "vestido de flores", "ropa para ir a una boda", "algo cómodo para el verano"]
idx = {v: int(k) for k, v in json.loads((B / "sheets" / "index.json").read_text()).items()}
res = {}
for prefix_name, prefix in (("search_result_query", "task: search result | query: "), ("search_query_text", "task: search query | text: "), ("none", "")):
    for q in queries:
        s = V @ embed_text(prefix + q)
        top = np.argsort(-s)[:6]
        res.setdefault(q, {})[prefix_name] = [(idx[stems[i]], ref[stems[i]]["category"].split("-", 1)[1], round(float(s[i]), 3)) for i in top]
for q, r in res.items():
    print(q)
    for k, v in r.items():
        print("  ", k, v)

if a.garment_vector:
    garment = np.frombuffer(base64.b64decode(a.garment_vector.read_text()), dtype="<f4")
    print("garment TEXT prefix probe: query 'Red summer dress' vs benchmark garment, rank among 142 photos + garment")
    for prefix_name, prefix in (("title: none | text: ", "title: none | text: "), ("none", "")):
        qv = embed_text(prefix + "Red summer dress")
        cos_garment = float(garment @ qv)
        rank = 1 + int((V @ qv > cos_garment).sum())
        print("  ", repr(prefix_name), "cos(garment)=", round(cos_garment, 4), f"rank={rank}/{len(stems) + 1}",
              "best photo cos=", round(float((V @ qv).max()), 4))
