# TASK-U09 — PDF A4 rework

## Goal
Manu finds the exported garment PDF ugly (R11). Today the page is 720×≥1280 pt, the photo is drawn without bitmap filtering, a primary-colour gradient is painted over the garment, a 4-cell grid sits in the middle and the footer is pinned far below, leaving a blank band (`artifacts/inv-r2-b.md` §R11). Produce the A4 editorial card of mockup `pdf.html`, keeping three earlier decisions: no Location row, metadata order Category → Season → Occasions → Fit, and text that wraps instead of ellipses.

## Read first
TS§9; `media/GarmentShareExporter.kt` (whole file, ~540 lines: `createSharePdf`, `drawGarmentCard`, `drawBitmapFitContain`, `drawImageGradientOverlay`, `drawColorRow`, `drawMetadataGrid`, footer with `ROBIA_LOGO_ASSET`, `calculatePdfPageHeight`, `GarmentShareItem` ~512); `RegressionSourceContractTest.kt` tests `garmentPdfExport_usesMobileReferenceLayoutAndBundledBrandAsset` (~861), `garmentPdfExport_containsImageAndPromotesColorHierarchy` (~884), `garmentPdfExport_keepsRequiredMetadataGridOrder` (~903, must stay unchanged and green).

## Steps
1. Fonts: download Inter static TTFs (Regular, SemiBold, Bold) from the latest official release on github.com/rsms/inter; save as `res/font/inter_regular.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`; add the OFL text to `assets/licenses/inter-OFL.txt`; record URL + sha256 in the receipt.
2. `media/PdfLayout.kt` (pure Kotlin, no `android.*` import): `PdfContent`, `PdfBox`, `PdfTextMeasurer`, `computePdfLayout(content: PdfContent, measurer)` as TS§9 (built from `GarmentShareItem` in the exporter; the pure file never sees a `Uri`); font auto-fit (title 30 → 22 pt, notes 12.5 → 10 pt) before overflowing notes to page 2.
3. Exporter: constants `PDF_PAGE_WIDTH = 595`, `PDF_PAGE_HEIGHT = 842`; draw from the layout with `StaticLayout` (no ellipsize; metadata values `maxLines = Int.MAX_VALUE`); photo with `Paint(ANTI_ALIAS_FLAG or FILTER_BITMAP_FLAG)` and the existing contain-fit `min(` scale; gradient only inside the rounded photo box, behind the photo; remove `drawImageGradientOverlay`, `ENABLE_PDF_IMAGE_GRADIENT_OVERLAY`, `MIN_PDF_PAGE_HEIGHT`, `calculatePdfPageHeight` and the old card/grid code when unused; keep the logo footer and "Created with Robia".
4. Rewrite the two design tests (~861, ~884) to pin the new design with the markers listed in TS§9 (tasks.md rule 11). Leave `garmentPdfExport_keepsRequiredMetadataGridOrder` untouched.
5. Do not edit `docs/ux/pdf_render/garment_pdf_rendering_notes.md` (not in the Files list); say in the receipt that it now describes the old design (coordinator follow-up).
6. `createShareImage` (PNG export), `GarmentShareItem` fields and `toGarmentShareItem` stay unchanged.
7. Sparse garments (revision 2, REQ-PD-02; most garments have no name and notes are optional): `PdfContent` is built from `GarmentShareItem` (no `Uri` in the pure layout); `pdfTitle(name, categoryValues, fallback)` = trimmed name, else first value of the Category metadata entry, else `pdf_title_fallback` "Garment" (new string, 3 locales); blank notes → no notes block and the photo box grows up to 600 pt; no colours with a hex → no swatch row; metadata rows with no values are omitted (order unchanged); no metadata at all → the left column takes the full width. Never leave an empty band > 40 pt above the footer (extra space goes to the photo, then to spacing).

## Tests
`PdfLayoutTest` (V-U09-1) with a fake measurer (chars per line = width / (0.5 × size)), including the sparse cases: category only, nothing but the photo, no colours.

## Done when
V-U09-1, V-U09-2, CI green.

## Do not
Change share intent/FileProvider, PNG export, the metadata set/order, or add a multi-garment PDF.
