package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The prismatic light system.
 *
 * The identity is sharp white on black, and colour appears as *light*: the
 * chromatic fringing a lens leaves on a bright edge. Two primitives implement
 * it, both plain draws — no shaders, because there is no shader API common to
 * Android and desktop Skia, and a fringe is only ever a few strokes.
 *
 * On black the fringes are drawn additively ([BlendMode.Plus]): where the
 * offset colour passes overlap they sum back toward white, and the colour
 * survives only at the edges — exactly how aberration behaves in a photo.
 * On white the same hues are drawn multiplied ([BlendMode.Multiply]) under a
 * black core: the inversion the light theme is defined by, same colours,
 * flipped compositing.
 *
 * These are deliberately spent only on interactive or highlighted elements.
 * Fringing everything would read as decoration; fringing the thing under your
 * finger reads as light.
 */

/**
 * A chromatic-aberration edge: warm fringe pulled one way, cool fringe the
 * other, achromatic core on top.
 *
 * [dark] flips the compositing, not the colours — pass the current theme's
 * darkness (see [LocalDarkTheme]).
 */
fun Modifier.chromaticEdge(
    dark: Boolean,
    cornerRadius: Dp = 6.dp,
    stroke: Dp = 1.5.dp,
    fringe: Dp = 1.2.dp,
    intensity: Float = 1f,
): Modifier = drawBehind {
    val radius = CornerRadius(cornerRadius.toPx())
    val strokePx = stroke.toPx()
    val shift = fringe.toPx()
    val style = Stroke(strokePx)

    fun ring(color: Color, offset: Offset, blend: BlendMode) {
        drawRoundRect(
            color = color,
            topLeft = offset,
            cornerRadius = radius,
            style = style,
            blendMode = blend,
        )
    }

    if (dark) {
        val blend = BlendMode.Plus
        ring(
            MasterToolPalette.FringeWarm.copy(alpha = 0.85f * intensity),
            Offset(-shift, -shift * 0.4f),
            blend,
        )
        ring(
            MasterToolPalette.FringeCool.copy(alpha = 0.85f * intensity),
            Offset(shift, shift * 0.4f),
            blend,
        )
        ring(Color.White.copy(alpha = 0.9f * intensity), Offset.Zero, BlendMode.SrcOver)
    } else {
        val blend = BlendMode.Multiply
        ring(
            MasterToolPalette.FringeWarm.copy(alpha = 0.55f * intensity),
            Offset(-shift, -shift * 0.4f),
            blend,
        )
        ring(
            MasterToolPalette.FringeCool.copy(alpha = 0.55f * intensity),
            Offset(shift, shift * 0.4f),
            blend,
        )
        ring(Color.Black.copy(alpha = 0.9f * intensity), Offset.Zero, BlendMode.SrcOver)
    }
}

/**
 * A ring of the full prismatic ramp, swept around the element at [angle]
 * degrees. The successor to the legacy tool's `--angle`-driven border.
 *
 * Static at a fixed angle, alive when driven by [animatedPrismAngle] or by a
 * pointer position — the caller owns the motion, this owns the paint.
 */
fun Modifier.prismaticBorder(
    angle: Float,
    cornerRadius: Dp = 6.dp,
    stroke: Dp = 2.dp,
    alpha: Float = 1f,
): Modifier = drawBehind {
    drawPrismRing(angle, cornerRadius.toPx(), stroke.toPx(), alpha)
}

private fun DrawScope.drawPrismRing(
    angle: Float,
    cornerRadiusPx: Float,
    strokePx: Float,
    alpha: Float,
) {
    // Sweep gradients start at 3 o'clock; rotating the *canvas* around the
    // centre is cheaper than rebuilding the brush per frame.
    val ramp = MasterToolPalette.Prism
    val brush = Brush.sweepGradient(colors = ramp + ramp.first())
    rotate(angle) {
        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(cornerRadiusPx),
            style = Stroke(strokePx),
            alpha = alpha,
        )
    }
}

/**
 * The original tool's card border, and it is a **foil** rather than a fringe.
 *
 * ## What it is
 *
 * `legacy/kai master tool.html:27676` drew it on every card in the deck grid,
 * and this is that effect rather than an interpretation of it: a ring inset four
 * per cent of the card's width, filled with a linear gradient of white → hot
 * pink → cyan → white at an angle that follows the pointer, plus a shimmer over
 * the art in the same two hues. The angle arithmetic is the legacy driver's
 * (`:18862`) verbatim — 135° at rest, swung ±25° across and ∓15° down — because
 * the *rate* is what makes it read as light on a surface being tilted rather
 * than as a rotating decoration, and that rate was tuned by somebody looking at
 * it.
 *
 * ## Why it is not [prismaticBorder]
 *
 * That one sweeps the six-hue ramp all the way round the element and turns on a
 * timer. It means "this is the card you asked for" — a reveal, a search hit —
 * and it is drawn *outside* the card, behind it, so it reads as a glow around
 * something. This is the opposite in every one of those respects: two hues, not
 * six; inside the card, over the art; and it moves only when a hand does. The
 * two coexist because they say different things, and `CardTile` lets the
 * highlight win when a card is wearing both.
 *
 * ## The shimmer, and why it is skipped without a pointer
 *
 * `mix-blend-mode: color-dodge` in the original, [BlendMode.ColorDodge] here.
 * Dodge on a dark surface is nearly a no-op and on a bright one it blows out
 * fast, which is exactly what a foil does. It is drawn only when [highlight] is
 * non-null — a card nobody is touching has no highlight to place, and a shimmer
 * parked in the middle of every card in a ninety-card grid is a smudge.
 *
 * @param angleDegrees the gradient's CSS angle: 0 is up, 90 is right.
 * @param highlight where the pointer is, normalised to −1..1 from the centre,
 *   or null when nothing is on the card.
 */
