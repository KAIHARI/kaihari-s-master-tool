package com.kaiharimoto.mastertool.ui.inspect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.PoseMotion
import com.kaiharimoto.mastertool.core.motion.PosePhysics
import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.fx.LocalFeedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import com.kaiharimoto.mastertool.ui.theme.ChromaticText
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.archivoExpandedFamily
import com.kaiharimoto.mastertool.ui.theme.prismaticBorder
import kotlin.math.abs

/** How far the card tilts under a hand, in degrees. */
private const val MAX_TILT = 22f

/**
 * A card held in the hand: the first full-3D moment.
 *
 * One card, one perspective layer, one spring. It rises off the table into
 * your grip; dragging (or moving the mouse) tilts it in space, light glints
 * across the face opposite the tilt, an iridescent band sweeps with the
 * angle, and a tap turns it over to the card back. Everything is the pose
 * spring from `core/motion` stepped in a single frame loop — interrupting any
 * motion mid-flight just retargets it, velocity intact, which is what makes
 * it feel held rather than played back.
 *
 * A [Dialog] rather than an in-tree scrim so it stacks above any open sheet —
 * the inspector is where cards are opened from, and the moment must land on
 * top of it. Esc and back are the dialog's own dismiss.
 */
@Composable
fun CardInspect3D(
    card: Card,
    onDismiss: () -> Unit,
) {
    val feedback = LocalFeedback.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Pose state: written by the frame loop, read only inside graphicsLayer.
        var motion by remember {
            mutableStateOf(
                PoseMotion.at(
                    // Enters from low and small, lying back — picked up, not
                    // popped in.
                    Pose3(position = Vec3(0f, 900f, 0f), rotX = -35f, scale = 0.4f)
                )
            )
        }
        var tilt by remember { mutableStateOf(Offset.Zero) }
        var flipped by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            feedback.play(SoundEffect.LIFT)
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                    previous = now
                    val target = Pose3(
                        position = Vec3.Zero,
                        rotX = tilt.y * -MAX_TILT,
                        rotY = tilt.x * MAX_TILT + if (flipped) 180f else 0f,
                        scale = 1f,
                    )
                    motion = PosePhysics.step(motion, target, SpringSpec.Bouncy, dt)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    feedback.play(SoundEffect.SNAP)
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            // Which face the viewer sees, from the sprung angle — the flip
            // swaps content exactly at the edge-on moment.
            val facingAway = ((motion.pose.rotY % 360f) + 360f) % 360f in 90f..270f

            Box(
                Modifier
                    .fillMaxHeight(0.82f)
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .graphicsLayer {
                        translationY = motion.pose.position.y
                        rotationX = motion.pose.rotX
                        rotationY = motion.pose.rotY
                        scaleX = motion.pose.scale
                        scaleY = motion.pose.scale
                        cameraDistance = 18f * density
                        shadowElevation = 26.dp.toPx() * motion.pose.scale
                        shape = RoundedCornerShape(12.dp)
                    }
                    .prismaticBorder(
                        angle = motion.pose.rotY * 2f + motion.pose.rotX,
                        cornerRadius = 12.dp,
                        stroke = 2.dp,
                        alpha = (abs(tilt.x) + abs(tilt.y)).coerceIn(0.25f, 1f),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(MasterToolPalette.SurfaceRaised)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            flipped = !flipped
                            feedback.play(SoundEffect.SLIDE)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                tilt = Offset(
                                    (tilt.x + drag.x / size.width * 2.4f).coerceIn(-1f, 1f),
                                    (tilt.y + drag.y / size.height * 2.4f).coerceIn(-1f, 1f),
                                )
                            },
                            onDragEnd = { tilt = Offset.Zero },
                            onDragCancel = { tilt = Offset.Zero },
                        )
                    },
            ) {
                if (!facingAway) {
                    AsyncImage(
                        model = card.imageUrl ?: card.imageUrlSmall,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // The light: a specular pool opposite the tilt, and an
                    // iridescent band that walks across with the angle — the
                    // foil catching the room.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val centre = Offset(
                                    size.width * (0.5f - tilt.x * 0.5f),
                                    size.height * (0.5f - tilt.y * 0.5f),
                                )
                                drawRect(
                                    brush = Brush.radialGradient(
                                        listOf(
                                            Color.White.copy(
                                                alpha = 0.22f +
                                                    0.16f * (abs(tilt.x) + abs(tilt.y)),
                                            ),
                                            Color.Transparent,
                                        ),
                                        center = centre,
                                        radius = size.maxDimension * 0.8f,
                                    ),
                                    blendMode = BlendMode.Screen,
                                )

                                val band = 0.5f + tilt.x * 0.6f
                                drawRect(
                                    brush = Brush.linearGradient(
                                        (band - 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                                        band.coerceIn(0f, 1f) to
                                            MasterToolPalette.Prism[
                                                (abs(band * 6).toInt()) %
                                                    MasterToolPalette.Prism.size
                                            ].copy(alpha = 0.14f),
                                        (band + 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.Plus,
                                )
                            },
                    )
                } else {
                    // The back, counter-rotated so it reads unmirrored.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                            .background(MasterToolPalette.Surface)
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .prismaticBorder(
                                    angle = motion.pose.rotY,
                                    cornerRadius = 10.dp,
                                    stroke = 1.5.dp,
                                    alpha = 0.7f,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChromaticText(
                                "KMT",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = archivoExpandedFamily(),
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Text(
                "drag to tilt · tap to turn over · tap outside to put down",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            )
        }
    }
}
