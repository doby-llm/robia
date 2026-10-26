# TASK-DUP-FAST — Duplicate scan and calibration: skip fresh hashes, block by category

## Goal
F18 (REQ-FB-18 a): the calibration run takes very long with 100+ garments. The cost is not the cosine. `scanCalibrationPairs` calls `currentPhotoHashes` for every active garment, one after the other on IO; per garment `ImageInputEncoder.encode` reads the file, decodes, scales to 1024 px, composites on white, PNG-compresses at q100 and SHA-256s it (planner estimate, not measured: 0.15-0.4 s each, so 100 garments take 15-40 s and 500 take minutes, which breaks REQ-DU-04's 2 s for 500). The pair math also builds all n(n−1)/2 pairs before the category filter and sorts them all before `take(30)` (125k pairs and about 25 MB of garbage at 500). The Browse scan (`scanDuplicates`) hashes only `sharedHashCandidates` and is cheap, but it should use the same shortcut.

## Read first
`R3-DEC-047`, `R3-DEC-051`; `ux2.md` §F18 (a); `suggestions/DuplicateScan.kt` (`scan`, `calibrationPairs`, `scanCalibrationPairs`, `sharedHashCandidates`, `currentPhotoHashes`, `eligibleGarments`, `calibrationCounts`); `data/EmbeddingRepository.kt` (`GarmentVectors`, `toGarmentVectors`); `data/local/EmbeddingEntities.kt` (`computed_at_epoch_millis`); `ui/DuplicateCalibrationScreen.kt` (the loading state from UI-CAL); `suggestions/engine/ImageInputEncoder.kt`; `DuplicateScanTest`, `DuplicateCalibrationScreenTest`.

## Files
`suggestions/DuplicateScan.kt`; `data/EmbeddingRepository.kt`; `ui/DuplicateCalibrationScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `GarmentVectors` gains `val computedAtEpochMillis: Long? = null` (the IMAGE row's `computed_at_epoch_millis`), set in `toGarmentVectors`. In memory only: no entity, DAO or Room change.
2. Pure `vectorIsFresh(item, vectors)`: true only when `computedAtEpochMillis != null` and `item.updatedAtEpochMillis <= computedAtEpochMillis` (the vector was computed after the garment last changed, so its stored `imageInputHash` is still the hash of the current photo). Unknown or newer → hash as today. **Before relying on it, list every path that replaces a garment's photo (editor Done, Quick Edit, replace photo, sync import with `photoChanged`, restore) and confirm each writes `updatedAtEpochMillis` at or after the change. If one does not, stop and ask one precise question (the rule is then unsafe).**
3. Replace `currentPhotoHashes` by a resolver taking the ids, items, vectors, an injectable `hash: suspend (String) -> String?` (the encoder + SHA in production) and `onProgress: (done: Int, total: Int) -> Unit`: fresh garments take their stored hash with no I/O; the rest are hashed with `async` bounded by `Semaphore(3)` (about 4 MB bitmap each); a null hash leaves the garment out; cancellation propagates and stops the rest. Use it in `scan` callers (`scanDuplicates`) and in `scanCalibrationPairs`, which gains an `onProgress` parameter (default no-op).
4. `calibrationPairs`: group `eligibleGarments` (sorted by id as today) by category, drop null categories, pack each block's normalised vectors into one `FloatArray`, compare `i < j` inside the block with a float dot, keep the best `top` in a bounded heap (order: cosine desc, then `a`, then `b`, exactly as the old sort) and sort only those at the end. Structure the pass as an internal function that visits every in-block pair with its cosine (`scanBlocks(garments, visit)`), so DUP2 can collect "cosine ≥ T_DUP" without a second implementation. Count the pairs examined for the complexity test (an internal counter or a returned stat).
5. `yield()` every few dozen rows of the block loop so a long calibration stays cancellable.
6. `DuplicateCalibrationScreen.kt`: collect the progress and show a determinate `LinearProgressIndicator` with "Preparing photos… N of M" (`duplicate_calibration_progress`, ES "Preparando fotos… %1$d de %2$d", DE "Fotos werden vorbereitet… %1$d von %2$d") while the hashing runs; keep the UI-CAL Loading/Empty/Pairs state function and the counts. Block `<!-- DUP-FAST -->` in the three `strings.xml`. Theme tokens only.
7. Keep a reference copy of the old all-pairs algorithm in `DuplicateScanTest` for the equality test (V-DUP-FAST-3).

## Tests
V-DUP-FAST-1..5 (CI); V-DUP-FAST-6 (REV); V-DUP-FAST-D (Manu). V-DUP1-2 (500 garments < 2 s) stays green.

## Done when
V-DUP-FAST-1..6 pass, V-DUP-FAST-D is ready for Manu; CI green. The receipt lists the photo-replacement paths of step 2 and the measured JVM time of the 500-garment test.

## Do not
Add a threshold or tier (DUP2), change the trigger (DUP-TRIGGER), touch Room, sync, the Browse banner or the top-30 selection; weaken an existing test.
