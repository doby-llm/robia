package com.gusanitolabs.robia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.core.designsystem.LocalReducedMotion
import com.gusanitolabs.robia.core.designsystem.extras

/** A toggle cross-fades its fill and sign over this long; under reduced motion it snaps. */
private const val CHIP_CROSSFADE_MILLIS = 150

/**
 * One add or removal chip as a switch: a 48 dp row around a 36 dp chip. A locked removal is not a switch and has no ripple.
 * [stateText] is the `review_cd_*` text, read as the state description. Taps give a light haptic tick.
 */
@Composable
internal fun ReviewChangeChip(
    label: String,
    visual: ChipVisual,
    stateText: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val switchModifier = if (visual.switch) {
        Modifier.toggleable(
            value = visual.filledSign,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .height(48.dp)
            .semantics { stateDescription = stateText }
            .then(switchModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        ReviewChipBody(label = label, visual = visual)
    }
}

/**
 * A category change as one unit: the "−" half, an arrow and the "+" half in a framed unit. The frame is ONE switch, unless the
 * "−" half is locked (a deleted category): then the frame is static and only the "+" half is the switch.
 */
@Composable
internal fun ReviewSwapChip(
    fromLabel: String,
    toLabel: String,
    removal: ChipVisual,
    addition: ChipVisual,
    frame: ChipVisual,
    stateText: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val onValueChange: (Boolean) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggle()
    }
    val frameSwitch = if (frame.switch) {
        Modifier.toggleable(value = frame.filledSign, enabled = enabled, role = Role.Switch, onValueChange = onValueChange)
    } else {
        Modifier
    }
    val halfSwitch = if (frame.switch) {
        Modifier
    } else {
        Modifier.toggleable(value = addition.filledSign, enabled = enabled, role = Role.Switch, onValueChange = onValueChange)
    }
    val outlineColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .height(48.dp)
            .semantics { stateDescription = stateText }
            .then(frameSwitch),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .drawBehind { drawChipOutline(outlineColor, dashed = frame.dashedOutline, cornerRadius = 14.dp.toPx()) }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReviewChipBody(label = fromLabel, visual = removal)
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = halfSwitch) {
                ReviewChipBody(label = toLabel, visual = addition)
            }
        }
    }
}

/**
 * The chip itself: a filled or hollow sign circle, the tag name (struck through for a removal that happens) and a solid or dashed
 * outline. Every colour comes from the review tokens or the scheme. The sign glyph is also the state, so colour is never alone.
 */
@Composable
private fun ReviewChipBody(
    label: String,
    visual: ChipVisual,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = if (visual.kind == ChipKind.Add) MaterialTheme.extras.addColors else MaterialTheme.extras.removeColors
    val on = visual.filledSign
    val textTarget = when {
        on -> palette.onContainer
        visual.kind == ChipKind.Add -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    val fill = animatedChipColor(if (on) palette.container else Color.Transparent)
    val text = animatedChipColor(textTarget)
    val sign = animatedChipColor(if (on) palette.sign else Color.Transparent)
    val glyph = animatedChipColor(if (on) palette.onSign else textTarget)
    val ring = animatedChipColor(scheme.outline)
    val outline = animatedChipColor(if (on) palette.border else scheme.outline)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .drawBehind { drawChipOutline(outline, dashed = visual.dashedOutline, cornerRadius = 10.dp.toPx()) }
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(sign)
                .then(if (on) Modifier else Modifier.border(width = 1.5.dp, color = ring, shape = CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (visual.kind == ChipKind.Add) Icons.Rounded.Add else Icons.Rounded.Remove,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = glyph,
            )
        }
        Text(
            text = label,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = text,
            textDecoration = if (visual.struck) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun animatedChipColor(target: Color): Color =
    animateColorAsState(targetValue = target, animationSpec = chipColorSpec()).value

@Composable
private fun chipColorSpec(): AnimationSpec<Color> =
    if (LocalReducedMotion.current) snap() else tween(durationMillis = CHIP_CROSSFADE_MILLIS)

/** A 1 dp outline around the chip; dashed for a change the user left out. */
private fun DrawScope.drawChipOutline(color: Color, dashed: Boolean, cornerRadius: Float) {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
        ),
    )
}
