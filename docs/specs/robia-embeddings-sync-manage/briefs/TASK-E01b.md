# TASK-E01b — Vision-token fallback (only if G-DEVICE-1 fails on latency)

## Goal
Fewer image tokens make the model ~2.5× faster with slightly different rankings. Try 70 tokens once.

## Steps
1. `EmbeddingRecipe.VISION_TOKENS = 70`; `RECIPE_ID = "eg2-r1-vt70"` (stored vectors from 140 must not be mixed).
2. On the Pi: rerun E02 `make_benchmark_image.py` and `export_fixture.py` with `--vision-tokens 70` (add the flag if missing) and commit the regenerated `garment_vector.b64` and `golden-scores.json`.
3. CI green → ask Manu to repeat the G-DEVICE-1 steps.
4. Still > 2000 ms → stop the whole E lane, report numbers to Manu for replanning.
