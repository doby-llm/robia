## Topic L: investigation (read-only, no changes made, nothing built)

Premise mismatches are marked **PREMISE DOES NOT MATCH**. Items marked *hypothesis* are not verified on a device.

### (1) Review card background

- **PREMISE DOES NOT MATCH:** Browse's gradient is not the card background. The card body is `surfaceContainerLowest` (`RobiaApp.kt:3615`, `GarmentGridCard`). The gradient is only the photo box, `GarmentPhotoPlaceholder` (`RobiaApp.kt:3846-3861`): a vertical brush from `surfaceContainerLow` to `swatch @ alpha 0.26` (`RobiaApp.kt:3857`).
- The photo uses `ContentScale.Fit` (`media/ImagePipeline.kt:371`, `:426`), so the gradient shows only in the letterbox margins.
- The colour input is coarse. Browse uses `UiWardrobeItem.primaryColor: DisplayColorLabel`, which is the stored `primaryDisplayLabel` (`RobiaApp.kt:5169`). That maps to 12 fixed swatches plus Unknown `#DADADA` (`RobiaApp.kt:5272-5285`). Only the top stop follows the theme.
- Review cards today: `TagReviewCandidateCard` (`TagReviewScreen.kt:565`, `Card` at `surfaceContainer`) with `TagReviewPhoto` (`:665`, `surfaceContainerHighest`, 132dp). `ColorReviewCandidateCard` (`ColorReviewScreen.kt:331`) with `ColorReviewPhoto`, same pattern. `ReviewScaffold.kt:144` is only the picker scroll fade, not a card.
- **PREMISE DOES NOT MATCH:** the review cards have no Discard button. Only Accept exists on each card.

**Minimal change**
1. Move `DisplayColorLabel.swatchColor()` (`RobiaApp.kt:5272`) and the gradient into a new `ui/GarmentSwatchBackground.kt`. Expose a pure `garmentSwatchBrush(swatch: Color)` and a thin `@Composable` wrapper. Use `remember(swatch)` so 35+ cards don't rebuild the brush.
2. Replace the background of `TagReviewPhoto` and `ColorReviewPhoto`, the photo area, not the whole card. This matches Browse exactly. The From/To pills and Accept button stay on opaque surfaces, so their contrast is unchanged. Whole-card gradient would need a new contrast check on the Accept button.
3. Colour source:
   - Tag review: `candidate.item.colorMetrics.primaryDisplayLabel`, the same stored value Browse uses.
   - Colour review: use the editable primary, `palette.colorForId(candidate.editablePrimaryColorId)` against `changeSet.afterPalette` (`ColorReviewScreen.kt:282`, `:339`). Derive the label with `ColorLabelResolver.fromRawValue(hex)`, as `ColorReviewScreen.kt:585` does. This matches what the strip shows and what Accept will save.
   - Edited colour: the new hex gives the new bucket.
   - Deleted colour: it is missing from `afterPalette`, so the primary is null and the swatch is Unknown. This matches the "no colour" strip.
   - No primary: Unknown, the same as Browse.
4. Light/dark: the top stop follows the theme; the swatch is fixed. There is no text on the photo area, so text contrast is not at risk. *Hypothesis:* in the no-photo branch the placeholder icon is tinted with the swatch (`RobiaApp.kt:3846-3900`). A white swatch (`#F8F9FA`) on a light gradient could be nearly invisible. Check on device.
5. Tests: `garmentSwatchLabel(hex)` as a JVM test (delegates to `ColorLabelResolver`), and `garmentSwatchBrush` stop values.

### (2) Description editor (`ManageTagsScreen.kt:579-603`)

- The counter is a `trailingIcon` Text (`:586`). The helper is a separate `Text` under the field (`:588-597`) with `maxLines = 1` and `Ellipsis`.
- **Truncation is certain by mechanism:** the EN helper (`values/strings.xml:547`) is about 68 characters at `labelMedium` in an AlertDialog column of roughly 250dp. It must wrap, and `maxLines = 1` cuts it.
- **Counter flush:** *hypothesis.* The trailing slot is only icon-inset and the Text has no padding. Not confirmed without rendering.
- **History:** E08 (`4c2d59f`, `ManageTagsScreen.kt:565-570`) used a `supportingText` Row with `SpaceBetween`, counter end-aligned. H03 (`e7ad44d`) moved the helper out to one line, which is where the ellipsis came from.
- **J03 change:** `f289d93` changed `padding(top = 4.dp)` to `10.dp` on the suggest-occasions Row (`:602`, shown only for `categoryId == "category"`). It is not in the description field, so the premise is only partly right. I did not find the "earlier blank gap" in history.

