package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.SyncAttentionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** V-S09: every attention kind maps to a title, a body and at least one action; the retired copy is gone. */
class SyncAttentionMappingTest {
    @Test
    fun everyKindHasTitleBodyAndAtLeastOneAction() {
        SyncAttentionKind.values().forEach { kind ->
            val presentation = kind.presentation()
            assertTrue("$kind needs a title", presentation.titleRes != 0)
            assertTrue("$kind needs a body", presentation.bodyRes != 0)
            assertTrue("$kind needs at least one action", presentation.actions.isNotEmpty())
        }
    }

    @Test
    fun kindsHaveDistinctTitles() {
        val titles = SyncAttentionKind.values().map { kind -> kind.presentation().titleRes }
        assertEquals(titles.size, titles.toSet().size)
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
