# Garment PDF rendering notes

Implemented in `app/src/main/java/com/gusanitolabs/robia/media/GarmentShareExporter.kt` with native Android `PdfDocument` rendering so the export remains fully offline.

Reference usage:
- `DESIGN.md`: Earth & Steel palette, Inter-like hierarchy, warm cream surfaces, white cards, soft dividers.
- `code.html` / `screen.png`: mobile-vertical item detail structure, hero garment image, color chips, 2x2 metadata grid, compact branded footer.

Intentional differences from the web reference:
- The web app header and bottom navigation are omitted from the PDF.
- The Fabric & Care/material section is omitted per task requirements.
- The footer uses bundled `app/src/main/assets/robia_logo.png` plus spark glyphs and `Created with Robia`.
- The clothing image gradient overlay is controlled by `GarmentShareExporter.ENABLE_PDF_IMAGE_GRADIENT_OVERLAY` and defaults to `true`.

Page/rendering shape:
- Width: 720px.
- Minimum height: 1280px (phone-like 9:16 canvas), expanding only when long garment content needs more space.
- Offline assets: no network fonts, CSS, or images are required at export time.
- The 2x2 metadata grid uses native canvas vector icons and expands row height so multi-selected values wrap without ellipses.
