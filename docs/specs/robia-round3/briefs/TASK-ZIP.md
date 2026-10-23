# TASK-ZIP — Download all my data (.zip)

## Goal
R3-11: a full local backup Manu can keep outside Drive. Works offline.

## Read first
`artifacts/inv-r3-b.md` §B3; `sync/LocalWardrobeSyncSnapshotRepository.kt` `exportSnapshot` l.58; `DriveSnapshotJson.encode`; `ui/DriveSettingsScreen.kt` l.55-247; `AndroidManifest.xml` allowBackup comment.

## Files
new `data/WardrobeZipExporter.kt`; `ui/DriveSettingsScreen.kt`; `ui/RobiaApp.kt`; `app/src/main/AndroidManifest.xml (comment only)`; new `app/src/test/java/com/gusanitolabs/robia/data/WardrobeZipExporterTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Row "Download all my data (.zip)" (`zip_export_row`, supporting `zip_export_hint` "Photos, originals and data, saved where you choose") in Drive settings, shown always.
2. `ActivityResultContracts.CreateDocument("application/zip")`, default name `robia-<yyyy-MM-dd>.zip`.
3. `WardrobeZipExporter.export(output: OutputStream, onProgress)` streams entries; progress dialog "Preparing your data… N of M" with Cancel.
4. Update the manifest comment to say Drive is the automatic backup and the zip is a user-initiated export (no attribute change).

## Tests
V-ZIP-1, V-ZIP-2.

## Done when
V-ZIP-1, V-ZIP-2, V-ZIP-D pass; CI green.

## Do not
Read anything from Drive, include models/thumbnails/caches, or buffer the archive in memory.
