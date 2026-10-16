## Topic J: merge proposal after restore, and offline status

I did not build or run anything, and I did not run the test suite. Everything below is from reading the code at `main` 5505ed6.

### Bottom line

**Certain (code path):** the proposal is created by a *forced* import cycle, not by the lineage check. Two forced cycles start on connect. The second one runs after the restore has filled the phone, sees `hasUserData() == true`, and takes `MergeWithRemote`. That path proposes whenever the remote has any user data, with no writer, revision, or clash check.

**Why the second cycle is queued:** on connect, `MainActivity.kt:176-186` sets the status to Connected and then calls `enqueue(ImportFullSnapshot)`. That `enqueue` runs `runCycle(forceImport = true)` inline (`WardrobeSyncOutboxProcessor.kt:243-267`). The Connected state also launches `restoreFreshInstallOnceIfNeeded()` (`:188-196`).

- `restoreFreshInstallOnceIfNeeded` checks "is the phone fresh?" at `:707-713`, **outside** the mutex.
- `runCycle` takes the mutex at `:732` and exports the local snapshot at `:788`, **inside** the mutex.
- So a forced run that waits behind the restore reads the restored data and proposes.

**Ruled out on the normal path:** lineage is recorded (`:1201` sets `lastKnownRemoteRevision` before any later step). The manifest revision is the snapshot revision (`GoogleDriveWardrobeRepository.kt:63-72`, `DriveWardrobeRepository.kt:118-124`), so `isNewerWriterThan` (`:1386-1387`) does not fire after a restore.

**Needs device evidence:** the restore log records each run's `reason=` (`:783-784`). If Manu's log shows `reason=enqueue:ImportFullSnapshot forceImport=true` after `reason=fresh_install_restore`, that confirms it.

### Premise check

| Premise | Verdict |
|---|---|
| Import leaves local lineage unrecorded, so the next cycle sees two histories | **Premise does not match** on the normal path (see `:1201`). It holds only in the crash window `:1195`→`:1201` (see H2 below). |
| Imported embedding/metadata rows are Queued, so local differs from remote | **Premise does not match.** Imported garments are written `Synced` (`LocalWardrobeSyncSnapshotRepository.kt:491`). Imported embeddings have `pendingUpload = false` (`:342-360`). Only vectors backfilled after import are pending, and they never gate a proposal. |
| Equivalence check failure causes a proposal | **Premise does not match.** `remoteAlreadyHolds` / `equivalentForUpload` (`SnapshotEquivalence.kt:17-41`) only decide whether to upload. They run after the writer check (`:916-926`), so they cannot create a proposal. |
| The post-restore "Syncing" is a real follow-up cycle | **Partly.** `syncIndicatorShowing` holds the Syncing label for 1.5 s after *any* run (`RobiaApp.kt:702`, `:741`). The proposing run itself calls `fetchSnapshot`, which downloads photo blobs (`:884`), so it takes seconds. |
| Manu's spec: the dialog appears only on a real clash, or when both sides have clothes | **Premise does not match the code.** Any newer backup from another writer that has user data proposes (`:924`). A forced run proposes with no lineage check at all (`:856`, `:883-890`). `ConnectFlowTest.kt:29-37` pins this behaviour. |
| Offline: sync checks connectivity before starting a cycle | **Premise does not match.** `sync/` has no connectivity check. The only network gate is WorkManager's `CONNECTED` constraint (`DriveSyncScheduler.kt:53-54`), which does not apply to the inline `enqueue` run. |

### (1) Scenario table

Here "forced" means an `ImportFullSnapshot` (connect or retry).

