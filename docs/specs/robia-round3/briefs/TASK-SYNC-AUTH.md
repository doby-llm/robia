# TASK-SYNC-AUTH — Drive sign-in errors: refresh and retry, correct classification, honest banner

## Goal
F8 (REQ-FB-08): Manu added 115 garments, the cloud upload ended `sync ended outcome=result=AuthBlocked` after 160 s, a red "sign in to Google Drive" error appeared in Browse and tapping Sign in did nothing. The account was never disconnected (status stayed Connected, the next Background cycle ran). Likely cause (medium confidence): a mid-run 401 (stale cached token) or a 403 with a reason the code does not list, both mapped to "sign in required" with no token refresh and no logged cause.

## Read first
`R3-DEC-041`; planner investigation `sync.md` §F8 (file:line leads on main `6d78c3a`); `sync/DriveHttpErrors.kt` (`classify`, `withRetry`); `sync/GoogleDriveWardrobeRepository.kt` `withAccessToken` (~253-290), `authBlocked()` (~354), `uploadChanged`/snapshot PUT (~538-568), the HTTP layer (~1155-1200) and `DriveApiResult` (~791); `sync/WardrobeSyncOutboxProcessor.kt` cycle end (~879), `authBlocked` clearing (~828), upload Blocked log (~1046); `MainActivity.kt` Sign in handler (~103-130, 152-177); `DriveHttpErrorsTest`; `sync/fakes/FakeDriveSnapshotApi.kt`.

## Files
`sync/DriveHttpErrors.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/WardrobeSyncOutboxProcessor.kt`; `MainActivity.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/DriveHttpErrorsTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveSnapshotApi.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `classify`: 403 reasons `rateLimitExceeded`, `userRateLimitExceeded`, `backendError` (existing) plus `dailyLimitExceeded`, `sharingRateLimitExceeded`, `quotaExceeded` and status `RESOURCE_EXHAUSTED` → Transient. Only 401 and 403 `forbidden`/`insufficientPermissions` → AuthRequired. Any other 403 reason, empty or HTML body → `Permanent(403)` (fails the cycle, retried later, never a sign-in banner). `storageQuotaExceeded` stays StorageFull.
2. `Retry-After`: parse the seconds form in the HTTP layer; `DriveApiResult.Transient` (and the internal classification result) carries an optional `retryAfterMillis`, capped at 60 s; HTTP-date or garbage → null. `withRetry` waits max(backoff with jitter, retryAfter) through the injected sleeper.
3. Unauthorized handling in `withAccessToken` (or at its call sites if cleaner): when a call returns `Unauthorized`, run `authorize()` once more to get a fresh token and retry that call once; a second `Unauthorized` (or a failed re-authorize with a resolution) → Blocked as today. One retry per call, never a loop.
4. Logging in the processor: the "sync ended" line for AuthBlocked/Retry/Failure carries the HTTP code and reason (and the failing call kind if known); the Blocked cause of ordinary uploads (~1046) is logged too. Developer log only; no new user-visible text in the log line beyond code and reason.
5. Clear the `authBlocked` flow when a cycle starts. In `MainActivity`, when the Sign in tap finds Drive still Connected (no resolution needed), show a short toast ("Checking your Google Drive connection…", new string x3) and request a sync, instead of silently rewriting state.

## Tests
V-SYNC-AUTH-1..4 (CI). Extend `FakeDriveSnapshotApi` to script a response sequence per call (401 then 200; 429 with a Retry-After header) and to count `authorize()`.

## Done when
V-SYNC-AUTH-1..5 pass, V-SYNC-AUTH-D is ready for Manu; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Add concurrency, change upload speed, add auth flows or scopes, touch the merge banner or Drive settings UI, or retry a 401 more than once per call.
