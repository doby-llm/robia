# TASK-BAT2 — Cutout pack download: Cancel and auto-resume on Wi-Fi

## Goal
#135 D4(e), accepted by Manu: the pack download needs Cancel and should resume by itself when Wi-Fi returns.

## Read first
`media/cutout/CutoutPack.kt` l.55-100; `suggestions/model/ModelStore.kt` download/resume l.92-160; `suggestions/model/ModelDownloadWorker.kt`; `ui/PreciseCutoutSettings.kt` l.80-100, 290-300.

## Files
`media/cutout/CutoutPack.kt`; `suggestions/model/ModelDownloadWorker.kt`; `ui/PreciseCutoutSettings.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutPackDownloadTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Generalise the worker with an input key selecting the pack or the embedding model (or add `CutoutPackDownloadWorker` mirroring it); enqueue unique `cutout_pack_download`, `NetworkType.UNMETERED` unless the user allowed mobile data.
2. Settings: while downloading show "Cancel" (`cutout_pack_cancel`); cancel = `cancelUniqueWork`, partial files kept.

## Tests
V-BAT2-1.

## Done when
V-BAT2-1, V-BAT2-D pass; CI green.

## Do not
Change pack contents, hashes or the embedding-model worker behaviour.
