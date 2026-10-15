package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
