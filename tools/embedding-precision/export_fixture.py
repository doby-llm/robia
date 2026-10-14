"""Export the golden scoring fixture for the Android JVM tests (TS§3.6, DEC-008, DEC-009).

Usage: export_fixture.py BASE MODEL OUT_JSON [--vision-tokens N] [--check]

BASE is the precision data dir (provisional_labels.json, processed/, emb/). Stored vectors in BASE/emb are used when
present; otherwise they are computed with MODEL. Without --check the fixture is written to OUT_JSON.

--check recomputes every vector with MODEL and asserts: max |diff| to the stored vectors <= 1e-4; rankings, marks and
selections built from the recomputed vectors equal those built from the stored ones (and OUT_JSON when it exists);
scores differ by <= 1e-4. Nothing is written.
"""
import argparse
import base64
import json
import sys
from pathlib import Path

import numpy as np
import litert_lm as L

sys.path.insert(0, str(Path(__file__).parent))
from labels import CATEGORIES, CLASSIFICATION_PREFIX, OCCASIONS  # noqa: E402

MODEL_ID = "embeddinggemma-2-text-vision-440m@e301f74d"
RECIPE_ID = "eg2-r1"
KNN_TOP = 3
MARGIN = {"TEXT": 0.010, "COMBINED": 0.50}  # DEC-008 (categories) and DEC-009 (occasions) share the thresholds
SUGGEST_OCCASIONS_OFF = {"category-accessories", "category-jewelry"}  # DefaultTags flag, suggestOccasions = false
# 6 garments: 2 dresses, 1 shorts, 1 skirt, 1 accessory, 1 blouse. Lowest photo index first, ambiguous excluded.
GARMENT_PICKS = [("category-dresses", 2), ("category-shorts", 1), ("category-skirts", 1),
                 ("category-accessories", 1), ("category-blouses", 1)]
LABEL_TABLE = [(tag, desc) for tag, (_, desc) in [*CATEGORIES.items(), *OCCASIONS.items()]]
N_CATEGORIES = len(CATEGORIES)
EPS = 1e-4


def embed(engine, content, opt):
    v = np.asarray(engine.compute_embedding(content, opt).embedding, dtype=np.float32)
    return v / np.linalg.norm(v)


def pick_garments(base):
    prov = json.loads((base / "provisional_labels.json").read_text())
    ordered = sorted((v["n"], stem, v) for stem, v in prov.items())
    picks = []
    for category, count in GARMENT_PICKS:
        chosen = [stem for _, stem, v in ordered if v["category"] == category and not v["ambiguous"]][:count]
        if len(chosen) < count:
            raise SystemExit(f"not enough unambiguous {category} photos")
        picks += [(stem, category) for stem in chosen]
    return [{"id": f"g{i}", "stem": stem, "category": category} for i, (stem, category) in enumerate(picks, 1)]


def stored_vectors(base, picks, vt):
    """(garments, labels) from BASE/emb, or None when any file is missing."""
    emb_dir = base / "emb"
    paths = [emb_dir / f"{g['stem']}_raw_vt{vt}.npy" for g in picks]
    if not all(p.exists() for p in paths) or not (emb_dir / "labels.npz").exists():
        return None
    labels = np.load(emb_dir / "labels.npz")
    garments = np.stack([np.load(p) for p in paths])
    return garments, np.concatenate([labels["desc__category"], labels["desc__occasion"]])


def computed_vectors(engine, opt, base, picks):
    garments = np.stack([embed(engine, L.Content.ImageFile(str(base / "processed" / f"{g['stem']}_raw.jpg")), opt)
                         for g in picks])
    labels = np.stack([embed(engine, CLASSIFICATION_PREFIX + desc, opt) for _, desc in LABEL_TABLE])
    return garments, labels


def b64(vec):
    return base64.b64encode(np.asarray(vec, dtype="<f4").tobytes()).decode()


def r6(x):
    return round(float(x), 6)


def zscore(x):
    sd = x.std()  # population std
    if sd < 1e-6:
        return np.zeros_like(x)
    return (x - x.mean()) / sd


def score(image, ids, vecs, examples):
    """DEC-008 / DEC-009 score construction. examples: tagId -> IMAGE vectors of other garments carrying the tag."""
    text = vecs @ image
    knn = {}  # index -> mean of the top-3 cosines to examples (undefined when there are none)
    for i, tag in enumerate(ids):
        sims = sorted((e @ image for e in examples.get(tag, [])), reverse=True)[:KNN_TOP]
        if sims:
            knn[i] = sum(sims) / len(sims)
    if len(knn) >= 2:
        scores = zscore(text)
        kz = zscore(np.array(list(knn.values())))
        for k, i in enumerate(knn):
            scores[i] += kz[k]
        return "COMBINED", scores
    return "TEXT", text


def ranking(ids, scores):
    return sorted(zip(ids, scores), key=lambda t: (-t[1], t[0]))


def category_result(ids, scores, mode):
    ranked = ranking(ids, scores)
    margin = ranked[0][1] - ranked[1][1]
    marked = ranked[0][0] if margin >= MARGIN[mode] else None
    return {"mode": mode, "ranked": [[t, r6(s)] for t, s in ranked], "margin": r6(margin), "marked": marked,
            "reason": "margin_ok" if marked else f"margin_below:{r6(margin)}"}


