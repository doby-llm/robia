# TASK-EX1 — Export screen: photo, format and fields

## Goal
R3-04: choose Robia photo or original, image or PDF, and which fields to show (mockups/export.html).

## Read first
`mockups/export.html`; `artifacts/inv-r3-b.md` §B2(a); `media/GarmentShareExporter.kt` l.49-109, 234-258; `media/PdfLayout.kt` l.79-225; `ui/ItemDetailScreen.kt` share menu l.246-289, l.447-483, `toGarmentShareItem` l.668-707.

## Files
new `ui/ExportScreen.kt`; `ui/ItemDetailScreen.kt`; `ui/RobiaApp.kt`; `media/GarmentShareExporter.kt`; `media/PdfLayout.kt`; `data/SettingsRepository.kt`; `core/model/ClothingModels.kt`; `app/src/test/java/com/gusanitolabs/robia/media/PdfLayoutTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/ExportOptionsTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `ExportOptions(photo: Robia|Original, format: Image|Pdf, fields: Set<ExportField>)` persisted in DataStore (`export_*` keys).
2. Route `RobiaRoute.Export(itemId)`; the detail share button navigates there (menu removed).
3. Image: vertical card PNG, long edge 1600 px: photo on the swatch backdrop, then name/category/colour chips/occasions/seasons/fit/location/notes as chosen, small "Robia" mark; PDF: existing layout fed only the chosen fields (location now supported).
4. Preview = the rendered image (or page 1 of the PDF) in-screen; Share uses the existing chooser helper.

## Tests
V-EX1-1..3.

## Done when
V-EX1-1, V-EX1-2, V-EX1-3, V-EX1-D pass; CI green.

## Do not
Change the PDF page size/fonts or add a library.
