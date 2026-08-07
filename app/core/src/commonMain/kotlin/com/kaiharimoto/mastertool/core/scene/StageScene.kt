package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.render.StageLighting
import kotlinx.serialization.Serializable

/**
 * Which room the table is in.
 *
 * A preference rather than a constant, on exactly the argument `docs/AAA.md`
 * #67 makes: rubber playmats are matte and a bare table is not, and which one
 * you are playing on is the user's decision rather than the renderer's.
 *
 * [MINIMAL] is the stage `docs/DESIGN.md` describes and it does not change. The
 * desk scenes are a *different contract*, written down in that handbook's
 * "Scenes" section: they may hold objects that are there because they are nice
 * rather than because they are needed, which is decoration, which minimal mode
 * bans. What survives in both is the rule that nothing idles — a room that
 * breathes when nobody is touching it is an engine with nothing to say.
 */
@Serializable
enum class Scene {
    MINIMAL,
    DESK,
    ;

    val displayName: String
        get() = when (this) {
            MINIMAL -> "Minimal"
            DESK -> "Desk"
        }
}

/** Which lamp is on in the desk scene, or whether to let the clock decide. */
@Serializable
enum class DeskLight {
    AUTO,
    DAY,
    NIGHT,
    ;

    val displayName: String
        get() = when (this) {
            AUTO -> "Auto"
            DAY -> "Day"
            NIGHT -> "Night"
        }
}

/** The answer [DeskLight] resolves to: a window, or a lamp. */
enum class TimeOfDay { DAY, NIGHT }

/**
 * What time it is, as far as a room is concerned.
 *
 * A pure function of a setting and an hour so that it can be tested without a
 * clock, which is the only reason it is a separate thing from reading the time.
 * The caller supplies the hour; this decides what it means.
 */
object DeskClock {

    /** The first hour of daylight, and the first hour after it. */
    const val DAWN = 7
    const val DUSK = 19

    /**
     * @param hour the local hour, 0..23. Anything outside that is wrapped rather
     *   than rejected: a scene is not worth an exception, and a platform that
     *   hands back a nonsense hour should still get a lit room.
     */
    fun resolve(setting: DeskLight, hour: Int): TimeOfDay = when (setting) {
        DeskLight.DAY -> TimeOfDay.DAY
        DeskLight.NIGHT -> TimeOfDay.NIGHT
        DeskLight.AUTO -> {
            val local = ((hour % 24) + 24) % 24
            if (local in DAWN until DUSK) TimeOfDay.DAY else TimeOfDay.NIGHT
        }
    }
}

/** What a piece of the room is made of, as far as the renderer is concerned. */
enum class Surface {
    /** The table the mat is lying on: the minimal stage's slab, or the desk. */
    TABLE,

    /** The wall the desk is pushed against. */
    WALL,
}

/**
 * One object in the room: a shape, and what it is made of.
 *
 * [name] is for tests and for reading a failure, not for the renderer — which
 * asks only [surface] and [box]. It is a plain string rather than an id type
 * because nothing looks a piece up; the room is drawn whole, every frame, in
 * the order the camera puts it in.
 */
data class ScenePiece(val name: String, val surface: Surface, val box: SceneBox)

/**
 * Everything past the felt, solved.
 *
 * A list, in no particular order — the renderer sorts it by projected depth,
 * because which piece is in front of which is a fact about where the camera is
 * and this is computed before the camera has been consulted.
 */
data class SceneModel(val pieces: List<ScenePiece>) {
    companion object {
        val Empty = SceneModel(emptyList())
    }
}