**Proposed change** (replaces `:588-597`):
```kotlin
supportingText = {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.tag_description_helper), modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(stringResource(R.string.tag_description_counter, description.length))
    }
},
```
- Drop `maxLines` so the helper wraps to two lines. Keep the `Row` unconditional, so no reserved-but-empty slot creates a gap.
- Verify on device that the counter's end inset is provided by the supporting-text slot. If not, add `padding(end = 4.dp)` to the counter.
- Shorten the copy only if two lines is rejected. No JVM test covers this layout; verify with screenshots in EN/ES/DE.

### (3) Cloud icon

- `CloudSyncStatusIcon` (`RobiaApp.kt:2254-2274`) has no `clickable`, so it is not tappable today. It only carries `contentDescription`.
- It is shown in the Browse top bar (`RobiaApp.kt:3496`) only when `showsBrowseIndicator` is true (`RobiaApp.kt:693-697`): Syncing, Waiting, Attention, Error.
- **PREMISE DOES NOT MATCH:** Pending shows no icon (`CloudSyncHeadline.Pending`, `RobiaApp.kt:667`). The icon Manu saw at "pending upload" was Syncing (pulsing CloudUpload) or Waiting (CloudQueue). Confirm which on device.
- There is no Snackbar host in the app. Use a `Popup` or `DropdownMenu` anchored to the icon. Outside-tap dismissal is non-modal.
- Data sync is a section of `SettingsScreen` (`RobiaApp.kt:3183`), not its own route. "Open Settings > Data sync" means `pushRoute(RobiaRoute.Settings)` (`onOpenSettings`, `RobiaApp.kt:1928`). Scrolling to the section is extra work.

**Design**
- New `ui/CloudStatusExplanation.kt`: `cloudStatusExplanation(headline, pendingCount): CloudStatusExplanation?`. Returns title and body string resource ids, pluralised, plus an optional `OpenDataSync` action.
  - Syncing: title, no action.
  - Waiting: title, plural body with `pendingCount`.
  - Attention: title plus the action.
  - Error: same copy as Attention. This is a product call.
  - Synced, Pending, Disabled, NotConfigured: null.
- Strings (new keys in EN, ES, DE):
  - Syncing: "Syncing your wardrobe with Drive" / "Sincronizando tu armario con Drive" / "Dein Kleiderschrank wird mit Drive synchronisiert".
  - Waiting: "%d change will upload when you are online" / "%d changes will upload when you are online" (plurals; ES and DE equivalents).
  - Attention: "Sync needs attention" / "La sincronización necesita atención" / "Synchronisierung braucht Aufmerksamkeit".
  - Action: reuse `data_sync` ("Data sync" / ES / DE) plus an "Open" prefix.
- Popup: auto-dismiss after about 4 s, paused while focused, closes on outside tap. Re-reads the indicator live. The icon gets `Role.Button`.
- Tests: JVM test on the mapping (headline to res, plural 1 and many, null cases, action presence), alongside `CloudSyncIndicatorTest`.

### (4) Smart suggestions timing

- **PREMISE DOES NOT MATCH:** `syncState.isProcessing` does not exist on `WardrobeSyncState` (`sync/WardrobeSyncGateway.kt:66-79`). The processing flag exists only inside the processor (`WardrobeSyncOutboxProcessor.kt:1358`). The UI-visible signals are `isRunningNow` (`RobiaApp.kt:639-641`) and `restoreProgress` (`WardrobeSyncGateway.kt:73`). There is no separate "importing" flag.
- **Root cause (certain from code):** `cloudAuthorizationInFlight` goes false as soon as the chooser result arrives (`MainActivity.kt:118`, `:165`, `:201`). Then the status becomes Connected and `ImportFullSnapshot` is enqueued (`MainActivity.kt:186`). The import run starts later (`WardrobeSyncOutboxProcessor.kt:255-275`). In that window every gate is open: flag false, no `restoreProgress`, not running. The model prompt appears there.
- **Second gap (certain):** `activeFirstRunPrompt` is only withdrawn when the prompt is no longer pending (`RobiaApp.kt:1050-1054`). When the gates close it stays open, so a restore that starts later does not close it.
- **Route (certain):** the first-run dialogs render at app level (`RobiaApp.kt:1613-1635`), so the model prompt can appear on any route today, not just Browse.
- Start route is Browse (`RobiaApp.kt:877-882`).

