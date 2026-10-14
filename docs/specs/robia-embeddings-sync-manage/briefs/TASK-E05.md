# TASK-E05 — Capture integration (single + batch)

## Goal
When the user takes or picks a photo (single add, Quick Edit re-process, batch add), Robia proposes the category (only if clearly better than the second) and 0–2 occasions, without overriding anything the user chose manually. The image vector computed here is stored on save so it is not computed twice.

## Read first
TS§3.7. `ui/AddEditClothingScreen.kt` after B02: where the old detector was called (fresh photo after colour extraction; `reprocessEditedPhoto`), `applyPredictedTags`, `reconcilePredictedTags`, generation/token guards, `PhotoProcessingStage`, `DeveloperDiagnosticsSection`. `ui/BatchAddClothingScreen.kt` (`processBatchDraft`, `mergePredictedTags`). `ui/RobiaApp.kt` `onSaveItem`/`onSaveItems`. Scorers from E04, holder from E01.

## Steps
1. `suggestions/SuggestionService.kt`: `suspend fun suggestForPhoto(photoUri: Uri, tags: List<GarmentTag>, selectedCategoryId: String?): SuggestionOutcome` (steps TS§3.7). `SuggestionOutcome = Unavailable | Ready(categoryResult, occasionResult?, imageVector, imageInputHash, durationMs)`. Examples = IMAGE vectors from `EmbeddingRepository` of garments having each tag. Label vectors via `holder.embedText(labelTextFor(tag))`, cached in a `Map<String, FloatArray>` keyed by label text (+ model/recipe).
2. Construct the service in `RobiaApplication`; pass it down to the screens as a parameter (same style as other dependencies in `RobiaApp.kt`).
3. Add/Edit: new stage `PhotoProcessingStage.PreparingSuggestions` (string `processing_stage_preparing_suggestions`). In the old detector spots call `suggestForPhoto`; apply `Ready` results through `reconcilePredictedTags` (user overrides win). `Unavailable` → skip silently. Call `holder.preload()` in a `LaunchedEffect(Unit)` when the screen opens.
4. Developer diagnostics in the editor: lines from the outcome (mode, top-5 label/text/knn/score, margin, marked, reason, duration).
5. "Export input image" button back in dev diagnostics: writes `ImageInputEncoder` PNG bytes to `Pictures/Robia/` via MediaStore (strings `developer_export_suggestion_input*`).
6. Batch: replace detector parameter by the service; keep `mergePredictedTags` semantics; store the outcome's vector in the draft (wrapper class with `equals` ignoring the array).
7. Save: after `upsertItem`, if the outcome's photo is the saved `photoUri` → `embeddingRepository.upsert(id, IMAGE, vector, hash, duration)`. TEXT vector: compute `garmentTextInput(name, notes)`; if non-null embed it (`DOCUMENT_PREFIX` already included) and upsert TEXT; if null delete TEXT. Do this in `appScope` (not blocking navigation). Never change the garment row again.

## Tests
`SuggestionServiceTest` (fake embedder + in-memory repository): Unavailable → no tags touched; category marked only above margin; flag off → occasion skipped; user override preserved through `reconcilePredictedTags`.

## Done when
CI green; V-E05a. Device check V-Z05/V-Z02 in Z01.
