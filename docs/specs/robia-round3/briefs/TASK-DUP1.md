# TASK-DUP1 — Duplicate finder: same-photo tier, banner, review, calibration screen

## Goal
R3-07. Planning evidence (artifacts/inv-r3-models.md §Duplicates): cosine alone confuses different black dresses with re-shots, so this task ships only the exact tier and a calibration screen; DUP2 adds the cosine tier with the threshold set from Manu's marks (G-DUP).

## Read first
`mockups/duplicates.html`; `artifacts/inv-r3-c.md` §C1 (vectors, input_hash, archive, banner slot BrowseGrid l.321-325, review scaffold); `suggestions/SearchRanker.kt`, `CategoryScorer.cosine`; `data/EmbeddingRepository.kt`; `ui/ReviewScaffold.kt`.

## Files
new `suggestions/DuplicateScan.kt`; new `ui/DuplicateReviewScreen.kt`; new `ui/DuplicateCalibrationScreen.kt`; `ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; `ui/SettingsScreen.kt`; `data/EmbeddingRepository.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `DuplicateScan.scan(items, vectors, currentPhotoHashes, dismissed: Set<PairKey> = emptySet()): List<DuplicateGroup>` — tier SamePhoto only here; `calibrationPairs(...)` for the dev screen.
2. Banner "Possible duplicates found · N groups to review" + Review in the suggestions-banner slot.
3. Review screen per mockup: Keep/Delete per garment (default Keep), "Not duplicates" (session only in DUP1), bottom "Delete N garments" with confirmation → `onDeleteItems` archive path.
4. Developer › Duplicate calibration: 30 pairs (two photos + cosine), Same/Different toggles, "Export marks" shares a JSON `[{a,b,cos,same}]`.

## Tests
V-DUP1-1, V-DUP1-2.

## Done when
V-DUP1-1, V-DUP1-2, V-DUP1-3, V-DUP1-D pass; CI green.

## Do not
Persist dismissals, add a DB table, or apply a cosine threshold.
