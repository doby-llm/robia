# Tasks: robia-round6

A task is Ready when the plan PR is merged and every predecessor is merged. Paths are relative to
`app/src/main/java/com/gusanitolabs/robia/` unless they start with `app/`, `.github/` or `docs/`. Line numbers refer to
main `fb1640d` and are hints only: find the code by the quoted text.

## Rules for every code worker (read first)
1. Start from the latest `origin/main`. Use branch `feat/<task-id-lowercase>-<slug>`, one PR per task, titled `<TASK-ID>: <title>`.
2. Touch **only** the files under "Files", and inside a shared file only the named region. You may always change
   imports, the three `strings.xml` files (`app/src/main/res/values{,-es,-de}/strings.xml`) and new test files under
   `app/src/test/`. For anything else, stop and ask one precise question.
3. Never build Android on the Raspberry Pi. Push and let CI build (`gh pr checks <n> --watch`, `gh run view <id> --log-failed`).
   If `pull_request` CI does not start, run `gh workflow run android-apk.yml --ref <branch>`.
4. Copy the user-visible text from this file verbatim. Put strings in ONE block at the end of each strings.xml, headed `<!-- <TASK-ID> -->`.
5. Change no behaviour outside "In scope". No refactors, no new libraries, no Room schema change.
6. Source-contract tests (`RegressionSourceContractTest`, `ReleaseGatesTest`, `scripts/check_*_static.py`) pin earlier
   designs. Rewrite a pinned assertion only where your task replaces that design, keep it equally strong, and list it in the receipt.
7. Colours come only from `MaterialTheme.colorScheme.*` / `MaterialTheme.extras`. Check light and dark.
8. Budget: 90 min. Stop after 2 failed CI fix cycles and include the failing log excerpt. A compiler `OutOfMemoryError` is a rerun, not an attempt.
9. Receipt (a PR comment) contains: commit SHA, CI run URL for that SHA, each V- row with its evidence (test name), the
   ES/DE strings added, what is not done or not verified, and each interpretation where the brief was silent.
10. The repository is PUBLIC: no photos, keys, tokens, e-mail addresses or home paths. 11. Do not merge.

## Graph and hot files
```
plan PR merged -> BI1, BS1, DU0 (wave 1, parallel) -> DM1, REL2 (as slots free)
DU0 (+ its docs PR artifacts/inv-r6-duplicates.md merged) -> DU1 -> Opus review
BI1, BS1, DM1, REL2, DU1 merged + main CI green -> PUB1 (release AAB -> Play Internal + closed Alpha)
```
- `ui/AddEditClothingScreen.kt`: BI1 owns `PhotoCaptureCardWithLaunchers` (the gallery launcher and the capture
  buttons). BS1 owns the `AddEditClothingScreen(` parameter list and the `LaunchedEffect(existingItem?.id, initialPhotoReviewState)`
  reset block. Neither touches the other's region.
- `ui/RobiaApp.kt`: BS1 owns the `RobiaRoute.BatchEditClothing ->` branch and `onSaveBatchDraft`. DM1 owns the two
  `developerModeAllowed(` call sites. DU1 owns the duplicate-scan `LaunchedEffect` (comment "REQ-DU-04") and the Settings wiring of its new row.
- Merge PRs one at a time. Before each merge, dry-run the merge with main (`git merge --no-commit --no-ff origin/main`, then `--abort`).
- Denominator for "Hechos X de Y": DU0, BI1, BS1, DM1, REL2, DU1, PUB1 = **7**.

---

## DU0: Why the duplicate banner does not always appear (read-only investigator, Sonnet 5.5 high)
- **Output:** `artifacts/inv-r6-duplicates.md`. The coordinator commits it in a docs-only PR and merges it before DU1 starts.
- **Question:** in which real situations does a phone with true duplicates show no "Possible duplicates" banner? For each
  cause give file:line, CERTAIN or HYPOTHESIS, and the smallest fix. Mark whether the fix fits DU1's Files.
- **Candidates found in planning (verify each one, do not assume):**
  1. The banner is hidden while any of the conditions in `duplicatePromptVisible` holds (RobiaApp.kt ~2007). This
     includes `browseAttentions.isNotEmpty()`. A sync attention card that stays (Drive not set up, auth blocked) would
     hide the banner forever.
  2. The automatic scan runs only on Browse, only after `embeddingBackfill.progress.settled` with `remaining == 0`
     (RobiaApp.kt ~1185). The backfill only runs while the suggestion model is Ready. With no model, or with a garment
     whose vector fails up to the attempt limit, `remaining` may never reach 0. Check whether failed garments count in `remaining`.
  3. `eligibleGarments` (DuplicateScan.kt ~545) drops a garment whose stored `imageInputHash` differs from the current
     photo hash, for example after a photo edit until the backfill re-embeds it, or after a restore.
  4. A "Very similar" pair needs cosine >= `T_DUP = 0.96` and the same primary colour when both have one (~65, ~197).
     Two photos of the same garment taken separately may score below that. This is a threshold matter, not a bug: report it, do not change it.
  5. `DuplicateScanHistory.lastSignature` lives only in memory and keys only on ids + photo URIs. Check whether a scan
     that ran while vectors were missing records a Success signature. If it does, the vectors that arrive later never trigger a rescan.
