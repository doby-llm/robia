package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.driveBackup
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding 1: a remote photo whose blob will not restore never replaces the phone's own photo reference. */
class GuardedPhotoRestoreTest {
    private val phoneUri = "file:///photos/a.jpg"

    private fun photo(
        restoredLocalUri: String? = null,
        failureCategory: String? = null,
    ): GarmentPhotoRecord = GarmentPhotoRecord(
        garmentId = "a",
        localUri = phoneUri,
        contentHash = "hash-a",
        restoredLocalUri = restoredLocalUri,
        restoreFailureCategory = failureCategory,
    )

    @Test
    fun importedPhotoUri_aRestoredFileWins() {
        assertEquals("file:///restored/a.jpg", importedPhotoUri(photo(restoredLocalUri = "file:///restored/a.jpg"), phoneHeldUri = phoneUri))
    }

    @Test
    fun importedPhotoUri_withoutARestoredFileTheSilentImportKeepsThePhonesOwnFile() {
        assertEquals(phoneUri, importedPhotoUri(photo(failureCategory = "remote_photo_blob_missing"), phoneHeldUri = phoneUri))
        assertEquals(phoneUri, importedPhotoUri(null, phoneHeldUri = phoneUri))
    }

    @Test
    fun importedPhotoUri_isNullOnlyWhenNeitherSideHasAFile() {
        assertNull(importedPhotoUri(photo(failureCategory = "remote_photo_blob_missing"), phoneHeldUri = null))
        assertNull(importedPhotoUri(photo(failureCategory = "remote_photo_blob_missing"), phoneHeldUri = ""))
        assertNull(importedPhotoUri(null, phoneHeldUri = null))
    }

    @Test
    fun merge_aGuardedRemotePhotoIsMergedAsBeforeAndKeepsItsGuard() {
        val local = exportedSnapshot(garmentEntity("a", photoUri = phoneUri))
        val backup = guardedBackup(local)

        val merged = mergeSnapshots(local, backup).photos.single()

        // The merge keeps the remote record as it was before this change. The phone's file survives the import (importedPhotoUri).
        assertEquals(phoneUri, merged.localUri)
        assertNull(merged.restoredLocalUri)
        // The guard still holds the restore's upload, so the phone's file is not uploaded over an unread remote blob.
        assertEquals("remote_photo_blob_missing", merged.restoreFailureCategory)
        assertEquals("hash-a", merged.contentHash)
    }

    @Test
    fun guardedPhotoOverPhoneFile_countsOnlyAGuardOnAGarmentThePhoneHoldsAFileFor() {
        val backup = guardedBackup(exportedSnapshot(garmentEntity("a", photoUri = phoneUri)))

        assertTrue(backup.guardedPhotoOverPhoneFile(exportedSnapshot(garmentEntity("a", photoUri = phoneUri))))
        // A fresh phone holds no file, so there is nothing for a silent restore to protect.
        assertFalse(backup.guardedPhotoOverPhoneFile(exportedSnapshot()))
        // A healthy remote photo is never a guarded one, whatever the phone holds.
        assertFalse(driveBackup(exportedSnapshot(garmentEntity("a", photoUri = phoneUri)), OTHER_DEVICE_ID, 50L)
            .guardedPhotoOverPhoneFile(exportedSnapshot(garmentEntity("a", photoUri = phoneUri))))
    }

    private fun guardedBackup(local: WardrobeSyncSnapshot): WardrobeSyncSnapshot =
        driveBackup(local, OTHER_DEVICE_ID, 50L).let { snapshot ->
            snapshot.copy(
                photos = snapshot.photos.map { it.copy(contentHash = "hash-a", restoreFailureCategory = "remote_photo_blob_missing") },
            )
        }

    @Test
    fun merge_aRestoredRemotePhotoStillWinsOverThePhonesRecord() {
        val local = exportedSnapshot(garmentEntity("a", photoUri = phoneUri))
        val backup = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = phoneUri)), OTHER_DEVICE_ID, 50L)
            .let { snapshot ->
                snapshot.copy(photos = snapshot.photos.map { it.copy(contentHash = "hash-a", restoredLocalUri = "file:///restored/a.jpg") })
            }

        val merged = mergeSnapshots(local, backup).photos.single()

        assertEquals("file:///restored/a.jpg", merged.restoredLocalUri)
    }

    @Test
    fun equivalence_aPhotoWithNoHashOnEitherSideNeverMatches() {
        // Before this fix, two photos with no hash compared equal (null == null).
        val local = exportedSnapshot(garmentEntity("a", photoUri = phoneUri))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = phoneUri)), OTHER_DEVICE_ID, 50L)
            .let { snapshot -> snapshot.copy(photos = snapshot.photos.map { it.copy(contentHash = null, sourceHash = null) }) }

        assertFalse(local.equivalentForUpload(remote))
    }
}
