# Batch E1–E3: read-only findings

Paths are under `app/src/main/java/com/gusanitolabs/robia/` unless noted. Line numbers are from this snapshot. Nothing was built or run. Timing numbers are model estimates, labelled HYPOTHESIS.

## Premise check

- **E1:** Partly matches. Cancel exists, but only through system Back. There is no on-screen cancel or close control.
- **E2:** PREMISE DOES NOT MATCH. An ETA is already implemented (`ui/BatchEta.kt`, B02 in `docs/specs`). It is hidden for the first two completions, resets on navigation, and its first value is biased low.
- **E3:** Matches. There are two 60 s module waits per photo when the module is missing. The exact exception that triggers the second wait is HYPOTHESIS.

---

## E1: cancel and exit

### Controls (CERTAIN)

| Control | Location | Behaviour |
|---|---|---|
| System Back | `ui/BatchAddClothingScreen.kt:140` `BackHandler { requestCancel() }` | No drafts: cancels at once. Any drafts: opens the dialog. |
| Discard dialog | `ui/BatchAddClothingScreen.kt:143-160` | Title `batch_discard_title` ("Discard this batch?"), body `batch_discard_body`, confirm `discard_changes`, dismiss `keep_editing`. Confirm calls `onCancelBatch()`. |
| Top bar | `ui/RobiaApp.kt:1715-1744` | **No back or close icon.** The arrow needs `routeStack.size > 1` (1724). Batch start calls `replaceRoute` (1199), so the stack has size 1. Only the Settings gear is shown (1784-1794). |
| Retry / Discard per row | `BatchAddClothingScreen.kt:440-445` | Shown only when `isRetryableForBatch()` (686-687): Failed, Interrupted, or NeedsReview with `backgroundFallback`. |
| Row tap | `BatchAddClothingScreen.kt:231-235` | Opens edit only for Ready or NeedsReview (`isSelectable`, 654-655). |
| Save N items | `BatchAddClothingScreen.kt:268-281` | Enabled only when nothing is Queued or Processing and at least one item is accepted (`canSaveBatch`, 170, 680-681). |
| Remove a queued item | none | Queued and Processing rows have no controls. |

Cancel runs `RobiaApp.cancelBatchAdd()` (1214-1221): `coordinator.cancel()`, clear drafts, `replaceRoute(Browse)`. Nothing is saved and there is no undo.

### Behaviour at each stage

- **Picking:** Happens in `AddEditClothingScreen` (multi-select at `ui/AddEditClothingScreen.kt:1259`, wired at `RobiaApp.kt:2805`), then `startBatchAdd` (1191). No batch UI exists yet.
- **Processing (Queued/Processing rows):** Back opens the dialog. The Settings gear pushes Settings (`handleSettingsClick`, 1400-1401). Row tap pushes `BatchEditClothing` (1889-1890). Processing keeps running in all three cases (see below).
- **Review (terminal rows):** Same Back dialog. Partial save is allowed only once everything is terminal. After a save, `batch_save_result` reports how many remain. When none remain, the app goes to Browse (1912).
- **Saving:** No separate state. `onSaveBatch` (1894-1913) is synchronous and removes saved drafts.

### Processing after leaving (CERTAIN unless noted)

- The job runs in `rememberCoroutineScope()` of `RobiaApp` (895-897), not the screen. It survives Settings and BatchEdit.
- There is no foreground service or WorkManager for the batch.
- Drafts live in `remember { mutableStateListOf }` (888-893), not `rememberSaveable`. `MainActivity` has no `configChanges` and no `screenOrientation` lock in the manifest. So process death or rotation drops the whole batch with no recovery. The rotation outcome follows Android's default recreation behaviour (HYPOTHESIS, not run).

### Processed drafts and temp files (CERTAIN)

- Drafts are in memory only. Cancel clears them (1216).
- `ClothingImageStore.writeProcessedBitmap` writes `subject-<uuid>.png`. `cropTransparentPixels` writes `cropped-subject-<uuid>.png` (`media/ClothingImageStore.kt:215-240`, 310-313). Both go to `getExternalFilesDir(PICTURES)/robia_clothing_images`.
- The batch path never deletes these files. The only `.delete()` calls are `ui/PhotoEditorScreen.kt:958` and `media/ClothingImageStore.kt:116`, neither in the batch path.
- The intermediate `subject-*.png` is left behind after the crop step (`BatchAddClothingScreen.kt:568-570`).
- Result: every cancelled or discarded item leaves its files on disk.

