# TASK-DUP2 — Duplicates: "very similar" tier and remembered dismissals

## Goal
Finish R3-07 with Manu's answer 6A (dismissals remembered on the phone and in Drive) and the calibrated threshold.

## Read first
G-DUP PLAN-CHANGE (`T_DUP` value); DUP1 code; `artifacts/inv-r3-c.md` DB section (migration pattern, sync options); OR2 optional-field pattern in `DriveSnapshotJson`.

## Files
`suggestions/DuplicateScan.kt`; `ui/DuplicateReviewScreen.kt`; new `data/local/DismissedDuplicateEntities.kt`; `data/local/RobiaDatabase.kt`; `data/local/Dao.kt`; `core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `ui/RobiaApp.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/data/local/Migration14To15Test.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `const val T_DUP` = the PLAN-CHANGE value; tier VerySimilar per V-DUP2-1; label "Very similar".
2. Entity + DAO + `MIGRATION_14_15_SQL`; "Not duplicates" writes all pairs of the group.
3. Snapshot `dismissedDuplicates: List<DismissedPair>` optional; export/import union; tombstoned garments' pairs dropped on export.
4. Coordinator commits `15.json` from the CI artifact.

## Tests
V-DUP2-1..3.

## Done when
V-DUP2-1, V-DUP2-2, V-DUP2-3, V-DUP2-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Bump the sync schema version or touch photo sync.
