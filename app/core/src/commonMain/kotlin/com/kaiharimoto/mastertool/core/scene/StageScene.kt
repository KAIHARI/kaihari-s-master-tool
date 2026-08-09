package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.Light
import com.kaiharimoto.mastertool.core.render.Lit
import com.kaiharimoto.mastertool.core.render.StageLighting
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
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

    /** The wall the desk is pushed against, and the jambs the window leaves in it. */
    WALL,

    /** What the desk is standing on. Dark, and mostly out of frame. */
    FLOOR,

    /** The window pane: sky by day, near enough to nothing at night. */
    GLASS,

    /** The lamp — its base, its mast and the shade that is the light. */
    SHADE,

    /**
     * The one thing in the room that is not furniture.
     *
     * A finish rather than an object, which is why it is here beside the wood
     * and the cloth even though no [ScenePiece] carries it: what a surface is
     * made of is the room's business, and the alternative — a hex value living
     * privately in whatever draws the prop — is exactly the split this enum
     * exists to prevent. Gold is the material with the strongest opinion about
     * the light on it, so the day room and the night room read as genuinely
     * different rooms through it before anything else on the desk changes.
     */
    GOLD,
}

/**
 * One object in the room: a shape, and what it is made of.
 *
 * [name] is for tests and for reading a failure, not for the renderer — which
 * asks only [surface] and [box]. It is a plain string rather than an id type
 * because nothing looks a piece up; the room is drawn whole, every frame, in
 * the order the camera puts it in.
 */
data class ScenePiece(
    val name: String,
    val surface: Surface,
    val box: SceneBox,
    /**
     * Light this piece *emits*, rather than light it receives. Null for anything
     * that is only a surface.
     *
     * A [Lit] because that type already means exactly "an amount of light, and
     * what colour it was", and because the room's two fixtures disagree about
     * colour: the shade at night is tungsten and the pane by day is sky.
     *
     * A renderer handed one of these does not call the rig at all, and that is
     * the physics rather than a shortcut — an emissive surface's brightness does
     * not depend on what is lighting the room it is in. It is also the only way
     * to draw one: `Tone.shade` can darken a colour and can never brighten it,
     * so radiance has to arrive as the base colour itself.
     */
    val emission: Lit? = null,
)

/**
 * Everything past the felt, solved.
 *
 * A list, in no particular order — the renderer sorts it by projected depth,
 * because which piece is in front of which is a fact about where the camera is
 * and this is computed before the camera has been consulted.
 */
