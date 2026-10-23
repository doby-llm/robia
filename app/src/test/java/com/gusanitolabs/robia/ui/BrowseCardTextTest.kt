package com.gusanitolabs.robia.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Error
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.RobiaSettings
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseCardTextTest {
    private val untitled = "Untitled garment"
    private val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }
    private val dresses = UiTag("category-dresses", "category", "Dresses")
    private val summer = UiTag("season-summer", "season", "Summer")
    private val winter = UiTag("season-winter", "season", "Winter")
    private val work = UiTag("occasion-work", "occasion", "Work")
    private val beach = UiTag("occasion-beach", "occasion", "Beach")
    private val colour = UiTag("color-red", "color", "Red")

    @Test
    fun cardTitleUsesTrimmedNameWhenPresent() {
        assertEquals("Mine", cardTitle("  Mine  ", listOf(dresses), untitled))
    }

    @Test
    fun cardTitleFallsBackToCategoryLabelWhenNameIsBlank() {
        assertEquals("Dresses", cardTitle("", listOf(dresses), untitled))
        assertEquals("Dresses", cardTitle("   ", listOf(summer, dresses), untitled))
    }

    @Test
    fun cardTitleFallsBackToUntitledWithoutCategory() {
        assertEquals(untitled, cardTitle("", listOf(summer, work), untitled))
        assertEquals(untitled, cardTitle("", emptyList(), untitled))
    }

    @Test
    fun cardMetaOrdersOccasionsThenSeasonsAlphabetically() {
        val tags = listOf(winter, work, summer, beach, dresses, colour)
        assertEquals("Beach · Work · Summer · Winter", cardMeta(tags, collator))
    }

    @Test
    fun cardMetaIsEmptyWithoutOccasionsOrSeasons() {
        assertEquals("", cardMeta(listOf(dresses, colour), collator))
    }

    @Test
    fun tileContentDescriptionUsesTitleAndMeta() {
        assertEquals("Dresses, Summer", tileContentDescription("", listOf(dresses, summer), untitled, collator))
        assertEquals("Mine, Work", tileContentDescription("Mine", listOf(work), untitled, collator))
    }

    @Test
    fun tileContentDescriptionOmitsEmptyMeta() {
        assertEquals("Mine", tileContentDescription("Mine", listOf(dresses), untitled, collator))
        assertEquals(untitled, tileContentDescription("", emptyList(), untitled, collator))
    }

    @Test
    fun photosOnlyIsTheDefaultLayoutAndRendersTiles() {
        assertEquals(BrowseLayout.PhotosOnly, RobiaSettings().browseLayout)
        assertEquals(BrowseCell.Tile, browseCellFor(BrowseLayout.PhotosOnly))
        assertEquals(BrowseCell.Card, browseCellFor(BrowseLayout.PhotosWithDetails))
    }

    @Test
    fun badgeForMatchesTheStatusTable() {
        val expected = mapOf(
            GarmentSyncStatus.Synced to null,
            GarmentSyncStatus.LocalOnly to null,
            GarmentSyncStatus.Dirty to CardBadge(Icons.Rounded.CloudUpload, R.string.badge_waiting, isError = false),
            GarmentSyncStatus.Queued to CardBadge(Icons.Rounded.CloudUpload, R.string.badge_waiting, isError = false),
            GarmentSyncStatus.FailedRetryable to CardBadge(Icons.Rounded.CloudUpload, R.string.badge_waiting, isError = false),
            GarmentSyncStatus.Running to CardBadge(Icons.Rounded.CloudUpload, R.string.badge_uploading, isError = false),
            GarmentSyncStatus.NeedsUserAction to CardBadge(Icons.Rounded.Error, R.string.badge_needs_attention, isError = true),
            GarmentSyncStatus.AuthBlocked to CardBadge(Icons.Rounded.CloudOff, R.string.badge_sign_in, isError = true),
        )
        assertEquals(GarmentSyncStatus.entries.toSet(), expected.keys)
        GarmentSyncStatus.entries.forEach { status ->
            assertEquals(status.name, expected[status], badgeFor(status))
        }
    }

    @Test
    fun photoRestoreAttentionShowsNeedsAttentionUnlessSyncErrorAlreadyShowsOne() {
        val needsAttention = CardBadge(Icons.Rounded.Error, R.string.badge_needs_attention, isError = true)
        val waiting = CardBadge(Icons.Rounded.CloudUpload, R.string.badge_waiting, isError = false)

        assertEquals(needsAttention, cardBadgeFor(GarmentSyncStatus.Synced, photoNeedsAttention = true))
        assertEquals(needsAttention, cardBadgeFor(GarmentSyncStatus.Dirty, photoNeedsAttention = true))
        assertEquals(badgeFor(GarmentSyncStatus.AuthBlocked), cardBadgeFor(GarmentSyncStatus.AuthBlocked, photoNeedsAttention = true))
        assertEquals(waiting, cardBadgeFor(GarmentSyncStatus.Dirty, photoNeedsAttention = false))
        assertNull(cardBadgeFor(GarmentSyncStatus.Synced, photoNeedsAttention = false))
    }

    private val waiting = CardBadge(Icons.Rounded.CloudUpload, R.string.badge_waiting, isError = false)
    private val uploading = CardBadge(Icons.Rounded.CloudUpload, R.string.badge_uploading, isError = false)
    private val signIn = CardBadge(Icons.Rounded.CloudOff, R.string.badge_sign_in, isError = true)
    private val needsAttention = CardBadge(Icons.Rounded.Error, R.string.badge_needs_attention, isError = true)
    private val waitingStatuses = listOf(GarmentSyncStatus.Dirty, GarmentSyncStatus.Queued, GarmentSyncStatus.FailedRetryable)

    @Test
    fun driveIsNotAddedOnlyWhenNotConfiguredOrDisabled() {
        assertEquals(
            setOf(DriveSyncConnectionStatus.NotConfigured, DriveSyncConnectionStatus.Disabled),
            DriveSyncConnectionStatus.entries.filterNot { it.isDriveEnabled }.toSet(),
        )
    }

    @Test
    fun withoutDriveNoWaitingStatusShowsAWaitingBadge() {
        listOf(DriveSyncConnectionStatus.NotConfigured, DriveSyncConnectionStatus.Disabled).forEach { drive ->
            waitingStatuses.forEach { status ->
                assertNull(
                    "$drive $status",
                    browseCardBadgeFor(status, photoNeedsAttention = false, driveEnabled = drive.isDriveEnabled, signInBannerShown = false),
                )
            }
        }
    }

    @Test
    fun withDriveWaitingStillShowsForUnsyncedGarments() {
        waitingStatuses.forEach { status ->
            assertEquals(
                status.name,
                waiting,
                browseCardBadgeFor(status, photoNeedsAttention = false, driveEnabled = true, signInBannerShown = false),
            )
        }
    }

    @Test
    fun uploadingAndPhotoAttentionAreNotHiddenByDriveOrBanner() {
        assertEquals(uploading, browseCardBadgeFor(GarmentSyncStatus.Running, false, driveEnabled = false, signInBannerShown = true))
        assertEquals(needsAttention, browseCardBadgeFor(GarmentSyncStatus.NeedsUserAction, false, driveEnabled = false, signInBannerShown = true))
        assertEquals(needsAttention, browseCardBadgeFor(GarmentSyncStatus.Synced, true, driveEnabled = false, signInBannerShown = false))
        assertEquals(needsAttention, browseCardBadgeFor(GarmentSyncStatus.Dirty, true, driveEnabled = false, signInBannerShown = false))
        assertEquals(needsAttention, browseCardBadgeFor(GarmentSyncStatus.AuthBlocked, true, driveEnabled = true, signInBannerShown = true))
        assertNull(browseCardBadgeFor(GarmentSyncStatus.Synced, false, driveEnabled = true, signInBannerShown = true))
    }

    @Test
    fun signedOutSignInBadgeStaysWhenTheBannerIsNotShown() {
        // Disconnected maps a pending garment to AuthBlocked in resolveForConnection; Drive is set up, so the rule does not apply.
        assertEquals(signIn, browseCardBadgeFor(GarmentSyncStatus.AuthBlocked, false, driveEnabled = true, signInBannerShown = false))
    }

    @Test
    fun whileTheSignInBannerShowsNoCardShowsWaitingOrSignIn() {
        (waitingStatuses + GarmentSyncStatus.AuthBlocked).forEach { status ->
            assertNull(status.name, browseCardBadgeFor(status, false, driveEnabled = true, signInBannerShown = true))
        }
    }

    @Test
    fun iconOnlyPillFromFourColumns() {
        assertFalse(badgeIconOnly(2))
        assertFalse(badgeIconOnly(3))
        assertTrue(badgeIconOnly(4))
        assertTrue(badgeIconOnly(5))
    }
}
