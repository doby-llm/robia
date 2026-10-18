"""Rev2: Socks+Underwear (variant c) + Accessories description variants. Image vectors vt70 (stored)."""
import sys, json
from pathlib import Path
import numpy as np
import litert_lm as L
sys.path.insert(0, __import__("os").path.join(__import__("os").path.dirname(__file__), "..", "embedding-precision"))
from labels import CATEGORIES, CLASSIFICATION_PREFIX  # noqa: E402
BASE = Path(__import__("os").environ.get("ROBIA_DATA", "data"))
NEW = {"category-socks": "socks or tights: hosiery worn on the feet and legs",
       "category-underwear": "underwear: bra, briefs, boxers or lingerie worn under clothes"}
ACC = {
  "current": "an accessory: belt, scarf, hat, sunglasses or a similar item",
  "belt_first": "an accessory such as a belt, a scarf, a hat or sunglasses",
  "belt_explicit": "an accessory: a belt or waist belt, a scarf, a hat, sunglasses or a similar item",
  "belt_strong": "a belt or other accessory: leather belt, waist belt, scarf, hat or sunglasses",
}
eng = L.EmbeddingEngine(sys.argv[1], backend=L.Backend.CPU(), vision_backend=L.Backend.CPU())
opt = L.EmbeddingOptions(normalize=True)
def emb(t):
    v = np.asarray(eng.compute_embedding(t, opt).embedding, np.float32); return v / np.linalg.norm(v)
labels = json.loads((BASE / "provisional_labels.json").read_text())
vecs = {s: np.load(BASE / "emb" / f"{s}_raw_vt70.npy") for s in labels if (BASE / "emb" / f"{s}_raw_vt70.npy").exists()}
base_ids = list(CATEGORIES); base_txt = {k: d for k, (_, d) in CATEGORIES.items()}
newv = {k: emb(CLASSIFICATION_PREFIX + d) for k, d in NEW.items()}
for name, acc in [("baseline_no_new", None)] + list(ACC.items()):
    txt = dict(base_txt)
    if acc: txt["category-accessories"] = acc
    ids = base_ids + (list(NEW) if acc else [])
    M = np.stack([emb(CLASSIFICATION_PREFIX + txt[k]) if k in txt else newv[k] for k in ids])
    ok = acc_ok = acc_n = fp_acc = 0; steal = {}
    for s, v in vecs.items():
        v = v / np.linalg.norm(v); p = ids[int(np.argmax(M @ v))]; g = labels[s]["category"]
        ok += p == g
        if g == "category-accessories": acc_n += 1; acc_ok += p == g
        elif p == "category-accessories": fp_acc += 1
        if p in NEW: steal[p] = steal.get(p, 0) + 1
    print(json.dumps({"variant": name, "top1": ok, "n": len(vecs), "acc_correct": f"{acc_ok}/{acc_n}", "other_predicted_as_acc": fp_acc, "stolen_by_new": steal}), flush=True)
