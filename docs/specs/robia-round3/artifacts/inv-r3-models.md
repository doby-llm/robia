# inv-r3-models — feasibility of d3-edge/d3-lite and of an outfit generator (planning evidence, 2026-10-22)

Scope: R3-12 ("port d3-lite/edge to the phone with GPU: better tags, outfit creator") and R3-16 (outfit generator: Text2Outfit, FashionCLIP 2.0, OutfitTransformer, hybrid CLIP+Transformer, reuse EmbeddingGemma 2).
Machine: Raspberry Pi 5 (4 cores, 16 GB), used as a proxy. Earlier rounds measured the Poco F3 at roughly the same speed as the Pi for EmbeddingGemma 2 on CPU (Pi 4.8–6.8 s/image at 70 vision tokens vs phone ~1.6 s median for `suggestion_ms` incl. scoring; the phone is faster, ~3x).
Every heavy run: capped `systemd-run` unit (3–4 GB, no swap), one at a time. Scripts: `tools/model-feasibility/` (copied from the planner workspace; paths from env vars).

## What "d3-lite / d3-edge" are
- vLLM Semantic Router **Decision 3.0** (released 2026-10-11, Apache-2.0). d3-edge 0.59 B, d3-lite 0.85 B (Qwen3.5-0.8B + 0.10 B vision encoder).
- They are **decision models**: given an input (text and images) and typed questions (choice / yes-no / score) they return a probability per option. They do not generate text and do not produce embeddings.
- Runtimes: PyTorch (official), MLX/CoreML (Apple), GGUF for llama.cpp (`/v1/systemone`, Q8_0 806 MB + 116 MB projector for d3-edge; 1.08 GB + 116 MB for d3-lite). No LiteRT/ONNX/TFLite port exists. On Android the only path is llama.cpp (JNI); its OpenCL backend is documented as not working on Adreno 6xx phone drivers (the Poco F3 has an Adreno 650), and community reports show OpenCL slower than CPU even on Adreno 740. So on Manu's phone d3 would run on CPU.

## Measurements
| Model / method | Task | Result | Latency (Pi, CPU 4 threads) | Memory |
|---|---|---|---|---|
| EmbeddingGemma 2 (app today, 70 tok) | category top-1, 128 photos, text-only | **83.6 %** (140 tok: 85.9 %; with own examples 89–96 %) | 4.8–6.8 s/image (phone ~1.6 s) | < 1 GB |
| d3-edge Q8_0, 1 photo 288×512 | category (21-way choice), same 128 photos and labels | **55.5 %** (skirts→dresses 26/26, shorts→dresses 12/34, accessories→tops 11/12) | **8.0 s median, 10.8 s p90** per question; load 6 s | < 4 GB cap |
| d3-lite Q8_0, 1 photo 288×512 | category, 37 non-ambiguous of 40 photos | **86.1 %** (EG2 on the same photos 86.1 %; both right 28, only EG2 3, only d3-lite 4, neither 2) | **15.7 s median** per question | < 4 GB cap |
| d3-edge, FITB 40 Polyvore questions (outfit collage + 4 labelled candidates) | pick the item that completes the outfit (chance 25 %) | **17.5 %** (below chance) | 7.3 s/question | < 4 GB |
| EG2 zero-shot FITB (mean image cosine to the outfit items), 60 questions | same | **63.3 %** (95 % CI 52–75); on d3's 40 questions 55 % | vectors already exist in the app | — |
| Marqo-FashionSigLIP (ONNX fp32, 372 MB vision) | category (128 photos) / FITB (60 q) | **57.0 %** / **45.0 %** | 0.8 s/image | ~1 GB |
| Literature (trained on Polyvore) | FITB | OutfitTransformer 59–67 %, hybrid CLIP+Transformer 69.2 % | — | — |

Notes on the FITB test: Polyvore "nondisjoint" validation FITB, fixed random sample (seed 20261022); the published files always put the right answer at index 0, so candidates were shuffled (seed 7) before every run. All four candidates share the blank's category, so the test measures style/colour compatibility, not category. Item titles are empty for 73 % of candidates, so text-only scores are not meaningful.

## Duplicates (R3-07)
EG2 vectors (70 tokens, photos WITH background) on Manu's 142 photos: all-pairs cosine p50 0.794, p99 0.920, max 0.989. Visual check of the top 16 pairs: re-shots of the same garment 0.966–0.989, but different black dresses also 0.955–0.977. Cosine alone is not a safe duplicate signal; the plan adds same-category + same-primary-colour conditions, an exact-hash tier, and a calibration gate on Manu's real (cut-out) wardrobe. Sheet with personal photos kept outside git.

## Verdict
- **d3-edge / d3-lite (R3-12): not viable for Robia now.** d3-edge is clearly worse than what the app already does for tags (55 % vs 84–86 %) and fails the outfit test (below chance). d3-lite matches EmbeddingGemma 2 on tags (86 %) and makes slightly different mistakes, but costs ~1.2 GB more download and ~15 s per question on the Pi (estimate 5–8 s on the Poco F3 CPU; GPU on Adreno 650 through llama.cpp is not supported), per tag group per photo. Only path to the phone is llama.cpp via JNI (no LiteRT/ONNX port). Re-evaluate if a LiteRT port or a smaller vision decision model appears; the bench scripts are kept.
- **Outfit generator (R3-16): viable as an embedding-based prototype, no new model.** EmbeddingGemma 2 vectors that Robia already stores pick the right item in 63 % of Polyvore FITB questions zero-shot, in the range that trained models report (59–69 %), at zero extra cost (ranking 35–500 garments is milliseconds). FashionCLIP-family models (Marqo-FashionSigLIP, the strongest public one) scored lower on both tasks here, so switching embedding models is not justified. Text2Outfit has no public weights; OutfitTransformer-style heads need training on Polyvore embeddings (~12 k images ≈ 15 h on the Pi with EG2), which is only worth it if the zero-shot prototype disappoints Manu on his own wardrobe.
- **Next step:** developer-mode "Outfit lab" (REQ-OF-01) + Manu's ratings (G-OUT) decide whether to invest in a trained compatibility head or a d3-lite reranker.
- **GPU:** EmbeddingGemma 2 runs on CPU in the app today. LiteRT-LM supports a GPU backend on Android; an ADB spike (S-GPU) can measure whether it halves latency on the Poco F3 before planning a task.

## Resumen (ES)
d3-edge/lite: no merece la pena ahora (peor o igual que lo que ya tenemos y 5-10 veces más lento, sin GPU en tu móvil). Outfits: sí es viable con los embeddings que ya tenemos (63 % en el test estándar, al nivel de modelos entrenados) → prototipo en modo developer y lo evalúas tú.