### States of the batch screen

- **Empty:** `batch_helper_empty` (214-222). Practically unreachable: `startBatchAdd` is only called with more than one photo, and the perf-fixture path (RobiaApp 867-869) is test-only.
- **Processing:** Queued rows at alpha 0.62 with label "Queued". Processing rows show a sweep overlay, label "Processing", or `background_removal_preparing` ("Preparing") while the module gate waits (`BatchAddClothingScreen.kt:380, 498-535`). Save disabled.
- **Review:** NeedsReview ("Needs review" plus the fallback message), Ready, Failed (red error text with Retry and Discard), Interrupted (Retry and Discard), and "Original photo accepted".
- **Footer:** attention line (`batchAttentionLine`, 252-260), save status message, Save button.
- **Sub-state:** `BatchEditClothing` (`RobiaApp.kt:2819-2839`). Dialog state is `showDiscardDialog`.

### Fix touch points for E1

- `BatchAddClothingScreen.kt:118-160`: add a visible cancel or close control; optionally say in the dialog that processing stops.
- `RobiaApp.kt:1715-1744`: add a close icon for `BatchAddClothing` that routes into `requestCancel`. The existing branch at 1727-1728 is effectively unreachable, because this route is only entered through `replaceRoute`.
- `RobiaApp.kt:1554-1566`: duplicate dialog. Keep one.
- `RobiaApp.kt:1214-1221`: add file cleanup. Cancel the job first (1215) and make sure a cancelled cutout cannot write after the delete.

---

## E2: ETA

### Where and when it renders (CERTAIN)

- Estimator: `ui/BatchEta.kt:25-47`. `estimate()` returns null while `completionTimes.size < 2` (34). It takes the mean of the last 4 gaps (35-37), multiplies by remaining, caps the rise at 1.2× per call (42), and lets it fall freely.
- Formatting: `batchEtaFor` (49-53). Under 10 s is AlmostDone. Under 90 s is seconds, rounded up to 10. Otherwise minutes.
- Composition: `BatchAddClothingScreen.kt:180-187`. The estimator is `remember`ed (180) and `countedProcessed` starts at the current `processedCount` (181). Completions before the screen opens are never counted. Timestamps are `System.currentTimeMillis()` at LaunchedEffect time, not at publish time.
- Render: `BatchProgressHeader` is LazyColumn item 0 (206-213, 288-322). The ETA sits beside the bar at 313-319 and is drawn by `BatchEtaText` (349-361, maxLines 1).
- Strings: `values/strings.xml:706-708` ("About %1$d s left", "About %1$d min left", "Almost done"). `values-es` (706-708) and `values-de` (706-708) are also translated.
- Hidden when remaining is 0 (186).

### Why it may never be seen (ranked)

1. **Not sticky (CERTAIN):** It is item 0 of the LazyColumn. Scrolling down to the review rows takes it off screen.
2. **Reset on navigation (CERTAIN):** Opening Settings or BatchEdit disposes the composable. `remember` recreates the estimator and counter, so the ETA goes blank until two new completions arrive.
3. **Needs two completions (HYPOTHESIS on timing):** See the table below.
4. **Module waits stall completions (HYPOTHESIS, likely in the default setup):** Each stall inflates the gap window for four completions. See E3.

### Timing (from code)

Symbols: `t` = per-photo cutout time, `s` = suggest time (`suggest_ms`), `R` = remaining photos, `P` = parallel cutouts. P is 2 for Balanced with ML Kit, and 1 for precise or MemorySaver (`CutoutOptions.kt:35-36`, `RobiaApplication.kt:207`, clamp at `BatchProcessingCoordinator.kt:74`).

