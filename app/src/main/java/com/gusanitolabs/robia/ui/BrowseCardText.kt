package com.gusanitolabs.robia.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.ui.graphics.vector.ImageVector
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import java.text.Collator
import java.util.Locale

internal const val BADGE_HOLD_MILLIS = 1200L

private const val CATEGORY_ID = "category"
private const val SEASON_ID = "season"
private const val OCCASION_ID = "occasion"
private const val CARD_META_SEPARATOR = " · "

internal data class CardBadge(
    val icon: ImageVector,
    val labelRes: Int,
    val isError: Boolean,
)

internal enum class BrowseCell { Tile, Card }

internal fun browseCollator(): Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

/** Name when present, otherwise the category label, otherwise the untitled fallback. */
internal fun cardTitle(name: String, tags: List<UiTag>, untitled: String): String {
    val trimmed = name.trim()
    if (trimmed.isNotBlank()) return trimmed
    return tags.firstOrNull { it.categoryId == CATEGORY_ID }?.label ?: untitled
}

/** Occasions alphabetically, then seasons alphabetically, joined with a middle dot. Empty when neither is set. */
internal fun cardMeta(tags: List<UiTag>, collator: Collator): String {
    val occasions = tags.filter { it.categoryId == OCCASION_ID }.map { it.label }.sortedWith(collator)
    val seasons = tags.filter { it.categoryId == SEASON_ID }.map { it.label }.sortedWith(collator)
    return (occasions + seasons).joinToString(CARD_META_SEPARATOR)
}

/** Accessibility label of a photos-only tile: the title, then the metadata when there is any. */
internal fun tileContentDescription(
    name: String,
    tags: List<UiTag>,
    untitled: String,
    collator: Collator = browseCollator(),
): String {
    val title = cardTitle(name, tags, untitled)
    val meta = cardMeta(tags, collator)
    return if (meta.isEmpty()) title else "$title, $meta"
}

/**
 * The line under a duplicate row: the trimmed name when not blank; else the category label and the colour, the known
 * ones joined; else the occasions and seasons; else null, so the row shows only its photo and its choices.
 */
internal fun duplicateRowLine(
    name: String,
    tags: List<UiTag>,
    colourLabel: String?,
    collator: Collator,
): String? {
    val trimmed = name.trim()
    if (trimmed.isNotBlank()) return trimmed
    val category = tags.firstOrNull { it.categoryId == CATEGORY_ID }?.label
    val identity = listOfNotNull(category, colourLabel)
        .filter { it.isNotBlank() }
        .joinToString(CARD_META_SEPARATOR)
    if (identity.isNotEmpty()) return identity
    return cardMeta(tags, collator).ifEmpty { null }
}

internal fun browseCellFor(layout: BrowseLayout): BrowseCell = when (layout) {
    BrowseLayout.PhotosOnly -> BrowseCell.Tile
    BrowseLayout.PhotosWithDetails -> BrowseCell.Card
}

/** The badge for a sync status; pass the status after `resolveForConnection`. Synced and LocalOnly show nothing. */
internal fun badgeFor(status: GarmentSyncStatus): CardBadge? = when (status) {
    GarmentSyncStatus.Synced,
    GarmentSyncStatus.LocalOnly -> null
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued,
    GarmentSyncStatus.FailedRetryable -> CardBadge(Icons.Rounded.CloudUpload, R.string.badge_waiting, isError = false)
    GarmentSyncStatus.Running -> CardBadge(Icons.Rounded.CloudUpload, R.string.badge_uploading, isError = false)
    GarmentSyncStatus.NeedsUserAction -> CardBadge(Icons.Rounded.Error, R.string.badge_needs_attention, isError = true)
    GarmentSyncStatus.AuthBlocked -> CardBadge(Icons.Rounded.CloudOff, R.string.badge_sign_in, isError = true)
}

/** A sync error keeps its own badge; otherwise photo-restore attention shows "Needs attention". */
internal fun cardBadgeFor(status: GarmentSyncStatus, photoNeedsAttention: Boolean): CardBadge? {
    val syncBadge = badgeFor(status)
    return when {
        syncBadge?.isError == true -> syncBadge
        photoNeedsAttention -> CardBadge(Icons.Rounded.Error, R.string.badge_needs_attention, isError = true)
        else -> syncBadge
    }
}

/** Statuses that wait for an upload to Drive. */
private val WAITING_BADGE_STATUSES = setOf(
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued,
    GarmentSyncStatus.FailedRetryable,
)

/**
 * The badge on a Browse card, after `resolveForConnection`. Two rules sit on top of [cardBadgeFor]:
 * without Drive added (NotConfigured or Disabled) nothing waits, so waiting statuses show no badge;
 * while the sign-in banner shows, no card shows Waiting or Sign in. Photo-restore attention is kept.
 */
internal fun browseCardBadgeFor(
    status: GarmentSyncStatus,
    photoNeedsAttention: Boolean,
    driveEnabled: Boolean,
    signInBannerShown: Boolean,
): CardBadge? {
    val hiddenByDrive = !driveEnabled && status in WAITING_BADGE_STATUSES
    val hiddenByBanner = signInBannerShown &&
        (status in WAITING_BADGE_STATUSES || status == GarmentSyncStatus.AuthBlocked)
    // A hidden sync status reads as LocalOnly, which has no badge; photo attention still applies.
    val shownStatus = if (hiddenByDrive || hiddenByBanner) GarmentSyncStatus.LocalOnly else status
    return cardBadgeFor(shownStatus, photoNeedsAttention)
}

/** From this many columns the per-card cloud pill shows only its icon; the label stays as its content description. */
internal const val ICON_ONLY_BADGE_MIN_COLUMNS = 4

internal fun badgeIconOnly(columns: Int): Boolean = columns >= ICON_ONLY_BADGE_MIN_COLUMNS

/** Keeps a shown badge for [BADGE_HOLD_MILLIS] so it does not blink; a badge that was not shown yet appears at once. */
internal fun visibleBadge(previous: CardBadge?, shownAtMs: Long, current: CardBadge?, nowMs: Long): CardBadge? =
    if (previous != null && nowMs - shownAtMs < BADGE_HOLD_MILLIS) previous else current
