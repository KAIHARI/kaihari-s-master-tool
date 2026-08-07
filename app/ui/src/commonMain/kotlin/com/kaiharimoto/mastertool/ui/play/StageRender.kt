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
import com.kaiharimoto.mastertool.core.layout.MatControls
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardMaterial
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Face
import com.kaiharimoto.mastertool.core.render.Lit
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

/** The seam between two cards in a pile: a shadow, not a line somebody drew. */
private val CardSeamColour = Color(0xFF16130E)

/**
 * The furniture the playmat is lying on.
 *
 * Dark, but nothing like as dark as the mat, and that gap is the whole reason
 * it exists. A true-black stage has no *place* in it: a mat drawn as gradients
 * on black is a mat floating in a void, and no amount of shading on the cards
 * makes a void into a room. Give it a table and the table has a thickness, and
 * a thickness has sides, and the sides swing into view as the camera comes down
 * — which is the one cue that says the thing you are turning is a solid object
 * rather than a picture being sheared.
 */
private val TableColour = Color(0xFF141519)

/** The playmat itself. Near enough to ink that the cards still own the screen. */
private val MatColour = Color(0xFF0A0A0E)

/**
 * What a shadow takes the mat down to — not black.
 *
 * A shadow was `Color.Black` at up to 0.66, laid over a mat that measures about
 * twelve levels of two hundred and fifty-five. It landed at **two**, which is
 * *below the void behind the table*. A shadow darker than the background is not
 * a shadow; it is a hole, and it is why nothing on this stage looks like it is
 * resting on anything.
 *
 * `docs/AAA.md` #18 says what it should be instead, and says it better than
 * this comment could: *"A shadow on lit felt is the felt, darker and a little
 * cooler. Neutral black is what a shadow looks like when nobody chose a colour
 * for it."* So this is the mat, most of its light removed and the little that
 * is left pushed cool — the colour of a surface reached only by the sky.
 *
 * It is deliberately still very dark. The real cure is that the mat has to
 * carry enough light to have some taken away, and that belongs to the daylight
 * pass rather than to a bug fix; what this changes is only that the shadow now
 * subtracts something instead of punching through to nothing.
 */
private val ShadowColour = Color(0xFF04060A)

/**
 * How far the playmat runs past the cards, and the table past the mat, in card
 * widths.
 *
 * Both were nearly three times this and both were wrong. A table margin of a
 * whole card width put a broad grey border round every side of the mat, and a
 * grey border is not furniture — on a stage whose entire premise is sharp white
 * on true black, it is a large light rectangle competing with the cards, which
 * is the one thing §11 of the handbook lists first under anti-patterns.
 *
 * What the table is for is the *edge*: the moment the camera comes down and a
 * solid side swings into view. That needs the table to extend past the mat far
 * enough to be a table and no further. A narrow reveal does the whole job.
 */
private const val MAT_MARGIN = 0.22f
private const val TABLE_MARGIN = 0.38f

/**
 * How thick the table top is, in card widths.
 *
 * A lip, not a plinth. Cut with the margin, and for the same reason: this is a
 * cue you should notice when you go looking for it at a low camera and never
 * think about at the reading seat.
 */
private const val TABLE_THICKNESS = 0.17f

/** The playmat's outline: everything the board occupies, plus its border. */
internal fun matSurface(layout: BoardLayout): Slot =
    layout.bounds.inflated(layout.cardWidth * MAT_MARGIN)

/** The table top's outline. */
internal fun tableSurface(layout: BoardLayout): Slot =
    matSurface(layout).inflated(layout.cardWidth * TABLE_MARGIN)

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
 *
 * Three channels rather than one, because the rig's lamps have temperatures now
 * and a lamp's colour only exists once it has landed on something. [Lit] works
 * out the three multipliers itself so that nothing here — and nothing in any
 * other renderer — can invent its own mapping from a warmth to a colour.
 */
private fun Color.shaded(lit: Lit): Color = Color(
    Tone.shade(red, lit.red),
    Tone.shade(green, lit.green),
    Tone.shade(blue, lit.blue),
    alpha,
)

/**
 * The faces of a solid you can actually see, in the order they must be painted.
 *
 * Two jobs and one traversal, because they are the same question asked twice.
 * [CardSolid.visible] answers *whether* a face is toward the camera, and it is
 * asked with the camera's **position** — `StagePlane.eyePoint` — rather than
 * with the direction the whole stage is lit from, because the projection those
 * faces are about to go through divides by distance and a direction is a lens
 * infinitely far away. That disagreement is what used to leave the deck with no
 * side walls to see it through.
 *
 * Then the order. A slab's walls were painted in the order the geometry happens
 * to list them — back, far, right, near, left — and near a corner-on view that
 * paints the far wall over the near one. Sorting by the depth of each face's own
 * centre is the painter's algorithm applied to six quads, which is cheap enough
 * to be beneath discussion and is the only thing that makes a solid look solid
 * from a corner.
 */