**Missing inputs to add**
1. `currentRoute == RobiaRoute.Browse` (`RobiaApp.kt:882`).
2. Busy: `isRunningNow`, `restoreProgress` not terminal (`CloudRestoreStatus`, `WardrobeSyncGateway.kt:242-249`), plus a new in-memory "import armed" flag set on chooser success and cleared on the terminal cycle or failure. The flag goes in `RobiaApplication.kt:56-62`, next to `cloudAuthorizationInFlight`.
3. Quiet period: 3 s after the last busy transition. Reuse the timestamp approach of `SYNC_INDICATOR_HOLD_MS` (~`RobiaApp.kt:705`). Keep the existing 1.5 s hold for the indicator only.
4. Browse-local and other dialogs, which today are missed by `anotherDialogOpen` (`RobiaApp.kt:1039`): `showBrowseDeleteDialog`, `showSmartPrompt` (`:3446`, `:3534`), `showBackupDeletionConfirmation` (`:932`), the pending colour and tag review offers (`pendingColorReviewChangeSet`, `pendingTagReview`, `tagReviewOfferInFlight`), and `mergeCombinePhase == Combining`.
5. System sheets and permission dialogs are not observable in Compose. *Hypothesis:* use an activity ON_RESUME gate.
6. Cloud setup flow: `cloudAuthorizationInFlight` covers the chooser. Failure and setup dialogs have no flag (*hypothesis*: add one).
7. Empty wardrobe with restore pending: `clothingItems.isEmpty() && connectionStatus == Connected && !driveFreshInstallRestoreAttempted` (`SettingsRepository.kt:56`, set at `:106`). Block until the attempt ends.

**Retry:** keep the `LaunchedEffect` keyed on the inputs. Compute `gateOpen` separately. When `gateOpen` goes false while the model prompt is active and unanswered, set `activeFirstRunPrompt = null`. The prompt stays pending and re-opens after 3 s of calm.

**Standing question:** yes, waiting forever is acceptable. Browse is the start route and the sparkle button (`RobiaApp.kt:3482`) remains a manual entry point. A user who only uses Manage never sees the prompt, and nothing is blocked.

**Files outside `ui/`:** not "none expected". Two are needed: `RobiaApplication.kt` (the import-armed flag) and `MainActivity.kt` (set on chooser success near `:186`, clear on the failure paths `:165` and `:201`).

**Test plan**
- Keep the six existing cases in `FirstRunPromptQueueTest.kt`.
- Add:
  - Case 7: model blocked on every non-Browse route.
  - Case 8: blocked during restore or import, including the armed window before `restoreProgress` exists.
  - Case 9: blocked by Browse-local dialogs, review offers, and merge Combining.
  - Case 10: quiet period. A busy-to-calm transition restarts the 3 s timer. Pure `quietEnoughSince(lastBusyAtMs, nowMs, quietMs = 3_000)`.
  - Case 11: an active model prompt is withdrawn when the gate closes and re-opens after calm, with no duplicate.
  - Case 12: empty wardrobe with restore pending blocks the model prompt. After the attempt flag is set and the wardrobe is non-empty, it is allowed.

**Minimal ordered fix list**
1. `ManageTagsScreen.kt:588-597`: move helper and counter into the `supportingText` Row. Screenshot-verify EN/ES/DE. No JVM test.
2. New `ui/GarmentSwatchBackground.kt`: extract the swatch and gradient from `RobiaApp.kt:3857` and `:5272`. Apply to `TagReviewPhoto` and `ColorReviewPhoto`, using the editable primary for colour review. Tests: `garmentSwatchLabel`, `garmentSwatchBrush`.
3. New `ui/CloudStatusExplanation.kt`: pure mapping plus strings in three locales. Make `CloudSyncStatusIcon` (`RobiaApp.kt:2254`) clickable with a `Popup` and 4 s auto-dismiss. Action goes to `pushRoute(RobiaRoute.Settings)`. Test the mapping.
4. `FirstRunPromptQueue.kt`: add the gate inputs and a pure `firstRunGateOpen(...)` (used for the model prompt only). Add tests 7-12.
5. `RobiaApp.kt:1036-1054`: wire the gate and withdraw the active model prompt when the gate closes.
6. `RobiaApplication.kt:56-62` and `MainActivity.kt:118, 165, 186, 201`: the import-armed flag. This is the only non-`ui/` change.
