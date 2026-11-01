# TASK-P03 — Open-source licences screen and data-safety facts

## Goal
Robia ships Apache-2.0/MIT SDKs and one bundled model (`magic_touch.tflite`) and downloads five model files at
runtime (EmbeddingGemma 2 LiteRT, u2netp, D-FINE, MobileSAM encoder/decoder). Their licences must be shown and the
Play Data safety form must match what the app and its SDKs really do (SPEC §7.1, §7.3). Audit open items: D-FINE
ONNX card has no licence field; `magic_touch` provenance is undocumented; telemetry of Play services/ML Kit/ONNX
Runtime in the merged manifest (audit S6–S9).

## Read first
SPEC §7.1, §7.3; `artifacts/inv-r4-play-readiness.md` §3, §6, §9; `suggestions/EmbeddingRecipe.kt` (`MODEL_URL`);
`media/cutout/CutoutPack.kt`; `media/cutout/InteractiveSelector.kt:21,50`; `app/src/main/assets/licenses/inter-OFL.txt`;
`gradle/libs.versions.toml`; `ui/SettingsScreen.kt`.

## Steps
1. `app/src/main/assets/licenses/third_party.md`: one section per item — name, version/commit, licence, upstream source
   URL, "downloaded at runtime" or "bundled". Items: every model URL in `EmbeddingRecipe` and `CutoutPack`,
   `magic_touch.tflite`, and each library in `libs.versions.toml` that ships in the APK (Compose/AndroidX = Apache-2.0,
   LiteRT-LM, MediaPipe, ONNX Runtime (MIT), ML Kit subject segmentation (Google ML Kit terms), Play services auth,
   Inter font (OFL, existing file)). Resolve D-FINE from the upstream repo (`Peterande/D-FINE`) and magic_touch from
   MediaPipe's model card with `curl`; if still unresolved write "unresolved — needs Manu" (never guess a licence).
2. New `ui/OpenSourceLicensesScreen.kt`: full-screen `Dialog` (no new route, RobiaApp.kt is not in Files) showing
   the asset as plain scrollable text with headings; close button. Settings › General row `settings_open_source_licenses`
   (ES "Licencias de código abierto", DE "Open-Source-Lizenzen").
3. `docs/specs/robia-round4/artifacts/data-safety.md`: answer Play's Data safety questions from code facts
   (data types collected/shared, purpose, optional, encrypted in transit, deletion), listing for every merged-manifest
   provider/service of the release variant whether it sends data (you can read the merged manifest from the CI
   `assembleStaging` intermediates only if CI uploads it; otherwise inspect library AAR manifests in `~/.gradle/caches`
   read-only) — mark uncertain items "verify in Play Console".
4. Test `LicensesAssetTest` (V-P03-1): parse `MODEL_URL` and every `url =` in `CutoutPack.kt` from source text and
   assert each URL appears in `third_party.md`.

## Done when
V-P03-1 green; V-P03-2 evidence in the receipt; V-P03-D for Manu.

## Do not
Add a licence-plugin dependency; change downloads; add a route.
