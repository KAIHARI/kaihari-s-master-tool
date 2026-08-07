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
import com.kaiharimoto.mastertool.core.scene.Scene
import com.kaiharimoto.mastertool.core.scene.SceneModel
import com.kaiharimoto.mastertool.core.scene.Surface
import com.kaiharimoto.mastertool.core.scene.TimeOfDay

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
    /** How much light lands in the middle of the pool the key throws on the mat. */
    val poolCore: Float,
    /** And at the edge of it, before it gives out entirely. */
    val poolEdge: Float,
    /** How far the pool reaches, as a share of the mat's longest side. */
    val poolReach: Float,
    /** How hard the mat darkens toward its corners. */
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
    }

    companion object {

        fun of(scene: Scene, time: TimeOfDay): StageLook = when (scene) {
            Scene.MINIMAL -> Minimal
            Scene.DESK -> when (time) {
                TimeOfDay.DAY -> DeskDay
                TimeOfDay.NIGHT -> DeskNight
            }
        }

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
            mat = Color(0xFF0A0A0E),
            shadow = Color(0xFF04060A),
            poolCore = 0.062f,
            poolEdge = 0.022f,
            poolReach = 0.95f,
            falloff = 0.55f,
        )

        /**
         * The desk in daylight: the same wood, under a window.
         *
         * The wood is dark, and that is the one place this scene argues with
         * itself. A real desk in a bright room is a large light surface, and a
         * large light surface beside a deck of cards is the anti-pattern
         * `docs/DESIGN.md` §11 lists first — chrome that competes with the
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
            mat = Color(0xFF0C0C11),
            shadow = Color(0xFF07080E),
            poolCore = 0.058f,
            poolEdge = 0.032f,
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
            mat = Color(0xFF0C0C11),
            shadow = Color(0xFF05060B),
            poolCore = 0.115f,
            poolEdge = 0.020f,
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
    model: SceneModel,
    stage: StagePlane,
    eye: Vec3,
    look: StageLook,
) {
    val eyeAt = stage.eyePoint(eye)

    model.pieces
        .sortedBy { piece -> piece.box.corners().maxOf { stage.project(it).depth } }
        .forEach { piece ->
            val colour = look.colourOf(piece.surface)
            CardSolid.visible(piece.box.faces(), eyeAt)
                .sortedBy { stage.project(it.centre).depth }
                .forEach { face ->
                    val path = Path()
                    face.corners.forEachIndexed { index, corner ->
                        val flat = stage.flatten(corner)
                        if (index == 0) path.moveTo(flat.x, flat.y) else path.lineTo(flat.x, flat.y)
                    }
                    path.close()
                    drawPath(path, colour.shaded(StageRig.face(face, eye, look.lighting)))
                }
        }
}
