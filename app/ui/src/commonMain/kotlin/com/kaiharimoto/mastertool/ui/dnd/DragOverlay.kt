package com.kaiharimoto.mastertool.ui.dnd

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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

    // The pointer arrives in root coordinates, but this overlay does not sit at
    // the root origin — the scaffold's content padding (status bars, cutouts)
    // displaces it. Without subtracting its own origin the ghost trails the
    // finger by exactly that inset.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    // The pickup is sprung, not instant: the card grows into your hand with a
    // little mass, shadow rising with it.
    val pickup = remember(session) { Animatable(0f) }
    LaunchedEffect(session) {
        pickup.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        Box(
            Modifier
                .size(if (width > 0.dp) width else 96.dp, if (height > 0.dp) height else 140.dp)
                .graphicsLayer {
                    val point = controller.pointer - overlayOrigin
                    translationX = point.x - size.width / 2f
                    translationY = point.y - size.height / 2f
                    // Lifted off the table rather than lying on it.
                    val v = pickup.value
                    val grow = 1f + 0.09f * v
                    scaleX = grow
                    scaleY = grow
                    rotationZ = 3f * v
                    shadowElevation = 18.dp.toPx() * v
                }
                .alpha(0.94f),
        ) {
            CardTile(card = session.card, format = format, tactile = false)
        }
    }
}
