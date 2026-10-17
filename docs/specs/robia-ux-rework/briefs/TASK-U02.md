# TASK-U02 — Browse card rework

## Goal
Browse cards have different heights, a blank first line, a redundant "Synced" text, clipped chips ("Dressed-u") and a white gap at the top corners (R9, R10, R13). Root causes (certain, `artifacts/inv-r2-a.md` + UX-DEC-001): new garments have `name = ""` but the card always renders `Text(item.name)`; `subtitle = notes.ifBlank { sync label }`; the tag Row is always emitted with unbounded chips (`TonalTag` has no ellipsis); the Card uses 12 dp while the photo clips 16 dp. Make every card uniform per mockup `browse.html` variant A.

## Read first
TS§2; `ui/BrowseGrid.kt` (after U00): `GarmentGridCard`, `GarmentCloudStatusBadge`, `garmentCloudStatusIcon/Tint`, `GarmentPhotoPlaceholder`, `TonalTag`, `toUiWardrobeItems`, `BrowseWardrobeScreen` (grid at the `LazyVerticalGrid` call); `core/designsystem/Motion.kt` (`pressScale`); sync state enum used by `cloud` in `toUiWardrobeItems` (follow `resolveForConnection`).

## Steps
1. New `ui/BrowseCardText.kt` with pure functions:
   - `internal fun cardTitle(name: String, tags: List<UiTag>, untitled: String): String` → `name.trim()` if not blank, else first tag with `categoryId == "category"` label, else `untitled`.
   - `internal fun cardMeta(tags: List<UiTag>, collator: Collator): String` → occasion labels sorted with collator, then season labels sorted, joined `" · "`.
   - `internal fun badgeFor(status: GarmentSyncStatus): CardBadge?` (`core/model/SyncModels.kt:31`; input = `item.syncStatus` AFTER `resolveForConnection`), `data class CardBadge(val icon: ImageVector, val labelRes: Int, val isError: Boolean)`. Exact table:
     | status | badge |
     |---|---|
     | Synced | null |
     | LocalOnly | null (Drive not set up: nothing to report on every card) |
     | Dirty, Queued, FailedRetryable | CloudUpload, `badge_waiting` "Waiting", not error |
     | Running | CloudUpload, `badge_uploading` "Uploading", not error |
     | NeedsUserAction | Error, `badge_needs_attention` "Needs attention", error |
     | AuthBlocked | CloudOff, `badge_sign_in` "Sign in", error |
     New strings in all locales (ES: "En espera", "Subiendo", "Requiere atención", "Inicia sesión"; DE: "Wartet", "Wird hochgeladen", "Aktion nötig", "Anmelden").
   - Anti-blink (standing preference): once a badge becomes visible it stays at least 1200 ms (`LaunchedEffect` holding the last non-null badge until the minimum time passed), then fades out 200 ms.
2. `GarmentGridCard`: `Card(shape = MaterialTheme.shapes.large, colors = surfaceContainerLowest, elevation 1.dp)`; selection border with the same shape; `Modifier.pressScale(interactionSource)` and pass the same `interactionSource` to `combinedClickable`.
   Inside: `Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f)) { GarmentPhotoPlaceholder(item, Modifier.matchParentSize(), clip = false); favourite; badge (only if badgeFor != null, bottom-end, pill with icon 14 dp + label labelSmall); selection check }`.
   Footer: `Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp))` with line 1 `Row(verticalAlignment = CenterVertically) { Text(cardTitle(...), titleSmall SemiBold, maxLines 1, Ellipsis, Modifier.weight(1f)); ColorDots }` and line 2 `Text(cardMeta(...).ifEmpty { " " }, bodySmall, onSurfaceVariant, maxLines 1, Ellipsis)` — the `" "` keeps the height constant.
   `ColorDots`: up to 2 circles 9 dp with 1 dp `Color.Black.copy(alpha = .12f)` border, primary then secondary, using the existing swatch colour helpers.
3. `GarmentPhotoPlaceholder(…, clip: Boolean = true)`: apply `.clip(shapes.large)` only when `clip`.
4. `toUiWardrobeItems`: `subtitle` = restore-attention message when needed, else `item.notes` (no sync fallback). Restore attention also maps to a badge (error style) — if a badge state for it already exists keep it.
5. Remove `TonalTag` if now unused (grep `ui/`).
6. `LazyVerticalGrid`: spacing 12 dp both directions; bottom content padding = existing nav inset + 16 dp (do not hard-code the nav height if a value exists).

7. `RegressionSourceContractTest`: the test around line 766-779 asserts `text = item.name` and `text = item.subtitle` (the old card). Replace those two assertions with `cardTitle(` and `cardMeta(` being used inside `GarmentGridCard`; keep the other assertions of that test. Keep `GarmentCloudStatusBadge` declared directly after `GarmentGridCard` and `GRID_THUMBNAIL_MAX_EDGE_PX` used in the card (test ~960).

## Tests
`BrowseCardTextTest`: V-U02-1 cases + `badgeFor` over `GarmentSyncStatus.entries` (V-U02-2).

## Done when
V-U02-1..4; receipt lists every string reused/added and a screenshot is NOT required (no device).

## Do not
Touch the filter bar, search, `AdvancedFiltersScreen`, the detail screen, or sync code. Do not change what counts as a "synced" state.
