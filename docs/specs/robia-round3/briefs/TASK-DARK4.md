# TASK-DARK4 — Dark mode: photo editor, analytics, export screen

## Goal
F14 (REQ-FB-14): the editor, Analytics and the Export screen look right in Dark, and the exports do not change at all. In the editor the content area judges the cutout, so it stays neutral and light; only the chrome follows the theme.

## Read first
`R3-DEC-045` (what stays light and why); `TASK-DARK1.md`; `ui/PhotoEditorScreen.kt`: Light-tab backdrop ~664 (`Color(0xFFE9E4DC)`, `Color(0xFFCFC3B4)`), `PreviewChip` ~1050 (`Color.White` text on `Color.Black` 46 %), `drawCheckerboard` ~1064 (`Color(0xFFEEEEEE)`/`Color.White`), temperature preset swatches ~1456 (blue to orange hex values); `ui/AnalyticsScreen.kt` (bars, labels, legend); `ui/ExportScreen.kt` (`renderPreview` ~260, the preview image ~226); `media/GarmentShareExporter.kt` (read only: it paints with `android.graphics.Color` and never reads the theme).

## Files
`ui/PhotoEditorScreen.kt`; `ui/AnalyticsScreen.kt`; `ui/ExportScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeEditorScanTest.kt`. UI-SMART changes `ui/PhotoEditorScreen.kt` first (the smart-tap code and the dead selector path); start from `origin/main` after it merged.

## Steps
1. `PhotoEditorScreen.kt`: add `// theme-fixed: <reason>` to the Light-tab backdrop, the checkerboard, the temperature preset swatches and the `PreviewChip` colours (they sit on or stand for a photo; the checkerboard and backdrop stay light so a dark garment is judged fairly). Every other colour is a scheme role; audit the top bar, mode chips, tool row, sheets, snackbar and the stroke overlays (`primary` / `error` at 35 % over the photo must stay visible on the light checkerboard in both themes).
2. `AnalyticsScreen.kt`: bars, axis labels, value labels, legend and empty state use scheme roles with the contrast of V-DARK1-2 on the dark surface; add no literal.
3. `ExportScreen.kt`: chrome follows the theme; the preview bitmap is shown unchanged, framed by a 1 dp `outlineVariant` outline so the white paper is delimited on the dark page; `renderPreview` and the export call take no theme input.
4. `DarkThemeEditorScanTest`: `ThemeSourceScan` over the Files list plus the source assertion of V-DARK4-1.

## Tests
V-DARK4-1 (CI), V-DARK4-2 (REV), V-DARK4-D (Manu).

## Done when
V-DARK4-1, V-DARK4-2 pass, V-DARK4-D is ready for Manu; CI green.

## Do not
Change `media/GarmentShareExporter.kt`, `media/PdfLayout.kt` or any exported file; make the checkerboard dark; change editor behaviour or the smart tap; edit the colour/tag review screens (look-only in V-DARK4-D).
