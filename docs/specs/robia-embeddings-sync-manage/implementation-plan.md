# Implementation plan — robia-embeddings-sync-manage

Revision 1. Tasks are defined in `tasks.md`; evidence rows in `verification-matrix.md`.

## Delivery model
- One PR per task against `main` from branch `feat/<task-id-lowercase>-<slug>` in `doby-llm/robia`. Merge only when the PR's `Android APK CI` is green and the task's acceptance rows pass. Main stays installable after every merge.
- Known temporary regression (accepted): between TASK-B02 (old classifier removed) and TASK-E05 (new suggestions), new garments get no automatic category/occasion. Manu agreed the old classifier performs poorly.
- No Android builds on the Raspberry Pi: CI is the build/test authority. Workers push and read CI results with `gh run watch` / `gh pr checks`.
- Device gates are run by Manu on his Poco F3 with the staging APK artifact.

## Gates
| Gate | What | Who | Unblocks |
|---|---|---|---|
| G-APPROVAL | Manu writes `APROBADO` on package revision 1 (incl. mockups) | Manu | all tasks |
| G-DEVICE-1 | Model benchmark on Poco F3 (TASK-E01 output): suggestion budget NFR-01, PSS NFR-03, staging startup | Manu | TASK-E05 and all later E tasks |
| G-DEVICE-2 | Final checklist (TASK-Z01) | Manu | close initiative |

G-DEVICE-1 outcomes: PASS → continue. FAIL with 140 tokens → TASK-E01b (set `VISION_TOKENS = 70`, rerun benchmark). FAIL with 70 → STOP E lane, replan with Manu. S, M and B lanes are independent-safe and continue.

## Slices and dependency graph

```
B01 toolchain+staging+R8+CI
 ├─ B03 app container (RobiaApplication)
 │   ├─ E01 model store + engine + dev benchmark ──► [G-DEVICE-1]
 │   └─ S04 processor + WorkManager + retries + conflict
 ├─ B02 remove old classifier
 │   └─ M01 Manage redesign + lag
 ├─ D01 schema v13 + snapshot contract
 ├─ S01 Drive HTTP layer: keys, WebP, index, streaming, errors
 │   ├─ S02 restore: concurrency, isolation, no re-upload, log batching
 │   └─ S03 upload: change detection, purge, guarded isolation (needs D01)
 └─ E04 scoring library (needs E02)
E02 precision tool + golden fixture (no deps)

S04 needs S02, S03, D01, B03
S05 sync attention UX (needs S04)
S06 sync progress UX (needs S04, S05)
E05 capture integration (needs E01+G-DEVICE-1, D01, E04, B02)
E06 backfill + embeddings in Drive (needs E05, S04)
E07 model download UX (needs E01)
E08 tag fields UI + review flow (needs E06, M01)
E09 search + similar garments (needs E06)
E10 item-detail diagnostics (needs E05)
Z01 final device validation (needs all)
```

Edge reasons:
- B01 → everything with Kotlin/Gradle changes: toolchain must build first (litertlm needs Kotlin 2.4/JDK 21).
- B03 → E01/S04: both register singletons in `RobiaApplication`.
- B02 → M01, E05: removal edits `ManageTagsScreen.kt`, `AddEditClothingScreen.kt`, `BatchAddClothingScreen.kt`; later tasks must start from the cleaned files.
- D01 → S03/S04/E05: new columns and snapshot fields are consumed.
- E02 → E04: golden fixture and label descriptions.
- G-DEVICE-1 → E05: no UI integration until latency/memory proven.
- E06 → E08/E09: review and search need stored vectors.
- M01 → E08: tag editor fields go into the redesigned Manage screen.

## Concurrency and file leases
Parallel lanes after B01: {B02, B03, D01, S01, E02, E04*} (*E04 after E02).
Shared hot files must have **one writer at a time** (task holds the lease from branch creation to merge; next task rebases on main):
- `ui/RobiaApp.kt`: B02 → M01 → S05 → S06 → E05 → E07 → E08 → E09 → E10 (order of leasing; non-conflicting tasks wait).
- `sync/WardrobeSyncOutboxProcessor.kt`: S02 → S03 → S04 → S06 → E06.
- `sync/GoogleDriveWardrobeRepository.kt`: D01 (only `DriveSnapshotJson` encode/decode) → S01 → S02 → S03.
- `res/values*/strings.xml`: append-only per task in the section `<!-- TASK-XXX -->`; conflicts resolved by rebase.
- `gradle/libs.versions.toml`, `app/build.gradle.kts`: B01, then E01 (lifecycle-process), S04 (work), D01 (sqlite-jdbc test dep).

Recommended dispatch waves:
1. B01 · E02
2. B03 · D01 · B02 · E04
3. S01 · E01 · M01
4. S02 → S03 · E07 · [G-DEVICE-1]
5. S04 → S05 · E05
6. E06 → E08 · E09 · E10
7. Z01

## Budgets
- Per task: one focused worker, 90 min wall-clock including CI waits; checkpoint at 70 % (push WIP branch + receipt). Two failed CI remediation cycles on the same task → stop, mark Blocked, report.
- CI run ≈ 10–15 min; do not re-run CI without a code change except for an infrastructure flake (max 1 re-run).

## Rollback
- Every task is a single PR → `git revert` of the merge commit.
- D01 (DB v13) is forward-only for installed apps: reverting code after install requires uninstall/reinstall + Drive restore. Mitigation: D01 merged only with S-lane tests green; Drive backup unaffected (optional fields).
- S03 purge deletes blobs only after a verified snapshot upload referencing the replacements.
