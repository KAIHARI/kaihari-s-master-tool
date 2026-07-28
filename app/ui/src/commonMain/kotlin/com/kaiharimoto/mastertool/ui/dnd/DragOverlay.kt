package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.ui.components.CardTile

/**
 * The card that follows your finger.
 *
 * Composed at the root rather than inside the pane it came from, so it is not
 * clipped by the grid it was lifted out of and stays above everything it passes
 * over.
 *
 * The pointer position is read inside `graphicsLayer`, whose block re-runs at
 * draw time and records its own reads. Reading it in the composable body instead
 * would recompose this subtree at pointer frequency, which is the single easiest
 * way to lose the frame budget on a drag.
 */
@Composable
fun DragOverlay(controller: DragController, format: Format) {
    val session = controller.session ?: return
    val density = LocalDensity.current

    val width = with(density) { session.size.width.toDp() }
    val height = with(density) { session.size.height.toDp() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(if (width > 0.dp) width else 96.dp, if (height > 0.dp) height else 140.dp)
                .graphicsLayer {
                    val point = controller.pointer
                    translationX = point.x - size.width / 2f
                    translationY = point.y - size.height / 2f
                    // Lifted off the table rather than lying on it.
                    scaleX = 1.08f
                    scaleY = 1.08f
                    rotationZ = 3f
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
