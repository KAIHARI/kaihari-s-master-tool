package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Face
import com.kaiharimoto.mastertool.core.render.Lit
import com.kaiharimoto.mastertool.core.render.StageLighting
import com.kaiharimoto.mastertool.core.render.StageRig
import com.kaiharimoto.mastertool.core.render.Tone
import com.kaiharimoto.mastertool.core.render.LightPool
import com.kaiharimoto.mastertool.core.scene.Scene
import com.kaiharimoto.mastertool.core.scene.ScenePainter
import com.kaiharimoto.mastertool.core.scene.SceneBox
import com.kaiharimoto.mastertool.core.scene.ScenePiece
import com.kaiharimoto.mastertool.core.scene.Surface
import com.kaiharimoto.mastertool.core.scene.TimeOfDay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import kotlin.math.abs

/**
 * The room: the furniture the mat is lying on, and what is behind it.
 *
 * Everything here turns a solved scene into paint and has no opinions of its
 * own, which is the same split `DeckFit` and `BoardLayouter` established.
 * `core/scene` says where the desk is and how tall the wall stands;
 * `core/render` says how the light lands on each face; this file knows which
 * hex value a piece of wood is and nothing else.
 *
 * ## Why the look is a parameter and not snapshot state
 *
 * [StageCameraState.eye] is `mutableStateOf` and the KDoc there explains why:
 * it changes sixty times a second while the table turns, and a draw that does
 * not subscribe to it silently stops re-running — which is how every specular
 * pool on the stage once froze mid-orbit.
 *
 * A [StageLook] is the opposite case. It changes when the user picks a
 * different room, or when the hour crosses dusk, and on no other frame ever. So
 * it travels as an ordinary parameter: when it changes, the composables holding
 * it recompose once and their draw lambdas are rebuilt, which is exactly the
 * right amount of work for a thing that happens twice a day.
 */

// ---- what a room is made of ------------------------------------------------------

/** Card stock, seen edge-on. Warm, because bleached white card does not exist. */
internal val CardStockColour = Color(0xFFE8E3D8)

/** The seam between two cards in a pile: a shadow, not a line somebody drew. */
internal val CardSeamColour = Color(0xFF16130E)

/**
 * Every colour and every light the stage is drawn with, for one room.
 *
 * One value rather than a scattering of file-private constants, because the
 * moment there are two rooms every one of those constants is a place the two
 * can disagree. It carries the rig as well as the palette for the same reason
 * `StageLighting`'s own KDoc gives: a surface lit by one preset beside a surface
 * lit by another is the single failure the rig exists to make impossible, and
 * the cheapest way to guarantee it is for there to be one object to pass.
 *
 * The colours belong to the *scene* and the light belongs to the *hour*. A desk
 * is the same wood at midnight as at noon — what changes is the lamp — and
 * getting that the wrong way round is how a renderer ends up with a day palette
 * and a night palette that have to be kept in step by hand.
 */
