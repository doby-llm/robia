# Embedding precision tool

Python tools that measured the accuracy of the on-device EmbeddingGemma 2 category and occasion suggestions
(decision log DEC-008 … DEC-011, technical spec §3.1 and §3.6). The Android JVM tests use their outputs as a golden
reference: `app/src/test/resources/suggestions/golden-scores.json` and `app/src/main/assets/benchmark/`.

Run it on a Linux machine with Python 3.12+ and [uv](https://docs.astral.sh/uv/). Nothing here is built by CI.

## Data and privacy

The tools read the photo collection from a **data directory** given on the command line (`BASE`). That directory
holds personal photos, provisional labels and cached vectors. **It is never committed.** `.gitignore` excludes
`tools/embedding-precision/data/` and `tools/embedding-precision/.venv/` as a safety net; keep the data outside the repo.

Only code and the three generated reference files belong in git:

- `app/src/test/resources/suggestions/golden-scores.json`: 6 garment vectors, 25 label vectors, expected scores (no photo names or photos).
- `app/src/main/assets/benchmark/garment.png`: a synthetic dress drawn by `make_benchmark_image.py`.
- `app/src/main/assets/benchmark/garment_vector.b64`: its reference IMAGE vector.

Before a commit check that no photo, vector or token file is staged (`git status`) and that no absolute home-directory path appears under `tools/` (excluding `.venv`).

## Setup

```sh
cd tools/embedding-precision
export UV_CACHE_DIR=/tmp/uv-cache   # optional, keeps the uv cache out of $HOME
uv sync                             # litert-lm==0.18.0, numpy, pillow
uv sync --group drive               # only for download_drive.py (google-api-python-client, google-auth)
```

The model is `embeddinggemma-2-text-vision-440m.litertlm` (SHA-256 `92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0`).
Pass its path as `MODEL` to every script that loads it.

## Scripts

| Script | Command | What it does |
| --- | --- | --- |
| `download_drive.py` | `uv run --group drive python download_drive.py BASE --token PATH` | Read-only download of the photos listed in `BASE/drive_listing.json` into `BASE/photos_raw/`. `PATH` is a local OAuth token JSON. Never commit it. |
| `contact_sheets.py` | `uv run python contact_sheets.py BASE` | Numbered contact sheets of `BASE/processed/*_raw.jpg` for labelling. |
| `labels.py` | (imported) | Label texts for the default category and occasion tags. Shared by the other scripts. Its texts are the app's default descriptions: do not edit them without changing `EmbeddingRecipe.kt`. |
| `embed.py` | `uv run python embed.py BASE MODEL [--vision-tokens 140] [--only STEM...]` | Caches `BASE/emb/<stem>_raw_vt140.npy` and `BASE/emb/labels.npz`. Resumable. |
| `report.py` | `uv run python report.py BASE OUT_HTML` | Self-contained HTML precision report (Spanish UI, scoring in the browser). |
| `probe_search.py` | `uv run python probe_search.py BASE MODEL --garment-vector PATH` | Occasion gap rules, text-query search prefixes, and the garment TEXT prefix check. Read-only. |
| `make_benchmark_image.py` | `uv run python make_benchmark_image.py OUT_DIR MODEL [--vision-tokens 140]` | Writes `garment.png` and `garment_vector.b64` into `OUT_DIR`. |
| `export_fixture.py` | `uv run python export_fixture.py BASE MODEL OUT_JSON [--vision-tokens 140] [--check]` | Writes the golden fixture from the stored vectors (or computes them when missing). With `--check` it recomputes every vector with the model and asserts the differences, rankings and selections. |

`--vision-tokens 70` is the pre-approved fallback (TASK-E01b). It changes the vectors, so the fixture and the benchmark
vector must be regenerated together. The stored file names carry the suffix (`_raw_vt70.npy`).

## Reproduce the Android references

```sh
uv run python export_fixture.py BASE MODEL ../../app/src/test/resources/suggestions/golden-scores.json
uv run python make_benchmark_image.py ../../app/src/main/assets/benchmark MODEL
uv run python export_fixture.py BASE MODEL ../../app/src/test/resources/suggestions/golden-scores.json --check
```

`--check` prints one JSON line with `"check": "ok"` when the recomputed vectors match the stored ones within 1e-4 and
the rankings, marks and selections are identical.

## Golden fixture format

- `modelId`, `recipeId`, `visionTokens`: `embeddinggemma-2-text-vision-440m@e301f74d`, `eg2-r1`, `140`.
- `labels[]`: `{tagId, text, b64}`. The 19 categories then the 6 occasions. `text` = `task: classification | text: ` + description. `b64` = float32 little-endian, 768 values.
- `garments[]`: `{id: "g1".."g6", category, image_b64}`. The 6 photos are 2 dresses, 1 shorts, 1 skirt, 1 accessory and 1 blouse: the lowest photo index of each category among the unambiguous provisional labels.
- `expected.{text,combined}.{category,occasion}[]`: one entry per garment with `mode` (`TEXT` or `COMBINED`), `ranked` (`[tagId, score]`, score desc, tagId asc), and for categories `margin` and `marked`; for occasions `g12`, `g23`, `selected` and `reason`. Scores are rounded to 6 decimals.
- In COMBINED mode each garment's category examples are the other five garments (DEC-008, kNN top-3).
- The fixture garments carry no occasion tags, so no occasion kNN examples exist. The COMBINED occasion fallback therefore
  has fewer than two defined examples, which DEC-008 and DEC-009 treat as TEXT mode. The `combined.occasion` entries equal the
  `text.occasion` entries, and the threshold is 0.010.
- Garments whose category has `suggestOccasions = false` (`category-accessories`, `category-jewelry`) get `ranked: []` and `reason: "flag_off"` (DEC-009).

## Probe result: garment TEXT prefix (TASK-E02 step 6)

Run on the Raspberry Pi 5 with `probe_search.py --garment-vector`. Query "Red summer dress" against the synthetic
benchmark garment, ranked among the 142 photos plus the garment (143 candidates):

| TEXT prefix | cos(query, garment) | rank | best photo cos |
| --- | --- | --- | --- |
| `title: none \| text: ` (`DOCUMENT_PREFIX`) | 0.7572 | 1/143 | 0.7501 |
| none | 0.7195 | 1/143 | 0.7115 |

The document prefix ranks the synthetic garment with a higher cosine. The recipe is unchanged.

The occasion rule counts printed by `probe_search.py` (top2-top3 variant, T = 0.010) are 69 one, 16 two, 45 none and
12 skipped accessories. They match the DEC-009 evidence.
