package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos

/** A frame at 60Hz. Used to discard the first, unbounded, gap. */
private const val NANOS_PER_FRAME = 16_666_667L

private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Scrolls a pane while a card is held near its edge.
 *
 * Composed once at the root beside `DragOverlay`, and does nothing at all until
 * a drag starts. It has to be a clock rather than something driven from pointer
 * movement: holding a card still against the bottom of a pane produces no moves,
 * and that is exactly the gesture — you are asking to see further down, not to
 * go anywhere.
 *
 * Nothing here recomposes per frame. The effect reads the pointer from inside a
 * coroutine, and only whether a drag exists is observed by composition.
 */
@Composable
fun DragAutoScroll(controller: DragController) {
    val dragging = controller.session != null

    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect

        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = now - previous
            previous = now

            // A long gap means the app was not drawing -- backgrounded, or a slow
            // frame after the drag started. Scrolling by the real elapsed time
            // would jump the list a screenful; one frame's worth is the honest
            // amount of scrolling that was actually asked for.
            val capped = elapsed.coerceIn(0L, NANOS_PER_FRAME * 3)
            controller.autoScrollStep(capped / NANOS_PER_SECOND)
        }
    }
}
