# TASK-DUP2 — Duplicates: "very similar" tier and remembered dismissals

## Goal
Finish R3-07 with Manu's answer 6A (dismissals remembered on the phone and in Drive) and Manu's threshold **T_DUP = 0.96** (R3-DEC-047; G-DUP is closed, no marking). 0.96 produces false positives for similar black dresses (cosine 0.955-0.977), so the persisted dismissals are not optional polish: they ship together with the tier or the same pair returns after every restart.

## Read first
`R3-DEC-047`, `R3-DEC-051`; DUP1 code; DUP-FAST's `scanBlocks` pass and DUP-TRIGGER's signature/outcome (both merged before this task); `artifacts/inv-r3-c.md` DB section (migration pattern, sync options); `artifacts/inv-r3-models.md` §Duplicates; OR2 optional-field pattern in `DriveSnapshotJson`.

## Files
`suggestions/DuplicateScan.kt`; `ui/DuplicateReviewScreen.kt`; new `data/local/DismissedDuplicateEntities.kt`; `data/local/RobiaDatabase.kt`; `data/local/Dao.kt`; `core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `ui/RobiaApp.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/data/local/Migration14To15Test.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `const val T_DUP = 0.96f`; tier VerySimilar per V-DUP2-1, built on DUP-FAST's blocked pass (visit in-block pairs, keep cosine ≥ `T_DUP`, same primary colour when both have one; no all-pairs list); group tier = SamePhoto if the component has a hash-equal edge, else VerySimilar (state the rule in the receipt); label "Very similar".
2. Entity + DAO + `MIGRATION_14_15_SQL`; "Not duplicates" writes all pairs of the group at once; the stored pairs are loaded into `DuplicateSession.dismissals` at start (before the first scan) and passed to the scan, so a dismissed pair does not return after a restart.
3. Snapshot `dismissedDuplicates: List<DismissedPair>` optional; export/import union; tombstoned garments' pairs dropped on export.
4. Coordinator commits `15.json` from the CI artifact.

## Tests
V-DUP2-1..4 (CI); V-DUP2-D (Manu).

## Done when
V-DUP2-1, V-DUP2-2, V-DUP2-3, V-DUP2-4, V-DUP2-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Bump the sync schema version or touch photo sync; change `T_DUP`; change the scan trigger or the hashing (DUP-TRIGGER, DUP-FAST).