/**
 * The room, solved in core the way `DeckFit` and `BoardLayouter` are.
 *
 * ## The one rule every piece obeys
 *
 * **Nothing in here overlaps the mat.** Not a preference — the reason the play
 * stage can hold a room at all without the foundation-sized work in
 * `docs/AAA.md` #92 and #93. Cards are one composable each, sorted into
 * `PlayScreen`'s `ordered` list; the room is painted in the single canvas
 * underneath all of them. Those are two orderings, and the only thing that
 * keeps them from contradicting each other is that no object in the second one
 * can ever need to be in front of an object in the first. A mug on the felt
 * would need exactly that, the first time a card was carried past it.
 *
 * So the felt is the boundary: everything the room contains stands beyond it,
 * on the desk around the mat, and [outsideTheMat] holds it to that in a test.
 * When a retained scene and a real depth sort exist, this rule can be dropped
 * and not before.
 *
 * ## How big anything is
 *
 * Every number below is in card widths or card heights, never pixels, for the
 * same reason `BoardLayouter` works that way: the card is the one object on
 * this stage whose size the user can judge, so it is the only honest ruler. A
 * desk measured in pixels is a desk that changes size when the window does.
 */
object Scenery {

    // ---- the minimal stage, unchanged ---------------------------------------------

    /**
     * How far the playmat runs past the cards, and the table past the mat, in
     * card widths.
     *
     * Moved here from the renderer without changing either number. They are
     * geometry, they were solved once by eye and argued for in
     * `docs/DESIGN.md` §10, and the composable that used to hold them had no
     * business being the only place they existed. The argument, kept: a table
     * margin of a whole card width put a broad grey border round every side of
     * the mat, and on a stage whose premise is sharp white on true black that is
     * a large light rectangle competing with the cards. What the table is for is
     * the *edge* — the moment the camera comes down and a solid side swings into
     * view — and a narrow reveal does the whole job.
     */
    const val MAT_MARGIN = 0.22f
    const val TABLE_MARGIN = 0.38f

    /** How thick the minimal stage's table is, in card widths. A lip, not a plinth. */
    const val TABLE_THICKNESS = 0.17f

    /** The playmat's outline: everything the board occupies, plus its border. */
    fun mat(layout: BoardLayout): Slot =
        layout.bounds.inflated(layout.cardWidth * MAT_MARGIN)

    /** The minimal stage's table top. */
    fun table(layout: BoardLayout): Slot =
        mat(layout).inflated(layout.cardWidth * TABLE_MARGIN)

    // ---- the desk -----------------------------------------------------------------

    /**
     * How far the desk reaches past the mat toward the player, in card widths.
     *
     * The near edge is the whole point of the desk — `docs/AAA.md` #61, "where
     * the felt stops and the wood starts. Nothing in this app currently ends
     * anywhere." It is deliberately far enough out to be off the bottom of the
     * screen at the reading seat and to swing into view as the camera comes
     * down, because an edge you can see from every angle is a border and an edge
     * you have to sit down to see is a table.
     */
    const val DESK_NEAR = 0.9f

    /** And how far it reaches the other way, to where the wall starts. */
    const val DESK_FAR = 0.5f

    /**
     * How wide the desk is, as a share of the stage it is drawn on.
     *
     * Wider than the screen on purpose. A desk with both ends in frame is a
     * table in a void with more steps; a desk that runs out of both sides of the
     * picture is a desk in a room, and the two cost the same because it is one
     * box either way.
     */
    const val DESK_SPAN = 1.6f

    /** How thick the desk top is, in card widths. Furniture, not a lip. */
    const val DESK_THICKNESS = 0.5f

    /**
     * How high the wall stands, in card *heights*.
     *
     * Bounded, and the bound is the reason the number is written down rather
     * than tuned in a composable. Everything with a z reaches the screen through
     * `StagePlane.project`, whose scale is `distance / (distance - depth)` — so
     * height does not merely grow, it grows *faster* the taller it gets, and a
     * wall tall enough to reach the camera's own plane would divide by nothing.
     * Three and a bit card heights is comfortably inside that everywhere in
     * `CameraEnvelope`; [WALL_CEILING] is where a test stops it.
     */
    const val WALL_HEIGHT = 3.2f

    /** The tallest a piece of this room may stand, in card heights. */
    const val WALL_CEILING = 4f

    /** How thick the wall is. It only shows as a lip along the desk's far edge. */
    const val WALL_THICKNESS = 0.6f

