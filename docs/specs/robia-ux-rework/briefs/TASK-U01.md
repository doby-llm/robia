# TASK-U01 — Motion primitives + Browse layout setting

## Goal
Manu says the app feels flat (R14). Later tasks need ONE shared set of subtle effects (press scale, AI shimmer, pop-in, one-shot sparkle burst) so screens look consistent and respect the system "remove animations" switch. Manu rejected haptics in revision 2: do not add any vibration. This task also adds the stored setting `browseLayout` that U02 and U05 use.

## Read first
TS§1; UX-DEC-007, UX-DEC-013; `core/designsystem/Theme.kt`; `core/model/ClothingModels.kt` `RobiaSettings` (~line 106); `data/SettingsRepository.kt` (interface, `DataStoreSettingsRepository.settings` mapping ~lines 50-59, how `developerModeUnlocked` is stored); test fakes `grep -rn ": SettingsRepository" app/src/test`.

## Steps
1. `core/designsystem/Motion.kt`: `LocalReducedMotion` (`staticCompositionLocalOf { false }`); `Modifier.pressScale(interactionSource, pressedScale = 0.97f)` with `collectIsPressedAsState` + `animateFloatAsState(spring(0.8f, Spring.StiffnessMediumLow))` + `graphicsLayer`; `rememberAiShimmerBrush(active)` (colours `#332421` → `#5A3A2C` → `#332421` with a light band `Color(0x59FFDCBE)`; `rememberInfiniteTransition`; idle = one pass of 2600 ms then 3 s pause, active = 1400 ms continuous; static brush when reduced motion); `Modifier.aiSurface(active, shape = CircleShape)`; `PopIn(visible, delayMillis, content)` (`AnimatedVisibility(enter = scaleIn(initialScale = 0.4f, animationSpec = keyframes { durationMillis = 300; 1.12f at 200 }) + fadeIn())`); `SparkleBurst(modifier, count = 7, seed = 0)`: draws `count` "✦" glyphs on fixed angles (`360 / count × i + seed × 13`) moving from radius 24 dp to 56 dp and fading 1 → 0 over 900 ms, once (`Animatable`, no loop); nothing when reduced motion. Put the geometry in a pure `internal fun sparklePositions(count: Int, seed: Int, progress: Float): List<Pair<Float, Float>>` (dp offsets).
2. `Theme.kt`: `RobiaTheme` provides `LocalReducedMotion` = `isReducedMotion(Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))`, read once with `remember`; pure `internal fun isReducedMotion(scale: Float): Boolean = scale == 0f`.
3. `ClothingModels.kt`: `enum class BrowseLayout { PhotosOnly, PhotosWithDetails }`; `RobiaSettings.browseLayout: BrowseLayout = BrowseLayout.PhotosOnly`.
4. `SettingsRepository.kt`: extract the `settings` flow mapping into `internal fun Preferences.toRobiaSettings(): RobiaSettings` (move-only; the flow becomes `dataStore.data.map { it.toRobiaSettings() }`); add key `stringPreferencesKey("browse_layout")` read as `BrowseLayout.entries.firstOrNull { it.name == value } ?: BrowseLayout.PhotosOnly`; `suspend fun setBrowseLayout(layout: BrowseLayout)` on the interface, `DataStoreSettingsRepository` and every test fake.

## Tests
`MotionTest` (`isReducedMotion`; `sparklePositions` deterministic for a seed, radii within 24–56 dp) and `SettingsMappingTest` with `mutablePreferencesOf()` against `toRobiaSettings()` (V-U01-1, V-U01-2).

## Done when
V-U01-1, V-U01-2, CI green.

## Do not
Use the primitives in any screen; add haptics or a haptics setting; bump the Compose BOM; add libraries; touch `ui/RobiaApp.kt`.
