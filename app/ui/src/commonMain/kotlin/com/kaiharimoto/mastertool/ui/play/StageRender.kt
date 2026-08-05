package com.kaiharimoto.mastertool.ui.play

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardMaterial
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Rot3
import com.kaiharimoto.mastertool.core.render.Shading
import com.kaiharimoto.mastertool.core.render.Shadows
import com.kaiharimoto.mastertool.core.render.StageRig
import com.kaiharimoto.mastertool.core.render.Tone
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Everything the play stage paints that is not a card's own picture.
 *
 * The whole file is a renderer and nothing in it decides anything: the
 * geometry, the light and the material all come out of `core/render`, already
 * solved and already tested, and what happens here is that floats become
 * paint. That split is the same one `DeckFit` and `BoardLayouter` established
 * — the arithmetic that can be wrong lives where it can be held to a test, and
 * the composable is left with no opinions to have.
 */

/** Card stock, seen edge-on. Warm, because bleached white card does not exist. */
private val CardStockColour = Color(0xFFE8E3D8)

/** The one radius every card on the stage is cut to. §8 of the handbook. */
internal val CardCornerRadius = 4.dp

/** Steps in a soft shadow. Five is where another one stops being visible. */
private const val SHADOW_STEPS = 5

/**
 * A colour under a light, which is a multiply and not an alpha.
 *
 * Fading toward transparent is what a *thinner* object does; getting darker is
 * what an unlit one does, and on a black stage the two happen to look similar
 * until something is drawn behind them, at which point only one of them is
 * still right.
 *
 * The multiply goes through [Tone] because a `Color`'s channels are sRGB, and
 * multiplying an encoding is not multiplying light: raw, a surface at half
 * illumination comes back nearer a quarter as bright, and every shaded edge on
 * the table slides toward the same muddy grey instead of staying its own
 * colour.
 */
private fun Color.shaded(amount: Float): Color = Color(
    Tone.shade(red, amount),
    Tone.shade(green, amount),
    Tone.shade(blue, amount),
    alpha,
)

// ---- the table ------------------------------------------------------------------

/**
 * The felt: a pool of light, a fall-off into the corners, and the zones.
 *
 * A true-black table cannot receive a dark shadow — there is nothing there to
 * take away — so the mat has to carry some light before anything resting on it
 * can remove any. The pool is placed *by the key light* rather than at the
 * centre of the table: a room lit from one side does not have its brightest
 * spot in the middle, and the moment the pool and the shadows disagree about
 * where the lamp is, the table stops being a place.
 */
internal fun DrawScope.drawFelt(layout: BoardLayout) {
    val mat = layout.field
    val span = max(mat.width, mat.height)
    val key = StageRig.Key.direction

    // Upstream of the light's travel: where it is coming from.
    val pool = Offset(
        x = mat.centerX - key.x * mat.width * 0.55f,
        y = mat.centerY - key.y * mat.height * 0.55f,
    )

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.062f),
                Color.White.copy(alpha = 0.022f),
                Color.Transparent,
            ),
            center = pool,
            radius = span * 0.95f,
        ),
        topLeft = Offset(mat.left - mat.width * 0.35f, mat.top - mat.height * 0.35f),
        size = Size(mat.width * 1.7f, mat.height * 1.7f),
    )

    // And the fall-off, which is what stops the pool from reading as a
    // rectangle of grey rather than as light landing on something.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, MasterToolPalette.Ink.copy(alpha = 0.55f)),
            center = Offset(mat.centerX, mat.centerY),
            radius = span * 0.78f,
        ),
        topLeft = Offset(mat.left - mat.width * 0.35f, mat.top - mat.height * 0.35f),
        size = Size(mat.width * 1.7f, mat.height * 1.7f),
    )

    layout.slots.forEach { (slot, rect) ->
        val pile = slot !is BoardSlot.Zone
        // Two lines, a hair apart: the cut edge of a groove in the mat, lit on
        // its far side and dark on its near one. The same trick as the card
        // hairline, and the reason the zones read as pressed into the felt
        // rather than printed on it.
        val inset = rect.width * 0.006f
        drawRoundRect(
            color = MasterToolPalette.Ink.copy(alpha = 0.55f),
            topLeft = Offset(rect.left, rect.top + inset),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(rect.width * 0.05f),
            style = Stroke(width = rect.width * 0.008f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = if (pile) 0.11f else 0.065f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(rect.width * 0.05f),
            style = Stroke(width = rect.width * 0.008f),
        )
    }
}

