package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.layout.DragPhysics
import com.kaiharimoto.mastertool.core.layout.DragSpring
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.ui.components.CardTile

private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * The card that follows your finger, a little behind it.
 *
 * Composed at the root rather than inside the pane it came from, so it is not
 * clipped by the grid it was lifted out of and stays above everything it passes
 * over.
 *
 * It trails the pointer on a spring and swings into the direction of travel.
 * A card pinned exactly to the pointer is where the illusion breaks — nothing
 * with mass tracks a hand perfectly, so something that does reads as a cursor
 * with a picture on it rather than as a card being carried. The physics is
 * `DragSpring`, in `:core` and tested, including the part where a long frame
 * gap must not launch the card off screen.
 *
 * Position is read inside `graphicsLayer`, whose block re-runs at draw time and
 * records its own reads. Reading it in the composable body instead would
 * recompose this subtree at pointer frequency, which is the single easiest way
 * to lose the frame budget on a drag.
 */
@Composable
fun DragOverlay(controller: DragController, format: Format) {
    val session = controller.session ?: return
    val density = LocalDensity.current

    val spring = remember { DragSpring() }
    // The rendered position, written once per frame. Snapshot state because the
    // layer has to redraw when it changes — which, during a drag, is every frame.
    var carried by remember { mutableStateOf(Offset.Zero) }
    var angle by remember { mutableStateOf(DragPhysics.REST_DEGREES) }

    LaunchedEffect(session.card.id, session.index) {
        // Starts on the pointer rather than springing in from wherever the last
        // drag ended.
        val start = controller.pointer
        spring.snapTo(start.x, start.y)
        carried = start

        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val target = controller.pointer
            spring.step(target.x, target.y, (now - previous) / NANOS_PER_SECOND)
            previous = now

            carried = Offset(spring.x, spring.y)
            angle = DragPhysics.angleFor(spring.lagX)
        }
    }

    val width = with(density) { session.size.width.toDp() }
    val height = with(density) { session.size.height.toDp() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(if (width > 0.dp) width else 96.dp, if (height > 0.dp) height else 140.dp)
                .graphicsLayer {
                    translationX = carried.x - size.width / 2f
                    translationY = carried.y - size.height / 2f
                    // Lifted off the table rather than lying on it.
                    scaleX = 1.08f
                    scaleY = 1.08f
                    rotationZ = angle
                }
                // The shadow the lift casts. Scale and rotation alone read as a
                // card that got bigger; a card is only off the table once
                // something underneath it says so.
                //
                // A radial gradient rather than Modifier.blur, which is a no-op
                // below Android 12 and this app's minimum is 26 -- the softness
                // has to be in the paint, not in a filter that may not run.
                .drawBehind {
                    val spread = size.width * 0.18f
                    drawOval(
                        brush = Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.55f to Color.Black.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                        ),
                        topLeft = Offset(-spread / 2f, size.height * 0.06f),
                        size = Size(size.width + spread, size.height + spread * 0.6f),
                    )
                }
                .alpha(0.92f),
        ) {
            // Already lifted, rotated and dimmed by the block above; the foil's
            // own lift and tilt on top of that reads as two effects fighting.
            CardTile(card = session.card, format = format, foil = false)
        }
    }
}
