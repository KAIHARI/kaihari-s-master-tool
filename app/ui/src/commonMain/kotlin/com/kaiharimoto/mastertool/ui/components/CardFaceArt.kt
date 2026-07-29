package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.visual.CardFace
import com.kaiharimoto.mastertool.core.visual.FrameKind
import com.kaiharimoto.mastertool.ui.theme.CardFrames
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A card, drawn, for when there is no picture of it.
 *
 * Sits under the artwork rather than instead of it, so it is what you see while
 * an image is arriving and what you see for good at a venue with no signal —
 * which is the case this whole application is built around, and which until now
 * rendered as forty grey rectangles with names written across them.
 *
 * The bands are fixed heights rather than proportions of the card. Cards in a
 * deck pane touch, so a name band that grew by a line on the long-named card
 * would step every band across the row out of line with its neighbours, and a
 * wall of that reads as broken rather than as forty cards.
 */
@Composable
fun CardFaceArt(card: Card, modifier: Modifier = Modifier) {
    val kind = remember(card.frameType) { CardFace.kindOf(card.frameType) }
    val frame = CardFrames.of(kind)
    val pips = CardFace.pips(card.level)

    BoxWithConstraints(
        modifier.background(
            Brush.linearGradient(
                0f to frame.light,
                0.52f to frame.base,
                1f to frame.dark,
                start = Offset.Zero,
                end = Offset(220f, 400f),
            ),
        ),
    ) {
        // One number drives every band, so a card in the pool at 70dp and the
        // same card in the showcase at 130dp are the same drawing rather than
        // two designs that happen to share a palette.
        val unit = maxWidth / 96f

        // Below this there is no room for a name, and 3sp of it is not a name
        // but a smear. A siding sheet's thumbnails are 34dp wide, and at that
        // size the frame colour on its own still says monster, spell or trap --
        // which is the whole of what a swatch that small can say.
        if (maxWidth < LEGIBLE) {
            Box(Modifier.fillMaxSize().padding(unit * 3)) {
                ArtWindow(card, frame.base, frame.dark)
            }
            return@BoxWithConstraints
        }

        Column(Modifier.fillMaxSize()) {
            Text(
                card.name,
                style = TextStyle(
                    fontSize = (8 * unit.value).sp,
                    lineHeight = (8.8f * unit.value).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = frame.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .height(unit * NAME_BAND)
                    .padding(start = unit * 4, end = unit * 4, top = unit * 3),
            )

            // Reserved whether or not there are stars on it, because a spell
            // beside a monster has to have its art window in the same place.
            Row(
                Modifier.height(unit * PIP_BAND).padding(horizontal = unit * 4),
                horizontalArrangement = Arrangement.spacedBy(unit * 1.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pips) {
                    Box(
                        Modifier
                            .size(unit * 4)
                            .clip(CircleShape)
                            // Ringed in the frame's ink, because gold on the gold
                            // Normal frame is a star nobody can count.
                            .background(frame.ink)
                            .padding(unit * 0.7f)
                            .clip(CircleShape)
                            .background(CardFrames.Pip),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = unit * 4),
            ) {
                ArtWindow(card, frame.base, frame.dark)

                CardFrames.attribute(card.attribute)?.let {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(unit * 3)
                            .size(unit * 7)
                            .clip(CircleShape)
                            .background(it),
                    )
                }
            }

            Row(
                Modifier
                    .height(unit * FOOT_BAND)
                    .fillMaxWidth()
                    .padding(horizontal = unit * 4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val small = tacticalStyle().copy(fontSize = (6.5f * unit.value).sp)

                Text(leftFoot(card, kind), style = small, color = frame.ink, maxLines = 1)
                Box(Modifier.weight(1f))
                Text(rightFoot(card, kind), style = small, color = frame.ink, maxLines = 1)
            }
        }
    }
}

/** The abstract field behind the art. Deterministic in the passcode. */
@Composable
private fun ArtWindow(card: Card, base: Color, dark: Color) {
    val seed = card.id.value
    val angle = remember(seed) { CardFace.angle(seed) }
    val shapes = remember(seed) { CardFace.shapes(seed) }

    Canvas(Modifier.fillMaxSize()) {
        // The wash runs across the window at the card's own angle, worked out
        // from the corner-to-corner distance so no card gets a gradient that
        // stops short of its own edge.
        val radians = angle * 2 * PI.toFloat()
        val reach = (size.width + size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val along = Offset(cos(radians) * reach, sin(radians) * reach)

        drawRect(
            brush = Brush.linearGradient(
                listOf(dark, base),
                start = centre - along,
                end = centre + along,
            ),
        )

        shapes.forEach {
            val side = minOf(size.width, size.height) * it.size
            rotate(degrees = it.turns * 360f, pivot = Offset(it.x * size.width, it.y * size.height)) {
                drawRect(
                    color = Color.White.copy(alpha = it.alpha),
                    topLeft = Offset(
                        it.x * size.width - side / 2f,
                        it.y * size.height - side / 2f,
                    ),
                    size = Size(side, side),
                )
            }
        }

        // The window's own edge, which is what makes it read as a window cut in
        // the frame rather than as a differently coloured half of the card.
        drawRect(color = Color.Black.copy(alpha = 0.45f), style = Stroke(width = 1.dp.toPx()))
    }
}

private fun leftFoot(card: Card, kind: FrameKind): String = when {
    !CardFace.isMonster(kind) -> kind.name
    else -> card.atk?.toString() ?: "?"
}

private fun rightFoot(card: Card, kind: FrameKind): String = when {
    kind == FrameKind.LINK -> card.linkValue?.let { "LINK-$it" }.orEmpty()
    !CardFace.isMonster(kind) -> ""
    else -> card.def?.toString() ?: "?"
}

/** Narrower than this and the card is a swatch rather than a face. */
private val LEGIBLE = 52.dp

// Band heights, in ninety-sixths of the card's width — the size a deck pane
// draws one at, and the size the proportions were looked at.
private const val NAME_BAND = 21f
private const val PIP_BAND = 6f
private const val FOOT_BAND = 12f
