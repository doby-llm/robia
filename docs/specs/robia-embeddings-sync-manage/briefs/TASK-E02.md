# TASK-E02 — Precision tool in repo + golden fixture (runs on the Raspberry Pi)

## Goal
Put the Python tool that measured accuracy on Manu's 142 photos into the repo (without photos or credentials), and produce two reference files the Android tests use to prove the Kotlin maths equals the Python maths: `golden-scores.json` and the benchmark image + its reference vector.

## Inputs on the Pi (read-only)
- Scripts: `~/.local/share/robia-planning/precision-data/scripts/` (`labels.py`, `embed.py`, `report.py`, `report_template.html`, `contact_sheets.py`, `download_drive.py`, `probe_occ_search.py`).
- Data: `~/.local/share/robia-planning/precision-data/` (`processed/*_raw.jpg`, `emb/*_raw_vt140.npy`, `emb/labels.npz`, `provisional_labels.json`). **Never commit anything from here except code.**
- Model: `~/.local/share/robia-planning/model/embeddinggemma-2-text-vision-440m.litertlm` (SHA-256 `92dcbea1…2eb0`).
- Python: create the project with `uv`; `export UV_CACHE_DIR=/tmp/uv-cache`. Dependencies: `litert-lm==0.18.0`, `numpy`, `pillow` (`google-api-python-client`, `google-auth` only for `download_drive.py`).

## Steps
1. Create `tools/embedding-precision/` with `uv init --no-workspace`, add deps, commit `pyproject.toml` + `uv.lock`.
2. Copy the scripts. Clean-up rules:
   - remove every `cut`/rembg reference (`--variants` default becomes `["raw"]`; only `_raw.jpg` inputs).
   - no hard-coded paths: data dir and model path are CLI arguments; `download_drive.py` takes `--token PATH` and `--folder-id ID` (no defaults containing personal paths or ids).
   - keep `labels.py` texts byte-identical (they are the app's default descriptions).
3. `export_fixture.py BASE MODEL OUT_JSON [--check]` per tasks.md E02. Use stored `.npy`/`labels.npz` when present; with `--check` recompute vectors with the model and assert max abs diff ≤ 1e-4 and identical rankings. The scoring code must implement DEC-008/DEC-009 exactly (copy from `report_template.html` JS / `probe_occ_search.py`), with `CLASSIFICATION_PREFIX` label texts from `labels.py` ("desc" recipe).
4. `make_benchmark_image.py OUT_DIR MODEL`: draw a simple dress silhouette (PIL polygons, two colours) 512×512 on transparent background → `garment.png`; compute its IMAGE vector with the app recipe (composite on white, ≤1024 edge, PNG bytes, 140 vision tokens, normalize) → `garment_vector.b64` (base64 of float32 little-endian, 768 values).
5. Outputs to commit: `app/src/test/resources/suggestions/golden-scores.json`, `app/src/main/assets/benchmark/garment.png`, `app/src/main/assets/benchmark/garment_vector.b64`.
6. TEXT-prefix probe (tasks.md E02) — record result in `tools/embedding-precision/README.md`; do not change the recipe.
7. `README.md` of the tool: purpose, how to run each script, "no personal data in git".
8. `.gitignore`: `tools/embedding-precision/data/`, `tools/embedding-precision/.venv/`.

## Checks before PR
`git status` shows no `.jpg`, `.npy`, `.npz`, token or `provisional_labels*` files; `grep -rn "/home/" tools/` → empty. Run `uv run python export_fixture.py … --check` and paste the output.

## Done when
V-E02 output in the receipt; CI green (no app code changes besides the 3 resource files).