fun DrawScope.drawPrismaticInset(
    angleDegrees: Float,
    cornerRadiusPx: Float,
    /** Ring thickness, as a fraction of the card's width — the CSS `padding: 4%`. */
    thickness: Float = FOIL_INSET,
    highlight: Offset? = null,
    alpha: Float = 1f,
) {
    if (alpha <= 0.001f || size.minDimension <= 2f) return

    val ring = (size.width * thickness).coerceAtLeast(1f)

    // The gradient line, in CSS's convention: 0° points up, angles run
    // clockwise. Screen y is down, so "up" is (0, −1) and "right" is (1, 0).
    val radians = angleDegrees * (PI.toFloat() / 180f)
    val dx = sin(radians)
    val dy = -cos(radians)
    // How long the gradient has to be to cover the box at this angle. The
    // standard projection of the two half-extents onto the gradient's own
    // direction — short of it, the last stop lands before the far corner and
    // the ring goes flat white in one corner at some angles and not others.
    val span = abs(size.width * dx) + abs(size.height * dy)
    val centre = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(centre.x - dx * span / 2f, centre.y - dy * span / 2f)
    val end = Offset(centre.x + dx * span / 2f, centre.y + dy * span / 2f)

    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White,
            0.28f to MasterToolPalette.FoilPink,
            0.65f to MasterToolPalette.FoilCyan,
            1f to Color.White,
            start = start,
            end = end,
        ),
        topLeft = Offset(ring / 2f, ring / 2f),
        size = Size(
            (size.width - ring).coerceAtLeast(0f),
            (size.height - ring).coerceAtLeast(0f),
        ),
        // The stroke's own centreline is half a ring inside the edge, so the
        // corner it traces is that much tighter than the card's.
        cornerRadius = CornerRadius((cornerRadiusPx - ring / 2f).coerceAtLeast(0f)),
        style = Stroke(ring),
        alpha = alpha,
    )

    if (highlight == null) return
    val at = Offset(
        size.width * (0.5f + highlight.x * 0.5f),
        size.height * (0.5f + highlight.y * 0.5f),
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            0f to MasterToolPalette.FoilPink.copy(alpha = 0.09f * alpha),
            0.4f to MasterToolPalette.FoilCyan.copy(alpha = 0.09f * alpha),
            0.7f to MasterToolPalette.FoilCyan.copy(alpha = 0.06f * alpha),
            1f to Color.Transparent,
            center = at,
            radius = size.maxDimension * 0.75f,
        ),
        topLeft = Offset(ring, ring),
        size = Size(
            (size.width - ring * 2f).coerceAtLeast(0f),
            (size.height - ring * 2f).coerceAtLeast(0f),
        ),
        cornerRadius = CornerRadius((cornerRadiusPx - ring).coerceAtLeast(0f)),
        blendMode = BlendMode.ColorDodge,
    )
}

/**
 * Where the gradient points, for a card the pointer is [feel] across.
 *
 * The legacy driver's own arithmetic. Across matters more than down (25 against
 * 15) because a card is taller than it is wide, so an equal weighting makes the
 * long axis feel sluggish.
 */
fun foilAngleFor(feel: Offset): Float = FOIL_REST + feel.x * 25f - feel.y * 15f

/** The angle a card at rest wears: down and to the right, as the original did. */
const val FOIL_REST = 135f

/** The ring's thickness as a fraction of the card's width. The CSS `padding: 4%`. */
const val FOIL_INSET = 0.04f

/**
 * Whether cards wear [drawPrismaticInset], as `UiPreferences.prismaticCards`
 * says.
 *
 * A composition local rather than a parameter because the readers are `CardTile`
 * and the play stage's `CardFace`, and both are reached through a dozen call
 * sites that have no business knowing about it — the same argument
 * [LocalDarkTheme] and `LocalCardBack` already make, and the same shape.
 *
 * Static, so toggling it invalidates the subtree once rather than subscribing
 * ninety tiles to a state object. It is read in composition and not in a draw
 * lambda: unlike a pose it changes when a person opens a menu, never per frame.
 *
 * Defaults to true so that anything standing a card up without a provider —
 * `:studio`, a preview — draws what the app draws.
 */
val LocalPrismaticCards = staticCompositionLocalOf { true }

/** A slow, continuous rotation for [prismaticBorder] — one full turn per [periodMs]. */
@Composable
fun animatedPrismAngle(periodMs: Int = 6000): State<Float> =
    rememberInfiniteTransition(label = "prism").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "prismAngle",
    )

/**
 * Text with a chromatic fringe: warm ghost one side, cool ghost the other,
 * true text on top. The wordmark treatment.
 *
 * Plain stacked draws rather than blend modes — text blending has no common
 * API worth the trouble, and at sub-pixel offsets the alpha ghosts read the
 * same. Costs two extra text layouts; use for single short lines only.
 */
@Composable
fun ChromaticText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fringe: Dp = 1.dp,
    fringeAlpha: Float = 0.75f,
) {
    Box(modifier) {
        val shift = fringe
        Text(
            text,
            style = style,
            color = MasterToolPalette.FringeWarm.copy(alpha = fringeAlpha),
            modifier = Modifier.offset(x = -shift, y = shift * 0.3f),
        )
        Text(
            text,
            style = style,
            color = MasterToolPalette.FringeCool.copy(alpha = fringeAlpha),
            modifier = Modifier.offset(x = shift, y = -shift * 0.3f),
        )
        Text(text, style = style, color = color)
    }
}
