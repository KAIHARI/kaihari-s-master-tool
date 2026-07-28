package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlinx.coroutines.delay

/** Long enough that scanning a grid does not strobe, short enough to feel free. */
private const val DWELL_MS = 350L

/**
 * Shows the card at readable size when a pointer rests on it.
 *
 * A mouse can ask a question just by pointing, and this app had no answer for
 * that: the touch design put everything behind a press, so a desktop user had to
 * open the inspector to read an effect and close it again to carry on. The
 * desktop tool being rebuilt here never had this in its deck builder either.
 *
 * Costs nothing on a tablet. Enter and Exit are pointer events that touch never
 * produces, so this simply never fires there — no platform check required.
 */
@Composable
fun HoverPreview(card: Card, content: @Composable () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(hovered, card.id) {
        if (!hovered) {
            visible = false
            return@LaunchedEffect
        }
        delay(DWELL_MS)
        visible = true
    }

    Box(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    when (awaitPointerEvent().type) {
                        PointerEventType.Enter -> hovered = true
                        PointerEventType.Exit -> hovered = false
                        // A press means the card is being used, not considered.
                        PointerEventType.Press -> hovered = false
                        else -> Unit
                    }
                }
            }
        },
    ) {
        content()

        if (visible) {
            Popup(
                popupPositionProvider = BesideAnchor,
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MasterToolPalette.Surface)
                        .border(1.dp, MasterToolPalette.Line, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                ) {
                    AsyncImage(
                        model = card.imageUrl ?: card.imageUrlSmall,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(280.dp)
                            .height(408.dp)
                            .clip(RoundedCornerShape(CARD_CORNER))
                            .foilSweep(),
                    )
                }
            }
        }
    }
}

/**
 * Places the preview beside the card, flipping and clamping to stay on screen.
 *
 * Deliberately not `TooltipBox`, which owns its own timing and is built for a
 * line of text, and not `TooltipArea`, which is desktop-only and would drag a
 * platform source set into a module that has none.
 */
private object BesideAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val toTheRight = anchorBounds.right
        val x = if (toTheRight + popupContentSize.width <= windowSize.width) {
            toTheRight
        } else {
            // No room on the right, so sit on the left of the card instead.
            (anchorBounds.left - popupContentSize.width).coerceAtLeast(0)
        }

        val y = anchorBounds.top
            .coerceAtMost(windowSize.height - popupContentSize.height)
            .coerceAtLeast(0)

        return IntOffset(x, y)
    }
}
