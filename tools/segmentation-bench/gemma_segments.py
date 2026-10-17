"""Manu's idea, step 2: label each segment with EmbeddingGemma 2 (app recipe: 70 vision tokens) against text prompts.
Usage: <embedding-precision venv python> -I gemma_segments.py OUT_DIR MODEL IDX...
Writes OUT_DIR/segs/{i}/labels.json: per segment cosine to each prompt, argmax label, garment flag, seconds.
"""
import sys, json, time
from pathlib import Path
import numpy as np
import litert_lm as L

out = Path(sys.argv[1]); model = sys.argv[2]; idx = [int(a) for a in sys.argv[3:]]
eng = L.EmbeddingEngine(model, backend=L.Backend.CPU(), vision_backend=L.Backend.CPU(), vision_tokens_per_image=70)
opt = L.EmbeddingOptions(normalize=True)
PFX = "task: classification | text: "
PROMPTS = {
    "garment": "a piece of clothing: a dress, skirt, shorts, top, trousers or jacket",
    "accessory": "a fashion accessory: a belt, bag, scarf, hat or jewelry",
    "hanger": "a clothes hanger made of wood, plastic or metal wire",
    "door": "a door or a wardrobe door",
    "wall": "a plain wall",
    "floor": "a floor or a carpet",
    "furniture": "furniture: a bed, chair, shelf or table",
}
GOOD = {"garment", "accessory"}


def emb(c):
    v = np.asarray(eng.compute_embedding(c, opt).embedding, np.float32); return v / np.linalg.norm(v)


names = list(PROMPTS); T = np.stack([emb(PFX + PROMPTS[n]) for n in names])
for i in idx:
    d = out / "segs" / f"{i:03d}"
    meta = json.loads((d / "meta.json").read_text())
    res = []; t0 = time.perf_counter()
    for s in meta["segments"]:
        v = emb(L.Content.ImageFile(str(d / f"seg_{s['k']}.png")))
        sc = T @ v; lab = names[int(np.argmax(sc))]
        order = np.sort(sc)[::-1]
        res.append({**s, "label": lab, "margin": round(float(order[0] - order[1]), 4),
                    "scores": {n: round(float(x), 4) for n, x in zip(names, sc)}, "good": lab in GOOD})
    dt = time.perf_counter() - t0
    (d / "labels.json").write_text(json.dumps({"segments": res, "gemma_s": round(dt, 2)}, indent=1))
    print(i, f"{len(res)} segs {dt:.1f}s", [(r["k"], r["label"], r["area"], r["margin"]) for r in res], flush=True)
