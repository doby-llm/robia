# Investigator: Drive "Waiting" badge without a Drive account (2026-10-23)

Read-only investigation on origin/main (c4ec513). Evidence for R3-DEC-018.

**CERTAIN**

1. **Chain.** `BrowseGrid.kt:556-557` calls `cardBadgeFor(item.syncStatus, …)`. That value is already resolved: `BrowseGrid.kt:881` runs `resolveForConnection`, and `BrowseGrid.kt:905` sets `syncStatus = cloud.status`.
2. **`resolveForConnection`** (`RobiaApp.kt:3195-3221`). In the NotConfigured/Disabled branch (`3205-3211`), only Dirty, Queued and AuthBlocked are kept. Everything else becomes LocalOnly. Dirty and Queued pass through unchanged (`3212`, `else -> this`).
3. **`badgeFor`** (`BrowseCardText.kt:63-68`). Dirty, Queued and FailedRetryable map to `CloudUpload` + `badge_waiting`, not error. It keys on status alone, not on `waitingForConnection`, so the badge shows online too.
4. **Save path.** `LocalRepositories.kt:68-70` → `Dao.kt:374-375` (`upsertItemWithTags`) → `queuedForSync()` at `Dao.kt:602-605`, which unconditionally sets `syncStatus = Queued`. There is no connection check. The `LocalOnly` default at `Entities.kt:26` is a constructor default that the save path never uses. I found no path that moves `clothing_items` from Queued to LocalOnly; the only hit is the migration default at `RobiaDatabase.kt:183`. For NotConfigured the processor only cancels work (`WardrobeSyncOutboxProcessor.kt:196-198`).
5. **Enum and settings.** `DriveSyncConnectionStatus` is at `core/model/SyncModels.kt:4-11`: Disabled, NotConfigured, Disconnected, Connected, Syncing, NeedsAttention. The persisted key is `drive_sync_connection_status` (`SettingsRepository.kt:314`), defaulting to NotConfigured (`:277`). The key `cloud_setup_prompt_interacted` (`:315`) is set when status is not NotConfigured (`:122`), on Disabled (`:162`), and explicitly (`:167`). The first-run prompt gate is `RobiaApp.kt:1023-1026`. "Signed out" maps to Disconnected (`drive_sync_status_disconnected`, "Google-Konto getrennt").
6. **Sign-in banner.** `SignInRequired` is raised only when `authBlocked || (Disconnected && pendingOperationCount > 0)` (`WardrobeSyncOutboxProcessor.kt:1504-1505`). NotConfigured never raises it.
7. **Existing test.** `GarmentConnectionStateTest.kt:66-69` ("not configured offline stays saved on this phone") pins NotConfigured to status `Queued`, `waiting=false`. It does not assert the badge.

**HYPOTHESIS**
- (hyp) "Not added" means NotConfigured. Disabled behaves identically in code (`RobiaApp.kt:3215-3216`), so I'd treat it the same unless Manu says otherwise.
- (hyp) No other Queued→LocalOnly transition exists. This is from grep, not an exhaustive read.

**COVERAGE: no.** The only text is `briefs/TASK-VIS.md:16`: "Sign-in: Browse knows whether the SignInRequired card is shown; when it is, Waiting/AuthBlocked badges are suppressed". `tasks.md:146` says "no Waiting/Sign-in card badges while the sign-in banner shows". Because the banner never shows under NotConfigured (point 6), VIS leaves the 200 icons in place. `inv-r3-a.md:140` also warns that changing the AuthBlocked mapping at `RobiaApp.kt:3204` would strip Waiting from Dirty and Queued too, so the fix must not go through that mapping.

**PROPOSED FIX** (not written)
- **Rule:** a pure function next to `cardBadgeFor` (`BrowseCardText.kt:75`). When Drive is not enabled (NotConfigured or Disabled), Dirty, Queued and FailedRetryable return null. AuthBlocked, Running, NeedsUserAction and photo-attention badges are unchanged.
- **Input:** add a boolean to `UiWardrobeItem`, set in `toUiWardrobeItems` (`RobiaApp.kt:876-911`, next to `waitingForConnection` at `:910`). The card at `BrowseGrid.kt:557` reads it.
- **Keep** `resolveForConnection` unchanged, so `GarmentConnectionStateTest` stays green.
- **Test:** in `BrowseCardTextTest`, a table covering NotConfigured/Disabled × {Dirty, Queued, FailedRetryable} → null; Connected × Dirty → Waiting; Disconnected × Queued → sign-in; Running → Uploading; photo attention still shows. Leave `badgeForMatchesTheStatusTable` (`:75`) as is.
- **Files:** `ui/BrowseCardText.kt`, `ui/RobiaApp.kt`, `ui/BrowseGrid.kt`, `BrowseCardTextTest.kt`, and `RegressionSourceContractTest.kt` if its anchors move.
- **Decision for Manu:** signed-out (Disconnected) shows "Sign in" error badges on the unsynced cards (`RobiaApp.kt:3204` → `BrowseCardText.kt:71`). That is not Waiting, but it is the same clutter. I left it out of this rule.

**PREMISE: matches.** With Drive NotConfigured (or Disabled), every unsynced garment shows Waiting, online or offline. The premise's offline-only framing is wrong, though: Waiting is status-driven, not offline-driven. Signed-out shows "Sign in", not Waiting.