    /** How far the wall runs past the ends of the desk. Walls do not stop. */
    const val WALL_OVERHANG = 1.12f

    /**
     * The room, for a scene and a board.
     *
     * @param surfaceWidth the stage's own width in pixels. The desk is sized
     *   against the *surface* rather than against the mat because its job is to
     *   run off the sides of the picture, and how much picture there is is not
     *   something the board knows.
     */
    fun of(
        scene: Scene,
        layout: BoardLayout,
        surfaceWidth: Float,
        surfaceHeight: Float,
    ): SceneModel = when (scene) {
        Scene.MINIMAL -> minimal(layout)
        Scene.DESK -> desk(layout, surfaceWidth, surfaceHeight)
    }

    /**
     * The minimal stage as a scene: one slab, exactly the one that already ships.
     *
     * Expressed this way so there is one renderer rather than two. The geometry
     * is unchanged and a test holds this box to producing the same six faces
     * `CardSolid.slab` did for it, which is what makes the unification safe to
     * make rather than merely tidy.
     */
    fun minimal(layout: BoardLayout): SceneModel {
        val table = table(layout)
        return SceneModel(
            listOf(
                ScenePiece(
                    name = "table",
                    surface = Surface.TABLE,
                    box = SceneBox.standing(
                        left = table.left,
                        top = table.top,
                        right = table.right,
                        bottom = table.bottom,
                        floor = -layout.cardWidth * TABLE_THICKNESS,
                        ceiling = 0f,
                    ),
                ),
            ),
        )
    }

    /**
     * The desk, and the wall behind it.
     *
     * Two objects, and `docs/AAA.md` #62 is the argument for stopping there:
     * *"There is a room past it. Dark, out of focus, present. It does not need
     * detail; it needs to exist."* A wall behind a desk is the cheapest true
     * statement about where the table is, and everything else — the window, the
     * lamp, the floor, the things on it — is a later release rather than a
     * smaller version of itself now.
     */
    fun desk(layout: BoardLayout, surfaceWidth: Float, surfaceHeight: Float): SceneModel {
        val mat = mat(layout)
        val card = layout.cardWidth

        val halfSpan = maxOf(surfaceWidth, mat.width) * DESK_SPAN / 2f
        val left = mat.centerX - halfSpan
        val right = mat.centerX + halfSpan
        val far = mat.top - card * DESK_FAR
        // Past the bottom of the picture as well as past the mat, and the
        // difference matters on a screen the board does not fill: an edge that
        // stops at the last card is a border round the board, and an edge that
        // stops beyond the glass is a table you are sitting at.
        val near = maxOf(mat.bottom, surfaceHeight) + card * DESK_NEAR

        val wallOverhang = halfSpan * WALL_OVERHANG
        val wallHeight = layout.cardHeight * WALL_HEIGHT

        return SceneModel(
            listOf(
                ScenePiece(
                    name = "wall",
                    surface = Surface.WALL,
                    box = SceneBox.standing(
                        left = mat.centerX - wallOverhang,
                        top = far - card * WALL_THICKNESS,
                        right = mat.centerX + wallOverhang,
                        bottom = far,
                        // Down as far as the desk goes, so that no seam of void
                        // shows between the two where the camera catches them.
                        floor = -card * DESK_THICKNESS,
                        ceiling = wallHeight,
                    ),
                ),
                ScenePiece(
                    name = "desk",
                    surface = Surface.TABLE,
                    box = SceneBox.standing(
                        left = left,
                        top = far,
                        right = right,
                        bottom = near,
                        floor = -card * DESK_THICKNESS,
                        ceiling = 0f,
                    ),
                ),
            ),
        )
    }

    /** Which rig lights which room. The one place that mapping is made. */
    fun lightingFor(scene: Scene, time: TimeOfDay): StageLighting = when (scene) {
        Scene.MINIMAL -> StageLighting.Minimal
        Scene.DESK -> when (time) {
            TimeOfDay.DAY -> StageLighting.DeskDay
            TimeOfDay.NIGHT -> StageLighting.DeskNight
        }
    }
}