private fun List<Face>.facingTheCamera(stage: StagePlane, eye: Vec3): List<Face> =
    CardSolid.visible(this, stage.eyePoint(eye))
        .sortedBy { stage.project(it.centre).depth }

// ---- the table ------------------------------------------------------------------

/**
 * The table: a top, and the sides of it you can see from where you are sitting.
 *
 * The same slab the cards are, at fifty times the size, and that is not a joke
 * about reuse — it is the reason this is eleven lines. A table is a rectangular
 * solid with a thickness lying flat on the stage, which is precisely what
 * [CardSolid.slab] describes and [CardSolid.visible] culls, so it gets the
 * back-face culling, the per-face normals and the rig's three lamps for free and
 * cannot end up lit by a different room than the cards standing on it.
 *
 * Drawn before everything, because it is under everything. The one face that is
 * skipped is the top, which is drawn flat afterwards: it sits at z = 0, where
 * [StagePlane.flatten] is the identity, so it needs no geometry at all.
 */
internal fun DrawScope.drawTable(layout: BoardLayout, stage: StagePlane, eye: Vec3) {
    val table = tableSurface(layout)
    val thickness = layout.cardWidth * TABLE_THICKNESS
    val pose = Pose3(position = Vec3(table.centerX, table.centerY, 0f))

    CardSolid.slab(pose, table.width, table.height, thickness)
        .facingTheCamera(stage, eye)
        .forEach { face ->
            // The top, which is the next thing drawn and is drawn flat.
            if (face.normal.z > 0.99f) return@forEach

            val path = Path()
            face.corners.forEachIndexed { index, corner ->
                val flat = stage.flatten(corner)
                if (index == 0) path.moveTo(flat.x, flat.y) else path.lineTo(flat.x, flat.y)
            }
            path.close()
            drawPath(path, TableColour.shaded(StageRig.face(face, eye)))
        }

    drawRect(
        color = TableColour.shaded(StageRig.lit(Rot3.FaceNormal, eye)),
        topLeft = Offset(table.left, table.top),
        size = Size(table.width, table.height),
    )
}

/**
 * The playmat: a pool of light, a fall-off into the corners, and the zones.
 *
 * A true-black table cannot receive a dark shadow — there is nothing there to
 * take away — so the mat has to carry some light before anything resting on it
 * can remove any. The pool is placed *by the key light* rather than at the
 * centre of the table: a room lit from one side does not have its brightest
 * spot in the middle, and the moment the pool and the shadows disagree about
 * where the lamp is, the table stops being a place.
 *
 * The mat covers the hand's band as well as the field, which it did not before.
 * A hand fanned out on bare void beneath a mat that stopped short of it was the
 * one place on this stage where the cards were demonstrably resting on nothing.
 */