data class SceneModel(
    val pieces: List<ScenePiece>,
    /**
     * The rig this room is lit by.
     *
     * Here rather than beside the preset because a lamp with a *place* has that
     * place in mat pixels, and mat pixels are a fact about the board's layout.
     * A rig and the room it lights are solved together or they are solved
     * against different tables.
     */
    val lighting: StageLighting = StageLighting.Minimal,
) {
    /**
     * Everything at or below the table top, and everything standing on it.
     *
     * The felt is drawn between the two, because the felt lies *on* the desk and
     * *under* the lamp, and a single pass could only ever put it on one side.
     * Well defined because no piece of this room straddles z = 0 — which is a
     * test, not a hope.
     */
    val ground: List<ScenePiece> get() = pieces.filter { it.box.max.z <= 0f }
    val standing: List<ScenePiece> get() = pieces.filter { it.box.max.z > 0f }

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
     * What this buys is that there is never a void below the felt, at any seat.
     * What it does **not** buy — and the first draft of this comment claimed it
     * did — is a visible near edge. Measured on a 1600x856 stage, the desk's
     * near edge projects to y = 1005 at the table seat and y = 1075 seated,
     * against a screen 856 tall, and dollying all the way out only brings it to
     * about 950: the term that puts it there is `flat`, which the camera's
     * distance does not scale. The board fills the stage vertically, so there is
     * simply no room below it for an edge to appear in.
     *
     * `docs/AAA.md` #61 is still answered, but by the *other* three sides.
     * `BoardLayouter` centres a seven-column field in whatever it is given, and
     * on a landscape tablet that leaves a third of the width as bare desk down
     * each side, plus a strip above the mat before the wall starts. That is
     * where the felt stops and the wood starts, and it is on screen at every
     * seat. The near edge is geometry the camera cannot currently reach, and it
     * would take a smaller board or a wider envelope to show it.
     */
    const val DESK_NEAR = 0.9f

    /** And how far it reaches the other way, to where the wall starts. */
    const val DESK_FAR = 0.5f

    /**
     * How much of the stage's height the board declines to use, so that there
     * is somewhere for the room to be.
     *
     * Pass to `BoardLayouter.solve`'s `roomAbove` for [Scene.DESK] and nothing
     * for [Scene.MINIMAL], which has no room to see.
     *
     * This is the number that makes the second half of the list in
     * `docs/LOOP.md` reachable at all. Everything in this room is placed
     * relative to the mat and scaled by a card, so the wall's base landed
     * wherever the board's far edge landed — which was the top of the screen —
     * and the wall, the window and the sky behind it were geometry nobody could
     * ever see. There is no way to show them that does not start with the board
     * being smaller than the stage.
     *
     * A fifth, and it is a fifth for a reason rather than by taste. The board is
     * height-constrained on every device this ships to, so the reserve comes
     * straight off the card: a fifth is exactly a fifth, 107dp of card down to
     * 85dp on the tablet. What it buys on that tablet is 176dp of screen above
     * the desk's far edge, which is the first framing in which the window is a
     * window rather than a hole nobody can see. Below about a tenth the wall is
     * a lip again; above about a quarter the cards are smaller than the deck
     * builder draws them, which is where a bigger room has started costing the
     * game.
     *
     * The narrowest phone pays the same fifth and can afford it — 115px of card
     * down to 92px, against a [BoardLayouter.MIN_CARD_WIDTH] of 28.
     */
    const val ROOM_ABOVE = 0.20f

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

    // ---- the room below the desk ---------------------------------------------------

    /** How far the desk top stands off the floor, in card widths — about 73cm. */
    const val DESK_STAND = 12.4f

    /** How thick the floor is. Only its top face is ever seen. */
    const val FLOOR_THICKNESS = 0.4f

    /** How far the floor runs past the desk, in card widths. */
    const val FLOOR_MARGIN = 8.0f

    // ---- the window ------------------------------------------------------------------

    /**
     * How wide the opening in the wall is, in card widths, and where its centre
     * sits as a fraction of the mat's width from the mat's left edge.
     *
     * Off to the left, opposite the lamp, so that the two fixtures are never
     * both on the same side of the table and the room has a direction whichever
     * hour it is.
     */
    const val WINDOW_SPAN = 3.4f
    const val WINDOW_AT = 0.12f

    /**
     * How high the sill and the head are, in card *heights* above the desk.
     *
     * Low, and this is measured rather than chosen. At the wall's plane the
     * largest z that lands anywhere on the glass is 94 pixels from overhead, 86
     * at the table seat and 128 seated — half a card height. So a window on this
     * wall is either low or invisible, and one at a realistic sill height would
     * be a piece of geometry nobody could ever see, lighting a room through a
     * hole above the frame.
     */
    const val WINDOW_SILL = 0.10f
    const val WINDOW_HEAD = 2.05f

    /**
     * How big the sky is, and how far off, in card widths.
     *
     * Only ever divided into one another, to give the source an angular size:
     * 21.7 degrees, which is what a window looks like from a desk beside it and
     * about three times the lamp's. That ratio is the whole reason day and night
     * read as two rooms rather than two colour grades — at the height a card is
     * carried at, daylight's shadow edge is two and a half times softer.
     *
     * The window keeps its **direction** and gains no position. See
     * [lightingFor] for why, which is a fact about this wall rather than a
     * preference.
     */
    const val SKY_RADIUS = 12.9f
    const val SKY_DISTANCE = 34.0f

    // ---- the lamp --------------------------------------------------------------------

    /**
     * Where the lamp stands: past the mat's right edge in card widths, and down
     * the mat's depth as a fraction of it.
     *
     * Beyond the felt, because nothing in this room may stand over the mat, and
     * on the far right because that is where a right-handed player's lamp is and
     * where it shadows *away* from the hand.
     */
    const val LAMP_OUT = 1.15f
    const val LAMP_ALONG = 0.26f

    /** How big the bulb is, in card widths: a 14cm shade. */
    const val LAMP_RADIUS = 1.2f

    /**
     * How high the lamp is **drawn**, in card widths — which is not how high its
     * light is. See [lampHeight].
     */
    const val LAMP_DRAWN = 2.2f

    /** The shade: half its width, half its depth, and how deep it hangs. */
    const val LAMP_SHADE_HALF = 0.55f
    const val LAMP_SHADE_DEPTH = 0.40f
    const val LAMP_SHADE_THICK = 0.42f

    /** The mast's half-width, and the base's half-width and thickness. */
    const val LAMP_MAST = 0.07f
    const val LAMP_BASE = 0.38f
    const val LAMP_BASE_THICK = 0.10f

    /** Where the lamp stands on the desk, in mat pixels. */
    fun lampFoot(layout: BoardLayout): Vec2 {
        val mat = mat(layout)
        return Vec2(
            x = mat.right + layout.cardWidth * LAMP_OUT,
            y = mat.top + mat.height * LAMP_ALONG,
        )
    }

    /**
     * How high the lamp's light is, in mat pixels.
     *
     * **Solved, not chosen**, and the reference quantity is the preset that
     * already ships. The ratio of horizontal to vertical in
     * `StageLighting.DeskNight.key.direction` is 0.854, and that ratio *is* how
     * long a night shadow is per unit of height. So the lamp stands at whatever
     * height makes the ray from it **to the middle of the table** have exactly
     * that ratio.
     *
     * What that guarantees is one point, and the claim is worth stating no
     * larger than it is: a card at the centre of the mat throws the shadow it
     * throws today, in length and in direction. Everywhere else both change —
     * the azimuth swings about fifteen degrees, and a card near the lamp throws
     * a markedly shorter shadow than one across the table from it. That is not
     * a cost of the anchoring, it *is* the feature: a lamp with a place throws
     * shadows that point away from it, and a direction cannot. What the
     * anchoring buys is that the middle of the board — where most of a game
     * happens — is not suddenly lit from a different height than it shipped
     * with, so the change reads as the room gaining a lamp rather than as
     * somebody having moved the light.
     *
     * It comes out at 704 pixels — about 38cm above the desk, which is a desk
     * lamp. That the honest number and the shipped preset agree this well is the
     * argument for solving it rather than typing one.
     */
    fun lampHeight(layout: BoardLayout): Float {
        val mat = mat(layout)
        val foot = lampFoot(layout)
        val shipped = StageLighting.DeskNight.key.direction.normalised()
        val ratio = sqrt(shipped.x * shipped.x + shipped.y * shipped.y) / abs(shipped.z)
        if (ratio <= 1e-4f) return layout.cardWidth * LAMP_DRAWN
        return hypot(mat.centerX - foot.x, mat.centerY - foot.y) / ratio
    }

    /** The lamp, as a point of light. */
    fun lamp(layout: BoardLayout): Vec3 {
        val foot = lampFoot(layout)
        return Vec3(foot.x, foot.y, lampHeight(layout))
    }

    /**
     * The room, for a scene, an hour and a board.
     *
     * @param surfaceWidth the stage's own width in pixels. The desk is sized
     *   against the *surface* rather than against the mat because its job is to
     *   run off the sides of the picture, and how much picture there is is not
     *   something the board knows.
     */
    fun of(
        scene: Scene,
        time: TimeOfDay,
        layout: BoardLayout,
        surfaceWidth: Float,
        surfaceHeight: Float,
    ): SceneModel = when (scene) {
        Scene.MINIMAL -> minimal(layout)
        Scene.DESK -> desk(time, layout, surfaceWidth, surfaceHeight)
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
            pieces = listOf(
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
            lighting = StageLighting.Minimal,
        )
    }

    /**
     * The desk, the wall it is pushed against with a window in it, the lamp
     * standing on it, and the floor underneath.
     *
     * Ten pieces against a budget of twenty, and **the same ten at every hour** —
     * a room does not repaint itself at dusk. What the hour changes is which
     * fixture is emitting and which rig is lighting the rest, which is the whole
     * of the difference between a scene and a colour grade.
     *
     * Two joints are worth knowing about, because both were bugs first:
     *
     * - **The wall stands *on* the desk and the desk runs *under* the wall.**
     *   The wall used to have a skirt down to the desk's underside, and that
     *   skirt is hidden by the desk top from every angle *and painted over it*,
     *   because the wall's tall front-top corner outruns the desk's near edge
     *   below about twenty-seven degrees of pitch. It ate thirty pixels of the
     *   forty-pixel band of bare wood between the wall and the mat — most of the
     *   thing `docs/AAA.md` #61 asks for. Sitting the wall on z = 0 and running
     *   the desk back under it leaves the two sharing exactly one face.
     * - **The four wall pieces and the pane tile the old single wall exactly.**
     *   No gap and no overlap, which is one line of test and the reason a window
     *   can be a *hole* rather than a bright rectangle stuck on a wall.
     */
    fun desk(
        time: TimeOfDay,
        layout: BoardLayout,
        surfaceWidth: Float,
        surfaceHeight: Float,
    ): SceneModel {
        val mat = mat(layout)
        val card = layout.cardWidth
        val tall = layout.cardHeight

        val halfSpan = maxOf(surfaceWidth, mat.width) * DESK_SPAN / 2f
        val left = mat.centerX - halfSpan
        val right = mat.centerX + halfSpan
        // Where the wall's face stands, and where the desk's far edge is behind it.
        val face = mat.top - card * DESK_FAR
        val back = face - card * WALL_THICKNESS
        // Past the bottom of the picture as well as past the mat, and the
        // difference matters on a screen the board does not fill: an edge that
        // stops at the last card is a border round the board, and an edge that
        // stops beyond the glass is a table you are sitting at.
        val near = maxOf(mat.bottom, surfaceHeight) + card * DESK_NEAR

        val wallOverhang = halfSpan * WALL_OVERHANG
        val wallLeft = mat.centerX - wallOverhang
        val wallRight = mat.centerX + wallOverhang
        val wallTop = tall * WALL_HEIGHT

        val openingHalf = card * WINDOW_SPAN / 2f
        val openingAt = mat.left + mat.width * WINDOW_AT
        val openingLeft = openingAt - openingHalf
        val openingRight = openingAt + openingHalf
        val sill = tall * WINDOW_SILL
        val head = tall * WINDOW_HEAD

        val foot = lampFoot(layout)
        val shadeTop = card * LAMP_DRAWN
        val shadeBottom = shadeTop - card * LAMP_SHADE_THICK
        val baseTop = card * LAMP_BASE_THICK

        fun wall(name: String, l: Float, r: Float, bottom: Float, top: Float) = ScenePiece(
            name = name,
            surface = Surface.WALL,
            box = SceneBox.standing(l, back, r, face, bottom, top),
        )

        return SceneModel(
            pieces = listOf(
                ScenePiece(
                    name = "floor",
                    surface = Surface.FLOOR,
                    box = SceneBox.standing(
                        left = left - card * FLOOR_MARGIN,
                        top = back - card * FLOOR_MARGIN,
                        right = right + card * FLOOR_MARGIN,
                        bottom = near + card * FLOOR_MARGIN,
                        floor = -card * (DESK_STAND + FLOOR_THICKNESS),
                        ceiling = -card * DESK_STAND,
                    ),
                ),
                ScenePiece(
                    name = "desk",
                    surface = Surface.TABLE,
                    box = SceneBox.standing(
                        left = left,
                        top = back,
                        right = right,
                        bottom = near,
                        floor = -card * DESK_THICKNESS,
                        ceiling = 0f,
                    ),
                ),
                wall("wall left", wallLeft, openingLeft, 0f, wallTop),
                wall("wall right", openingRight, wallRight, 0f, wallTop),
                wall("sill", openingLeft, openingRight, 0f, sill),
                wall("header", openingLeft, openingRight, head, wallTop),
                ScenePiece(
                    name = "pane",
                    surface = Surface.GLASS,
                    // Flush with the wall rather than set back in a reveal. Set
                    // back, it shows a twelve-pixel band at the table seat and
                    // nothing at all from overhead, because anything pushed back
                    // in y projects straight off the top of the picture. Flush,
                    // it gets the whole band, and the left jamb's inner face
                    // still reads as the edge of a frame.
                    box = SceneBox.standing(openingLeft, back, openingRight, face, sill, head),
                    emission = paneLight(time),
                ),
                ScenePiece(
                    name = "lamp base",
                    surface = Surface.SHADE,
                    box = SceneBox.standing(
                        left = foot.x - card * LAMP_BASE,
                        top = foot.y - card * LAMP_BASE,
                        right = foot.x + card * LAMP_BASE,
                        bottom = foot.y + card * LAMP_BASE,
                        floor = 0f,
                        ceiling = baseTop,
                    ),
                ),
                ScenePiece(
                    name = "lamp mast",
                    surface = Surface.SHADE,
                    box = SceneBox.standing(
                        left = foot.x - card * LAMP_MAST,
                        top = foot.y - card * LAMP_MAST,
                        right = foot.x + card * LAMP_MAST,
                        bottom = foot.y + card * LAMP_MAST,
                        floor = baseTop,
                        ceiling = shadeBottom,
                    ),
                ),
                ScenePiece(
                    name = "lamp shade",
                    surface = Surface.SHADE,
                    box = SceneBox.standing(
                        left = foot.x - card * LAMP_SHADE_HALF,
                        top = foot.y - card * LAMP_SHADE_DEPTH,
                        right = foot.x + card * LAMP_SHADE_HALF,
                        bottom = foot.y + card * LAMP_SHADE_DEPTH,
                        floor = shadeBottom,
                        ceiling = shadeTop,
                    ),
                    emission = shadeLight(time),
                ),
            ),
            lighting = lightingFor(Scene.DESK, time, layout),
        )
    }

    /**
     * What the window is showing.
     *
     * It always carries an emission and only the amount changes, because a
     * window always shows the outside and the outside at night is very nearly
     * black. Routed through the rig instead, a night pane would come back as a
     * mid-grey rectangle — a lit window in a dark room, with the light behind it.
     */
    private fun paneLight(time: TimeOfDay): Lit = when (time) {
        TimeOfDay.DAY -> Lit(1f, StageLighting.DeskDay.key.warmth)
        TimeOfDay.NIGHT -> Lit(0.02f, -0.85f)
    }

    /**
     * What the lamp is doing.
     *
     * Null by day, which is not the lamp being switched off so much as it being
     * a pale ceramic object in a bright room — shaded like everything else. At
     * night it is the brightest thing in the frame after a card, and its warmth
     * comes off the rig's own key so a fixture and the light it throws cannot be
     * different colours.
     */
    private fun shadeLight(time: TimeOfDay): Lit? = when (time) {
        TimeOfDay.DAY -> null
        TimeOfDay.NIGHT -> Lit(1f, StageLighting.DeskNight.key.warmth)
    }

    /**
     * Which rig lights which room, once the board's size is known.
     *
     * The presets in `StageLighting` are pure ratios and directions and stay
     * that way — not one byte of that file changes for this. What is added here
     * is everything measured in **mat pixels**, because a lamp's position and a
     * source's size are lengths, and a length is a fact about the board's layout
     * rather than about a preset.
     *
     * **The lamp gets a place; the window does not**, and that is geometry
     * rather than laziness. Three constructions were tried for a positioned
     * window and all three fail on this wall: a source at the aperture's centre
     * gives a horizontal-to-vertical ratio of 3.4, so a carried card's shadow
     * lands four hundred pixels away instead of eighty; a source pushed back
     * along the shipped ray drifts its direction by seven and a half degrees and
     * moves every daylight shadow in the app; and a source at sky height on the
     * shipped ray crosses this wall's plane at z = 991 against a wall 511 tall —
     * it enters *above* the window rather than through it. There is no position
     * inside this wall that produces daylight. What the window gets instead is
     * its real angular *size*, which is the half of the physics that is visible:
     * daylight's shadows are soft and lamplight's are not.
     */
    fun lightingFor(scene: Scene, time: TimeOfDay, layout: BoardLayout): StageLighting =
        when (scene) {
            Scene.MINIMAL -> StageLighting.Minimal
            Scene.DESK -> when (time) {
                TimeOfDay.DAY -> StageLighting.DeskDay.let { rig ->
                    rig.copy(
                        key = rig.key.copy(
                            radius = layout.cardWidth * SKY_RADIUS,
                            distance = layout.cardWidth * SKY_DISTANCE,
                        ),
                    )
                }
                TimeOfDay.NIGHT -> StageLighting.DeskNight.let { rig ->
                    val mat = mat(layout)
                    rig.copy(
                        key = Light.at(
                            position = lamp(layout),
                            aimedAt = Vec3(mat.centerX, mat.centerY, 0f),
                            intensity = rig.key.intensity,
                            warmth = rig.key.warmth,
                            ambient = rig.key.ambient,
                            radius = layout.cardWidth * LAMP_RADIUS,
                        ),
                    )
                }
            }
        }
}
