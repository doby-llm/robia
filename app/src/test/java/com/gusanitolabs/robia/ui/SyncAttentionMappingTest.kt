package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.SyncAttentionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** V-S09: every attention kind maps to its own title, body and actions; the retired copy is gone. */
class SyncAttentionMappingTest {
    @Test
    fun everyKindMapsToItsOwnTitleAndBody() {
        val presentations = SyncAttentionKind.values().map { kind -> kind.presentation() }
        assertEquals(SyncAttentionKind.values().size, presentations.map { it.titleRes }.toSet().size)
        assertEquals(SyncAttentionKind.values().size, presentations.map { it.bodyRes }.toSet().size)
        presentations.forEach { assertNotEquals(it.titleRes, it.bodyRes) }
    }

    @Test
    fun eachKindOffersExactlyItsExpectedActions() {
        assertEquals(
            mapOf(
                SyncAttentionKind.SignInRequired to listOf(SyncAttentionAction.SignIn),
                SyncAttentionKind.DriveFull to listOf(SyncAttentionAction.Retry, SyncAttentionAction.OpenStorage),
                SyncAttentionKind.PhotoUnrecoverable to listOf(SyncAttentionAction.RetryPhoto, SyncAttentionAction.KeepWithoutPhoto),
                SyncAttentionKind.PhotoUploadFailed to listOf(SyncAttentionAction.Retry),
                SyncAttentionKind.BackupConflict to listOf(SyncAttentionAction.UseBackup, SyncAttentionAction.KeepThisPhone),
                SyncAttentionKind.MergeConfirmation to listOf(SyncAttentionAction.ConfirmMerge, SyncAttentionAction.CancelMerge),
            ),
            SyncAttentionKind.values().associateWith { kind -> kind.presentation().actions },
        )
    }

    @Test
    fun conflictOffersBothResolutionsAndDriveFullOffersRetry() {
        assertEquals(
            listOf(SyncAttentionAction.UseBackup, SyncAttentionAction.KeepThisPhone),
            SyncAttentionKind.BackupConflict.presentation().actions,
        )
        assertTrue(SyncAttentionKind.DriveFull.presentation().actions.contains(SyncAttentionAction.Retry))
        assertTrue(SyncAttentionKind.PhotoUploadFailed.presentation().actions.contains(SyncAttentionAction.Retry))
    }

    @Test
    fun retiredFailedRetryableCopyIsGoneFromEveryLocale() {
        listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-es/strings.xml",
            "app/src/main/res/values-de/strings.xml",
            "app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt",
        ).forEach { relativePath ->
            assertFalse(relativePath, source(relativePath).contains("garment_sync_failed_retryable"))
        }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
            Path.of("../..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
