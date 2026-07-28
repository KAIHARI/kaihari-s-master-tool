package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize

/**
 * How long a finger must sit still before a move counts as a drag rather than a
 * scroll. Only applies where there is something to scroll.
 */
private const val HOLD_MS = 120L

/** A press held still for this long is asking for the menu, not for a drag. */
private const val MENU_MS = 400L

/**
 * Makes its content draggable, and its long press useful when it is not dragged.
 *
 * The important parameter is [competesWithScroll]. Where the grid underneath can
 * scroll, an early finger movement is far more likely to be a scroll than a
 * drag, so the gesture waits a beat before claiming it. Where everything already
 * fits — which, with the panes sized to fit, is the normal case for a deck
 * section — there is no scroll to be confused with, so the drag starts the
 * instant the finger moves past the touch slop. No hold, no wait.
 *
 * That is the whole reason the panes size themselves: a grid that never scrolls
 * can hand its gestures straight to the card.
 *
 * One detector owns the press. A `combinedClickable` with `onLongClick` and a
 * drag detector on the same node fight over it, so long press is reported from
 * here instead. Tap is deliberately left alone — nothing is consumed unless a
 * drag actually starts, so the tile's own `clickable` still sees it.
 */
@Composable
fun DragSource(
    controller: DragController,
    key: Any,
    competesWithScroll: Boolean,
    session: () -> DragSession,
    onLongPress: () -> Unit,
    onDropped: (DragSession, DropHover?) -> Unit,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Not named `size`: `PointerInputScope` has one of its own, and a shadowed
    // name in the middle of a gesture is not something to leave for later.
    var tileSize by remember { mutableStateOf(IntSize.Zero) }
    var lifted by remember { mutableStateOf(false) }
    // Where the finger is resting, while it is still resting. Null the moment the
    // press turns into anything else, which is what cancels the ring.
    var pressAt by remember { mutableStateOf<Offset?>(null) }

    val haptics = LocalHapticFeedback.current

    Box(
        Modifier
            .onGloballyPositioned {
                origin = it.positionInRoot()
                tileSize = it.size
            }
            // The card is in your hand now, so the space it left is shown empty.
            .alpha(if (lifted) 0.3f else 1f)
            .pointerInput(key, competesWithScroll) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    val settleFor = if (competesWithScroll) HOLD_MS else 0L

                    var latest: PointerInputChange = down
                    pressAt = down.position

                    // Wait for the gesture to say what it is. Every way out of
                    // this block has to put the ring away, hence the finally.
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@awaitEachGesture
                            latest = change

                            val heldFor = change.uptimeMillis - down.uptimeMillis
                            val travelled = (change.position - down.position).getDistance()

                            if (!change.pressed) {
                                // Let go without dragging. Held still and long
                                // enough is a menu press; anything shorter is a
                                // tap, and the tile's own click handler is
                                // welcome to it.
                                if (heldFor >= MENU_MS && travelled <= slop) onLongPress()
                                return@awaitEachGesture
                            }

                            if (travelled > slop) {
                                // Moved too soon to be anything but a scroll.
                                if (heldFor < settleFor) return@awaitEachGesture
                                break
                            }
                        }
                    } finally {
                        pressAt = null
                    }

                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    lifted = true

                    val started = session().copy(size = tileSize)
                    // Started from where the finger is, not from where it landed,
                    // so the card does not jump by the slop distance.
                    controller.start(started, origin + latest.position)

                    val completed = drag(down.id) { change ->
                        change.consume()
                        controller.move(change.positionChange())
                    }

                    lifted = false
                    if (completed) {
                        onDropped(started, controller.finish())
                    } else {
                        controller.cancel()
                    }
                }
            },
    ) {
        content()
        LongPressRing(pressAt, MENU_MS)
    }
}
