package com.gusanitolabs.robia.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.LocalReducedMotion
import com.gusanitolabs.robia.core.designsystem.SparkleBurst
import kotlinx.coroutines.delay

private const val REVIEW_COMPLETE_MILLIS = 1600L
private const val REVIEW_COMPLETE_REDUCED_MILLIS = 1200L
private const val REVIEW_COMPLETE_CHECK_SCALE_MILLIS = 300

/**
 * Which review was committed. Named apart from [ReviewKind], which the review walk uses. Tags and colours leave for Manage;
 * duplicates leave for Browse, where the duplicate review was opened from (REQ-FB-27).
 */
internal enum class ReviewCompleteKind { Tags, Colors, Duplicates }

/** The summary shown on the completion screen: which review, and how many garments it changed. */
internal data class ReviewCompleteText(val kind: ReviewCompleteKind, val count: Int)

/** The completion screen for a review that changed garments; null when none changed, so no screen shows. */
internal fun reviewCompleteText(kind: ReviewCompleteKind, changedGarments: Int): ReviewCompleteText? =
    if (changedGarments <= 0) null else ReviewCompleteText(kind, changedGarments)

/**
 * How long the completion screen stays before it clears itself. The caller has already moved to Manage (tags, colours) or
 * to Browse (duplicates).
 */
internal fun reviewCompleteDurationMs(reducedMotion: Boolean): Long =
    if (reducedMotion) REVIEW_COMPLETE_REDUCED_MILLIS else REVIEW_COMPLETE_MILLIS

/**
 * Short "done" moment after a tag, colour or duplicate review is committed: a check in a soft circle with a few sparks, then
 * the summary. Leaves by itself after [reviewCompleteDurationMs], or at once on a tap.
 */
@Composable
internal fun ReviewCompleteOverlay(text: ReviewCompleteText, onFinished: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val checkScale = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) checkScale.animateTo(1f, tween(durationMillis = REVIEW_COMPLETE_CHECK_SCALE_MILLIS))
    }
    LaunchedEffect(Unit) {
        delay(reviewCompleteDurationMs(reducedMotion))
        currentOnFinished()
    }
    val message = when (text.kind) {
        ReviewCompleteKind.Tags -> pluralStringResource(R.plurals.review_complete_tags, text.count, text.count)
        ReviewCompleteKind.Colors -> pluralStringResource(R.plurals.review_complete_colors, text.count, text.count)
        ReviewCompleteKind.Duplicates -> pluralStringResource(R.plurals.review_complete_duplicates, text.count, text.count)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .clickable { currentOnFinished() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(144.dp),
            ) {
                SparkleBurst(modifier = Modifier.matchParentSize(), count = 7)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = checkScale.value
                            scaleY = checkScale.value
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
