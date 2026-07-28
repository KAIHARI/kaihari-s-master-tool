package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
                .alpha(0.92f),
        ) {
            CardTile(card = session.card, format = format)
        }
    }
}