// ---- shadows ----------------------------------------------------------------------

/**
 * What a card takes out of the light under it.
 *
 * Drawn as a handful of nested copies rather than blurred, because there is no
 * blur available inside a `DrawScope` on both platforms and a shadow this soft
 * does not need one: five steps is where a sixth stops being visible. The step
 * alpha is solved so the copies *compose* to the alpha asked for rather than
 * summing past it, which is the difference between a soft shadow and a smudge.
 */
internal fun DrawScope.drawCardShadow(
    pose: Pose3,
    width: Float,
    height: Float,
    cardHeight: Float,
) {
    val shadow = Shadows.cast(pose, width, height, StageRig.Key, cardHeight) ?: return
    if (shadow.alpha <= 0.01f) return

    val corners = shadow.corners.map { Offset(it.x, it.y) }
    val middle = corners.fold(Offset.Zero) { sum, c -> sum + c } / corners.size.toFloat()

    // A hard shadow does not need five copies to look hard, and most of the
    // shadows on this table are hard — everything resting on the felt. The
    // handful that are soft are the ones in someone's hand, which is also the
    // handful anyone is looking at.
    val rings = when {
        shadow.spread < cardHeight * 0.04f -> 2
        shadow.spread < cardHeight * 0.12f -> 3
        else -> SHADOW_STEPS
    }
    val step = 1f - (1f - shadow.alpha).pow(1f / rings)

    // Outermost first, so the overlaps build toward the core.
    for (ring in rings downTo 1) {
        val grow = shadow.spread * ring / rings
        drawPath(
            path = polygon(corners, middle, grow),
            color = Color.Black.copy(alpha = step),
        )
    }

    // The tight darkness of actual contact, which is a different phenomenon
    // from the cast shadow and has to let go far faster: it belongs under the
    // card's own footprint, not under where the light throws it.
    if (shadow.contact > 0.02f) {
        val footprint = CardSolid
            .face(pose.copy(position = Vec3(pose.position.x, pose.position.y, 0f)), width, height)
            .map { Offset(it.x, it.y) }
        val centre = footprint.fold(Offset.Zero) { sum, c -> sum + c } / footprint.size.toFloat()
        drawPath(
            path = polygon(footprint, centre, cardHeight * 0.012f),
            color = Color.Black.copy(alpha = 0.5f * shadow.contact),
        )
    }
}