| Mode | Completion times | First ETA | Value shown |
|---|---|---|---|
| P=2 (Balanced, ML Kit) | C1 = t+s, C2 = t+2s | About t+2s, i.e. 20–60 s after start, plus any module wait | Gap1 = s, so about s·R. Far too low: with t=40, s=2, R=58 it reads about 2 min against a steady ~19 min. Then it climbs at most 20% per completion. |
| P=1 (precise or MemorySaver) | C1 = t+s, C2 = 2t+s | 40–120 s | About t·R, which is roughly right |

Logcat tag `RobiaBatch` (`batch_item … cutout_ms, suggest_ms, wait_ms`, `BatchProcessingCoordinator.kt:121`) gives the real values of `s` and `t`.

### Minimal change (proposal)

1. Stamp completions in the publish path (`RobiaApp.updateBatchDraft`, 1156-1159, when status becomes terminal), not in composition. Hoist the estimator next to the batch state (888-902). Create or reset it in `startBatchAdd` (1191-1201) and `cancelBatchAdd` (1214-1221).
2. Seed the estimator with the batch start time. The first gap becomes start to C1 (t+s), so the ETA appears at the first completion. For P=2 that is an overestimate which then falls, with no upward jump.
3. Call `estimate()` once per completion, not on each recomposition. It mutates `shownMs`, so per-recomposition calls would ratchet the cap up.
4. Optional: move `BatchProgressHeader` out of the LazyColumn so it is pinned. This is a layout change (B02 scope).

Risks:
- `BatchEtaTest.kt` encodes the current contract (null before 2 completions, mean of last 4 gaps). It must be rewritten.
- If the estimator is hoisted while stamping stays in composition, completions made while the screen was off-screen all get the same timestamp on return. That gives zero gaps and a false "Almost done". Stamping at publish avoids this.
- A module-wait stall stays in the gap window for four completions. Drops are free, so the display recovers.

---

## E3: engine and module wait

### Engine selection (CERTAIN)

- Default is `cutoutEngine = Automatic` (`core/model/ClothingModels.kt:129`). `usePrecise = packReady && Automatic` (`media/cutout/CutoutOptions.kt:24-25`). So a batch uses the precise engine whenever the cutout pack is ready, and ML Kit otherwise.
- `PhotoBackgroundRemover.removeBackground` (`media/PhotoBackgroundRemover.kt:16-21`) runs `cutoutFallbackChain` (`media/cutout/CutoutEngineRegistry.kt:44-57`):
  1. Precise, via `preciseResult` (27-32). It has a 30 s timeout (`CutoutEngineRegistry.kt:12`) that includes waiting for the precise gate (`PreciseCutoutEngine.kt:54-59`).
  2. ML Kit, via `mlKitResult` (34-47).
- Parallelism: `BatchParallelism.cutouts = batchCutouts(mode, cutoutOptions.usePrecise)` (`RobiaApplication.kt:207`). Suggestion is always serial (`BatchProcessingCoordinator.kt:108-120`).
- Mismatch: `usePrecise` ignores `preciseLatch.isTripped` (`CutoutEngineRegistry.kt:30`, `PhotoBackgroundRemover.kt:29`). After a latch trip the batch runs ML Kit at P=1 (impact HYPOTHESIS).

### Where the wait is (CERTAIN)

- `MlKitSubjectSegmentationBackgroundRemovalEngine.kt:53-56` passes `ensureModule = { gate.ensureAvailable(timeout = MODULE_WAIT_TIMEOUT) }`, where `MODULE_WAIT_TIMEOUT = 60.seconds` (117).
- The lambda discards the `GateResult`, so after a failed wait the code still calls `process`.
- `runWithModuleRetry` (around 152-165): `ensureModule()` at 157. If the first attempt fails with `MODEL_UNAVAILABLE`, it calls `ensureModule()` again at 162 and retries.
- `SegmentationModuleGate.ensureAvailable` (`media/SegmentationModuleGate.kt:76-100`): fast path `port.isAvailable()` (80). Otherwise `requestInstall()`, then poll every 2 s (`POLL_INTERVAL_MS`, 107) until the 60 s timeout (95). It keeps no state between calls, and concurrent calls each wait separately.
- Per photo with the module missing: about 60 s + 60 s ≈ 120 s, then fallback to the original. This needs Play Services present (`hasGooglePlayServices`, 36-40) and the second attempt failing with `MODEL_UNAVAILABLE` (HYPOTHESIS that ML Kit throws `UNAVAILABLE`; mapping at 122-133).
- With P=2 both photos wait while holding both permits. Effective throughput is about 60 s per photo, so a 60-photo batch could take about an hour (HYPOTHESIS on frequency).
- Warm-up: `RobiaApplication.kt:193-195` calls `ensureAvailable()` once at launch in the background.
- The "Preparing" label on each row (`BatchAddClothingScreen.kt:380, 498-535`) is visible to the user during these waits.

