# TASK-DARK1 — Dark mode: theme, setting, system bars, shared components

## Goal
F14 (REQ-FB-14, R3-DEC-044, R3-DEC-045): Manu asked for a dark mode as a setting, default Light. This task builds the infrastructure: the preference, the Settings row, the dark colour scheme, the system-bar handling and the shared pieces every screen uses. DARK2 to DARK4 convert the screens; the garment review redesign (REV) is built on these tokens.

## Read first
`R3-DEC-045` (the investigation and every decision below); `core/designsystem/Theme.kt` and `Color.kt` (there is no `ui/theme/`); `MainActivity.kt` `onCreate`; `res/values/styles.xml` (light parent, `windowLightStatusBar`); `ui/RobiaApp.kt` `RobiaApp(` (~358-423, `RobiaTheme {` ~423, preview ~3463); `ui/SettingsScreen.kt` (`BrowseLayoutDialog` ~733 as the pattern, the General group ~187); `data/SettingsRepository.kt` (keys ~340-370, `exportOptions()` ~287, interface defaults like `setExportOptions` ~78); `ui/GarmentSwatchBackground.kt`; `docs/ux/earth_steel/DESIGN.md`; the dark palette `DARK` in `mockups/review-redesign/build.py` (design worktree of this round; the values are copied below).

## Files
`core/designsystem/Color.kt`; `core/designsystem/Theme.kt`; `core/model/ClothingModels.kt` (`ThemeMode`, `RobiaSettings.themeMode` only); `data/SettingsRepository.kt`; `MainActivity.kt`; `ui/RobiaApp.kt` (the `RobiaTheme` call, one `themeMode` parameter, the marker on the `swatchColor` fallback only); `ui/SettingsScreen.kt` (Appearance group and dialog only); `ui/GarmentSwatchBackground.kt`; `res/values/styles.xml`; `res/values/colors.xml`; the three `strings.xml`; tests `core/designsystem/RobiaColorSchemeTest.kt` (new), `data/SettingsMappingTest.kt`, `ui/GarmentSwatchBackgroundTest.kt`, `ui/ThemeSourceScan.kt` (new), `ui/DarkThemeSharedScanTest.kt` (new), `media/ExportsIgnoreThemeTest.kt` (new), all under `app/src/test/java/com/gusanitolabs/robia/`.

