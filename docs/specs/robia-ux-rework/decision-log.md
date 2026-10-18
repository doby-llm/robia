# Decision log — robia-ux-rework

Status legend: **D** decided by planner on evidence (approved with the packet), **Q** pending Manu's answer (default = option A, used by the plan), **R** rejected alternative (kept for the record).

## UX-DEC-001 — Browse card (R9, R10, R13) · Q3 → hybrid (approved 2026-10-17, see UX-DEC-013)
- **Decision:** uniform card: photo 4:5 clipped by the card (16 dp), fixed 2-line footer (title rule REQ-BR-02, occasions/season as text with ellipsis, colour dots), no chips, cloud badge only for non-synced states.
- **Evidence (code, certain):** `RobiaApp.kt` `GarmentGridCard` 3766-3871 renders `Text(item.name)` although new garments are saved with `name = ""` (`AddEditClothingScreen.kt:911`), so line 1 is empty; subtitle falls back to the sync label "Synced" (`toUiWardrobeItems` 5306-5310); tag `Row` always emitted; `TonalTag` 4087-4101 has `maxLines = 1` without ellipsis and no weight ("Dressed-u"); card uses `shapes.medium` 12 dp while `GarmentPhotoPlaceholder` clips `shapes.large` 16 dp (corner gap). Earth & Steel DESIGN.md asks 16 px card radius.
- **Alternative B (photo-only 3-column grid):** shown in `mockups/browse.html`; loses names/tags in the grid.

## UX-DEC-002 — Reset filters (R3) · approved
- Reset next to Filters clears filters only; search has its own clear (✕) and similar-to has its chip. Today the empty-state button clears both (`RobiaApp.kt:3488-3492`): it keeps that behaviour because it is a "show everything" action.

## UX-DEC-003 — Filter semantics (R2) · approved 2026-10-17: OR within a section, AND across
- Multi-select everywhere; OR inside a section, AND across sections. Today `BrowseFilterState.matches` requires ALL selected tag ids (`selectedTagIds.all(itemTagIds::contains)`, `RobiaApp.kt:365`) and Category/Location are single-select by `toggleSingleValueTag`, so two seasons return only garments carrying both.
- Implementation stays a pure function with unit tests (`BrowseFilterState.matches(item, colours, tagCategoryById)`).

## UX-DEC-004 — Manage swipe (R1) · D
- `HorizontalPager` (foundation, BOM 2024.12.01 already present) with `ScrollableTabRow` as indicator; `beyondViewportPageCount = 0`; hoisted tab id stays the single source of truth (`settledPage` → callback; callback → `animateScrollToPage`).

## UX-DEC-005 — Garment form and detail rows (R5) · mockup approved 2026-10-17
- Premise check: the add form has no 2-column grid; the odd 2-column boxes are `DetailMetadataGrid` (5 cells `chunked(2)`, `RobiaApp.kt:4503-4553`). The add form has card-in-card styling (`MetadataShellCard` inside `CardSection`). Both screens move to list rows + bottom-sheet pickers. No Name field is reintroduced (commit 528c53d "Polish no-name garment add flow").

## UX-DEC-006 — Processing feedback (R6) · D
- Own Compose code, no library: diagonal sweep brush; shimmer masked to the foreground alpha via `graphicsLayer(compositingStrategy = Offscreen)` + `drawWithContent { drawContent(); drawRect(brush, blendMode = SrcAtop) }`; glow = blurred tinted copy behind (API 31+ `RenderEffect`, otherwise skipped). Library alternatives (Lottie, compose-shimmer) rejected: none masks to an arbitrary bitmap alpha and they add weight.

## UX-DEC-007 — Motion (R14) · haptics REJECTED by Manu 2026-10-17 ("no le veo el sentido a la vibración"); motion approved
- (Rev 1 proposed haptics with `LocalView.performHapticFeedback`; withdrawn in rev 2. Nothing of it remains in the plan.) Motion: press scale, AI shimmer, pop-in, one-shot sparkle burst; system "remove animations" respected.

## UX-DEC-008 — Settings (R12) · D
- Grouped navigation rows; Language and Google Drive become sub-screens; Browse layout row (rev 2; the rev-1 haptics switch is withdrawn); Precise cutout row. `LanguageSettingsScreen` (read-only dead end, `RobiaApp.kt:5250-5295`) is replaced by the real picker.

