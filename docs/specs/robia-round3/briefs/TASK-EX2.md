# TASK-EX2 — Export: Robia frame switch and a visible Preview

## Goal
F3 (REQ-FB-03) and F4 (REQ-FB-04): Manu wants a switch to show or hide the Robia frame (the white border and the logo around the image, in Image and PDF), switchable off only when every other Show option is off, so the output is purely the photo (Original or Robia cutout). And when he taps Preview nothing seems to happen because the preview appears below the fold.

## Read first
`R3-DEC-041`; `mockups/export.html`; `TASK-EX1.md`; `ui/ExportScreen.kt` (`showPreview` ~82, Preview button ~211, preview image ~226, `renderPreview` ~260); `media/GarmentShareExporter.kt` (image card ~49-130, PDF ~77-130); `media/PdfLayout.kt`; `data/SettingsRepository.kt` (`exportOptions()` ~287, `writeExportOptions` ~294); `core/model/ClothingModels.kt` `ExportOptions` (~246); `ExportOptionsTest`; `PdfLayoutTest`.

## Files
`ui/ExportScreen.kt`; `media/GarmentShareExporter.kt`; `media/PdfLayout.kt`; `data/SettingsRepository.kt`; `core/model/ClothingModels.kt` (`ExportOptions` gains the frame flag only); `app/src/test/java/com/gusanitolabs/robia/ui/ExportOptionsTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/PdfLayoutTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `ExportOptions.showFrame: Boolean = true`, persisted with the other `export_*` keys (absent → true). Pure `resolveFrame(options)` → `(checked, enabled)`: all Show fields off → `(showFrame, enabled = true)`; any field on → `(true, enabled = false)`.
2. Screen: a "Robia frame" switch (hint: "White border and Robia logo around the photo") placed under the Show switches; disabled with a one-line hint when a field is on.
3. Exporter and PDF: with the frame off (and no fields) the image export is the photo alone at the same long-edge rule (1600 px), no border, no logo, no text; the PDF page holds only the photo with no border, logo or text. With the frame on, output is unchanged.
4. Preview: while rendering show a loading state on the Preview button/area (progress indicator); when the bitmap is ready scroll the screen smoothly to the preview (`animateScrollTo`/`animateScrollToItem`); with reduced motion on, `scrollTo` (no animation) or none. Use the existing reduced-motion helper (`core/designsystem/Motion.kt`).

## Tests
V-EX2-1..3 (CI), V-EX2-4 (REV).

## Done when
V-EX2-1..4 pass, V-EX2-D is ready for Manu; CI green.

## Do not
Change export fields, formats, PDF page size/fonts, zip, or add a library.
