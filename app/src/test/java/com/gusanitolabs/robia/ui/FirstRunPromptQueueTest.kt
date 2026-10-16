package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunPromptQueueTest {
    private val allPending = FirstRunPromptInputs(
        settingsLoaded = true,
        cloudAuthorizationInFlight = false,
        anotherDialogOpen = false,
        cloudSetupPending = true,
        mergeConfirmationPending = true,
        backupConflictPending = true,
        modelDownloadPending = true,
    )

    // Case 1: nothing shows before settings load, even with every prompt pending.
    @Test
    fun nothingShowsBeforeSettingsLoad() {
        assertNull(nextFirstRunPrompt(allPending.copy(settingsLoaded = false)))
    }

    // Case 2: the model prompt must not appear behind the Google account chooser.
    @Test
    fun nothingShowsWhileCloudAuthorizationIsInFlight() {
        assertNull(nextFirstRunPrompt(allPending.copy(cloudAuthorizationInFlight = true)))
        assertEquals(
            FirstRunPrompt.ModelDownload,
            nextFirstRunPrompt(
                FirstRunPromptInputs(
                    settingsLoaded = true,
                    cloudAuthorizationInFlight = false,
                    anotherDialogOpen = false,
                    cloudSetupPending = false,
                    mergeConfirmationPending = false,
                    backupConflictPending = false,
                    modelDownloadPending = true,
                ),
            ),
        )
    }

    // Case 3: no first-run prompt opens over another dialog.
    @Test
    fun nothingShowsWhileAnotherDialogIsOpen() {
        assertNull(nextFirstRunPrompt(allPending.copy(anotherDialogOpen = true)))
    }

    // Case 4: the cloud setup offer comes before everything else.
    @Test
    fun cloudSetupComesFirst() {
        assertEquals(FirstRunPrompt.CloudSetup, nextFirstRunPrompt(allPending))
    }

    // Case 5: a merge proposal comes before a backup conflict, and a conflict before the model download.
    @Test
    fun mergeThenConflictThenModel() {
        val withoutCloud = allPending.copy(cloudSetupPending = false)
        assertEquals(FirstRunPrompt.MergeConfirmation, nextFirstRunPrompt(withoutCloud))
        val withoutMerge = withoutCloud.copy(mergeConfirmationPending = false)
        assertEquals(FirstRunPrompt.BackupConflict, nextFirstRunPrompt(withoutMerge))
        val modelOnly = withoutMerge.copy(backupConflictPending = false)
        assertEquals(FirstRunPrompt.ModelDownload, nextFirstRunPrompt(modelOnly))
    }

    // Case 6: nothing pending means nothing to show.
    @Test
    fun nothingPendingShowsNothing() {
        assertNull(
            nextFirstRunPrompt(
                allPending.copy(
                    cloudSetupPending = false,
                    mergeConfirmationPending = false,
                    backupConflictPending = false,
                    modelDownloadPending = false,
                ),
            ),
        )
    }

    private val browseAtRest = ModelPromptGateInputs(
        onBrowse = true,
        syncRunning = false,
        cloudImportArmed = false,
        cloudAuthorizationInFlight = false,
        browseDialogOpen = false,
        mergeCombining = false,
        mergeOrConflictPending = false,
        emptyWardrobeRestorePending = false,
        quietEnough = true,
    )

    // Case 7: the model prompt shows only on Browse; every other route holds it.
    @Test
    fun modelPromptOnlyOnBrowse() {
        assertTrue(firstRunGateOpen(browseAtRest))
        assertFalse(firstRunGateOpen(browseAtRest.copy(onBrowse = false)))
    }

    // Case 8: a Drive download, or the window before its import starts, holds the model prompt.
    @Test
    fun modelPromptWaitsForDriveWork() {
        assertFalse(firstRunGateOpen(browseAtRest.copy(syncRunning = true)))
        assertFalse(firstRunGateOpen(browseAtRest.copy(cloudImportArmed = true)))
        assertFalse(firstRunGateOpen(browseAtRest.copy(cloudAuthorizationInFlight = true)))
    }

    // Case 9: Browse dialogs, review offers, a merge being combined and a pending merge or conflict prompt all hold it.
    @Test
    fun modelPromptWaitsForOtherDialogsAndMerge() {
        assertFalse(firstRunGateOpen(browseAtRest.copy(browseDialogOpen = true)))
        assertFalse(firstRunGateOpen(browseAtRest.copy(mergeCombining = true)))
        assertFalse(firstRunGateOpen(browseAtRest.copy(mergeOrConflictPending = true)))
    }

    // Case 10: the gate needs a calm period after the last busy transition; a new busy transition restarts it.
    @Test
    fun calmPeriodIsThreeSecondsFromLastBusyTransition() {
        assertTrue(quietEnoughSince(lastBusyAtMs = null, nowMs = 0L))
        assertFalse(quietEnoughSince(lastBusyAtMs = 10_000L, nowMs = 12_999L))
        assertTrue(quietEnoughSince(lastBusyAtMs = 10_000L, nowMs = 13_000L))
        // Busy again at 14 s: the 3 s timer restarts from that transition, not from the first calm mark.
        assertFalse(quietEnoughSince(lastBusyAtMs = 14_000L, nowMs = 15_000L))
        assertTrue(quietEnoughSince(lastBusyAtMs = 14_000L, nowMs = 17_000L))
    }

    // Case 11: an active model prompt is withdrawn when the gate closes (pending becomes false) and comes back after
    // calm, once, with no duplicate.
    @Test
    fun activeModelPromptIsWithdrawnAndReturnsAfterCalm() {
        val modelOnly = allPending.copy(
            cloudSetupPending = false,
            mergeConfirmationPending = false,
            backupConflictPending = false,
        )
        assertEquals(FirstRunPrompt.ModelDownload, nextFirstRunPrompt(modelOnly))
        val gateClosed = modelOnly.copy(modelDownloadPending = false)
        assertNull(nextFirstRunPrompt(gateClosed))
        assertEquals(FirstRunPrompt.ModelDownload, nextFirstRunPrompt(modelOnly))
    }

    // Case 12: an empty wardrobe with a Drive connection whose fresh-install restore has not run yet holds the model
    // prompt; once the attempt is recorded, the prompt is allowed.
    @Test
    fun emptyWardrobeWaitsForFreshInstallRestore() {
        assertFalse(firstRunGateOpen(browseAtRest.copy(emptyWardrobeRestorePending = true)))
        assertTrue(firstRunGateOpen(browseAtRest.copy(emptyWardrobeRestorePending = false)))
    }
}
