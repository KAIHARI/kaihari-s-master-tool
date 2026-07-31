package com.kaiharimoto.mastertool.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.animatedPrismAngle
import com.kaiharimoto.mastertool.ui.theme.prismaticBorder

/** Yu-Gi-Oh! cards are 59 x 86 mm. Everything that shows one uses this ratio. */
const val CARD_ASPECT_RATIO = 59f / 86f

/**
 * A single card.
 *
 * Tap only. Long press belongs to `DragSource`, which wraps these: two detectors
 * competing for the same press is a gesture that works differently depending on
 * how fast you were.
 *
 * [tactile] is the material feel: the tile tilts toward the pointer or the
 * pressing finger, lifts with a shadow, and a specular sheen slides across it
 * opposite the tilt — a card under light, not a rectangle under a cursor. The
 * gesture layer only *reads* events (Initial pass, nothing consumed), so taps,
 * long presses and drags behave exactly as before. Idle tiles cost nothing:
 * the springs only run while a pointer is on the card.
 *
 * [outline] is membership: a solid ring in a group's colour, for a card that
 * has been *chosen*. [highlighted]'s turning prismatic ring means something
 * else — "this is the one you asked for" — and wears badly on a dozen cards at
 * once, so the two are deliberately different treatments and outline wins.
 */
@Composable
fun CardTile(
    card: Card,
    modifier: Modifier = Modifier,
    format: Format = Format.TCG,
    copies: Int = 0,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    outline: Color? = null,
    tactile: Boolean = true,
    onClick: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(4.dp)
    val banStatus = card.banStatus(format)

    // A highlighted card wears the full prismatic ring, slowly turning — the
    // "look here" treatment for reveals and flashes. An outlined one is being
    // held in a group instead, and stays still.
    val ringed = highlighted && outline == null
    val prismAngle = if (ringed) animatedPrismAngle().value else 0f

    // Where the pointer sits on the card, normalised to -1..1 from the centre.
    // Offset.Zero doubles as "nobody is touching this".
    var feel by remember { mutableStateOf(Offset.Zero) }
    val feelSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 480f)
    val tiltX by animateFloatAsState(feel.y * -7f, feelSpring, label = "tiltX")
    val tiltY by animateFloatAsState(feel.x * 7f, feelSpring, label = "tiltY")
    val lift by animateFloatAsState(
        if (feel != Offset.Zero) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "lift",
    )

    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .then(
                if (tactile) {
                    Modifier
                        .graphicsLayer {
                            rotationX = tiltX
                            rotationY = tiltY
                            val grow = 1f + 0.05f * lift
                            scaleX = grow
                            scaleY = grow
                            cameraDistance = 14f * density
                            shadowElevation = lift * 14.dp.toPx()
                            this.shape = shape
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    when (event.type) {
                                        PointerEventType.Enter,
                                        PointerEventType.Press,
                                        PointerEventType.Move,
                                        -> {
                                            val position = event.changes.first().position
                                            val inside = position.x in 0f..size.width.toFloat() &&
                                                position.y in 0f..size.height.toFloat()
                                            feel = if (!inside) {
                                                Offset.Zero
                                            } else {
                                                Offset(
                                                    (position.x / size.width * 2f - 1f)
                                                        .coerceIn(-1f, 1f),
                                                    (position.y / size.height * 2f - 1f)
                                                        .coerceIn(-1f, 1f),
                                                )
                                            }
                                        }

                                        PointerEventType.Exit,
                                        PointerEventType.Release,
                                        -> feel = Offset.Zero

                                        else -> Unit
                                    }
                                }
                            }
                        }
                } else {
                    Modifier
                },
            )
            .then(
                if (ringed) {
                    Modifier.prismaticBorder(angle = prismAngle, cornerRadius = 4.dp, stroke = 2.5.dp)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(MasterToolPalette.SurfaceRaised)
            .then(
                when {
                    outline != null -> Modifier.border(2.5.dp, outline, shape)
                    ringed -> Modifier
                    else -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                },
            )
            .clickable(onClick = onClick),
    ) {
        // Drawn beneath the artwork rather than only when both URLs are absent:
        // a card whose image fails to load — the offline case this app is built
        // for — otherwise renders as a blank rectangle with nothing to read.
        Text(
            text = card.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center).padding(4.dp),
        )

        AsyncImage(
            model = card.imageUrlSmall ?: card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // The sheen: a soft light that sits opposite the tilt, the way a
        // sleeve catches the room. Skipped entirely while the card is at rest.
        if (tactile && lift > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val centre = Offset(
                            size.width * (0.5f - feel.x * 0.45f),
                            size.height * (0.5f - feel.y * 0.45f),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f * lift),
                                    Color.Transparent,
                                ),
                                center = centre,
                                radius = size.maxDimension * 0.75f,
                            ),
                            blendMode = BlendMode.Screen,
                        )
                    },
            )
        }

        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }

        if (banStatus != BanStatus.UNLIMITED) {
            BanBadge(banStatus, Modifier.align(Alignment.TopStart).padding(3.dp))
        }

        if (copies > 0) {
            CopyBadge(copies, Modifier.align(Alignment.TopEnd).padding(3.dp))
        }

        overlay()
    }
}

@Composable
private fun BanBadge(status: BanStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        BanStatus.FORBIDDEN -> "0" to MasterToolPalette.Danger
        BanStatus.LIMITED -> "1" to MasterToolPalette.Warning
        BanStatus.SEMI_LIMITED -> "2" to MasterToolPalette.MainAccent
        BanStatus.UNLIMITED -> return
    }

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MasterToolPalette.Ink,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CopyBadge(copies: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MasterToolPalette.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = copies.toString(),
            fontSize = 13.sp,
            color = MasterToolPalette.Ink,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
