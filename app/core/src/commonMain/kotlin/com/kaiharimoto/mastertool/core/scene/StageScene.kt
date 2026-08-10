package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.Face
import com.kaiharimoto.mastertool.core.render.Light
import com.kaiharimoto.mastertool.core.render.Lit
import com.kaiharimoto.mastertool.core.render.Ring
import com.kaiharimoto.mastertool.core.render.StageLighting
import com.kaiharimoto.mastertool.core.render.Turned
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

    /**
     * The joinery around that pane: two stiles, two rails and a pair of bars.
     *
     * Its own surface rather than [WALL] because painted timber and painted
     * plaster are the one pair of materials in this room a person can tell
     * apart at a glance, and because it is the whole reason the opening reads
     * as a window rather than as a bright rectangle somebody cut in a wall.
     */
    FRAME,

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
    /**
     * The shape this piece really is, when a box is not it.
     *
     * Trailing and null by default, so every piece written before this existed
     * goes on being drawn from `box.faces()` and is bit-identical — the same
     * inert-default discipline `CardSolid.slab`'s `backScale = 1f` and
     * `Light.position = null` already keep, and for the same reason: this file
     * is upstream of a golden.
     *
     * [box] does not stop mattering when this is set. It goes on being what the
     * room is sorted by, what `SceneryTest` measures, and what has to clear the
     * mat — because `ScenePainter` needs a separating axis and a bounding box
     * supplies one exactly as well as the real solid would, while being a shape
     * the painter can reason about at all. What the mesh may not do is leave the
     * box: a piece drawn outside the volume it was sorted by is a piece that can
     * be painted in the wrong order, and `SceneryTest` holds it.
     *
     * The real rule is the painter's algorithm rather than convexity: the
     * renderer orders a piece's faces by the depth of their own centres, so the
     * faces the camera can *see* have to come out in the right order that way. A
     * convex solid satisfies that for free and is the safe answer — a lamp is
     * four pieces rather than one for exactly this reason, since its cove and
     * its overhanging shade are not.
     *
     * A shell is the one exception in the room, and it earns it by argument
     * rather than by inspection: back-face culling removes the outer far side
     * and the inner near side of a cone before the sort ever sees them, so what
     * is left is the inside of the far half, then the rim, then the outside of
     * the near half, and no pair of those can be got wrong. See
     * [Scenery.lampShade]. Anything else that is not convex wants the same
     * paragraph written for it before it goes here.
     */
    val mesh: List<Face>? = null,
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
    const val WINDOW_SILL = 0.24f
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

    // ---- the joinery around the opening ----------------------------------------------

    /**
     * How wide the two stiles are, in card widths, and how deep the two rails
     * are, in card heights.
     *
     * They stand **outside** the opening rather than overlapping it, which is
     * both how a window is actually built and the only arrangement that costs
     * the glass nothing: this window is 264 mat pixels wide and every pixel of
     * it was hard won.
     */
    const val FRAME_STILE = 0.18f
    const val FRAME_RAIL = 0.10f

    /**
     * How far the frame and the bars stand proud of the wall, in card widths.
     *
     * Proud rather than set back in a reveal, for the reason the pane's own
     * comment gives: anything pushed back in y projects straight off the top of
     * the picture, and a reveal shows twelve pixels at the table seat and
     * nothing from overhead. Standing it forward puts the same edge where the
     * camera can always see it.
     *
     * The frame may not interpenetrate the wall, and this is what keeps it from
     * doing so — it starts at the wall's front face and goes out. `ScenePainter`
     * sorts boxes by the nearest point each reaches, which is the painter's rule
     * for solids that **do not share volume**; two boxes that do have no correct
     * order and will swap as the camera turns.
     */
    const val FRAME_PROUD = 0.12f

    /**
     * The glazing bars: how thick, and how far forward of the wall.
     *
     * Set back from the frame's own face, because that is where a bar sits and
     * because the small step between the two is most of what says this is
     * joinery rather than a grid drawn on the glass.
     */
    const val FRAME_BAR = 0.075f
    const val FRAME_BAR_PROUD = 0.055f

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

    /**
     * The shade, as a lathe turns one: how wide it is at the rim, how wide at
     * the opening it leaves for the finial, and how far down from [LAMP_DRAWN]
     * its underside sits.
     *
     * A cone rather than a cuboid, and the numbers are a shade's rather than a
     * box's. An empire shade's opening is a little over half its rim — narrower
     * than that is a coolie and reads as a hat, wider is a drum and reads as a
     * tin. The rim rolls: [LAMP_SHADE_ROLL] is how far the wire hoop at the
     * bottom stands proud of the cone above it, and it is a fortieth of a card,
     * which is one pixel of silhouette at the reference size and is the whole
     * difference between a lampshade and a paper cone.
     */
    const val LAMP_SHADE_RIM = 0.62f
    const val LAMP_SHADE_TOP = 0.34f
    const val LAMP_SHADE_THICK = 0.58f
    const val LAMP_SHADE_ROLL = 0.025f

    /**
     * How thick the cloth is, in card widths.
     *
     * Three pixels. It is what turns the shade from a cone into a shell with an
     * opening you can see into — see [lampShade] — and it is the only dimension
     * of the lamp that is a real measurement of a real object rather than a
     * proportion of one.
     */
    const val LAMP_SHADE_WALL = 0.03f

    /**
     * The finial: the bead that screws down over the harp and holds the shade
     * on, standing above [LAMP_DRAWN].
     *
     * It is four pixels of brass and it is worth its piece, because it is the
     * only thing in the lamp's silhouette that is not a body of the lamp — a
     * shade with nothing on top of it reads as a funnel balanced on a stick.
     */
    const val LAMP_FINIAL = 0.16f
    const val LAMP_FINIAL_WIDE = 0.075f

    /**
     * The stem's width at the foot and at the neck, and the collar it carries.
     *
     * Two numbers because a turned stem tapers — a column of one width is a
     * dowel, and the eye reads the taper before it reads anything else about
     * the object. The collar is where a harp would be fixed, and it is there
     * for the same reason the finial is: it breaks a long plain run.
     */
    const val LAMP_MAST = 0.07f
    const val LAMP_MAST_NECK = 0.052f
    const val LAMP_COLLAR = 0.095f

    /**
     * The base: how wide it stands, how thick its rim is, and how high the cove
     * above it rises before it becomes the stem.
     *
     * A weighted foot rather than a tile. [LAMP_BASE] is unchanged, so the
     * footprint the room was arranged around has not moved.
     */
    const val LAMP_BASE = 0.38f
    const val LAMP_BASE_THICK = 0.045f
    const val LAMP_BASE_COVE = 0.155f

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

    // ---- the lamp, as four things a lathe made ---------------------------------------

    /**
     * The four silhouettes the lamp is turned from, bottom to top.
     *
     * Four rather than one because `ScenePiece.mesh` may only hold a **convex**
     * solid: the renderer orders a piece's own faces by the depth of their
     * centres, which is the painter's algorithm and is exactly right for a
     * convex body and quietly wrong for anything else. A lamp is not convex —
     * the cove above its foot faces back toward the middle of the object, and
     * the underside of the shade overhangs the stem entirely — so it is four
     * convex pieces stacked in z, which is also what makes `ScenePainter`'s
     * separating axis answer for them: z separates every pair.
     *
     * They meet at exact shared heights ([baseTop], [neckTop], [shadeTop]) for
     * the same reason the wall's four pieces tile the opening exactly: a joint
     * is two surfaces at one number, and two numbers that ought to be equal and
     * are computed twice are a seam that opens at some board size nobody tried.
     */
    fun baseTop(layout: BoardLayout): Float = layout.cardWidth * LAMP_BASE_COVE

    fun neckTop(layout: BoardLayout): Float =
        layout.cardWidth * (LAMP_DRAWN - LAMP_SHADE_THICK)

    fun shadeTop(layout: BoardLayout): Float = layout.cardWidth * LAMP_DRAWN

    fun lampBase(layout: BoardLayout): List<Ring> {
        val card = layout.cardWidth
        return listOf(
            // A foot with a chamfer under it, so it sits on the desk on a line
            // rather than on its whole face — which is what stops the join
            // between a round object and a flat one reading as a sticker.
            Ring(card * LAMP_BASE * 0.93f, 0f),
            Ring(card * LAMP_BASE, card * LAMP_BASE_THICK * 0.4f),
            Ring(card * LAMP_BASE, card * LAMP_BASE_THICK),
            Ring(card * LAMP_BASE * 0.78f, card * LAMP_BASE_THICK * 1.9f),
            Ring(card * LAMP_MAST * 1.7f, baseTop(layout)),
        )
    }

    fun lampStem(layout: BoardLayout): List<Ring> {
        val card = layout.cardWidth
        val neck = neckTop(layout)
        val run = neck - baseTop(layout)
        return listOf(
            Ring(card * LAMP_MAST, baseTop(layout)),
            Ring(card * LAMP_MAST_NECK, baseTop(layout) + run * 0.86f),
            Ring(card * LAMP_COLLAR, baseTop(layout) + run * 0.91f),
            Ring(card * LAMP_MAST_NECK * 0.9f, neck),
        )
    }

    /**
     * The shade, and it is a **shell**: up the outside, across the opening, and
     * back down the inside.
     *
     * Turned as a solid first, which is simpler and was wrong for one reason
     * that only a picture could have found. A solid frustum has a lid, and a lid
     * at this camera is a large bright disc sitting in the middle of the rim —
     * so the object came out as a drum with a plate on it rather than as a cone.
     * A real shade has a hole there, and what you see through the hole is the
     * far half of its own **inside**, which is a darker surface than the outside
     * by day and the brightest thing in the room at night.
     *
     * A shell is not convex, and `ScenePiece.mesh`'s rule is the painter's
     * algorithm rather than convexity as such: the faces the camera can see have
     * to sort correctly by the depth of their own centres. They do here, and the
     * reason is the shape rather than luck — back-face culling removes the outer
     * far side and the inner near side outright, and what is left is the inner
     * *far* surface, then the rim, then the outer *near* surface, in that order
     * from any seat in the envelope. There is no pair left to get wrong.
     */
    fun lampShade(layout: BoardLayout): List<Ring> {
        val card = layout.cardWidth
        val bottom = neckTop(layout)
        val top = shadeTop(layout)
        val run = top - bottom
        val wall = card * LAMP_SHADE_WALL
        return listOf(
            // Up the outside: the rim, the hoop it is rolled over, the cone.
            Ring(card * LAMP_SHADE_RIM, bottom),
            Ring(card * (LAMP_SHADE_RIM + LAMP_SHADE_ROLL), bottom + run * 0.05f),
            Ring(card * LAMP_SHADE_RIM, bottom + run * 0.12f),
            Ring(card * LAMP_SHADE_TOP, top),
            // Over the opening and straight back down the inside.
            Ring(card * LAMP_SHADE_TOP - wall, top),
            Ring(card * LAMP_SHADE_RIM - wall, bottom),
        )
    }

    fun lampFinial(layout: BoardLayout): List<Ring> {
        val card = layout.cardWidth
        val foot = shadeTop(layout)
        val rise = card * LAMP_FINIAL
        return listOf(
            Ring(card * LAMP_FINIAL_WIDE * 0.45f, foot),
            Ring(card * LAMP_FINIAL_WIDE, foot + rise * 0.42f),
            Ring(card * LAMP_FINIAL_WIDE * 0.72f, foot + rise * 0.74f),
            Ring(card * LAMP_FINIAL_WIDE * 0.20f, foot + rise),
        )
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
        // The lathe stands at the lamp's foot on the desk, unturned: a body of
        // revolution has nothing a turn about its own axis could do to it, and
        // saying so here is cheaper than a comment on every ring.
        val spindle = Pose3(position = Vec3(foot.x, foot.y, 0f))
        val turnedBase = Turned.solid(spindle, lampBase(layout))
        val turnedStem = Turned.solid(spindle, lampStem(layout))
        // Closed, because the shade's profile comes back round to where it
        // started: it is a shell rather than a solid, so there is no end left
        // open for a cap to go on.
        val turnedShade = Turned.solid(spindle, lampShade(layout), closed = true)
        val turnedFinial = Turned.solid(spindle, lampFinial(layout))

        fun wall(name: String, l: Float, r: Float, bottom: Float, top: Float) = ScenePiece(
            name = name,
            surface = Surface.WALL,
            box = SceneBox.standing(l, back, r, face, bottom, top),
        )

        // Everything from here to the bars stands in front of the wall's face
        // and never inside it. See FRAME_PROUD: the paint order is a separating
        // axis, and two boxes that share volume have no correct order at all.
        val stile = card * FRAME_STILE
        val rail = tall * FRAME_RAIL
        val proud = card * FRAME_PROUD
        val barHalf = card * FRAME_BAR / 2f
        val barOut = card * FRAME_BAR_PROUD
        val midX = (openingLeft + openingRight) / 2f
        val midZ = (sill + head) / 2f

        fun frame(name: String, l: Float, r: Float, bottom: Float, top: Float, out: Float) =
            ScenePiece(
                name = name,
                surface = Surface.FRAME,
                box = SceneBox.standing(l, face, r, face + out, bottom, top),
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
                // The stiles run the full height and the rails span between
                // them, which is how a sash is put together and — not by
                // coincidence — the only division of a rectangular frame into
                // four boxes where no two of them share a corner.
                frame("stile left", openingLeft - stile, openingLeft, sill - rail, head + rail, proud),
                frame("stile right", openingRight, openingRight + stile, sill - rail, head + rail, proud),
                frame("head rail", openingLeft, openingRight, head, head + rail, proud),
                frame("bottom rail", openingLeft, openingRight, sill - rail, sill, proud),
                frame("bar upright", midX - barHalf, midX + barHalf, sill, head, barOut),
                frame("bar left", openingLeft, midX - barHalf, midZ - barHalf, midZ + barHalf, barOut),
                frame("bar right", midX + barHalf, openingRight, midZ - barHalf, midZ + barHalf, barOut),
                ScenePiece(
                    name = "lamp base",
                    surface = Surface.SHADE,
                    box = SceneBox.around(turnedBase),
                    mesh = turnedBase,
                ),
                ScenePiece(
                    name = "lamp mast",
                    surface = Surface.SHADE,
                    box = SceneBox.around(turnedStem),
                    mesh = turnedStem,
                ),
                ScenePiece(
                    name = "lamp shade",
                    surface = Surface.SHADE,
                    box = SceneBox.around(turnedShade),
                    mesh = turnedShade,
                    emission = shadeLight(time),
                ),
                ScenePiece(
                    name = "lamp finial",
                    surface = Surface.SHADE,
                    box = SceneBox.around(turnedFinial),
                    mesh = turnedFinial,
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
