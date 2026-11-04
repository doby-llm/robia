# TASK-TH2 — Settings "Colour palette" row and dialog

## Goal
Let the user switch between Tobacco & Bordeaux (default) and Earth & Steel. TH1 already stores the choice and themes the
app from it; this task adds the row and the dialog in Settings › Appearance.

## Read first
SPEC §1.1 REQ-TH-7, §1.2 (copy). `ui/SettingsScreen.kt`: Appearance group (~:205-213), `showThemeDialog` (:159),
`ThemeModeDialog` (~:742-775, the pattern to copy), `ThemeMode.labelRes` (:646). `core/designsystem/Theme.kt`
`robiaColorScheme(palette, dark)` and `MaterialTheme.extras.isDark` (from TH1).

## Steps
1. Under the existing "Theme" row in the Appearance group add `SettingsNavRow(icon = Icons.Rounded.ColorLens,
   title = stringResource(R.string.settings_color_palette), value = stringResource(cutoutSettings.colorPalette.labelRes),
   onClick = { showPaletteDialog = true })` (`showPaletteDialog` = `rememberSaveable`, like `showThemeDialog`).
   `Icons.Rounded.ColorLens` is in material-icons-extended, already a dependency.
2. `private val ColorPalette.labelRes` → `R.string.palette_earth_steel` / `R.string.palette_tobacco_bordeaux`.
3. `PaletteDialog(current, onSelected, onDismiss)` copied from `ThemeModeDialog`: one radio row per palette, ordered by
   `java.text.Collator.getInstance(<current app locale>)` on the label text (Manu's standing rule). Before the label, a
   row of three 12 dp circles: `primary`, `secondaryContainer`, `tertiary` of `robiaColorScheme(palette,
   MaterialTheme.extras.isDark)`, each with a 1 dp `outlineVariant` border. Content description of the row = the label.
4. On select: close the dialog, `scope.launch { app.settingsRepository.setColorPalette(palette) }`. No snackbar.
5. Strings: SPEC §1.2 table verbatim in one `<!-- TH2 -->` block per strings.xml (`&amp;`).

## Tests
- V-TH2-1 `PaletteLabelTest`: every `ColorPalette` maps to a distinct label resource (pure function extracted as
  `internal fun paletteLabelRes(p: ColorPalette): Int`).
- V-TH2-2 source-contract test: `SettingsScreen.kt` contains `setColorPalette(` and `settings_color_palette`, and the
  three strings.xml contain the three keys (equal key sets, no duplicates).
- V-TH2-3 ordering helper `internal fun sortedPalettes(labels: Map<ColorPalette,String>, collator: Collator)` tested with
  EN, ES and DE labels.

## Done when
V-TH2-1 … V-TH2-3 green; receipt includes a line per locale with the three strings.

## Do not
Change the Theme row or `ThemeModeDialog`; add a preview screen; touch any other Settings row.
