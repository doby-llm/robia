# TASK-B02 — Remove the old classifier

## Goal
Delete the TFLite "additional info" classifier and everything that only exists for it (module, model, scripts, CI steps, "AI-detected tag" warnings). It performs poorly and is replaced by EmbeddingGemma later (E05). Until E05 is merged, new garments get no automatic category/occasion — this is accepted.

## Read first
TS§3.13 (complete removal list). Files: `ui/AddEditClothingScreen.kt`, `ui/BatchAddClothingScreen.kt`, `ui/RobiaApp.kt`, `ui/ManageTagsScreen.kt`, `media/EditorPhotoState.kt`, `RegressionSourceContractTest.kt`.

## Steps
1. Find every usage first: `grep -rn -i -E "tflite|tensorflow|additionalinfo|additional_info|AiDetectedTag|rename_ai_tag|delete_ai_tag" --exclude-dir=build .` and keep the list in the PR description.
2. Delete: directories `additional-info-core/`, `additional-info-cli/`, `app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/`, `app/src/main/assets/additional_info/`, `app/src/test/java/com/gusanitolabs/robia/media/additionalinfo/`; scripts `scripts/check_additional_info_android_parity_static.py`, `scripts/check_additional_info_tflite_drift.py`, `scripts/debug_additional_info_inference.py`, `scripts/run_additional_info_shared_cli.sh`; `docs/additional_info_classifier_debug.md`.
3. `settings.gradle.kts`: remove both `include(":additional-info-…")`. `app/build.gradle.kts`: remove `implementation(project(":additional-info-core"))` and tensorflow-lite. `libs.versions.toml`: remove `tensorflowLite` + `tensorflow-lite`.
4. CI (`android-apk.yml`): remove the 3 steps "Set up Python for TFLite drift check", "Install TFLite drift check dependencies", "Check bundled TFLite model drift".
5. The core module held `AdditionalInfoPrediction.kt` (types `AdditionalInfoDetectionResult`, `AdditionalInfoLabelScore`) and maybe other shared models: if anything in `app/` still imports a class from `additional-info-core` that is **not** classifier-related, move that file into `app/src/main/java/com/gusanitolabs/robia/core/model/` unchanged (same package) and say so in the receipt.
6. `AddEditClothingScreen.kt`:
   - remove `additionalInfoDetector`, the detection blocks (fresh photo and `reprocessEditedPhoto`), the original-photo fallback detection, `addDetectionDebugLines`, `formatDebugTopK`, `appendDebugScores`, `exportAdditionalInfoInputImage` and the button wired to it (`onExportInputImage`), and the enum entry `PhotoProcessingStage.DetectingAdditionalInformation`.
   - keep and rename `reconcilePredictedAdditionalInfoTags` → `reconcilePredictedTags`; keep `applyPredictedTags`; set `MODEL_PREDICTED_CATEGORIES = setOf("category", "occasion")`. If they become unused, keep them with the comment `// Reused by TASK-E05 (EmbeddingGemma suggestions).` and `@Suppress("unused")`.
7. `BatchAddClothingScreen.kt`: remove the `additionalInfoDetector` parameter and the call in `processBatchDraft`; keep `mergePredictedTags` (same comment if unused).
8. `RobiaApp.kt`: remove `batchAdditionalInfoDetector` and the argument passed to batch processing.
9. `ManageTagsScreen.kt`: remove `AdditionalInfoModelOutputTags` checks, `pendingModelTagRename`, the delete-warning flow and `AiDetectedTagWarningDialog`. Delete a custom tag directly (same as today for custom tags). `canEdit = true` for every row; keep "add" disabled for `season` (`category.id != "season"`).
10. `EditorPhotoState.kt`: rename `additionalInfoSourceUri` → `suggestionSourceUri`, `canonicalAdditionalInfoUri` → `canonicalSuggestionUri` (all usages).
11. Strings (3 locales): delete `rename_ai_tag_title`, `rename_ai_tag_body`, `rename_anyway`, `delete_ai_tag_title`, `delete_ai_tag_body`, `delete_anyway`, `processing_stage_detecting_additional_information`, `developer_export_input_image*`. Grep each name before deleting; keep any still referenced.
12. `RegressionSourceContractTest.kt` / `ImagePipelineContractTest.kt`: delete only assertions that reference removed symbols/files. Do not weaken other assertions.
13. `docs/google_drive_sync_setup_plan.md` mentions the classifier: leave it (historical doc).

## Tests
None new. The grep in V-B03 must return nothing (paths under `docs/specs` and `docs/google_drive_sync_setup_plan.md` excluded).

## Done when
CI green; V-B03 grep output (empty) pasted in the receipt; V-B04.

## Do not
Add any suggestion code; change photo cropping or color extraction.
