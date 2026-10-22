# TASK-PRF — Browse scroll performance at 4 columns

## Goal
R3-01: scrolling at 4 columns is not smooth. Ranked suspects with code evidence in inv-r3-a.md §A1.

## Read first
`artifacts/inv-r3-a.md` §A1; `media/ImagePipeline.kt` l.111-170, 219, 236-308, 326, 485-636; `ui/BoundedGarmentImage.kt` l.25-68; `ui/BrowseGrid.kt` l.145-171, 357-444; `ui/RobiaApp.kt` l.931-938; `scripts/check_image_thumbnail_pipeline_static.py` l.100-160; G-ADB1 baseline numbers in the PLAN-CHANGE.

## Files
`media/ImagePipeline.kt`; `media/ImagePipelineContracts.kt`; `ui/BoundedGarmentImage.kt`; `ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; `scripts/check_image_thumbnail_pipeline_static.py`; `app/src/test/java/com/gusanitolabs/robia/media/ImagePipelineContractTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. (a) Telemetry: build stats lazily only when the sink is enabled; never `listFiles` on the main thread.
2. (b) `BoundedGarmentImage`: skip the request until `onSizeChanged` delivers bounds (placeholder meanwhile).
3. (c) Prefetch with the display bounds per column count, or remove it.
4. (d) `contentType` on grid items. (e) `@Immutable` UI item (tags as `ImmutableList` or a stable wrapper) and build items only when garments/tags change, not on every sync emission.
5. Report what each step changed in the receipt; the coordinator measures at G-ADB2.

## Tests
V-PRF-1, V-PRF-2.

## Done when
V-PRF-1, V-PRF-2, V-PRF-3, V-PRF-4 pass; CI green.

## Do not
Change image quality, cache sizes or Coil version.