- **Allowed tools:** read-only (`--disallowedTools Edit Write`, prompt on stdin). Limit: 30 min, 600–900 words.

## BI1: Batch import: faster copy and a visible "Preparing photos" state (F1, Haiku)
- **Root cause found in planning (verify it):** in `PhotoCaptureCardWithLaunchers`, the `OpenMultipleDocuments` result
  copies every picked photo **one by one** (`uris.mapNotNull { copyContentUriToPrivateStorage(...) }`) before
  `onBatchPhotosSelected` runs. Nothing is shown meanwhile. 175 full-size photos take 10–20 s.
- **In scope:**
  1. A pure, JVM-testable helper (new file `ui/PhotoImport.kt`) that copies the URIs with **at most 4 in flight**,
     returns results **in the picked order**, reports `(done, total)` after each photo, skips a photo whose copy fails,
     and rethrows `CancellationException`. On cancellation it deletes the copies already made (the caller passes a delete lambda).
  2. As soon as the picker returns more than one URI, the add screen shows a non-dismissable progress state in the
     photo card: a determinate `LinearProgressIndicator` and the text `batch_import_preparing`. The gallery and camera
     buttons are disabled meanwhile. A second picker launch is impossible.
  3. Back during the copy cancels it, deletes the copies made, and leaves the screen as Back does today. Nothing is
     left in the image folder. Use `ClothingImageStore.deleteBatchDraftFiles`: `gallery-<uuid>.jpg` names match its pattern.
  4. A single picked photo keeps today's path with no visible change.
  5. One `BatchTelemetry.log` line: `import copied=<n> failed=<n> ms=<n>`.
- **Out:** lazy copy on the batch screen, persistable URI permissions, changes to the batch processing pipeline.
- **Files:** `ui/AddEditClothingScreen.kt` (only `PhotoCaptureCardWithLaunchers`); new `ui/PhotoImport.kt`; 3× strings.xml.
- **Copy:** `batch_import_preparing` EN `Preparing photos… %1$d of %2$d` · ES `Preparando fotos… %1$d de %2$d` · DE `Fotos werden vorbereitet… %1$d von %2$d`.
- **Review:** none (no data writes; temp files only). The coordinator reads the diff and checks the cancellation delete.
- **Acceptance:**
  - V-BI1-1 (CI): order kept with uneven copy times.
  - V-BI1-2 (CI): never more than 4 copies in flight (counter in the fake).
  - V-BI1-3 (CI): progress is monotonic and ends at (total, total).
  - V-BI1-4 (CI): a failed copy is skipped and the others are kept.
  - V-BI1-5 (CI): cancellation deletes every copy already made and rethrows.
  - V-BI1-6 (DEV): 100+ photos show "Preparing photos… N of M" within 1 s of the picker closing, then the batch screen opens.
  - V-BI1-7 (DEV): Back during the copy returns without a batch.

## BS1: AI mark (sparkle) in batch edit (F2, Haiku)
- **Root cause found in planning:** single add marks a field with `isAiSuggested(selected, modelOwnedTagIds)` and
  `isColorAiSuggested(modelPrimaryColorGeneration, modelSecondaryColorGeneration)` (GarmentFieldRows.kt ~54–61). The
  batch edit route opens the same `AddEditClothingScreen` with `existingItem = draft.toBatchEditItem(...)`. Its reset
  block (AddEditClothingScreen.kt ~677–706) sets `modelOwnedTagIds = emptyList()` and both colour generations to
  null, and `BatchDraftItem` does not record which values came from the model. Result: no sparkle in batch edit.
