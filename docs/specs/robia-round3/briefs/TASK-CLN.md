# TASK-CLN — One-time cleanup of orphan photo files

## Goal
Every pick so far left the raw copy and an intermediate PNG on disk forever (inv-r3-b.md §B2(b)5, inv-r3-e.md §E1); OR1 stops that for new photos; this removes the existing orphans once, safely.

## Read first
`artifacts/inv-r3-b.md` §B2(b); `media/ClothingImageStore.kt` l.27-46, 215-246, 310-313; `RobiaApplication.kt` start-up l.180-210; `data/local/Dao.kt` (photo URIs query).

## Files
`media/ClothingImageStore.kt`; new `media/OrphanPhotoCleanup.kt`; `RobiaApplication.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/OrphanPhotoCleanupTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Pure `planOrphanDeletion(files: List<FileInfo>, referenced: Set<String>, now: Long): List<FileInfo>` with the rules of V-CLN-1 (match by file name, compare URIs by last path segment).
2. Referenced set = all `photo_uri` + `original_uri` (archived included) + URIs of any in-memory batch drafts/editor state exposed by a small registry (`ActivePhotoRefs`) that BAT1/OR1 paths register into.
3. Run once in `RobiaApplication` (appScope, IO) after first DB access; DataStore flag `orphan_cleanup_v1_done`.

## Tests
V-CLN-1.

## Done when
V-CLN-1, V-CLN-2, V-CLN-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Delete anything outside the app image dir, any referenced file, or run on every launch.