## Steps
1. **Setting.** In `ClothingModels.kt`: `enum class ThemeMode(val storageValue: String) { Light("light"), Dark("dark"), System("system") }` with `fromStorage(value)` (null or unknown → Light) and `RobiaSettings.themeMode: ThemeMode = ThemeMode.Light`. In `SettingsRepository`: key `theme_mode`; interface members with defaults so test fakes compile: `val themeMode: Flow<ThemeMode> get() = flowOf(ThemeMode.Light)` and `suspend fun setThemeMode(mode: ThemeMode) {}`; the DataStore class overrides both (the flow maps `dataStore.data` only, no migration work) and `toRobiaSettings()` fills `themeMode`. The setting is not part of any sync snapshot.
2. **Settings UI.** A new group `settings_group_appearance` between Backup and General with one `SettingsNavRow`: icon (a theme/palette icon from the icon set already in use), title `settings_theme`, value = the label of the stored mode; tap opens an `AlertDialog` of three radio rows in the pattern of `BrowseLayoutDialog` (order Light, Dark, Follow system). Read `app.settingsRepository.settings` and write `setThemeMode` the way the cutout rows already do, so no parameter threading through `RobiaApp`. Strings (EN; ES/DE yourself): `settings_group_appearance` "Appearance", `settings_theme` "Theme", `settings_theme_light` "Light", `settings_theme_dark` "Dark", `settings_theme_system` "Follow system". Keep every assertion of `RegressionSourceContractTest.settings_isDedicatedRouteAndDiagnosticsStayDeveloperGated` true.
3. **Scheme.** `Color.kt` keeps the light tokens byte for byte and adds dark tokens. `Theme.kt`: `fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean`; `RobiaLightColorScheme` becomes `internal` for the test; `RobiaDarkColorScheme = darkColorScheme(...)` with every role the light scheme sets; `RobiaTheme(darkTheme: Boolean = false, content)` picks the scheme and provides `LocalRobiaExtras` (`isDark`, `photoWellTop = Color(0xFFF3F3F4)`, `photoWellBottom = Color(0xFFD1CFCF)`; both wells identical in both themes, they are the backdrop behind garment cutouts). `MaterialTheme.extras` accessor in the style of `LocalReducedMotion`. Dark roles (starting values from the mockups' `DARK` and `DESIGN.md`; adjust a value only if V-DARK1-2 fails and list changes in the receipt):

   | Role | Dark | Role | Dark |
   |---|---|---|---|
   | background, surface | `#171312` | primary | `#EAD7D1` |
   | surfaceDim | `#120E0D` | onPrimary | `#2A1C19` |
   | surfaceBright, surfaceContainerHighest, surfaceVariant | `#3A3330` | primaryContainer | `#54433F` |
   | surfaceContainerLowest | `#211C1A` | onPrimaryContainer | `#F7DDD8` |
   | surfaceContainerLow | `#1D1817` | inversePrimary | `#6D5A56` |
   | surfaceContainer | `#2B2421` | secondary | `#D7C3B0` |
   | surfaceContainerHigh | `#2E2826` | onSecondary | `#3A2E22` |
   | onBackground, onSurface | `#EDE0DC` | secondaryContainer | `#4A3C31` |
   | onSurfaceVariant | `#CDBFBB` | onSecondaryContainer | `#F4DFCC` |
   | outline | `#9C8D8A` | tertiary | `#FFB786` |
   | outlineVariant | `#544845` | onTertiary | `#4E2600` |
   | inverseSurface | `#EDE0DC` | tertiaryContainer | `#6D390F` |
   | inverseOnSurface | `#2A1C19` | onTertiaryContainer | `#FFDCC7` |
   | surfaceTint | `#DAC1BC` | error / onError | `#FFB4AB` / `#690005` |
   | scrim | `#000000` | errorContainer / onErrorContainer | `#93000A` / `#FFDAD6` |

   (`surfaceContainerLowest` lighter than `surfaceContainerLow` is deliberate: a card on the page is lighter than the page, as in the mockups.)
4. **MainActivity.** Before `super.onCreate`: read the stored mode once with a bounded blocking read (`runBlocking { withTimeoutOrNull(250) { repository.themeMode.first() } } ?: ThemeMode.Light`), set the dark window style when the resolved flag is dark (system flag from `resources.configuration.uiMode`), and call `enableEdgeToEdge` with the style for that flag. In `setContent`: `val themeMode by repository.themeMode.collectAsState(initial = early)`, `val dark = resolveDarkTheme(themeMode, isSystemInDarkTheme())`, `DisposableEffect(dark) { enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark }, navigationBarStyle = SystemBarStyle.auto(<library default light scrim>, <library default dark scrim>) { dark }); window.setBackgroundDrawable(ColorDrawable(<scheme background>)); onDispose {} }`, and pass `themeMode` to `RobiaApp`. Nothing else in the file changes (sync-auth code is SYNC-AUTH's).
5. **RobiaApp.** New parameter `themeMode: ThemeMode = ThemeMode.Light`; the `RobiaTheme {` call becomes `RobiaTheme(darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())) {`; the preview stays Light. The `Color(0xFFDADADA)` fallback of `swatchColor` gets a `// theme-fixed: garment colour data` marker.
6. **Resources.** `styles.xml`: keep `Theme.Robia` as it is; add `Theme.Robia.Dark` (parent `Theme.Robia`) with `android:windowBackground` `@color/robia_background_dark`, `android:windowLightStatusBar` false and `android:windowLightNavigationBar` false; `colors.xml`: `robia_background_dark` `#171312`. No `values-night` (the default is Light, a dark phone with the default setting must not flash dark; R3-DEC-045).
7. **Photo well.** `GarmentSwatchBackground.kt`: `rememberGarmentSwatchBrush` takes its top stop from the photo-well token instead of `surfaceContainerLow` (same `#F3F3F4`); the file gets one `// theme-fixed-file: garment colour data and photo backdrop stops` line. `garmentSwatchStops`/`garmentSwatchBrush` signatures stay.
8. **Scan helper and tests.** `ThemeSourceScan.kt` (test helper): given repo-relative paths, reads each source file and reports every `Color(0x`, `Color.White`, `Color.Black` (whole words, so `DisplayColorLabel.Black` is not a match) on a line without `// theme-fixed: <non-empty reason>`, skipping a file that has a `// theme-fixed-file: <reason>` line; `Color.Transparent` is ignored; it locates the repo root the way `RegressionSourceContractTest.source()` does. `DarkThemeSharedScanTest` runs it over `ui/RobiaApp.kt`, `ui/SettingsScreen.kt`, `ui/GarmentSwatchBackground.kt` and tests the helper itself on inline strings (missing marker fails, empty reason fails, marker passes, file marker passes). `RobiaColorSchemeTest`: pins every light token hex (copy of today's `Color.kt` values) and the dark contrast pairs of V-DARK1-2 with a small WCAG helper. `ExportsIgnoreThemeTest` per V-DARK1-5.

## Tests
V-DARK1-1 to V-DARK1-5 (CI), V-DARK1-6 (REV), V-DARK1-D (Manu).

## Done when
V-DARK1-1 to V-DARK1-6 pass, V-DARK1-D is ready for Manu; CI green. The receipt lists the final dark hex table and any value changed from the brief.

## Do not
Convert other screens (DARK2 to DARK4); add `values-night` or a splash library; sync the setting; change how Light looks; touch the sync code in `MainActivity.kt`; make exports read the theme.