## UX-DEC-009 — PDF (R11) · D
- A4 portrait, Inter OFL bundled under `res/font` (Regular, SemiBold, Bold, ~0.9 MB total, OFL text in `assets/licenses/`), `Paint.FILTER_BITMAP_FLAG`, gradient behind the photo only, logo footer kept. Earlier decisions kept (pinned in `RegressionSourceContractTest`, commit 80767fc): no Location row, metadata order Category → Season → Occasions → Fit, wrap instead of ellipses. The page-size/overlay pins are replaced because R11 replaces that design. Lookbook (multi-garment) is out of scope.

## UX-DEC-010 — Taxonomy (R15) · approved A + Accessories description with belts (UX-DEC-015)
- Add Socks and Underwear only. Evidence (`artifacts/seg-benchmark.md` §Taxonomy, EmbeddingGemma on 142 labelled photos, TEXT mode top-1): baseline 120/142; + socks + underwear (+ swimwear) → 118/142 (2 stolen); + sleepwear → 96/142 (sleepwear stole 32 dresses/shorts) → **R** sleepwear. Swimwear harmless (0 stolen) but not requested → optional (Q5 B).
- No positive sock/underwear photos exist in the set: recall is a device row (V-Z2-14), not a measured claim.
- Sync: like every default, the new rows are seeded locally on each device and not queued; a user tag with the same normalised name (e.g. a custom "Socks") is merged into the built-in id by the existing H03 dedupe at startup, so the user sees one row (its custom description is not kept).