def occasion_result(ids, scores, mode, flag_on):
    if not flag_on:
        return {"mode": mode, "ranked": [], "g12": None, "g23": None, "selected": [], "reason": "flag_off"}
    ranked = ranking(ids, scores)
    g12 = ranked[0][1] - ranked[1][1]
    g23 = ranked[1][1] - ranked[2][1]
    if g12 > MARGIN[mode]:
        selected = [ranked[0][0]]
    elif g23 > MARGIN[mode]:
        selected = [ranked[0][0], ranked[1][0]]
    else:
        selected = []
    return {"mode": mode, "ranked": [[t, r6(s)] for t, s in ranked], "g12": r6(g12), "g23": r6(g23),
            "selected": selected, "reason": "margin_ok" if selected else f"margin_below:{r6(g23)}"}


def build(picks, garments, labels, vt):
    cat_ids = [t for t, _ in LABEL_TABLE[:N_CATEGORIES]]
    occ_ids = [t for t, _ in LABEL_TABLE[N_CATEGORIES:]]
    cat_vecs, occ_vecs = labels[:N_CATEGORIES], labels[N_CATEGORIES:]
    expected = {"text": {"category": [], "occasion": []}, "combined": {"category": [], "occasion": []}}
    for j, g in enumerate(picks):
        examples = {}  # the other fixture garments are each other's examples; occasion tags: none in the fixture
        for k, h in enumerate(picks):
            if k != j:
                examples.setdefault(h["category"], []).append(garments[k])
        flag_on = g["category"] not in SUGGEST_OCCASIONS_OFF
        for name, cat_examples in (("text", {}), ("combined", examples)):
            mode, cat_scores = score(garments[j], cat_ids, cat_vecs, cat_examples)
            expected[name]["category"].append({"garment": g["id"], **category_result(cat_ids, cat_scores, mode)})
            mode, occ_scores = score(garments[j], occ_ids, occ_vecs, {})
            expected[name]["occasion"].append({"garment": g["id"], **occasion_result(occ_ids, occ_scores, mode, flag_on)})
    return {
        "modelId": MODEL_ID,
        "recipeId": RECIPE_ID,
        "visionTokens": vt,
        "labels": [{"tagId": tag, "text": CLASSIFICATION_PREFIX + desc, "b64": b64(vec)}
                   for (tag, desc), vec in zip(LABEL_TABLE, labels)],
        "garments": [{"id": g["id"], "category": g["category"], "image_b64": b64(vec)}
                     for g, vec in zip(picks, garments)],
        "expected": expected,
    }


def compare(a, b):
    """Asserts identical rankings, modes, marks and selections; returns the largest score difference."""
    worst = 0.0
    for name in ("text", "combined"):
        for kind in ("category", "occasion"):
            for x, y in zip(a["expected"][name][kind], b["expected"][name][kind]):
                where = f"{name}/{kind}/{x['garment']}"
                assert x["garment"] == y["garment"], where
                assert x["mode"] == y["mode"], where
                assert [t for t, _ in x["ranked"]] == [t for t, _ in y["ranked"]], f"ranking differs at {where}"
                assert x.get("marked") == y.get("marked"), f"mark differs at {where}"
                assert x.get("selected") == y.get("selected"), f"selection differs at {where}"
                for (_, sx), (_, sy) in zip(x["ranked"], y["ranked"]):
                    worst = max(worst, abs(sx - sy))
    return worst


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("base", type=Path)
    ap.add_argument("model")
    ap.add_argument("out", type=Path)
    ap.add_argument("--vision-tokens", type=int, default=140)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()

    picks = pick_garments(a.base)
    stored = stored_vectors(a.base, picks, a.vision_tokens)
    if a.check and stored is None:
        raise SystemExit("--check needs the stored vectors in BASE/emb (run embed.py first)")

    if not a.check and stored is not None:
        fixture = build(picks, *stored, a.vision_tokens)
        a.out.write_text(json.dumps(fixture, separators=(",", ":")) + "\n")
        print(json.dumps({"written": str(a.out), "bytes": a.out.stat().st_size,
                          "garments": len(picks), "labels": len(LABEL_TABLE), "source": "stored"}))
        return

    engine = L.EmbeddingEngine(a.model, backend=L.Backend.CPU(), vision_backend=L.Backend.CPU(),
                               vision_tokens_per_image=a.vision_tokens)
    opt = L.EmbeddingOptions(normalize=True)
    garments, labels = computed_vectors(engine, opt, a.base, picks)
    if stored is None:
        fixture = build(picks, garments, labels, a.vision_tokens)
        a.out.write_text(json.dumps(fixture, separators=(",", ":")) + "\n")
        print(json.dumps({"written": str(a.out), "bytes": a.out.stat().st_size,
                          "garments": len(picks), "labels": len(LABEL_TABLE), "source": "computed"}))
        return

    vec_diff = {"garments": float(np.abs(garments - stored[0]).max()),
                "labels": float(np.abs(labels - stored[1]).max())}
    assert vec_diff["garments"] <= EPS and vec_diff["labels"] <= EPS, vec_diff
    recomputed = build(picks, garments, labels, a.vision_tokens)
    from_stored = build(picks, *stored, a.vision_tokens)
    score_diff = {"vs_stored": compare(recomputed, from_stored)}
    assert score_diff["vs_stored"] <= EPS, score_diff
    if a.out.exists():
        score_diff["vs_out_json"] = compare(recomputed, json.loads(a.out.read_text()))
        assert score_diff["vs_out_json"] <= EPS, score_diff
    print(json.dumps({"check": "ok", "max_abs_vector_diff": vec_diff, "max_abs_score_diff": score_diff,
                      "rankings_identical": True, "selections_identical": True,
                      "garments": [g["id"] for g in picks], "labels": len(LABEL_TABLE),
                      "vision_tokens": a.vision_tokens}))


if __name__ == "__main__":
    main()
