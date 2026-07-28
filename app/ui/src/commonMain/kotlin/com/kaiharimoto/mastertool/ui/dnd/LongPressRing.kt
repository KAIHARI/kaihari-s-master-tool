package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

private val RING_RADIUS = 18.dp
private val RING_WIDTH = 2.5.dp

/**
 * A ring that fills under a finger while a press is becoming a long press.
 *
 * A long press with no feedback is indistinguishable from a press that is not
 * working, which is what the menu on these cards was: four hundred milliseconds
 * of nothing, and no reason to believe holding longer would help. Filling a ring
 * turns the same gesture into a mechanic — you can see it arriving, and you can
 * see that letting go early would have cancelled it.
 *
 * Drawn at [pressAt] within the card rather than at its centre, because the
 * gesture belongs to the finger and following it is what makes the ring read as
 * a response to the press rather than as part of the card.
 *
 * Fires a haptic on completion but does **not** open anything. The menu still
 * opens on release, which is what keeps holding-then-dragging possible: pressing
 * for a while must not commit you to a menu you did not want.
 */
@Composable
fun BoxScope.LongPressRing(pressAt: Offset?, durationMillis: Long) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    // Read here rather than in the draw block, which is not a composable scope.
    val accent = LocalMasterToolColors.current.accentBright

    LaunchedEffect(pressAt) {
        if (pressAt == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            // Linear: the ring is a clock, and easing it would misreport how much
            // longer the press has to be held.
            animationSpec = tween(durationMillis.toInt(), easing = LinearEasing),
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    if (pressAt == null) return

    Canvas(Modifier.matchParentSize()) {
        val swept = progress.value
        if (swept <= 0f) return@Canvas

        val radius = RING_RADIUS.toPx()
        val stroke = RING_WIDTH.toPx()

        // Track, so the ring reads as filling rather than as growing out of
        // nothing, and so it is visible against artwork of any brightness.
        drawArc(
            color = MasterToolPalette.Ink.copy(alpha = 0.45f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(pressAt.x - radius, pressAt.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke),
        )

        drawArc(
            color = accent,
            // From twelve o'clock, the only place a fill reads as time passing.
            startAngle = -90f,
            sweepAngle = 360f * swept,
            useCenter = false,
            topLeft = Offset(pressAt.x - radius, pressAt.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