/** The same shape, pushed out from its own centre by [grow] pixels. */
private fun polygon(corners: List<Offset>, centre: Offset, grow: Float): Path {
    val path = Path()
    corners.forEachIndexed { index, corner ->
        val out = corner - centre
        val distance = out.getDistance()
        val point = if (distance < 0.01f) corner else corner + out / distance * grow
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

// ---- solids -------------------------------------------------------------------------

/**
 * The white edge of a card, a stack or a deck.
 *
 * This is the thing whose absence made a pile read as several rectangles
 * printed on the felt: cards are objects, objects have sides, and at fifteen
 * degrees you can see them. The faces come from `CardSolid` already
 * back-face-culled, and every corner is flattened separately — the top of a
 * deck is genuinely further from the felt than its base, and drawing the two
 * ends at the same place is what a fake stack looks like.
 */
internal fun DrawScope.drawSolidEdges(
    pose: Pose3,
    width: Float,
    height: Float,
    depth: Float,
    stage: StagePlane,
    eye: Vec3,
) {
    if (depth <= 0.4f) return

    val printed = Rot3.normal(pose)
    CardSolid.visible(CardSolid.slab(pose, width, height, depth), eye).forEach { face ->
        // Everything except the printed face, which the card's own composable
        // is about to draw over the top of this anyway.
        if (abs(face.normal dot printed) > 0.99f) return@forEach

        val path = Path()
        face.corners.forEachIndexed { index, corner ->
            val flat = stage.flatten(corner)
            if (index == 0) path.moveTo(flat.x, flat.y) else path.lineTo(flat.x, flat.y)
        }
        path.close()
        drawPath(path, CardStockColour.shaded(StageRig.face(face, eye)))
    }
}

// ---- one card's surface ----------------------------------------------------------------

/**
 * What the light does to one card, drawn over its art inside its own layer.
 *
 * Four things, in the order light does them: the face darkens as it turns from
 * the key, a specular pool sits wherever a mirror at this angle would send the
 * lamp, foil splits that pool into the prismatic ramp, and the cut edge picks
 * up a rim that brightens as the card goes edge-on.
 *
 * The pool is the one that matters. A card getting *brighter* when you tilt it
 * is a brightness animation; a pool of light that slides across the face is
 * the reason you tilt a real card to read the small print, and it is the whole
 * difference between a rectangle with a picture on it and a thing made of
 * something.
 */
internal fun DrawScope.drawCardSurface(
    pose: Pose3,
    material: CardMaterial,
    eye: Vec3,
    radiusPx: Float,
) {
    val shade = Shading.of(pose, material, StageRig.Key, eye)
    val radius = CornerRadius(radiusPx)

    // The face darkens by the same law the edges do, but a card's art is a
    // picture this cannot read, so the shading has to arrive as a veil laid
    // over it. Its opacity is solved in core rather than taken as `1 - diffuse`
    // — alpha composites in the encoding just as a raw multiply does, and a
    // face left uncorrected beside an edge that was corrected is worse than
    // neither, because then the two disagree about where the lamp is.
    val veil = Tone.veil(shade.diffuse)
    if (veil > 0.004f) {
        drawRoundRect(color = Color.Black.copy(alpha = veil), cornerRadius = radius)
    }

    if (shade.specular > 0.004f) {
        val hotspot = Offset(shade.hotspot.x * size.width, shade.hotspot.y * size.height)

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = shade.specular),
                    Color.White.copy(alpha = shade.specular * 0.3f),
                    Color.Transparent,
                ),
                center = hotspot,
                radius = size.maxDimension * 0.8f,
            ),
            cornerRadius = radius,
            blendMode = BlendMode.Plus,
        )

        // Foil. The ramp is thrown along the axis the pool is travelling on,
        // centred on the pool itself, so the colour sweeps with the tilt and
        // dies with the highlight — colour as light, never as decoration.
        if (material.iridescence > 0f) {
            val away = Offset(shade.hotspot.x - 0.5f, shade.hotspot.y - 0.5f)
            val distance = away.getDistance()
            val axis = if (distance < 0.01f) Offset(1f, 0f) else away / distance
            val reach = size.maxDimension * 0.55f
            val strength = shade.specular * material.iridescence * 0.55f
            val ramp = MasterToolPalette.Prism

            drawRoundRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.26f to ramp[0].copy(alpha = strength),
                        0.38f to ramp[1].copy(alpha = strength),
                        0.5f to ramp[2].copy(alpha = strength),
                        0.62f to ramp[3].copy(alpha = strength),
                        0.74f to ramp[4].copy(alpha = strength),
                        1f to Color.Transparent,
                    ),
                    start = hotspot - axis * reach,
                    end = hotspot + axis * reach,
                ),
                cornerRadius = radius,
                blendMode = BlendMode.Plus,
            )
        }
    }

    // The cut edge. Always faintly there, because a card has one; brighter as
    // the card turns away, because that is when you are looking along it.
    val rim = 0.09f + shade.fresnel * 0.7f
    val hairline = max(1f, size.minDimension * 0.006f)
    drawRoundRect(
        color = Color.White.copy(alpha = rim),
        topLeft = Offset(hairline / 2f, hairline / 2f),
        size = Size(size.width - hairline, size.height - hairline),
        cornerRadius = radius,
        style = Stroke(hairline),
    )
}
