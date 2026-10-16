package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** K02: what the Browse cloud icon's popup says, per headline. */
class CloudStatusExplanationTest {
    @Test
    fun syncingExplainsWithoutAction() {
        assertEquals(
            CloudStatusExplanation(R.string.cloud_status_syncing),
            cloudStatusExplanation(CloudSyncHeadline.Syncing, pendingCount = 2),
        )
    }

    @Test
    fun waitingCountsTheChangesAsPlural() {
        assertEquals(
            CloudStatusExplanation(R.plurals.cloud_status_waiting_changes, quantity = 1),
            cloudStatusExplanation(CloudSyncHeadline.Waiting, pendingCount = 1),
        )
        assertEquals(
            CloudStatusExplanation(R.plurals.cloud_status_waiting_changes, quantity = 7),
            cloudStatusExplanation(CloudSyncHeadline.Waiting, pendingCount = 7),
        )
    }

    @Test
    fun attentionAndErrorOfferDataSync() {
        val expected = CloudStatusExplanation(R.string.cloud_status_attention, opensDataSync = true)
        assertEquals(expected, cloudStatusExplanation(CloudSyncHeadline.Attention, pendingCount = 0))
        assertEquals(expected, cloudStatusExplanation(CloudSyncHeadline.Error, pendingCount = 0))
    }

    @Test
    fun quietStatesHaveNoPopup() {
        listOf(
            CloudSyncHeadline.Synced,
            CloudSyncHeadline.Pending,
            CloudSyncHeadline.Disabled,
            CloudSyncHeadline.NotConfigured,
        ).forEach { headline ->
            assertNull(headline.name, cloudStatusExplanation(headline, pendingCount = 3))
        }
    }
}
