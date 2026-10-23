# Model feasibility benchmarks (planning, not run by CI)

Scripts behind `docs/specs/robia-round3/artifacts/inv-r3-models.md`. Paths are placeholders: set `ROBIA_PLANNING_DIR` / `ROBIA_FEAS_DIR` (or edit the constants) before running; personal photos are never part of the repository.
- `heavy.sh NAME MEMMAX CMD...` — one capped `systemd-run --user` unit at a time (flock).
- `d3_bench.py` — d3-edge/d3-lite (llama.cpp `llama-server`, `/v1/systemone`) category choice per photo; `--min-img-tokens`.
- `d3_fitb.py` — d3 on Polyvore FITB (outfit collage + 4 labelled candidates).
- `polyvore_prep.py` — fixed FITB sample (seed 20261022) + image extraction; candidates must be shuffled (published files put the answer at index 0).
- `eg2_polyvore.py`, `fitb_eval.py` — EmbeddingGemma 2 (LiteRT-LM 0.18.0, 70 vision tokens) zero-shot FITB.
- `siglip_bench.py` — Marqo-FashionSigLIP ONNX, category + FITB.
