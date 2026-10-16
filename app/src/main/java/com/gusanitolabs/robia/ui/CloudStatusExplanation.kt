package com.gusanitolabs.robia.ui

import androidx.annotation.AnyRes
import com.gusanitolabs.robia.R

/**
 * What the cloud icon's popup says. [messageRes] is a plural when [quantity] is set, and [opensDataSync] adds the
 * action that opens Data sync in Settings.
 */
internal data class CloudStatusExplanation(
    @AnyRes val messageRes: Int,
    val quantity: Int? = null,
    val opensDataSync: Boolean = false,
)

/** The explanation for the Browse cloud icon, or null when the icon shows nothing that needs explaining. */
internal fun cloudStatusExplanation(headline: CloudSyncHeadline, pendingCount: Int): CloudStatusExplanation? = when (headline) {
    CloudSyncHeadline.Syncing -> CloudStatusExplanation(R.string.cloud_status_syncing)
    CloudSyncHeadline.Waiting -> CloudStatusExplanation(R.plurals.cloud_status_waiting_changes, quantity = pendingCount)
    CloudSyncHeadline.Attention,
    CloudSyncHeadline.Error -> CloudStatusExplanation(R.string.cloud_status_attention, opensDataSync = true)
    CloudSyncHeadline.Synced,
    CloudSyncHeadline.Pending,
    CloudSyncHeadline.Disabled,
    CloudSyncHeadline.NotConfigured -> null
}