@Immutable
internal data class StageLook(
    val scene: Scene,
    val time: TimeOfDay,
    val lighting: StageLighting,
    /** The furniture the playmat is lying on. */
    val table: Color,
    /** What is behind the desk. Dark, out of focus, present. */
    val wall: Color,
    /** What the desk is standing on. */
    val floor: Color,
    /** The window pane, when the light behind it is not doing the talking. */
    val glass: Color,
    /** The lamp: its base, its mast, and the shade that is the light. */
    val shade: Color,
    /**
     * The puzzle: the one surface on this stage with a colour of its own.
     *
     * Deliberately a dark gold rather than a bright one. Everything the shading
     * does to a surface is a *multiply* — [Tone] can darken a colour and can
     * never brighten it — so a base colour is the brightest that surface will
     * ever be, and a bright yellow here would be a bright yellow object beside
     * the cards at every angle. Dark, it is nearly black in the corner of a day
     * room and comes alight when the lamp is on it, which is the whole point of
     * having it there.
     */
    val gold: Color,
    /** The playmat itself. Near enough to ink that the cards still own the screen. */
    val mat: Color,
    /**
     * What a shadow takes the mat down to — never black.
     *
     * A shadow darker than the surface behind it is not a shadow, it is a hole.
     * `docs/AAA.md` #18 says what it should be instead and says it better: a
     * shadow on lit felt is the felt, darker and a little cooler.
     */
    val shadow: Color,
    /**
     * How bright the reflection of the lamp is on the felt, at its centre and
     * at its edge.
     *
     * Renamed from `poolCore`/`poolEdge` and **not renumbered**: they are the
     * strength of an additive highlight and always were. What changes is only
     * where that highlight goes — it used to be placed from the key's
     * *direction*, which cannot move, and it is now the mirror image of a lamp
     * that has a place, so it slides down the felt as you sit down.
     */
    val sheenCore: Float,
    val sheenEdge: Float,
    /**
     * How far the sheen spreads, and how hard the mat darkens toward its
     * corners, for a room whose key has no place.
     *
     * Only the unplaced path reads these. Where there is a real lamp both are
     * derived — the spread from the felt's roughness and the source's size, the
     * darkening from the attenuation itself — which is twelve hand-tuned numbers
     * across three rooms collapsing into the geometry.
     */
    val poolReach: Float,
    val falloff: Float,
) {
    /**
     * The colour of the light itself, for the one thing on the mat that is a
     * reflection of the lamp rather than a surface under it.
     *
     * A pool of lamplight on a rubber mat is warm because the lamp is. Taking it
     * from the rig rather than from a hex value is what stops the felt and the
     * cards disagreeing about what colour the room is lit by.
     */
    val poolColour: Color get() = Color.White.shaded(Lit(1f, lighting.key.warmth))

    fun colourOf(surface: Surface): Color = when (surface) {
        Surface.TABLE -> table
        Surface.WALL -> wall
        Surface.FLOOR -> floor
        Surface.GLASS -> glass
        Surface.SHADE -> shade
        Surface.GOLD -> gold
    }

    companion object {

        /**
         * The palette for a room, and the rig it is lit by.
         *
         * The rig is handed in rather than looked up, because a placed lamp's
         * position is in mat pixels and this object has never been told how big
         * the board is. `Scenery` solves the room and its lighting together and
         * passes both here, so the two cannot end up describing different
         * tables.
         */
        fun of(scene: Scene, time: TimeOfDay, lighting: StageLighting): StageLook = when (scene) {
            Scene.MINIMAL -> Minimal
            Scene.DESK -> when (time) {
                TimeOfDay.DAY -> DeskDay
                TimeOfDay.NIGHT -> DeskNight
            }
        }.copy(lighting = lighting)

        /**
         * The stage as it has always been, to the hex value.
         *
         * These numbers were file-private constants in `StageRender.kt` and are
         * unchanged. The argument for the two pool alphas, kept because it is
         * still true and still unfinished: they were chosen against solids that
         * came back up to a fifth darker than they now do, before the shading
         * stopped multiplying an sRGB encoding, so the pool is currently a touch
         * dim for the objects standing in it. Re-tuning it is a look-at-the-
         * tablet judgement rather than a correctness fix.
         */
        private val Minimal = StageLook(
            scene = Scene.MINIMAL,
            time = TimeOfDay.NIGHT,
            lighting = StageLighting.Minimal,
            table = Color(0xFF141519),
            wall = Color(0xFF141519),
            floor = Color(0xFF0D0B09),
            glass = Color(0xFFDCE4EA),
            shade = Color(0xFFE4DAC6),
            // Never drawn: nothing stands on the minimal stage. Present because
            // a palette with a hole in it is a palette somebody fills in with a
            // literal the first time a room needs one.
            gold = Color(0xFF8C6B1F),
            mat = Color(0xFF0A0A0E),
            shadow = Color(0xFF04060A),
            sheenCore = 0.062f,
            sheenEdge = 0.022f,
            poolReach = 0.95f,
            falloff = 0.55f,
        )

        /**
         * The desk in daylight: the same wood, under a window.
         *
         * The wood is dark, and that is the one place this scene argues with
         * itself. A real desk in a bright room is a large light surface, and a
         * large light surface beside a deck of cards is the anti-pattern
         * `docs/DESIGN.md` §12 lists first — chrome that competes with the
         * cards. So the stock is a dark walnut rather than a pine, the light
         * does the brightening, and the mat stays the darkest thing in frame.
         *
         * The pool is broad and shallow because that is what a window makes.
         * Daylight arrives from the whole sky and every wall in the room, so
         * there is barely a hotspot and barely a vignette; the mat is nearly
         * evenly lit, and the shadows are what carry the shape instead.
         */
        private val DeskDay = StageLook(
            scene = Scene.DESK,
            time = TimeOfDay.DAY,
            lighting = StageLighting.DeskDay,
            table = Color(0xFF2E2419),
            wall = Color(0xFF191B22),
            floor = Color(0xFF0D0B09),
            glass = Color(0xFFDCE4EA),
            shade = Color(0xFFE4DAC6),
            // The same gold as at night, and the difference is all light: a
            // window is a broad, cool source with almost no highlight to give,
            // so by day this reads as the shape of a thing in the corner rather
            // than as metal.
            gold = Color(0xFF8C6B1F),
            mat = Color(0xFF0C0C11),
            shadow = Color(0xFF07080E),
            sheenCore = 0.058f,
            sheenEdge = 0.032f,
            poolReach = 1.05f,
            falloff = 0.34f,
        )

        /**
         * The same desk at night, with one lamp on it.
         *
         * Identical wood and identical wall — a room does not repaint itself at
         * dusk — and everything that differs is light. The pool is small, hot
         * and hard-edged because a bulb a foot above a table is very nearly a
         * point source, and the fall-off is the strongest in the app because
         * past the lamp's reach there is genuinely nothing lighting the desk.
         *
         * That fall-off is doing the work the ambient is not allowed to do. See
         * [StageLighting]: dropping the rig's ambient to buy darkness would
         * break the single-veil approximation that shades card art, so the
         * darkness is bought outside the cards instead — which is also the
         * handbook's own preference, since a card is supposed to be the
         * brightest object in the frame.
         */
        private val DeskNight = StageLook(
            scene = Scene.DESK,
            time = TimeOfDay.NIGHT,
            lighting = StageLighting.DeskNight,
            table = Color(0xFF2E2419),
            wall = Color(0xFF191B22),
            floor = Color(0xFF0D0B09),
            glass = Color(0xFFDCE4EA),
            shade = Color(0xFFE4DAC6),
            // The same gold. A room does not repaint its ornaments at dusk
            // either, and the whole difference between the two is the lamp —
            // which is a point source close by, so this is the one surface in
            // the app that gets a real moving highlight rather than a wash.
            gold = Color(0xFF8C6B1F),
            mat = Color(0xFF0C0C11),
            shadow = Color(0xFF05060B),
            sheenCore = 0.115f,
            sheenEdge = 0.020f,
            poolReach = 0.62f,
            falloff = 0.74f,
        )
    }
}

