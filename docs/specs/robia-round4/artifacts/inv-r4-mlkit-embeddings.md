# ML Kit GenAI Embeddings API — impact on Robia (planner research, 2026-10-30)

Sources (primary):
- https://developers.google.com/ml-kit/genai/embedding/android (last updated 2026-10-29)
- https://developers.googleblog.com/en/google-ai-edge-with-embeddinggemma-2/ (2026-10-06: "ML Kit in the coming weeks")

## What the API is
- `com.google.mlkit:genai-embedding:1.0.0-alpha1`. "Offered in preview, not subject to any SLA or deprecation policy;
  changes may break backward compatibility."
- Model `embedding_gemma_v2` served by **Android AICore** (system component), NPU when available, CPU/XNNPACK fallback.
  Text + image, 256 tokens (image = 70 tokens), 768-d float vector — same dimension/token budget as Robia today.
- Prerequisites today: device supports AICore AND **the Google account on the device is enrolled in the AICore
  Developer Preview program** AND latest AICore + Private Compute Services from Play.
- Model is updated by Google: vectors from a new model version live in an incompatible space; `deprecationDate` /
  `shutdownDate` force a full re-index. Quota limits (more liberal when charging); docs recommend indexing only while
  charging + idle via WorkManager.

## Robia today (code, main 0ceb916)
- `EmbeddingRecipe.kt`: `embeddinggemma-2-text-vision-440m@e301f74d`, recipe `eg2-r1-vt70`, 768-d, 70 vision tokens.
- Model is **not in the APK**: downloaded on demand from Hugging Face (~388 MB, SHA-256 pinned) by `ModelStore`
  (`RobiaApplication.kt:103`), run with LiteRT-LM 0.18.0; features hidden until downloaded.
- Vectors are synced to Drive keyed by MODEL_ID/RECIPE_ID; precision thresholds (category/occasion margins, kNN,
  search weights 0.7/0.3) were calibrated on this exact model + recipe.
- Premise check: the notification says we ship ~300 MB inside the APK. We do not; APK stays small already.

## Assessment
1. Does it affect us? Not now. Nothing breaks; our pinned HF model + LiteRT-LM path keeps working.
2. Recommend for Play Store? **Not as the only engine, not yet.**
   - Alpha + Developer-Preview account gating: ordinary Play users cannot use it today → feature would be invisible
     for almost everyone.
   - AICore device coverage only (recent flagships); Robia minSdk 26.
   - Model can change under us → mandatory re-index + re-calibration of every threshold; vectors stop being
     comparable across the user's devices (Drive-synced vectors would mismatch between a phone with AICore model vN
     and one with the downloaded model).
   - Quotas: our batch backfill (hundreds of garments) is exactly the workload they throttle.
3. What it would buy later (when GA without the preview program): no 388 MB download, NPU speed, lower RAM.
   Best fit then: an optional **engine adapter** (`Embedder` interface) chosen at runtime when AICore reports the
   same model family, with vectors tagged by `modelSignature` and kept separate from the downloaded-model vectors.
4. Play-readiness of the CURRENT approach (relevant whatever happens): downloading a model from Hugging Face at runtime
   is allowed by Play policy (it is data, not executable code), but must be disclosed (size, Wi-Fi prompt) — Robia
   already asks before download. Alternative for Play: **Play for On-device AI (AI packs)** delivers the model through
   Play itself; earlier spec (robia-embeddings-sync-manage TS §417) rejected it only because "no Play release planned".

## Recommendation for the plan
- No implementation task now. Add a watch item (follow-up) "re-evaluate when genai-embedding reaches beta/GA without
  Developer Preview enrolment"; trigger = ML Kit release notes.
- If Manu wants, an optional, cheap spike later: on an AICore device enrolled in the preview, compare ML Kit vectors vs
  our LiteRT vectors on the precision dataset (same thresholds) — only then decide an adapter.