## SEG-DEC-001 — Fix ML Kit reliability first (R4) · D
- Root cause (certain by code + upstream issue googlesamples/mlkit#829): the app never uses `ModuleInstallClient`; ML Kit's optional subject-segmentation module is downloaded lazily by Play services, and the engine returns `MODEL_UNAVAILABLE` ("Waiting for the subject segmentation optional module…") until then; no wait/retry exists (`MlKitSubjectSegmentationBackgroundRemovalEngine.kt:77-83`). Fix: availability check + urgent install + bounded wait + one automatic retry. "Always fails" on some devices stays possible (module never delivered): then the precise pack is the remedy.

## SEG-DEC-002 — Soft edges (R8) · D
- ML Kit path: request `enableForegroundConfidenceMask()` and build alpha = smoothstep(0.35, 0.65, conf) refined by a guided filter (r = 3 px at 1024 px, eps = 1e-3) on luminance; write PNG with soft alpha. Thumbnail decode uses sample size ≤ 2× target before bilinear scaling. Measured on the Pi: guided filter costs 0.04 s per 1024 px photo.

## SEG-DEC-003 — Precise cutout pipeline (R7) · approved A with doubts; hanger-first added (SEG-DEC-007)
- **Decision:** pipeline v10 (REQ-SG-05) shipped as an optional downloadable pack; ML Kit stays default and fallback.
- **Evidence (Pi 5 CPU, 4 threads, Manu's 142 photos, never committed):** u2netp 0.7 s; Interactive Segmenter 3 taps 0.5 s; D-FINE-S 0.9 s; MobileSAM encoder 3-4 s + decoder 0.2 s (only when a hanger is detected); guided filter 0.04 s. Median 2.2 s without hanger, ~6 s with hanger. Fixes white-on-white door leaks and door-handle specks; removes most wire hangers; removes clip-bar hangers on skirts/shorts about half the time (detector misses). Visual sheets were reviewed by the planner and shared privately with Manu.
- **Models (all permissive):** u2netp (Apache-2.0, 4.5 MB), MediaPipe magic_touch.tflite (Apache-2.0, 6.2 MB), D-FINE-S Objects365 ONNX (Apache-2.0, 42 MB), MobileSAM encoder + multi-mask decoder ONNX (MIT, 28 + 16 MB). Hashes in TS§SG.
- **Rejected (R):** ISNet as base (170 MB, 7 s, same result after refinement); BiRefNet-lite (OOM > 4 GB RSS); SAM2.1-tiny (134 MB, 6-10 s; MobileSAM equal quality); u2net_cloth_seg (human parsing, fails on flat garments); morphological hanger removal (cut straps); ultralytics YOLO hanger models (AGPL).
- **Manu's idea — segment everything + EmbeddingGemma label per segment (R):** tested on 6 photos: MobileSAM 48-point grid 15-24 s + EmbeddingGemma 43-58 s per photo; label margins 0.001-0.06; white blouses labelled hanger/door → empty union. A trained detector (D-FINE, class Hanger) is the working form of the same idea.
- **Gemma as hanger/no-hanger judge (R):** 8/11 picks with margins 0.001-0.03 = noise.

## SEG-DEC-004 — Manual refine (R7 "selección inteligente") · approved; moved into the photo editor (UX-DEC-011)
- MediaPipe Interactive Segmenter (`com.google.mediapipe:tasks-vision:1.1.1`, model bundled in assets, 6 MB) behind a "Refine" button: Keep/Remove tap → region mask → alpha union/subtraction. Bundled so it works without the pack and offline.

## SEG-DEC-005 — Runtime packaging · D
- ONNX Runtime Android `com.microsoft.onnxruntime:onnxruntime-android:1.31.0` for the three ONNX models; MediaPipe tasks-vision for Interactive Segmenter. Release APK size growth gate: ≤ +25 MB per ABI split; otherwise add `abiFilters("arm64-v8a")` for staging/release (pre-approved fallback).
- Pack download reuses `ModelStore` generalized by file name/sha (no second downloader).

## SEG-DEC-006 — Gates · D
- G-SEG-1 (performance, device): thresholds in REQ-SG-07. Pre-approved fallback when failing: drop MobileSAM (hanger removal only by the photo editor), re-measure once; if still failing, the pack stays developer-only and Manu decides.
- G-SEG-2 (visual, device): Manu photographs ≥ 10 garments (≥ 3 on hangers, ≥ 2 white-on-white, socks if available) with the pack ON and OFF and rates each cutout OK/NOK. Pass = pack NOK count < ML Kit NOK count and no garment part lost that ML Kit kept. Fail → pack stays opt-in in Developer only.

## PROC-DEC-001 — Hot file split first · D
- `ui/RobiaApp.kt` has 5569 lines and every UI item touches it. Task U00 performs a move-only split into 6 files (same package, no logic change) so U02/U03/U05/U06/U09 can run in parallel on disjoint files. Source-contract tests that read `RobiaApp.kt` switch to a helper concatenating all `ui/*.kt` sources.

## PROC-DEC-002 — Reviews · D
- Independent read-only review required for: SG1 (failure/fallback paths), SG2a (native resources, model integrity, fallback), T01 (seeding + sync + scoring fixture), U03 (filter semantics), U06 (form save path). Others: coordinator 5-check (diff vs allowed files, grep for data writes, CI, receipt, mockup match).

## Revision 2 (Manu feedback 2026-10-17, `feedback/2026-10-17-manu-rev1-feedback.md`)

## SEG-DEC-007 — Hanger first (Manu: background enclosed by the hanger is not removed) · D
- Cause (planner, contact sheet of 142 cutouts on magenta): after the hanger is subtracted, the strip of door/wall between a clip bar and the waistband (or inside a wire hook) stays, because saliency + interactive selection had already joined it to the garment; the hanger removal only deletes the hanger pixels.
- Tried: (A) fill the hanger pixels from the surroundings and run saliency + selection again on the filled photo, so the enclosed strip now touches the outside background; (B) delete opaque regions near the hanger whose colour matches the background ring. B wrongly deletes light waistbands and white trims (#125, #138) → rejected. A removes the enclosed strip on #89, #80, #130, #132 and keeps light waistbands; on #129 the re-selection lost the lower part → guard (band rule): the new selection is only used in a band from the hanger down to 0.6 × hanger height (min 8 % of the garment height); below it the previous result is kept. With the band, #129 stays complete and #89/#80/#130/#132/#138 keep the improvement. The cheap median fill matched OpenCV Telea on the 10 test garments, so the phone version needs no OpenCV. Cost on the Pi (full 142 run, chosen variant): median +2.4 s, p90 +3.5 s on garments with a hanger; no garment part lost in the 12 largest-change cases.
- On the phone the fill is pure Kotlin (median ring colour + box blur), no OpenCV; G-SEG-2 judges it.
- Honest limit: automatic removal still misses some clip hangers (detector misses); the Cutout tab of the photo editor is the fallback Manu accepted ("en el peor de los casos … el usuario con el segmentador"). Speed: the precise pack stays optional and off by default until G-SEG-1/G-SEG-2 say otherwise; ML Kit remains the default.

## UX-DEC-011 — One photo editor instead of the quick-edit dialog · D (Manu: "a tu juicio")
- The quick-edit dialog (brightness + temperature) becomes a full-screen editor with tabs Cutout and Light. Cutout: Keep / Remove, smart selection by tap or short swipe (MediaPipe Interactive Segmenter selects the whole piece) and a Brush toggle for exact small fixes; Undo, Reset, pinch-zoom. Light: the existing controls, unchanged processing.
- Why: one entry point ("Edit photo") for every photo fix, room for zoom and precise touches that a dialog lacks, and the Light controls get a larger preview. Alternatives: separate Refine screen (two entry points) — rejected; keep the dialog and add a button — rejected (too small for masks).

## UX-DEC-012 — Batch screen as a list · D
- One row per garment: thumbnail, position, status chip, detected category · occasions (2 lines max). Grid rejected: rows with variable text make an irregular grid (Manu's point). The status text under the chip and the duplicated helper sentence (header + bottom bar) are removed. ETA right of the progress bar from the last 4 completion gaps, smoothed so it never jumps up by more than 20 %.

## PROC-DEC-004 — Batch speed via an in-app pipeline, not multiprocessing · D
- Android has no cheap multiprocessing for this; the expensive steps are native (ML Kit in Play services, EmbeddingGemma via LiteRT) and already use several cores each. The embedding engine is a single native instance behind a Mutex (`EmbeddingEngineHolder`), so two embeddings in parallel would only queue (or double ~0.8 GB of memory with a second engine — rejected).
- Chosen: overlap stages — up to 2 ML Kit cutouts (1 with the precise pack) run while the single embedding worker processes the previous garment; results publish in screen order. Expected ≈ 40-45 % less wall time per garment (cutout ≈ 1.5 s and suggestion ≈ 1.6 s on Poco F3 → ≈ 1.6-1.8 s instead of ≈ 3.1 s); V-Z2-21 measures it with per-garment log lines.

## UX-DEC-013 — Browse hybrid · D (Manu)
- Default: 2 columns, photos only (same tile size for all garments). Setting "Browse layout" switches to "Photos with details" (the card of UX-DEC-001). TalkBack reads name/category/occasions from the tile.

## UX-DEC-014 — Review completion screen · D (Manu)
- After committing a tag or colour review: 1.6 s screen with a check, a one-shot burst of 6-8 small sparks and "Tags updated on N garments"; tap skips; no screen when nothing changed. Subtle by design (no confetti, no loop, no sound).

## UX-DEC-015 — Accessories description includes belts · D (Manu)
- Measured with the app's recipe (vt70 image vectors, 142 labelled photos): current text 9/12 accessories, 114/142 overall; "an accessory: a belt or waist belt, a scarf, a hat, sunglasses or a similar item" + Socks/Underwear → 11/12, 115/142, 0 false accessories. A stronger "a belt or other accessory: …" reached 12/12 but pulled 2 other garments into Accessories → rejected.

## UX-DEC-016 — Sparse PDF · D (Manu: most garments have no name and notes are optional)
- Title = name, else category, else "Garment"; empty blocks are omitted and the photo grows to fill the page; no blank band. Three mockup cases.

## Revision 3 — findings and pending decisions (2026-10-17)
Source: `feedback/2026-10-17-manu-rev2-feedback.md` (R16–R20). Evidence: `artifacts/inv-r3-zoom-colors-storage.md`, `artifacts/inv-r3-drive-photo-audit.md` (sub-agent, key claims re-verified by the planner), `artifacts/inv-r3-ricardo.md` (sub-agent, key sources re-fetched).
- **UX-DEC-017 Browse pinch zoom (R16) · D (planner):** feasible with Compose foundation 1.7 (BOM 2024.12.01), no library. `GridCells.Fixed(n)`, n 2..4 (Photos only) / 2..3 (Photos with details), persisted `browse_columns`, `animateItem()`, first visible item anchored. New task U10 after U02.
- **DATA-DEC-001 Drive usage (R18) · D (planner):** sum `size` of the `appDataFolder` listing that every upload already fetches (`GoogleDriveWardrobeRepository.kt:352, 683-697`); show in Settings › Google Drive. No new scope or request. New task D01 after U05.
- **COL-DEC-001 colour extraction (R17) · D (Manu: "1. Sí" → add Gray, task C01):** no Gray swatch exists (`gray-charcoal` = "Beige / Cream" #D8C3A5, `DefaultTags.kt:47`); low-saturation pixels can only become black/white/beige/brown, so grey and denim garments read as brown/beige. Adding Gray #8A8A8A: 30/142 change, ≈18 better / ≈6 worse (planner visual check). Optional speed fix: decode with `inSampleSize` (full-size decode today, `ClothingImageStore.kt:145`). ΔE2000 / k-means: no gain expected.
- **SYNC-DEC-001 photo integrity (R20) · planner-verified findings:**
  - Restore never compares downloaded bytes with the record's `contentHash`/`byteSize`; any decodable image is accepted (`GoogleDriveWardrobeRepository.kt:1155-1210`).
  - Upload trusts the 200 response without size/checksum check (`:431-451`).
  - `CopyRemote` keeps a remote record without checking that its blob exists in the Drive index (`:384-390`).
  - Account binding is dead code: `authorizedAccountEmail` / `expectedAccountEmail` are never assigned in main (only declared, `WardrobeSyncGateway.kt:71-86`; read at `RobiaApp.kt:1054`).
  - Only the edited photo exists, both locally (`photo_uri`) and on Drive (lossy WebP q90 ≤ 2048 px). The original camera photo is not kept after save (`AddEditClothingScreen.kt:600`).
  - Safe today: replace order (upload → snapshot → purge), purge exclusions, partial-download cleanup, guarded restore with user-visible attention, error classification.
  - Not fixable in-app: photos added and never synced are lost on uninstall (Android backup is disabled on purpose).
  - D (Manu "2. A", "3. A"): task D02 = exact restore/upload, CopyRemote index check, account binding, read-only Check photos; the original photo is not backed up (one photo per garment).
- **SELL-DEC-001 Ricardo (R19) · REJECTED (Manu: "Descartado 100%"); findings kept for reference:**
  - Ricardo's public seller API was switched off on 2026-09-01 with no successor (magnalister notice, 2026-06-17; legacy docs only).
  - No verified URL prefill.
  - The app is `com.qxl.Client` and handles ricardo.ch links (assetlinks.json verified).
  - Least-permission path: a "Sell on Ricardo" button that prepares title + description (editable), copies them to the clipboard, and shares the photo with `ACTION_SEND` if Ricardo accepts images, otherwise opens `https://www.ricardo.ch/de/list/`. The user completes and publishes in Ricardo.
  - No new Android permission.

- **PROC-DEC-005 (rev 3):** Manu "5. A": U10, C01, D01, D02 join this round before APROBADO (wave W5).
- **SEG-DEC-008 (rev 3, R21) · D (approved with the plan):** keep v11 as the precise-cutout pipeline. Dedicated matting models (IS-Net, BEN2, BiRefNet-lite) keep the hanger and, on white doors, the door; BiRefNet-lite does not fit 5 GB at 1024 px; BEN2 ~90 s/photo. LaMa inpainting of the hanger = same result as the median fill at ×3 time. The quality bottleneck is hanger detection (missed clips, wooden clip hangers). Follow-up (not this round): open-vocabulary hanger detector ≤ 100 MB / ≤ 2 s. Evidence `artifacts/inv-r3-matting.md`; generative results in `artifacts/inv-r3-generative.md`.

## G-APPROVAL (2026-10-18)
Manu: "APROBADO" on revision 3 (Telegram). Execution model (Manu): the planner does not implement; coordinator = Claude Sonnet 5.5 effort high; workers and independent reviewers = Claude Haiku 5.5 effort high. Independent review list extended with D02 (photo integrity).