// ---- the two helpers every surface on the stage goes through -----------------------

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
 * Three channels rather than one, because the rig's lamps have temperatures and
 * a lamp's colour only exists once it has landed on something. [Lit] works out
 * the three multipliers itself so that nothing here — and nothing in any other
 * renderer — can invent its own mapping from a warmth to a colour.
 */
internal fun Color.shaded(lit: Lit): Color = Color(
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
internal fun List<Face>.facingTheCamera(stage: StagePlane, eye: Vec3): List<Face> =
    CardSolid.visible(this, stage.eyePoint(eye))
        .sortedBy { stage.project(it.centre).depth }

// ---- the room ----------------------------------------------------------------------

/**
 * Everything past the felt, painted from furthest to nearest.
 *
 * ## The sort is per object first, per face second
 *
 * Faces inside one box are ordered by the depth of their own centres, which is
 * what `facingTheCamera` has always done and is right for a convex solid.
 * *Between* boxes that is not enough, and the wall is the counter-example that
 * proves it: it stands behind the desk and its front face reaches up into the
 * air, so the centre of that face is nearer the camera than the middle of the
 * desk top even though every part of the wall is further away in the room. Sort
 * by face centres alone and the wall paints over the desk.
 *
 * So a box is ordered by the closest point it reaches — the largest depth of its
 * eight corners — which is the ordinary painter's rule for solids that do not
 * interpenetrate, and none of them do. It costs eight projections per piece,
 * for a room that is two pieces.
 *
 * ## Why the top face is not special-cased
 *
 * `drawTable` used to skip the face whose normal pointed straight up and draw a
 * flat `drawRect` for it afterwards, on the true observation that `flatten` is
 * the identity at z = 0. That was an optimisation for the one solid that was
 * guaranteed to be lying flat, and the room is full of solids that are not. The
 * general path costs four flattens that happen to be identities and removes the
 * only place the table could have been drawn by different arithmetic than
 * everything standing on it.
 */
internal fun DrawScope.drawScene(
    pieces: List<ScenePiece>,
    stage: StagePlane,
    eye: Vec3,
    look: StageLook,
    pool: LightPool? = null,
    prop: Prop? = null,
) {
    if (pieces.isEmpty() && prop == null) return
    val eyeAt = stage.eyePoint(eye)

    // The prop joins the sort as a *box* and is drawn from its own faces. That
    // is the whole trick, and it is what lets `ScenePainter` stay ignorant of
    // any shape that is not axis-aligned: what the painter's algorithm needs
    // from an object is a separating axis, and a bounding box that shares no
    // volume with anything supplies one exactly as well as the real solid would.
    // `PuzzleTest.itStandsClearOfEverythingElseInTheRoom` is what makes that
    // last clause true, and it is the precondition, not a nicety.
    val standIn = prop?.let { ScenePiece(name = "prop", surface = it.surface, box = it.box) }
    val propFaces = prop?.faces.orEmpty()
    val all = if (standIn == null) pieces else pieces + standIn

    ScenePainter
        .order(all, eyeAt) { box -> box.corners().maxOf { stage.project(it).depth } }
        .forEach { piece ->
            drawSolid(
                faces = if (piece === standIn) propFaces else piece.box.faces(),
                stage = stage,
                eyeAt = eyeAt,
                eye = eye,
                look = look,
                colour = look.colourOf(piece.surface),
                emission = piece.emission,
                pool = pool,
            )
        }
}

/**
 * A solid in the room that is not furniture: ordered by a box, drawn from its
 * own faces.
 *
 * Two shapes for one object, and the split is the point. [box] is everything it
 * could ever occupy — turned to its diagonal and lifted all the way — which is
 * what the paint order needs and is the *only* thing it needs. [faces] is where
 * it actually is this frame.
 *
 * Painting it last instead was the first version, on the reasoning that it
 * stands alone on the bare left of the desk with nothing between it and the
 * camera. That reasoning quietly assumed a camera in front of the table, and
 * yaw on this stage is free: walk round past about a hundred and forty-five
 * degrees and you are looking at the room from behind its own wall, where the
 * header above the window is genuinely nearer to you than the desk is. Measured
 * seated at 150°, the puzzle drew through it over a 269 by 280 pixel patch.
 *
 * It casts no shadow, and that is deliberate rather than unfinished. Nothing
 * else in this room casts one — the lamp does not, the desk does not — because
 * shadows here belong to the cards, which are what the stage is about. One
 * object throwing a shadow onto a desk where nothing else does would not read as
 * better lighting; it would read as the one thing that had been given special
 * treatment. `docs/AAA.md` #61d is where that gets revisited, with the lamp.
 */
internal class Prop(
    val surface: Surface,
    val box: SceneBox,
    val faces: List<Face>,
)

/**
 * One solid, painted: back-face culled, depth-sorted among its own faces, and
 * shaded a face at a time.
 *
 * Shared by the furniture and the prop deliberately. Every rule in here was
 * learned the hard way once — which faces to skip near the lens, why an emissive
 * surface is not shaded, why the light pool goes only on a face that is both
 * level and at z = 0 — and a second copy of it is a second place for those to be
 * re-learned.
 */
private fun DrawScope.drawSolid(
    faces: List<Face>,
    stage: StagePlane,
    eyeAt: Vec3,
    eye: Vec3,
    look: StageLook,
    colour: Color,
    emission: Lit?,
    pool: LightPool?,
) {
    CardSolid.visible(faces, eyeAt)
        .sortedBy { stage.project(it.centre).depth }
        .forEach { face ->
            // Past the lens, the projection stops being a projection: it clamps
            // rather than dividing by zero, so the quad flies a few hundred
            // thousand pixels away instead of vanishing, and a stripe of desk
            // crosses the picture. Nothing in the room reaches it at any legal
            // pose today; `StagePlane.reaches` carries the argument and
            // `SceneryTest` carries the measurement.
            if (face.corners.any { !stage.reaches(it) }) return@forEach

            val path = Path()
            face.corners.forEachIndexed { index, corner ->
                val flat = stage.flatten(corner)
                if (index == 0) path.moveTo(flat.x, flat.y) else path.lineTo(flat.x, flat.y)
            }
            path.close()

            if (emission != null) {
                // A fixture is not shaded, and that is the physics rather than a
                // shortcut: how bright a lit lampshade is does not depend on what
                // is lighting the room it is in. It is also the only way to draw
                // one at all — `Tone` can darken a colour and can never brighten
                // it, so radiance has to arrive as the base colour itself.
                drawPath(path, colour.shaded(emission))
            } else {
                drawPath(path, colour.shaded(StageRig.face(face, eye, look.lighting)))
            }

            // And the light the lamp throws on it — for a surface facing straight
            // up **and lying at z = 0**, which is the desk top and nothing else.
            //
            // The plane matters as much as the normal. The pool is drawn as a
            // circle in the mat's own coordinates, which is the right shape only
            // because `StagePlane.flatten` is the identity at z = 0; on the
            // floor, thirteen hundred pixels down, the path has been flattened
            // and the gradient has not, so the two would disagree about where the
            // lamp is by more than the pool is wide. The floor is also in the
            // desk's shadow, which is the physical version of the same answer.
            if (
                pool != null &&
                emission == null &&
                face.normal.z > 0.99f &&
                abs(face.centre.z) < 0.5f
            ) {
                drawPath(path, poolBrush(pool, colour))
            }
        }
}

/**
 * The lamp's light on a flat surface, as a gradient.
 *
 * Every stop is the surface's own colour under a real [Lit] rather than a wash
 * of some other colour laid over it, so nothing here composites light over a
 * backdrop it does not know: the arithmetic is exact at each stop and only the
 * interpolation between two correct colours is an approximation.
 *
 * Drawn as a circle in the mat's own coordinates, which is right because
 * `StagePlane.flatten` is the identity at z = 0 — so the ellipse it becomes on
 * screen is the one the camera would have made. That is the same property the
 * shuffle marks rely on, and it is why a pool costs one path rather than a
 * subdivision.
 */
private fun poolBrush(pool: LightPool, surface: Color): Brush = Brush.radialGradient(
    colorStops = pool.stops.mapIndexed { index, lit ->
        (index / (pool.stops.size - 1f)) to surface.shaded(lit)
    }.toTypedArray(),
    center = Offset(pool.foot.x, pool.foot.y),
    radius = pool.radius,
)