### Implementing "wait once per batch, max 60 s"

Recommended, at gate level:
- In `SegmentationModuleGate` (66-100), record a monotonic `waitStartedAt` on the first slow-path wait. Each `ensureAvailable` uses `remaining = 60 s − elapsed` as its timeout and returns Unavailable at once when that is zero or less. Keep the `isAvailable()` fast path first, so a module that installs mid-batch is used by later photos.
- Add a `startBatch()` reset, called from `RobiaApp.startBatchAdd` (1191-1201). Do not call it from `retryBatchDraft` (1203-1207), or Retry would re-grant the wait.
- Result: the second `ensureModule()` in `runWithModuleRetry` returns at once. The 60 s budget is shared by concurrent photos.

Alternatives: a per-batch token passed from the coordinator (more plumbing through the `BackgroundRemovalEngine` interface), or skipping the retry whenever the first wait failed.

Risks and coupling:
- After the budget is spent, every remaining photo gets its original. Each lands as NeedsReview (`BatchAddClothingScreen.kt:589, 621`) and needs explicit acceptance (663-665). That is the trade-off to accept.
- The gate is a process-wide singleton (`SegmentationModuleGates`, 112-119). The single-photo editor uses the same remover, so a spent budget also affects it until the next batch starts. The reset policy needs a decision.
- Cancelling during a wait is safe: `withTimeoutOrNull` and `delay` are cancellable, and `preparing` is reset in `finally`.
- The stall feeds into the E2 ETA (see above).

---

## RobiaApp.kt touch list

| Function or block | Lines | Relevance |
|---|---|---|
| Batch state declarations | 888-902 | E2 if the estimator is hoisted |
| Route helpers `pushRoute`, `replaceRoute`, `popRoute` | 1106-1118 | Background for E1 |
| `updateBatchDraft` | 1156-1159 | E2: stamp completions here |
| `processQueuedBatchDrafts` | 1161-1189 | Read-only for E1 and E3; no change needed |
| `startBatchAdd` | 1191-1201 | E3: budget reset. E2: estimator start or reset |
| `retryBatchDraft` | 1203-1207 | Must not reset the E3 budget |
| `discardBatchDraft` | 1209-1212 | None |
| `cancelBatchAdd` | 1214-1221 | E1: file cleanup, close path. E2: reset |
| BackHandler | 1542-1551 | Not enabled on the batch route; unchanged |
| Discard dialog | 1554-1566 | E1: duplicate of the screen dialog |
| Top bar navigation | 1715-1744 (batch branch 1727-1728) | E1: close icon |
| Bottom bar exclusions | 1805-1814 | None |
| Batch callbacks | 1887-1925 | E1 only if adding a partial-save path |
| NavHost batch branches | 2807-2818, 2819-2839, 2840-2851 | E2 if passing the estimator as a parameter |

Hot-file coupling: E1 and E2 both touch `startBatchAdd` and `cancelBatchAdd`, and E2 and E3 both touch `startBatchAdd`. The planner should sequence them.

Other files for the planner: `ui/BatchAddClothingScreen.kt` (118-160, 180-187, 206-213, 288-322, 349-361, 440-445), `ui/BatchEta.kt`, `ui/BatchProcessingCoordinator.kt`, `media/SegmentationModuleGate.kt` (76-100), `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt` (53-56, 117, 152-165), `RobiaApplication.kt` (193-207), and the tests `BatchEtaTest.kt` and `BatchProcessingCoordinatorTest.kt`.
