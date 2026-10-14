"""Compute (and cache) EmbeddingGemma 2 embeddings for processed photos and label texts.

Usage: embed.py BASE MODEL [--vision-tokens N] [--only STEM...]
Reads BASE/processed/<stem>_raw.jpg. Writes BASE/emb/<stem>_raw[_vtN].npy (normalized float32[768]),
BASE/emb/labels.npz and appends BASE/timings.jsonl.
Resumable: existing .npy files are skipped.
"""
import argparse
import json
import resource
import sys
import time
from pathlib import Path

import numpy as np
import litert_lm as L

sys.path.insert(0, str(Path(__file__).parent))
from labels import CATEGORIES, OCCASIONS, TEXT_RECIPES  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument("base", type=Path)
ap.add_argument("model")
ap.add_argument("--vision-tokens", type=int, default=None)
ap.add_argument("--only", nargs="*")
a = ap.parse_args()

emb_dir = a.base / "emb"
emb_dir.mkdir(exist_ok=True)
t0 = time.time()
engine = L.EmbeddingEngine(a.model, backend=L.Backend.CPU(), vision_backend=L.Backend.CPU(),
                           vision_tokens_per_image=a.vision_tokens)
init_s = time.time() - t0
opt = L.EmbeddingOptions(normalize=True)


def emb(content):
    v = np.asarray(engine.compute_embedding(content, opt).embedding, dtype=np.float32)
    return v / np.linalg.norm(v)


labels_path = emb_dir / "labels.npz"
if not labels_path.exists():
    arrays = {}
    for recipe, fn in TEXT_RECIPES.items():
        for group, table in (("category", CATEGORIES), ("occasion", OCCASIONS)):
            arrays[f"{recipe}__{group}"] = np.stack([emb(fn(name, desc)) for name, desc in table.values()])
    np.savez(labels_path, **arrays)

stems = a.only or sorted({p.name.rsplit("_", 1)[0] for p in (a.base / "processed").glob("*_raw.jpg")})
suffix = f"_vt{a.vision_tokens}" if a.vision_tokens else ""
log = open(a.base / "timings.jsonl", "a")
for i, stem in enumerate(stems, 1):
    out = emb_dir / f"{stem}_raw{suffix}.npy"
    if not out.exists():
        t = time.time()
        v = emb(L.Content.ImageFile(str(a.base / "processed" / f"{stem}_raw.jpg")))
        dt = time.time() - t
        np.save(out, v)
        log.write(json.dumps({"stem": stem, "variant": "raw", "seconds": round(dt, 3),
                              "vision_tokens": a.vision_tokens}) + "\n")
        log.flush()
    if i % 10 == 0:
        print(f"{i}/{len(stems)} elapsed={time.time() - t0:.0f}s", flush=True)
print(json.dumps({"init_s": round(init_s, 2), "images": len(stems),
                  "max_rss_mb": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss // 1024,
                  "total_s": round(time.time() - t0, 1)}))
