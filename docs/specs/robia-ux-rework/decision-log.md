# Decision log — robia-ux-rework

Status legend: **D** decided by planner on evidence (approved with the packet), **Q** pending Manu's answer (default = option A, used by the plan), **R** rejected alternative (kept for the record).

## UX-DEC-001 — Browse card (R9, R10, R13) · Q3 default A
- **Decision:** uniform card: photo 4:5 clipped by the card (16 dp), fixed 2-line footer (title rule REQ-BR-02, occasions/season as text with ellipsis, colour dots), no chips, cloud badge only for non-synced states.
- **Evidence (code, certain):** `RobiaApp.kt` `GarmentGridCard` 3766-3871 renders `Text(item.name)` although new garments are saved with `name = ""` (`AddEditClothingScreen.kt:911`), so line 1 is empty; subtitle falls back to the sync label "Synced" (`toUiWardrobeItems` 5306-5310); tag `Row` always emitted; `TonalTag` 4087-4101 has `maxLines = 1` without ellipsis and no weight ("Dressed-u"); card uses `shapes.medium` 12 dp while `GarmentPhotoPlaceholder` clips `shapes.large` 16 dp (corner gap). Earth & Steel DESIGN.md asks 16 px card radius.
- **Alternative B (photo-only 3-column grid):** shown in `mockups/browse.html`; loses names/tags in the grid.

## UX-DEC-002 — Reset filters (R3) · Q4b default A
- Reset next to Filters clears filters only; search has its own clear (✕) and similar-to has its chip. Today the empty-state button clears both (`RobiaApp.kt:3488-3492`): it keeps that behaviour because it is a "show everything" action.

## UX-DEC-003 — Filter semantics (R2) · Q4 default A
- Multi-select everywhere; OR inside a section, AND across sections. Today `BrowseFilterState.matches` requires ALL selected tag ids (`selectedTagIds.all(itemTagIds::contains)`, `RobiaApp.kt:365`) and Category/Location are single-select by `toggleSingleValueTag`, so two seasons return only garments carrying both.
- Implementation stays a pure function with unit tests (`BrowseFilterState.matches(item, colours, tagCategoryById)`).

## UX-DEC-004 — Manage swipe (R1) · D
- `HorizontalPager` (foundation, BOM 2024.12.01 already present) with `ScrollableTabRow` as indicator; `beyondViewportPageCount = 0`; hoisted tab id stays the single source of truth (`settledPage` → callback; callback → `animateScrollToPage`).

## UX-DEC-005 — Garment form and detail rows (R5) · D (mockup approval Q7)
- Premise check: the add form has no 2-column grid; the odd 2-column boxes are `DetailMetadataGrid` (5 cells `chunked(2)`, `RobiaApp.kt:4503-4553`). The add form has card-in-card styling (`MetadataShellCard` inside `CardSection`). Both screens move to list rows + bottom-sheet pickers. No Name field is reintroduced (commit 528c53d "Polish no-name garment add flow").

## UX-DEC-006 — Processing feedback (R6) · D
- Own Compose code, no library: diagonal sweep brush; shimmer masked to the foreground alpha via `graphicsLayer(compositingStrategy = Offscreen)` + `drawWithContent { drawContent(); drawRect(brush, blendMode = SrcAtop) }`; glow = blurred tinted copy behind (API 31+ `RenderEffect`, otherwise skipped). Library alternatives (Lottie, compose-shimmer) rejected: none masks to an arbitrary bitmap alpha and they add weight.

## UX-DEC-007 — Haptics and motion (R14) · Q6 default A
- `LocalView.performHapticFeedback`: CONFIRM / REJECT on API 30+, fallback CONTEXT_CLICK / LONG_PRESS; chip/switch = CLOCK_TICK. Setting "Haptic feedback" default ON. Respect `ANIMATOR_DURATION_SCALE == 0`. No BOM bump (the newer Compose HapticFeedbackType constants are not in BOM 2024.12.01).

## UX-DEC-008 — Settings (R12) · D
- Grouped navigation rows; Language and Google Drive become sub-screens; Haptic feedback switch; Precise cutout row. `LanguageSettingsScreen` (read-only dead end, `RobiaApp.kt:5250-5295`) is replaced by the real picker.

## UX-DEC-009 — PDF (R11) · D
- A4 portrait, Inter OFL bundled under `res/font` (Regular, SemiBold, Bold, ~0.9 MB total, OFL text in `assets/licenses/`), `Paint.FILTER_BITMAP_FLAG`, gradient behind the photo only, logo footer kept. Earlier decisions kept (pinned in `RegressionSourceContractTest`, commit 80767fc): no Location row, metadata order Category → Season → Occasions → Fit, wrap instead of ellipses. The page-size/overlay pins are replaced because R11 replaces that design. Lookbook (multi-garment) is out of scope.

## UX-DEC-010 — Taxonomy (R15) · Q5 default A
- Add Socks and Underwear only. Evidence (`artifacts/seg-benchmark.md` §Taxonomy, EmbeddingGemma on 142 labelled photos, TEXT mode top-1): baseline 120/142; + socks + underwear (+ swimwear) → 118/142 (2 stolen); + sleepwear → 96/142 (sleepwear stole 32 dresses/shorts) → **R** sleepwear. Swimwear harmless (0 stolen) but not requested → optional (Q5 B).
- No positive sock/underwear photos exist in the set: recall is a device row (V-Z2-14), not a measured claim.
- Sync: like every default, the new rows are seeded locally on each device and not queued; a user tag with the same normalised name (e.g. a custom "Socks") is merged into the built-in id by the existing H03 dedupe at startup, so the user sees one row (its custom description is not kept).

