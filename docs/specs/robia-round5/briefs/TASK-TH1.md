# TASK-TH1 — Palette model, Tobacco & Bordeaux tokens, theme wiring

## Goal
Robia has one colour set (Earth & Steel, `core/designsystem/Color.kt`) in light and dark. Manu wants a palette choice
with **Tobacco & Bordeaux · Light / Airy as the default** and Earth & Steel still available. This task adds the model,
the persisted setting, the new tokens and the wiring; TH2 adds the Settings row. After this task, with no UI, every
install (new or updated) shows Tobacco & Bordeaux because the absent setting reads as the new default.

## Read first
- SPEC §1.1 (REQ-TH-1..6b, 8); `artifacts/tobacco-bordeaux-airy.md` (every hex; light `onTertiaryContainer` is the
  corrected **#FFD9DE**, never #8E4E58).
- `core/designsystem/Color.kt`, `Theme.kt` (`RobiaLightColorScheme`, `RobiaDarkColorScheme`, `RobiaExtras`,
  `RobiaLightExtras`, `RobiaDarkExtras`, `RobiaTheme`, `resolveDarkTheme`).
- `core/model/ClothingModels.kt:218-230` (`ThemeMode` pattern to copy), `:256` (`RobiaSettings.themeMode`).
- `data/SettingsRepository.kt:81-84` (interface defaults for fakes), `:176-182` (DataStore flow + setter), `:297`, `:386`.
- `MainActivity.kt:68-104` (early theme read, window background at :92, `RobiaApp(... themeMode = themeMode)`).
- `ui/RobiaApp.kt:390` (`themeMode` parameter) and `:463` (`RobiaTheme(` call).
- Tests: `app/src/test/.../core/designsystem/RobiaColorSchemeTest.kt`, `ReviewChangeColorsTest.kt`,
  `app/src/test/.../data/SettingsMappingTest.kt`.

## Steps
1. `ClothingModels.kt`, next to `ThemeMode`:
   ```kotlin
   /** Settings > Appearance > Colour palette (round 5). Device-local: not part of any snapshot. */
   enum class ColorPalette(val storageValue: String) {
       EarthSteel("earth_steel"),
       TobaccoBordeaux("tobacco_bordeaux"),
       ;
       companion object {
           /** Absent or unknown stored values read as Tobacco & Bordeaux, the default (R5-DEC-D04). */
           fun fromStorage(value: String?): ColorPalette = entries.firstOrNull { it.storageValue == value } ?: TobaccoBordeaux
       }
   }
   ```
   Add `val colorPalette: ColorPalette = ColorPalette.TobaccoBordeaux` to `RobiaSettings` after `themeMode`.
2. `SettingsRepository.kt`: interface gets `val colorPalette: Flow<ColorPalette> get() = flowOf(ColorPalette.TobaccoBordeaux)`
   and `suspend fun setColorPalette(palette: ColorPalette) {}` (same doc style as the theme ones). `DataStoreSettingsRepository`
   overrides both with key `private val colorPaletteKey = stringPreferencesKey("color_palette")`, maps it in the
   settings builder next to `themeMode = ...` (`colorPalette = ColorPalette.fromStorage(preferences[colorPaletteKey])`).
   Not added to any sync/export/snapshot code.
3. New `core/designsystem/TobaccoBordeauxColor.kt`: `internal val Tobacco<Role>` / `TobaccoDark<Role>` constants for every
   row of the token table (same naming scheme as `Color.kt`), then `internal val TobaccoLightColorScheme = lightColorScheme(...)`,
   `TobaccoDarkColorScheme = darkColorScheme(...)` setting **every** role the Earth & Steel schemes set, and
   `TobaccoLightExtras` / `TobaccoDarkExtras` (`addColors`/`removeColors`/photo wells = the same values as Earth & Steel;
   `sparkOnPrimary` light #FFD9DE, dark #6A303A).
4. `Theme.kt`:
   - add `val wellScheme: ColorScheme` to `RobiaExtras` (KDoc: "light scheme of the active palette; ink on the
     always-light photo well"). Earth & Steel extras pass `RobiaLightColorScheme` (light and dark); Tobacco extras pass
     `TobaccoLightColorScheme` (light and dark).
   - `fun robiaColorScheme(palette: ColorPalette, dark: Boolean): ColorScheme` and
     `fun robiaExtras(palette: ColorPalette, dark: Boolean): RobiaExtras` (pure, `when` over both enums).
   - `RobiaTheme(darkTheme: Boolean = false, palette: ColorPalette = ColorPalette.TobaccoBordeaux, content)` uses them.
     Keep the old names `RobiaLightColorScheme` etc. (tests pin them).
5. `MainActivity.kt`: read `earlyPalette` exactly like `earlyThemeMode` (same timeout constant; fallback
   `ColorPalette.TobaccoBordeaux`); in `setContent` collect `settingsRepository.colorPalette` with
   `initial = earlyPalette`; `DisposableEffect(dark, palette)`; window background =
   `robiaColorScheme(palette, dark).background.toArgb()`; pass `colorPalette = palette` to `RobiaApp`. Remove the now
   unused `RobiaBackground`/`RobiaDarkBackground` imports only if unused.
6. `RobiaApp.kt`: new parameter `colorPalette: ColorPalette = ColorPalette.TobaccoBordeaux` after `themeMode`;
   `RobiaTheme(darkTheme = ..., palette = colorPalette)`. Nothing else in this file.
7. Photo-well sites (REQ-TH-5): replace `RobiaLightColorScheme.<role>` with `MaterialTheme.extras.wellScheme.<role>` at
   `AddEditClothingScreen.kt:1700,1706,1714`, `BatchAddClothingScreen.kt:528`, `BrowseGrid.kt:821,822,836`,
   `DuplicateReviewScreen.kt:288`; drop the unused import. Keep the existing comments.
8. `PhotoEditorScreen.kt`: add `// theme-fixed: drawn over the photo, same in every palette` at the `RobiaPrimary`/
   `RobiaError` overlay (:626) and the `RobiaSurfaceContainerHigh` swatch (:1173). No code change.

## Tests (new file `app/src/test/.../core/designsystem/ColorPaletteTest.kt`; extend `SettingsMappingTest`)
- V-TH1-1 `tobaccoLightAndDarkMatchTheTokenTable`: every role of `TobaccoLightColorScheme`/`TobaccoDarkColorScheme`
  equals the table (copy the table as a `mapOf` like `RobiaColorSchemeTest.LIGHT_HEX`; reuse its `roles()` helper by copy).
- V-TH1-2 `tobaccoTextPairsMeetWcag`: the 12 text pairs of the artifact ≥ 4.5 in both modes; spark/primary ≥ 3;
  outline/surface ≥ 3 in dark (light outline 4.31 also ≥ 3).
- V-TH1-3 `paletteFromStorage`: null, "", "x" → TobaccoBordeaux; each `storageValue` round-trips.
- V-TH1-4 `robiaColorSchemeSelectsPaletteAndMode`: 4 combinations return the 4 schemes; Earth & Steel light is still
  `RobiaLightColorScheme` (existing `lightSchemeKeepsEveryTodaysHex` stays green, unchanged).
- V-TH1-5 `wellSchemeIsTheLightSchemeOfThePalette` in both modes; chip colours of Tobacco extras equal Earth & Steel's.
- V-TH1-6 source-contract: no file under `ui/` imports `RobiaLightColorScheme` any more (read the 4 files as text).

## Done when
V-TH1-1 … V-TH1-6 green in CI; receipt lists every replaced site.

## Do not
Add Settings UI or strings (TH2); change Earth & Steel hexes; touch exports, `GarmentSwatchBackground.kt`, styles.xml,
colors.xml, the manifest, sync or snapshot code.