| Scenario | Today | Should |
|---|---|---|
| Fresh install + empty remote | Forced run, `RestoreRemoteThenUpload`. `NotFound` gives an empty snapshot, nothing imported, `Success` (`:1218-1224`). No dialog. | Same. No dialog, no upload. |
| Fresh install + remote with data (restore) | Restore imports and sets lastKnown (`:1195`, `:1201`). Then the second forced run proposes (`:856`, `:883`). **Bug.** | One restore. The trailing run is a no-op. |
| Phone with data + empty remote | Non-forced: `uploadLocalSnapshot` uploads. Forced: `proposeMergeOrUpload` sees empty remote and uploads (`:889`). Correct. | Same, silently. |
| Phone with data + remote with data, same lineage | Non-forced: uploads (writer check false). Forced (connect): proposes even if equal (`:856`). **Bug.** | Silent upload, or no-op if equivalent. |
| Both with data, different lineage (real clash) | Non-forced: proposes (`:924`). Correct. | Keep the dialog, but skip it when local ⊆ remote or merged equals remote. |
| Reinstall of same account (new `deviceId`, empty) | Same as the restore row. Old writer id is simply "another writer". | Restore silently. |
| Second device with data, never synced (lastKnown = 0) | Proposes whenever remote has data and writer differs (`R > 0`). | Silent fast-forward if local ⊆ remote; dialog only if divergent. |
| After restore completes | Forced re-run proposes (**bug**). Non-forced: uploads only if not equivalent, or when backfilled vectors are pending. | No-op unless real pending work. |
| Restore interrupted between `:1195` and `:1201` | Data is on the phone, `lastKnown = 0`, flag not burned. Next start: restore sees data, sets the flag (`:708-711`). Next cycle: writer differs and `R > 0`, so **proposal**. | Base recorded atomically with the import, or a restore marker that means "base = R". |
| Restore while offline | Fetch fails with retries (`withRetry`), `restoreProgress` retryable, flag not burned (`:720`). Correct, but slow. | Same, with no network call. |
| Two devices racing | No ETag or `If-Match` in `sync/` (grep found none). Upload is last-writer-wins, with a window between `fetchManifest` (`:911`) and `upsertSnapshot` (`:928`). Unverified whether the HTTP layer adds a precondition. | Conditional write. Proposal if revision changed under us. |
| Restore then immediate edit | Edits stay Queued because `markSynced` only touches claimed rows (`:1292-1296`). Likely fine. Not traced in full. | Same. |

### (2) Ranked causes

1. **Certain by code:** forced cycle after restore → `MergeWithRemote` → `proposeMergeOrUpload` / `proposeMerge` (`:856`, `:883-902`) with no lineage or clash check. Ordering evidence is at `:707-713` and `:788`.
2. **Hypothesis H2:** the crash window between `:1195` and `:1201`. Lineage is lost, and the next normal cycle proposes.
3. **Ruled out:** lineage not recorded on the normal path; equivalence causing the dialog.

### (3) Fix design

**Decision function (pure, in `sync/`):**
- Inputs: `local`, `remote`, `base` (revision and writer of the last state both sides agreed on, or null), `localWasEmptyAtStart`.
- Outputs, in order:
  1. Remote has no user data → Upload (or Skip if local is empty).
  2. `base != null` and `remote.revision <= base.revision` → Upload (ordinary).
  3. `localWasEmptyAtStart` → FastForward (import, no dialog).
  4. `equivalentForUpload(local, remote)` → RecordBase, no dialog.
  5. `mergeSnapshots(local, remote)` equivalent to remote (local ⊆ remote) → FastForward, no dialog.
  6. `base != null` and local is unchanged since base (fingerprint match) → FastForward.
  7. Otherwise → Propose (both sides have data, divergent).

**Recording base:** write a restore marker (`restoringBase = R`) before the import. On startup, if the marker is present and local now has data, base = R. Otherwise keep the marker and retry. This closes H2 without relying on DataStore and Room being atomic together.

**Pure and unit-testable pieces:**
- `decideMerge` (new, pure), tested with fixtures from the real path: `ClothingItemEntity` → `toGarmentRecord` / `toPhotoRecord`, then JSON round-trip through the existing codec (`SnapshotJsonCompatTest` style). Do not hand-build the snapshots.
- `equivalentForUpload` and `mergeSnapshots` (existing, pure).
- `classifyOffline(throwable)` (new, pure): `UnknownHost`, `SocketTimeout`, `Connect`, `SSLHandshake`, `NoRouteToHost`.

**Corner cases the tests should cover:**
- Partial restore: hydration fails for some photos. Base must still be set so the restore is not proposed.
- Interrupted restore: the restore marker path above.
- Restore offline: must not burn `driveFreshInstallRestoreAttempted`.
- Two devices: needs a conditional upload (not verified in the HTTP layer).
- Edit during restore: the edited row must stay Queued.

