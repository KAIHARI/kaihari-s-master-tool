package com.kaiharimoto.mastertool.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.kaiharimoto.mastertool.core.visual.FoilMath
import com.kaiharimoto.mastertool.core.visual.FoilShading
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How far a card lifts out of the grid while it is being looked at. */
private const val LIFT_SCALE = 1.06f

/** Ring thickness as a fraction of the card's short side, from the original's 4% padding. */
private const val RING_FRACTION = 0.045f

/** Peak opacity of the specular wash. Above this it stops reading as a reflection. */
private const val SPECULAR_ALPHA = 0.09f

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

// The band's own colours, which are the card's foil rather than the app's palette.
private val RING_PINK = Color(0xFFFF69B4)
private val RING_CYAN = Color(0xFF00FFFF)

/**
 * Makes a card catch the light like foil.
 *
 * The arithmetic is [FoilMath], in `:core` and under test. This is only the
 * binding: a holographic ring whose band swings with the pointer, a specular
 * wash on the far side from it, and a tilt towards it.
 *
 * **Nothing here reads the pointer during composition.** It is held in snapshot
 * state and read only inside the `graphicsLayer` and draw lambdas, which run in
 * the draw phase. That is the rule `DragController` already follows and for the
 * same reason: a card that recomposed on every pointer move would recompose a
 * hundred times a second to do what a redraw does.
 *
 * The card lifts slightly and rises above its neighbours while it is lit. That
 * matters more than it would have before the gutter closed — cards in a deck
 * pane now touch, so without the lift the highlight would run underneath the
 * cards either side of it.
 */
@Composable
fun Modifier.foil(enabled: Boolean = true): Modifier {
    // Remembered unconditionally: the flag can change between compositions and
    // a conditional remember would lose the pointer when it did.
    var pointer by remember { mutableStateOf<Offset?>(null) }
    if (!enabled) return this

    return this
        .zIndex(if (pointer != null) 1f else 0f)
        .pointerInput(Unit) {
            // Watched on the Initial pass and never consumed, so the drag
            // detector wrapping this card sees every event exactly as before.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pointer = when (event.type) {
                        PointerEventType.Exit, PointerEventType.Release -> null
                        else -> event.changes.firstOrNull()?.position
                    }
                }
            }
        }
        .graphicsLayer {
            val shading = shadingFor(pointer, size) ?: return@graphicsLayer
            rotationX = shading.rotationXDegrees
            rotationY = shading.rotationYDegrees
            scaleX = LIFT_SCALE
            scaleY = LIFT_SCALE
            // cameraDistance stays at its default deliberately: its units are
            // density-relative, so a value hand-picked to flatter a deck tile
            // would distort the same card drawn at preview size.
        }
        .drawWithContent {
            drawContent()
            shadingFor(pointer, size)?.let { drawFoil(it) }
        }
}

/**
 * Foil on a card nobody is pointing at, lit by a light that moves instead.
 *
 * Touch has no hover, so on a tablet [foil] only ever fires while a finger is
 * covering the card it is lighting. This is for the two places a card is shown
 * to be looked at rather than to be picked up — the hover preview and the
 * inspector — where a slow sweep is the whole point and there is exactly one
 * card on screen to animate.
 *
 * Deliberately not used on deck tiles: forty cards each running their own
 * animation is forty layers invalidating every frame, to show an effect at a
 * size too small to read.
 */
@Composable
fun Modifier.foilSweep(periodMillis: Int = 7_000): Modifier {
    val transition = rememberInfiniteTransition(label = "foil")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear because the sweep is already an ellipse; easing it as well
            // would make the light hesitate at two arbitrary points of the loop.
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
        ),
        label = "sweep",
    )

    return this
        .graphicsLayer {
            if (size.width <= 0f || size.height <= 0f) return@graphicsLayer
            val shading = FoilMath.sweep(progress.value, size.width, size.height)
            rotationX = shading.rotationXDegrees
            rotationY = shading.rotationYDegrees
        }
        .drawWithContent {
            drawContent()
            if (size.width <= 0f || size.height <= 0f) return@drawWithContent
            drawFoil(FoilMath.sweep(progress.value, size.width, size.height))
        }
}

/**
 * Shading for a card being pointed at, or null for one that is not.
 *
 * Both callers want the same answer in the same frame, and both run in the draw
 * phase where the size is already known.
 */
private fun shadingFor(pointer: Offset?, size: Size): FoilShading? {
    if (pointer == null || size.width <= 0f || size.height <= 0f) return null
    return FoilMath.shade(pointer.x, pointer.y, size.width, size.height)
}

/**
 * The two layers over the artwork, in this order because the ring is an object
 * on the card and the specular is light falling across all of it, ring included.
 */
private fun DrawScope.drawFoil(shading: FoilShading) {
    val corner = CornerRadius(CARD_CORNER.toPx())
    val ring = size.minDimension * RING_FRACTION
    if (ring <= 0f) return

    val (start, end) = gradientEnds(shading.gradientAngleDegrees, size)
    drawRoundRect(
        brush = Brush.linearGradient(
            0.00f to Color.White,
            0.28f to RING_PINK,
            0.65f to RING_CYAN,
            1.00f to Color.White,
            start = start,
            end = end,
        ),
        // Inset by half the stroke so the ring lands inside the card rather than
        // straddling its edge, where half of it would be clipped away.
        topLeft = Offset(ring / 2f, ring / 2f),
        size = Size(size.width - ring, size.height - ring),
        cornerRadius = corner,
        style = Stroke(ring),
    )

    val tint = Color(shading.specularRed, shading.specularGreen, shading.specularBlue)
    drawRoundRect(
        brush = Brush.radialGradient(
            0f to tint.copy(alpha = SPECULAR_ALPHA),
            0.4f to tint.copy(alpha = SPECULAR_ALPHA * 0.66f),
            1f to Color.Transparent,
            center = Offset(shading.specularX * size.width, shading.specularY * size.height),
            radius = size.maxDimension,
        ),
        cornerRadius = corner,
        // Dodge rather than a plain overlay: it brightens what is already bright
        // and leaves the dark parts of the artwork alone, which is what a gloss
        // does and what a translucent white rectangle does not.
        blendMode = BlendMode.ColorDodge,
    )
}

/**
 * Where a CSS-style gradient angle starts and ends across a card.
 *
 * Zero degrees points up and the angle runs clockwise, as `linear-gradient`
 * defines it, so the constants carried over from the original still mean there
 * what they meant here.
 */
private fun gradientEnds(angleDegrees: Float, size: Size): Pair<Offset, Offset> {
    val radians = angleDegrees * DEGREES_TO_RADIANS
    val dx = sin(radians)
    val dy = -cos(radians)

    // The box's extent along the gradient's own axis.
    val length = abs(size.width * dx) + abs(size.height * dy)
    val centre = Offset(size.width / 2f, size.height / 2f)
    val half = Offset(dx * length / 2f, dy * length / 2f)

    return (centre - half) to (centre + half)
}