## SEG-DEC-001 — Fix ML Kit reliability first (R4) · D
- Root cause (certain by code + upstream issue googlesamples/mlkit#829): the app never uses `ModuleInstallClient`; ML Kit's optional subject-segmentation module is downloaded lazily by Play services, and the engine returns `MODEL_UNAVAILABLE` ("Waiting for the subject segmentation optional module…") until then; no wait/retry exists (`MlKitSubjectSegmentationBackgroundRemovalEngine.kt:77-83`). Fix: availability check + urgent install + bounded wait + one automatic retry. "Always fails" on some devices stays possible (module never delivered): then the precise pack is the remedy.

## SEG-DEC-002 — Soft edges (R8) · D
- ML Kit path: request `enableForegroundConfidenceMask()` and build alpha = smoothstep(0.35, 0.65, conf) refined by a guided filter (r = 3 px at 1024 px, eps = 1e-3) on luminance; write PNG with soft alpha. Thumbnail decode uses sample size ≤ 2× target before bilinear scaling. Measured on the Pi: guided filter costs 0.04 s per 1024 px photo.

## SEG-DEC-003 — Precise cutout pipeline (R7) · Q1 default A
- **Decision:** pipeline v10 (REQ-SG-05) shipped as an optional downloadable pack; ML Kit stays default and fallback.
- **Evidence (Pi 5 CPU, 4 threads, Manu's 142 photos, never committed):** u2netp 0.7 s; Interactive Segmenter 3 taps 0.5 s; D-FINE-S 0.9 s; MobileSAM encoder 3-4 s + decoder 0.2 s (only when a hanger is detected); guided filter 0.04 s. Median 2.2 s without hanger, ~6 s with hanger. Fixes white-on-white door leaks and door-handle specks; removes most wire hangers; removes clip-bar hangers on skirts/shorts about half the time (detector misses). Visual sheets were reviewed by the planner and shared privately with Manu.
- **Models (all permissive):** u2netp (Apache-2.0, 4.5 MB), MediaPipe magic_touch.tflite (Apache-2.0, 6.2 MB), D-FINE-S Objects365 ONNX (Apache-2.0, 42 MB), MobileSAM encoder + multi-mask decoder ONNX (MIT, 28 + 16 MB). Hashes in TS§SG.
- **Rejected (R):** ISNet as base (170 MB, 7 s, same result after refinement); BiRefNet-lite (OOM > 4 GB RSS); SAM2.1-tiny (134 MB, 6-10 s; MobileSAM equal quality); u2net_cloth_seg (human parsing, fails on flat garments); morphological hanger removal (cut straps); ultralytics YOLO hanger models (AGPL).
- **Manu's idea — segment everything + EmbeddingGemma label per segment (R):** tested on 6 photos: MobileSAM 48-point grid 15-24 s + EmbeddingGemma 43-58 s per photo; label margins 0.001-0.06; white blouses labelled hanger/door → empty union. A trained detector (D-FINE, class Hanger) is the working form of the same idea.
- **Gemma as hanger/no-hanger judge (R):** 8/11 picks with margins 0.001-0.03 = noise.

## SEG-DEC-004 — Manual refine (R7 "selección inteligente") · Q2 default A
- MediaPipe Interactive Segmenter (`com.google.mediapipe:tasks-vision:1.1.1`, model bundled in assets, 6 MB) behind a "Refine" button: Keep/Remove tap → region mask → alpha union/subtraction. Bundled so it works without the pack and offline.

## SEG-DEC-005 — Runtime packaging · D
- ONNX Runtime Android `com.microsoft.onnxruntime:onnxruntime-android:1.31.0` for the three ONNX models; MediaPipe tasks-vision for Interactive Segmenter. Release APK size growth gate: ≤ +25 MB per ABI split; otherwise add `abiFilters("arm64-v8a")` for staging/release (pre-approved fallback).
- Pack download reuses `ModelStore` generalized by file name/sha (no second downloader).

## SEG-DEC-006 — Gates · D
- G-SEG-1 (performance, device): thresholds in REQ-SG-07. Pre-approved fallback when failing: drop MobileSAM (hanger removal only by Refine), re-measure once; if still failing, the pack stays developer-only and Manu decides.
- G-SEG-2 (visual, device): Manu photographs ≥ 10 garments (≥ 3 on hangers, ≥ 2 white-on-white, socks if available) with the pack ON and OFF and rates each cutout OK/NOK. Pass = pack NOK count < ML Kit NOK count and no garment part lost that ML Kit kept. Fail → pack stays opt-in in Developer only.

## PROC-DEC-001 — Hot file split first · D
- `ui/RobiaApp.kt` has 5569 lines and every UI item touches it. Task U00 performs a move-only split into 6 files (same package, no logic change) so U02/U03/U05/U06/U09 can run in parallel on disjoint files. Source-contract tests that read `RobiaApp.kt` switch to a helper concatenating all `ui/*.kt` sources.

## PROC-DEC-002 — Reviews · D
- Independent read-only review required for: SG1 (failure/fallback paths), SG2a (native resources, model integrity, fallback), T01 (seeding + sync + scoring fixture), U03 (filter semantics), U06 (form save path). Others: coordinator 5-check (diff vs allowed files, grep for data writes, CI, receipt, mockup match).