- **In scope:**
  1. `BatchDraftItem` gains `modelOwnedTagIds: List<String> = emptyList()` and `modelPrimaryColor: Boolean = false`,
     `modelSecondaryColor: Boolean = false`. These live in memory only and are never written to Room.
  2. The batch pipeline fills them. `suggestStage` records the predicted tag ids that `mergePredictedTags` actually put
     into `selectedTagIds`. The colour flags are true when `cutoutStage` set that colour.
  3. `AddEditClothingScreen` gains one optional parameter (for example `initialAiMarks: AiMarks? = null`; default =
     today's behaviour). The reset block applies it, so the sparkle shows exactly as in single add. Changing a field in
     the editor removes its mark, as in single add.
  4. Returning from batch edit (`onSaveBatchDraft`): a pure function keeps a tag-category's ownership only if that
     category's selection is unchanged versus the draft, and a colour flag only if that colour is unchanged. Reopening
     the draft therefore shows the right marks.
  5. Retry of a draft clears the marks before reprocessing.
- **Out:** batch list rows, Browse, item detail, anything persisted.
- **Files:** `ui/BatchAddClothingScreen.kt`; `ui/AddEditClothingScreen.kt` (parameter list and the reset block only);
  `ui/RobiaApp.kt` (`RobiaRoute.BatchEditClothing ->` branch and `onSaveBatchDraft` only); tests.
- **Review:** none. The coordinator checks that no Room entity or mapper changed (`git diff --name-only | grep -E "data/|sync/"` is empty)
  and that the existing-garment edit path passes no marks.
- **Acceptance:**
  - V-BS1-1 (CI): a draft from a fake pipeline has the merged predicted ids as owned, and a tag the user had chosen is not owned.
  - V-BS1-2 (CI): the carry-back function keeps an unchanged category, drops a changed one, and handles colours alike.
  - V-BS1-3 (CI): Retry clears the marks.
  - V-BS1-4 (CI, source contract): the batch edit route passes the marks, and the plain edit route does not.
  - V-BS1-5 (DEV): batch of 3, open one, sparkle on category, colour and other suggested fields as in single add.
    Change the category, go back, reopen: only the category lost it.

## DM1: Developer mode in release builds (F3, Haiku)
- **Facts from planning:** `ui/ReleaseGates.kt` has `developerModeAllowed(buildType) = buildType != "release"`. It is
  used in `RobiaApp.kt` for the 10-tap unlock (~1637) and to mask `developerModeUnlocked` (~468). `ReleaseGatesTest` pins
  both. The staging build is also minified with R8, so developer screens already run in minified builds.
- **In scope:**
  1. Developer mode can be unlocked and used in `release` exactly as in staging. Rewrite the pinned `ReleaseGatesTest`
     assertions to the new rule with equal strength (rule 6). Keep `BuildConfig.DEBUG` gates (performance fixtures) unchanged.
  2. Audit (in the receipt): list every surface shown behind developer mode, with file:line, what it shows, and whether
     it can delete data, send data off the phone other than through the user's own share sheet, or expose an e-mail,
     token or account id. Keep any surface with such a risk out of release, behind `developerModeAllowed`, and name it.
- **Files:** `ui/ReleaseGates.kt`; `ui/RobiaApp.kt` (the two `developerModeAllowed(` call sites only); `ReleaseGatesTest.kt`;
  `RegressionSourceContractTest.kt` (only assertions about this gate).
- **Review:** none. The coordinator reads the audit list and greps the diff for `BuildConfig.DEBUG` (no change allowed).
- **Acceptance:**
  - V-DM1-1 (CI): `developerModeAllowed("release")` follows the new rule.
  - V-DM1-2 (CI): the unlock handler and the settings mask still read the gate, now allowed for release.
  - V-DM1-3 (REC): the audit list.
  - V-DM1-4 (DEV, after PUB1): Play build: 10 taps on Settings shows "Developer mode unlocked" and the section appears.

## REL2: Version 1.0.1 (Haiku, tiny)
- **In scope:** `versionName = "1.0.1"` in `app/build.gradle.kts`. The versionCode stays CI-derived. Grep tests and
  `scripts/` for `1.0.0` and update any pin. Nothing else.
- **Files:** `app/build.gradle.kts`; a test only if it pins the version. **Review:** none.
- **Acceptance:** V-REL2-1 (CI green). V-REL2-2 (REC): `grep -n versionName app/build.gradle.kts` shows 1.0.1.

## DU1: Settings › Smart features › "Check for duplicates" (F4, Haiku; independent Opus review)
- **Predecessors:** DU0's artifact merged on main. Read it first, and fix every cause it marks CERTAIN whose fix fits
  the Files below. Put the rest in the receipt as follow-ups. Do not change `T_DUP` or the colour rule.
- **In scope:**
  1. New row in the Smart features group of `SettingsScreen.kt`, shown only when the suggestion model is Ready. It
     has the `duplicate_check_*` strings below and a leading icon from the existing icon set.
  2. A tap starts a **forced scan**, which skips `duplicateScanDue`. It loads stored dismissals first, as the automatic
     scan does, then calls `scanDuplicates`, then `DuplicateSession.replaceGroups(...)`. It records the signature exactly
     as the automatic scan does (Success records, Failed keeps the previous one).
  3. The scan runs in the app shell's scope, not the Settings composition, so leaving Settings does not cancel it. It runs off the main thread.
  4. A forced and an automatic scan never run at the same time and never publish out of order. Use one `Mutex` (or
     equivalent) in `DuplicateSession` taken by both paths. A forced tap while any scan runs does not start a second one.
  5. The row shows `duplicate_check_running` with progress while the scan runs; use the `onProgress` of the hash
     step if `scanDuplicates` can expose it without changing the automatic path's behaviour. Then:
     - Groups found and the user is still on Settings: push `RobiaRoute.DuplicateReview`.
     - Groups found and the user has left Settings: nothing extra happens; the Browse banner rules apply.
     - No groups: the row shows `duplicate_check_none`, with no dialog or snackbar.
     - Garments not compared: if active garments lack a current image vector, the result line adds `duplicate_check_not_ready`.
     - Failure: the row shows `duplicate_check_failed`.
  6. Pairs marked "Not duplicates" never come back through the forced scan.
- **Files:** `ui/SettingsScreen.kt` (Smart features group plus its new parameters); `ui/RobiaApp.kt` (duplicate-scan
  region, a new forced-scan function, Settings wiring); `suggestions/DuplicateScan.kt`; a new small pure state file
  `ui/DuplicateCheckState.kt` if useful; tests.
- **Copy:**
  - `duplicate_check_title`: EN `Check for duplicates` · ES `Buscar duplicados` · DE `Nach Duplikaten suchen`
  - `duplicate_check_summary`: EN `Find garments that may be the same` · ES `Busca prendas que podrían ser la misma` · DE `Findet Kleidungsstücke, die gleich sein könnten`
  - `duplicate_check_running`: EN `Checking… %1$d of %2$d` · ES `Comprobando… %1$d de %2$d` · DE `Wird geprüft… %1$d von %2$d`
  - `duplicate_check_none`: EN `No possible duplicates found` · ES `No se han encontrado posibles duplicados` · DE `Keine möglichen Duplikate gefunden`
  - `duplicate_check_failed`: EN `Couldn't check. Try again.` · ES `No se pudo comprobar. Inténtalo de nuevo.` · DE `Prüfung fehlgeschlagen. Bitte erneut versuchen.`
  - plurals `duplicate_check_not_ready`:
    - EN one `%d garment isn't ready to compare yet` · other `%d garments aren't ready to compare yet`
    - ES one `%d prenda aún no está lista para comparar` · other `%d prendas aún no están listas para comparar`
    - DE one `%d Kleidungsstück ist noch nicht bereit zum Vergleich` · other `%d Kleidungsstücke sind noch nicht bereit zum Vergleich`
- **Review:** independent, Opus 5.5 high, `PROMPT_REVIEW.md`. Verdict `SAFE|NOT SAFE`. BLOCKING classes:
  (a) two scans publish out of order, or a stale result overwrites a newer one;
  (b) a dismissed pair comes back;
  (c) `CancellationException` swallowed, or the mutex/permit left held after cancel;
  (d) unbounded work on the main thread;
  (e) the recorded signature makes the automatic scan skip a wardrobe it never scanned successfully;
  (f) the user lands on an empty review screen;
  (g) any DB write other than the existing dismissal path;
  (h) any DU0 CERTAIN fix that changes another feature's behaviour.
  Anything else is DEFERRED. Budget: 2 review rounds; a round-2 review checks only the previous BLOCKING list.
- **Acceptance:**
  - V-DU1-1 (CI): the forced scan runs even when the signature is unchanged.
  - V-DU1-2 (CI): a forced scan during an automatic one publishes once, in order (fakes with gated suspension).
  - V-DU1-3 (CI): dismissed pairs stay out.
  - V-DU1-4 (CI): the pure state maps running, none, groups, not-ready and failed to the right text and action.
  - V-DU1-5 (CI): a source contract shows the row is hidden when the model is not Ready.
  - V-DU1-6 (DEV): two photos of the same garment, then Settings › Check for duplicates opens "Possible duplicates".
  - V-DU1-7 (DEV): with no duplicates, the row says "No possible duplicates found".

## PUB1: Release 1.0.1 to Google Play (coordinator + Hermes browser)
See `RELEASE.md`. Counted when Internal testing shows the new versionCode as available and the Alpha release is submitted for review.

---

## Device checks for Manu after PUB1 (Spanish in the message, his own words; silence = OK)
1. Lote con 100+ fotos: aparece "Preparando fotos… N de M" enseguida y luego la pantalla de lote.
2. Lote: entrar en una prenda, la estrella aparece en categoría/color/etc. como al añadir una sola.
3. Ajustes: 10 toques en Ajustes desbloquea el modo desarrollador en la versión de Play.
4. Ajustes › Funciones inteligentes › Buscar duplicados: con dos fotos de la misma prenda abre "Posibles duplicados";
   sin duplicados dice "No se han encontrado posibles duplicados".
