package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

/**
 * Makes its content draggable, and its long-press useful when it is not dragged.
 *
 * There is exactly one long-press detector here on purpose. A `combinedClickable`
 * with `onLongClick` and a drag-after-long-press detector on the same node both
 * want the same gesture and fight over it, so the press is owned by the drag and
 * a press that never moved is reported back as a plain long press. Tap is left
 * alone — it is a different gesture and still belongs to the tile.
 */
@Composable
fun DragSource(
    controller: DragController,
    key: Any,
    session: () -> DragSession,
    onLongPress: () -> Unit,
    onDropped: (DragSession, DropHover?) -> Unit,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Not named `size`: `PointerInputScope` has one of its own, and a shadowed
    // name in the middle of a gesture is not something to leave for later.
    var tileSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .onGloballyPositioned {
                origin = it.positionInRoot()
                tileSize = it.size
            }
            .pointerInput(key) {
                var travel = 0f
                var active: DragSession? = null

                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        travel = 0f
                        val started = session().copy(size = tileSize)
                        active = started
                        controller.start(started, origin + local)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travel += amount.getDistance()
                        controller.move(amount)
                    },
                    onDragEnd = {
                        val landed = controller.finish()
                        val started = active
                        active = null
                        // A press that stayed put was never a drag; it was the
                        // long press it started life as.
                        if (travel < viewConfiguration.touchSlop || started == null) {
                            onLongPress()
                        } else {
                            onDropped(started, landed)
                        }
                    },
                    onDragCancel = {
                        active = null
                        controller.cancel()
                    },
                )
            },
    ) {
        content()
    }
}