internal fun DrawScope.drawFelt(layout: BoardLayout) {
    val mat = matSurface(layout)
    val span = max(mat.width, mat.height)
    val key = StageRig.Key.direction

    val corner = CornerRadius(layout.cardWidth * 0.16f)
    val matTopLeft = Offset(mat.left, mat.top)
    val matSize = Size(mat.width, mat.height)

    // The mat itself, as a surface rather than as an absence.
    drawRoundRect(color = MatColour, topLeft = matTopLeft, size = matSize, cornerRadius = corner)

    // Upstream of the light's travel: where it is coming from.
    val pool = Offset(
        x = mat.centerX - key.x * mat.width * 0.55f,
        y = mat.centerY - key.y * mat.height * 0.55f,
    )

    // These two alphas were picked by eye against the old, darker solids: a
    // fully facing pile edge used to come back at 0.66 and now comes back at
    // 0.79, because the shading it goes through stopped multiplying an sRGB
    // encoding (see `Tone`). So the pool is currently a touch dim for the
    // objects standing in it. Re-tuning it is a look-at-the-tablet judgement
    // rather than a correctness fix, which is why it deliberately did not ride
    // in with the encoding change — but the next pass over the felt should know
    // that these numbers were chosen for a lamp that has since got brighter.
    //
    // Both are bounded by the mat now rather than by a rectangle half again its
    // size. That was harmless while there was nothing outside the mat to spoil;
    // with a table under it, the fall-off's job — darkening toward the corners —
    // was being done to the table's corners too, and eating the one surface that
    // tells you the mat is lying on something.
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.062f),
                Color.White.copy(alpha = 0.022f),
                Color.Transparent,
            ),
            center = pool,
            radius = span * 0.95f,
        ),
        topLeft = matTopLeft,
        size = matSize,
        cornerRadius = corner,
    )

    // And the fall-off, which is what stops the pool from reading as a
    // rectangle of grey rather than as light landing on something.
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, MasterToolPalette.Ink.copy(alpha = 0.55f)),
            center = Offset(mat.centerX, mat.centerY),
            radius = span * 0.78f,
        ),
        topLeft = matTopLeft,
        size = matSize,
        cornerRadius = corner,
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
    /**
     * How deep the body under this face is, so a pile shadows from the felt.
     *
     * Without it a deck is shadowed as its top card, which is forty cards up in
     * the air: the shadow walks out from under the deck by the deck's own
     * height, softens as though it were being held, and loses the contact
     * darkness that is the only thing saying it is *resting*. The deck then
     * reads as a card hovering beside two floating white edges. See
     * [Shadows.cast].
     */
    bodyDepth: Float = 0f,
) {
    // `shadow.alpha` is an opacity somebody chose rather than a fraction of
    // light — `Shadows` keeps the constant behind it private for that reason —
    // so the sRGB correction that went through the solids left it alone. There
    // is no `1 - light` in it to correct.
    //
    // It is not untouched by that change, though. It was tuned against solids
    // that came back up to a fifth darker than they now do, so a card currently
    // throws the shadow of a dimmer lamp than the one lighting its own edges.
    // That is a judgement to make with the tablet in hand rather than
    // arithmetic, and it is left for the next tuning pass instead of guessed at.
    val shadow = Shadows.cast(pose, width, height, StageRig.Key, cardHeight, bodyDepth = bodyDepth)
        ?: return
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
            color = ShadowColour.copy(alpha = step),
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
            color = ShadowColour.copy(alpha = 0.5f * shadow.contact),
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
 * printed on the felt: cards are objects, objects have sides, and at any angle
 * the table is seen from you can see them. The faces come from `CardSolid`
 * already back-face-culled, and every corner is flattened separately — the top
 * of a deck is genuinely further from the felt than its base, and drawing the
 * two ends at the same place is what a fake stack looks like.
 *
 * The printed face and the back are both skipped, which is one test rather than
 * two: the back's normal is the printed one negated, so the absolute value of
 * the dot product catches both. That is also what guarantees everything reaching
 * the seam code below is a *side* quad, whose corners run front, front, back,
 * back — the ordering the ruling depends on.
 */
internal fun DrawScope.drawSolidEdges(
    pose: Pose3,
    width: Float,
    height: Float,
    depth: Float,
    stage: StagePlane,
    eye: Vec3,
    /** How many cards are in this body, so its side can be ruled into that many. */
    layers: Int = 1,
) {
    if (depth <= 0.4f) return

    val printed = Rot3.normal(pose)
    // A single card is square with itself; a pile is not, and the slouch is what
    // says so from directly overhead, where its height has gone.
    val lean = if (layers > 1) CardSolid.pileLean(depth, width) else Vec3.Zero

    CardSolid.slab(pose, width, height, depth, lean)
        .facingTheCamera(stage, eye)
        .forEach { face ->
        // Everything except the printed face, which the card's own composable
        // is about to draw over the top of this anyway.
        if (abs(face.normal dot printed) > 0.99f) return@forEach

        // The face's four corners come round as: two on the printed face, then
        // the same two on the back. So 0→3 and 1→2 are the two edges that run
        // down through the body, which is the axis the cards are stacked along.
        val flat = face.corners.map { stage.flatten(it) }

        // A face the eye is level with presents no area, and card stock at full
        // brightness over no area is a bright white line lying across whatever
        // is behind it. `visible` cannot catch this — the normal says the face
        // is comfortably toward us — so it is caught here, where the projection
        // has already had its say. See `CardSolid.MIN_DRAWN_AREA`.
        if (CardSolid.flatArea(flat.map { Vec2(it.x, it.y) }) < CardSolid.MIN_DRAWN_AREA) {
            return@forEach
        }

        val path = Path()
        flat.forEachIndexed { index, at ->
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        path.close()
        drawPath(path, CardStockColour.shaded(StageRig.face(face, eye)))

        // And the seams. A pile's side is not a white band — it is thirty cards
        // seen end-on, and the dark lines between them are most of what says so.
        // Without them a deck reads as a solid block of stock, which is a
        // difference you can see instantly and cannot name until it is fixed:
        // the block is a *ramp*, and a ramp has no count you can read off it.
        val down = Offset(flat[3].x - flat[0].x, flat[3].y - flat[0].y).getDistance()
        val seams = CardSolid.layerLines(layers, down)
        if (seams == 0) return@forEach

        val seam = CardSeamColour.shaded(StageRig.face(face, eye))
        for (line in 1..seams) {
            val t = line.toFloat() / (seams + 1)
            drawLine(
                color = seam,
                start = Offset(
                    flat[0].x + (flat[3].x - flat[0].x) * t,
                    flat[0].y + (flat[3].y - flat[0].y) * t,
                ),
                end = Offset(
                    flat[1].x + (flat[2].x - flat[1].x) * t,
                    flat[1].y + (flat[2].y - flat[1].y) * t,
                ),
                strokeWidth = 1f,
            )
        }
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
    // neither, because then the two disagree about where the lamp is. One alpha
    // cannot be exact for every channel of a picture, and `Tone.MID_TONE` says
    // which way it misses — dark art comes back a little bright — and how much
    // worse that gets if the rig's ambient is ever brought down.
    val veil = Tone.veil(shade.diffuse)
    if (veil > 0.004f) {
        drawRoundRect(color = Color.Black.copy(alpha = veil), cornerRadius = radius)
    }

    if (shade.specular > 0.004f) {
        val hotspot = Offset(shade.hotspot.x * size.width, shade.hotspot.y * size.height)
        // A highlight is a reflection of the lamp rather than of the room, so
        // it is the one place a temperature arrives undiluted. Everywhere else
        // on this table the rig's white ambient washes most of it back out.
        val lamp = Color.White.shaded(shade.lamp)

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    lamp.copy(alpha = shade.specular),
                    lamp.copy(alpha = shade.specular * 0.3f),
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

// ---- the two things on the table that are not cards -----------------------------

/** The chip a control is printed on: the felt, lifted just enough to be a thing. */
private val ControlColour = Color(0xFF1A1A21)

/**
 * The shuffle marks, lying on the felt under the deck and the extra deck.
 *
 * A mark rather than a label, and that is not a style choice — the mat is a
 * tilted plane, so a word printed on it keystones, and eight-point type read at
 * twenty-one degrees off square is a smudge. Two arrows crossing is the one
 * glyph that means shuffle in every card game anybody has played, and an arrow
 * survives being seen at an angle in a way a letterform does not.
 *
 * Drawn flat at z = 0, where `StagePlane.flatten` is the identity, so this needs
 * no geometry at all — it turns with the table because the table's own layer
 * turns it, which is the whole point of it being *on* the table.
 *
 * This is the first thing ever drawn on this felt that is not a card, and
 * `docs/DESIGN.md` §10 says there should be nothing. The exception is argued for
 * where the geometry lives, in `MatControls`.
 */
internal fun DrawScope.drawMatControls(layout: BoardLayout, field: PlayField) {
    MatControls.all(layout).forEach { (control, slot) ->
        // A control for a pile that is not there is a button that does nothing,
        // and a button that does nothing is worse than no button.
        if (field.pile(control.pile).size < 2) return@forEach

        val radius = CornerRadius(slot.height * 0.28f)
        drawRoundRect(
            color = ControlColour,
            topLeft = Offset(slot.left, slot.top),
            size = Size(slot.width, slot.height),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = MasterToolPalette.LineLight,
            topLeft = Offset(slot.left, slot.top),
            size = Size(slot.width, slot.height),
            cornerRadius = radius,
            style = Stroke(max(1f, slot.height * 0.045f)),
        )

        drawShuffleMark(slot, MasterToolPalette.TextMuted)
    }
}

/**
 * Two arrows crossing, drawn inside [slot].
 *
 * Built from the slot rather than from constants so it is the same mark at every
 * card size the fitter can solve for — the board resizes with the deck, and a
 * glyph in absolute pixels would be a postage stamp on one board and a poster on
 * another.
 */
private fun DrawScope.drawShuffleMark(slot: Slot, colour: Color) {
    val inset = slot.height * 0.28f
    val left = slot.left + inset * 1.4f
    val right = slot.right - inset * 1.4f
    val top = slot.top + inset
    val bottom = slot.bottom - inset
    val stroke = max(1f, slot.height * 0.075f)
    val head = slot.height * 0.16f

    // Two paths that cross in the middle, each ending in an arrowhead — one
    // going down the way, one going up, which is what says "these two swapped".
    listOf(top to bottom, bottom to top).forEach { (from, to) ->
        val path = Path().apply {
            moveTo(left, from)
            cubicTo(
                left + (right - left) * 0.35f, from,
                right - (right - left) * 0.35f, to,
                right, to,
            )
        }
        drawPath(path, colour, style = Stroke(stroke))

        // The head, pointing the way the line arrived.
        val point = Path().apply {
            moveTo(right, to)
            lineTo(right - head, to - head * 0.62f)
            lineTo(right - head, to + head * 0.62f)
            close()
        }
        drawPath(point, colour)
    }
}
