# TASK-U01 — Motion and haptics primitives

## Goal
Manu says the app feels flat (R14). Later tasks need ONE shared set of subtle effects (press scale, haptics, AI shimmer, pop-in) so screens look consistent and can be switched off. This task only creates them plus a "Haptic feedback" setting value.

## Read first
TS§1; UX-DEC-007; `core/designsystem/Theme.kt`; `core/model/ClothingModels.kt` `RobiaSettings` (line ~106); `data/SettingsRepository.kt` (how `developerModeUnlocked` is stored); `ui/RobiaApp.kt` line with `RobiaTheme {` (~448).

## Steps
1. `core/designsystem/Haptics.kt`: `enum class RobiaHaptic { Confirm, Reject, Tick }`; `internal fun hapticConstantFor(kind: RobiaHaptic, sdkInt: Int): Int` per TS§1; `class RobiaHaptics(view: View, enabled: () -> Boolean) { fun perform(kind) { if (enabled()) view.performHapticFeedback(hapticConstantFor(kind, Build.VERSION.SDK_INT)) } }`; `val LocalRobiaHaptics = staticCompositionLocalOf<RobiaHaptics?> { null }` and `@Composable fun rememberRobiaHaptics(): (RobiaHaptic) -> Unit` returning a no-op when null.
2. `core/designsystem/Motion.kt`: `LocalReducedMotion` (read `Settings.Global.getFloat(contentResolver, ANIMATOR_DURATION_SCALE, 1f) == 0f` once in the provider); `Modifier.pressScale(interactionSource, pressedScale = 0.97f)` using `collectIsPressedAsState` + `animateFloatAsState(spring(0.8f, Spring.StiffnessMediumLow))` + `graphicsLayer { scaleX = s; scaleY = s }`; `rememberAiShimmerBrush(active)` (colours: primaryContainer `#332421` → `#5A3A2C` → `#332421` with a translating light band `rgba(255,220,190,.35)`; `rememberInfiniteTransition`; period 2600 ms with 3 s pause when idle, 1400 ms when active; static brush if reduced motion); `Modifier.aiSurface(active, shape)`; `PopIn(visible, delayMillis, content)` using `AnimatedVisibility(enter = scaleIn(initialScale = 0.4f, animationSpec = keyframes { durationMillis = 300; 1.12f at 200 }) + fadeIn())`.
3. `Theme.kt`: `RobiaTheme(hapticsEnabled: Boolean = true, content)` provides `LocalReducedMotion` and `LocalRobiaHaptics` (`RobiaHaptics(LocalView.current) { hapticsEnabled }`).
4. `RobiaSettings.hapticsEnabled: Boolean = true`. In `data/SettingsRepository.kt` first extract the inline mapping of the `settings` flow (`dataStore.data.map { preferences -> ... }`, ~lines 50-59) into `internal fun Preferences.toRobiaSettings(): RobiaSettings` (move-only; the flow becomes `dataStore.data.map { it.toRobiaSettings() }`), then add key `booleanPreferencesKey("haptics_enabled")` read as `?: true`, and `suspend fun setHapticsEnabled(enabled: Boolean)` in the interface, `DataStoreSettingsRepository` and every test fake (`grep -rn ": SettingsRepository" app/src`).
5. `ui/RobiaApp.kt`: at the `RobiaTheme {` root pass `hapticsEnabled = settings.hapticsEnabled` (find where settings are collected; if settings are not available at that point, provide the value one level down where they are — one-line change, explain in receipt).

## Tests
`HapticsTest` (V-U01-1 cases); `SettingsMappingTest` using `mutablePreferencesOf()` (androidx.datastore.preferences.core, already on the classpath) against `toRobiaSettings()` (V-U01-2).

## Done when
V-U01-1, V-U01-2, CI green.

## Do not
Use the primitives in any screen; bump Compose BOM; add libraries.
