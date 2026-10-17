# Segmentation bench (planner reference, not built by CI)

Reference implementation of the "precise cutout" pipeline benchmarked for `docs/specs/robia-ux-rework` (see `artifacts/seg-benchmark.md`).
Personal photos are never committed: point `ROBIA_PHOTOS` at a local folder of JPEGs.

```sh
uv venv .venv && uv pip install --python .venv/bin/python onnxruntime pillow numpy opencv-python-headless ai-edge-litert onnx
mkdir -p models   # download the files listed in TS§SG2a (+ isnet-general-use.onnx for bench.py) and check sha256
ROBIA_PHOTOS=/path/to/photos .venv/bin/python -I bench.py out 0 1 2        # u2netp + ISNet masks, src images
.venv/bin/python -I bench_v3.py out u2netp 0 1 2                             # magic_touch refinement
.venv/bin/python -I bench_v4.py out 0 1 2                                    # hanger detection (D-FINE)
ROBIA_PHOTOS=/path/to/photos .venv/bin/python -I pipeline_v10.py out         # full chosen pipeline -> out/final_v10/*.png
```
`pipeline_v10.py` is generated from `pipeline.py` (planner patch: plausibility rule, second detector pass, box-only SAM hanger mask, 10 % island rule); it is the reference for SG2a.
`seg_everything.py` + `gemma_segments.py`: the rejected "segment everything + EmbeddingGemma labels" experiment. `taxonomy_check.py`: new-category regression on stored EmbeddingGemma vectors.