**Trap for the offline fix:** if the offline short-circuit returns before `restoreRemoteThenUpload`, `restoreProgress` stays null. `restoreFreshInstallOnceIfNeeded` (`:720-723`) then burns the flag and the empty phone never restores. The short-circuit must keep the retryable semantics.

### (4) The post-restore "Syncing"

- **Legitimate only if** backfilled embeddings are pending (`hasPendingEmbeddings`, `:750`), which triggers an upload.
- **Otherwise redundant.** The restore already uploaded or was equivalent (`:1227-1234`).
- **Proposal:** make the trailing run a no-op (fix 1 below). The 1.5 s hold is UI-only: it can stay, or it can be suppressed when the run did no work.

### (5) Offline timeline

- **Sync-side gap:** `enqueue` runs `runCycle` inline with no connectivity check (`:243-267`). Each Drive call goes through `withRetry` (5 attempts, backoff 1/2/4/8 s with jitter, `DriveHttpErrors.kt:46-`, callers at `GoogleDriveWardrobeRepository.kt:811/831/895`). So the cycle reports `Retry` only after tens of seconds. Meanwhile the mutex is held.
- **Why "Pending" appeared at all:** `cloudSyncIndicator` (`RobiaApp.kt:658-667`) shows "Waiting for connection" whenever `pending > 0 && !online`. "Waiting to sync" (`Pending`) is reachable only when `online == true`. So `online` was still true during the delay.
- **Likely reason for `online == true`:** `hasActiveInternet` checks only `NET_CAPABILITY_INTERNET`, not `VALIDATED` (`RobiaApplication.kt:180-182`). A Wi-Fi or other network with no uplink reads as online. The DNS failure then shows the network was present but unreachable. This is a hypothesis: ask Manu what network was active, or check `dumpsys connectivity`.
- **Error card:** `cycleDriveFailure` → `SyncCycleSummary.errorClass` / `errorMessage` (`WardrobeSyncOutboxProcessor.kt:818-825`, `:949-952`). `cloudSyncIndicator` builds `detail` for any error (`RobiaApp.kt:671-673`). `DeveloperCloudSyncCard` renders it in `colorScheme.error` (`RobiaApp.kt:~2345`). The red is not expected offline. A DNS failure should be "Waiting for connection", in neutral colour, with the raw class kept as secondary text.
- **Immediate offline:** use the connectivity flow (`RobiaApplication.kt:165-175`) inside the processor. When offline, `enqueue` and `runCycle` return `Retry` without network calls and without an error class. Resume with `requestSync` when a validated network returns.

### (6) Fix list

1. **sync/ (certain bug):** forced import on a non-empty phone must not propose unconditionally. Route it through `decideMerge`. Re-check freshness inside the mutex in `restoreFreshInstallOnceIfNeeded`. Change `ConnectFlowTest.kt:29-37` to match. Add a test: restore then forced import → no `pendingMergeSummary`.
2. **sync/ (hypothesis H2):** restore marker and base recording in `restoreRemoteThenUpload` (`:1195`, `:1201`), plus settings keys for base revision and writer.
3. **sync/:** proposal only when divergent base + user data on both sides + not local ⊆ remote (`decideMerge`, `:916-924`). Fast-forward path for second device (`lastKnown = 0`).
4. **sync/:** offline short-circuit in `enqueue` / `runCycle` that keeps the restore flag semantics. Offline flag on `SyncCycleSummary`, classified from the throwable.
5. **app wiring (RobiaApplication.kt):** require `VALIDATED` for online; on regain, `requestSync` if pending. Feeds the processor, so treat as sync/-adjacent.
6. **UI-only:** developer card renders offline cycles as neutral "Waiting for connection" with raw class in secondary text (`RobiaApp.kt:667-674`, `:2302-2350`).
7. **UI-only:** optional tuning of the 1.5 s Syncing hold (`RobiaApp.kt:702`).

**Independent review required:** fixes 1-5. **UI-only:** fixes 6-7.
