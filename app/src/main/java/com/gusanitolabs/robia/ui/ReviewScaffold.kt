package com.gusanitolabs.robia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.pressScale
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import kotlinx.coroutines.delay

// Shared skeleton of the review screens (colour mapping now, tag review later). Look must stay identical across them.

/** An accepted card stays this long with its accepted highlight before it starts leaving. */
internal const val REVIEW_ACCEPT_HIGHLIGHT_MILLIS = 200

/** The accepted card fades and shrinks away over this long; the same timing in both review screens. */
internal const val REVIEW_ACCEPT_EXIT_MILLIS = 250

/** A resolved card slides this far out, to the right when accepted and to the left when discarded. */
internal val REVIEW_CARD_EXIT_SLIDE = 40.dp

/** Which way a resolved review card leaves. */
internal enum class ExitDirection { Accept, Discard }

/** The picker list has a bottom fade and an option count only while more options are below the visible part. */
internal fun scrollHintVisible(value: Int, max: Int): Boolean = max > 0 && value < max

/**
 * What Discard undoes, as a pure plan built from the change that was saved. Applied through the normal save and delete
 * paths, in this order: tombstones cleared, tags and colours restored or removed, then garments put back.
 */
internal data class ReviewUndoPlan(
    val restoreTags: List<GarmentTag> = emptyList(),
    val removeTags: List<GarmentTag> = emptyList(),
    val restoreColors: List<MainColor> = emptyList(),
    val removeColors: List<MainColor> = emptyList(),
    val restoreGarments: List<ClothingItem> = emptyList(),
) {
    /** Entity type and id of each restored tag and colour; a deletion being undone left a tombstone under these keys. */
    val tombstonesToClear: List<Pair<String, String>>
        get() = restoreTags.map { tag -> "garment_tag" to tag.id } + restoreColors.map { color -> "main_color" to color.id }
}

/** What a review walks: a tag review and a colour save or delete look at photographed garments; a colour restore at every active one. */
internal enum class ReviewKind { Tag, Colour, ColourRestore }

/**
 * Whether a review has a garment to show. Without one no prompt or screen opens: the save still lands, and a colour delete
 * or restore still clears its references, applied directly by the caller.
 */
internal fun hasReviewableGarments(items: List<ClothingItem>, kind: ReviewKind): Boolean = when (kind) {
    ReviewKind.Tag, ReviewKind.ColourRestore -> items.any { item -> !item.isArchived }
    ReviewKind.Colour -> items.any(ClothingItem::isEligibleForColorReview)
}

/** Accept and Discard share a half-width slot in a two-column card: the default 24 dp side padding would squeeze the label. */
internal val ReviewCardActionPadding = PaddingValues(horizontal = 8.dp)

/**
 * Wraps a review card that is resolved, by Accept or Discard. Resolving it shows the state for a moment, then fades and
 * shrinks it away and calls [onGone], which removes it from the list. Accepted proposals stay in the screen's state for the commit.
 */
@Composable
internal fun ReviewAcceptedCard(
    resolved: Boolean,
    onGone: () -> Unit,
    direction: ExitDirection = ExitDirection.Accept,
    content: @Composable () -> Unit,
) {
    var leaving by remember { mutableStateOf(false) }
    val currentOnGone by rememberUpdatedState(onGone)
    LaunchedEffect(resolved) {
        if (resolved) {
            delay(REVIEW_ACCEPT_HIGHLIGHT_MILLIS.toLong())
            leaving = true
            delay(REVIEW_ACCEPT_EXIT_MILLIS.toLong())
            currentOnGone()
        }
    }
    val slideOffsetPx = with(LocalDensity.current) { REVIEW_CARD_EXIT_SLIDE.roundToPx() }
    val slideSign = if (direction == ExitDirection.Accept) 1 else -1
    AnimatedVisibility(
        visible = !leaving,
        exit = slideOutHorizontally(animationSpec = tween(REVIEW_ACCEPT_EXIT_MILLIS)) { slideSign * slideOffsetPx } +
            fadeOut(animationSpec = tween(REVIEW_ACCEPT_EXIT_MILLIS)) +
            shrinkVertically(animationSpec = tween(REVIEW_ACCEPT_EXIT_MILLIS)),
    ) {
        content()
    }
}

/**
 * Scrollable option list for the review pickers. While options are below the visible part, a bottom fade and an
 * "N options" caption say so, because a scroll area looks like the whole list otherwise.
 */
@Composable
internal fun ReviewPickerList(
    optionCount: Int,
    verticalSpacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val showHint = scrollHintVisible(scrollState.value, scrollState.maxValue)
    Column {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                content = content,
            )
            if (showHint) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerHigh),
                            ),
                        ),
                )
            }
        }
        if (showHint) {
            Text(
                text = pluralStringResource(R.plurals.review_picker_option_count, optionCount, optionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun ReviewProgressHeader(
    title: String,
    countText: String,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
        )
    }
}

@Composable
internal fun ReviewHelperRow(
    isScanning: Boolean,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ReviewEmptyCard(
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun BoxScope.ReviewBottomBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .height(56.dp)
                .pressScale(interactionSource),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun ReviewDiscardDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.discard_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.review_keep_reviewing))
            }
        },
    )
}
