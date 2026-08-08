package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.scene.Puzzle
import com.kaiharimoto.mastertool.core.scene.Scene
import com.kaiharimoto.mastertool.core.scene.SceneBox
import com.kaiharimoto.mastertool.core.scene.ScenePainter
import com.kaiharimoto.mastertool.core.scene.ScenePiece
import com.kaiharimoto.mastertool.core.scene.Scenery
import com.kaiharimoto.mastertool.core.scene.Surface
import com.kaiharimoto.mastertool.core.scene.TimeOfDay
import java.awt.Color as AwtColour
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * A picture of the stage, without a device.
 *
 * ## Why this exists
 *
 * Every rule this renderer obeys is tested, and until now none of them could be
 * *seen*. That gap is not theoretical: the daylight patch cut in the release
 * before last was arithmetically reasonable and visually a stain — every ring of
 * it darker than the wood it landed on — and it took a page of reasoning about
 * `Tone.shade` to establish what one glance at a frame would have said
 * instantly. The paint-order bug that put the puzzle through the window header
 * was the same kind of thing.
 *
 * So this renders the same values the app draws from, to a PNG, in the module
 * that compiles anywhere. `:ui` needs AGP in its `plugins` block and AGP is
 * served only from Google's Maven, so it cannot even be *configured* without
 * network access this environment does not have — but everything that decides
 * what a frame looks like was deliberately put in `:core`, and this is the
 * dividend.
 *
 * ## What it is honest about
 *
 * It is a **preview, not a screenshot**, and the difference has three parts.
 *
 * - It projects with [StagePlane.project] rather than flattening into the mat
 *   and letting a `graphicsLayer` do the perspective. Those agree by
 *   construction — `StagePlane`'s KDoc is about exactly that — so the polygons
 *   land where Compose puts them, but Compose's rasteriser is not this one and
 *   antialiasing will differ by a pixel.
 * - It draws the room, the felt and the prop. It does not draw cards, card art,
 *   text, or the prismatic fringing, because those are `:ui`'s and are the part
 *   of the stage that the existing tests cannot reach either.
 * - **The palette is a mirror.** `docs/DESIGN.md` puts the hex values in `:ui`
 *   on purpose and this module cannot see them, so [Palette] below is a copy.
 *   A colour changed there and not here makes this preview wrong while leaving
 *   the app right — which is the failure mode to keep in mind before trusting a
 *   colour judgement made from one of these frames. Geometry, paint order and
 *   *relative* lightness are exact; absolute hue is a copy.
 */
object StageFrame {

    /**
     * The colours from `ui/play/SceneRender.kt`, mirrored.
     *
     * Kept in the order that file declares them so the two can be read side by
     * side. If they disagree, that file is right and this one is stale.
     */
    data class Palette(
        val table: Int,
        val wall: Int,
        val floor: Int,
        val glass: Int,
        val shade: Int,
        val gold: Int,
        val mat: Int,
    ) {
        fun of(surface: Surface): Int = when (surface) {
            Surface.TABLE -> table
            Surface.WALL -> wall
            Surface.FLOOR -> floor
            Surface.GLASS -> glass
            Surface.SHADE -> shade
            Surface.GOLD -> gold
        }
    }

    private val Minimal = Palette(
        table = 0x141519, wall = 0x141519, floor = 0x0D0B09, glass = 0xDCE4EA,
        shade = 0xE4DAC6, gold = 0x8C6B1F, mat = 0x0A0A0E,
    )
    private val Desk = Palette(
        table = 0x2E2419, wall = 0x191B22, floor = 0x0D0B09, glass = 0xDCE4EA,
        shade = 0xE4DAC6, gold = 0x8C6B1F, mat = 0x0C0C11,
    )

    private fun paletteFor(scene: Scene) = if (scene == Scene.MINIMAL) Minimal else Desk

    /**
     * One frame, written to [into].
     *
     * The draw order is `PlayScreen`'s, line for line: the room below the desk
     * top, then the felt lying on it, then everything standing on it with the
     * prop sorted in among them.
     */
    fun render(
        scene: Scene,
        time: TimeOfDay,
        layout: BoardLayout,
        width: Int,
        height: Int,
        pose: CameraPose,
        turns: Int = 0,
        lifted: Float = 0f,
        /** An alternative solid for the prop, for comparing one shape against another. */
        propFaces: List<Face>? = null,
        into: File,
    ) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        // True black, because that is what the app paints behind the stage.
        g.color = AwtColour.BLACK
        g.fillRect(0, 0, width, height)

        val plane = pose.planeFor(width.toFloat(), height.toFloat())
        val eye = StageRig.eye(plane.tiltDegrees, plane.yawDegrees)
        val eyeAt = plane.eyePoint(eye)
        val model = Scenery.of(scene, time, layout, width.toFloat(), height.toFloat())
        val palette = paletteFor(scene)
        val pool = StageRig.pool(model.lighting, eye)

        drawPieces(g, model.ground, plane, eye, eyeAt, model.lighting, palette, pool, null)
        drawFelt(g, layout, plane, palette, model.lighting, eye, pool)

        val prop = if (Puzzle.standsIn(scene)) {
            val at = Puzzle.stirred(layout, turns, lifted)
            Puzzle.reach(layout) to (propFaces ?: Puzzle.solid(layout, at))
        } else {
            null
        }
        drawPieces(g, model.standing, plane, eye, eyeAt, model.lighting, palette, null, prop)

        g.dispose()
        into.parentFile?.mkdirs()
        ImageIO.write(image, "png", into)
    }

    // ---- the same two passes `drawScene` makes ---------------------------------------

    private fun drawPieces(
        g: java.awt.Graphics2D,
        pieces: List<ScenePiece>,
        plane: StagePlane,
        eye: Vec3,
        eyeAt: Vec3,
        lighting: StageLighting,
        palette: Palette,
        pool: LightPool?,
        prop: Pair<SceneBox, List<Face>>?,
    ) {
        if (pieces.isEmpty() && prop == null) return
        val standIn = prop?.let { ScenePiece("prop", Surface.GOLD, it.first) }
        val all = if (standIn == null) pieces else pieces + standIn

        ScenePainter
            .order(all, eyeAt) { box -> box.corners().maxOf { plane.project(it).depth } }
            .forEach { piece ->
                val faces = if (piece === standIn) prop!!.second else piece.box.faces()
                val base = palette.of(piece.surface)
                CardSolid.visible(faces, eyeAt)
                    .sortedBy { plane.project(it.centre).depth }
                    .forEach { face ->
                        if (face.corners.any { !plane.reaches(it) }) return@forEach
                        val path = pathOf(face.corners, plane)
                        val lit = piece.emission ?: StageRig.face(face, eye, lighting)
                        g.color = shaded(base, lit)
                        g.fill(path)

                        if (
                            pool != null && piece.emission == null &&
                            face.normal.z > 0.99f && abs(face.centre.z) < 0.5f
                        ) {
                            paintPool(g, path, base, pool, plane)
                        }
                    }
            }
    }

    /**
     * The playmat.
     *
     * Deliberately simpler than `drawFelt`: the mat's own colour under the rig,
     * plus the lamp's pool. The additive sheen and the shuffle marks are left
     * out because they are `:ui`'s and this is a preview of the *room*.
     */
    private fun drawFelt(
        g: java.awt.Graphics2D,
        layout: BoardLayout,
        plane: StagePlane,
        palette: Palette,
        lighting: StageLighting,
        eye: Vec3,
        pool: LightPool?,
    ) {
        val mat = Scenery.mat(layout)
        val corners = listOf(
            Vec3(mat.left, mat.top, 0f),
            Vec3(mat.right, mat.top, 0f),
            Vec3(mat.right, mat.bottom, 0f),
            Vec3(mat.left, mat.bottom, 0f),
        )
        if (corners.any { !plane.reaches(it) }) return
        val path = pathOf(corners, plane)
        val up = Face(corners, Vec3(0f, 0f, 1f))
        g.color = shaded(palette.mat, StageRig.face(up, eye, lighting))
        g.fill(path)
        if (pool != null) paintPool(g, path, palette.mat, pool, plane)
    }

    /**
     * The lamp's pool, as a radial gradient in *screen* space.
     *
     * The app draws it as a circle in the mat's own coordinates and lets the
     * camera turn it into an ellipse. Java2D has no perspective transform, so
     * the foot is projected and the radius is taken from a point one radius away
     * along the mat's x — which is the same ellipse's semi-axis at the centre
     * and drifts from a true projection toward the edges. Close enough to judge
     * where the light is; not close enough to measure it.
     */
    private fun paintPool(
        g: java.awt.Graphics2D,
        path: Path2D,
        base: Int,
        pool: LightPool,
        plane: StagePlane,
    ) {
        val foot = plane.project(Vec3(pool.foot.x, pool.foot.y, 0f))
        val edge = plane.project(Vec3(pool.foot.x + pool.radius, pool.foot.y, 0f))
        val radius = kotlin.math.hypot(edge.x - foot.x, edge.y - foot.y)
        if (radius <= 1f) return

        val fractions = FloatArray(pool.stops.size) { it / (pool.stops.size - 1f) }
        // Java2D rejects a gradient whose stops are not strictly increasing.
        for (i in 1 until fractions.size) {
            if (fractions[i] <= fractions[i - 1]) fractions[i] = fractions[i - 1] + 1e-4f
        }
        val colours = Array(pool.stops.size) { shaded(base, pool.stops[it]) }

        val clip = g.clip
        g.clip(path)
        g.paint = RadialGradientPaint(
            Point2D.Float(foot.x, foot.y),
            radius,
            fractions,
            colours,
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
        )
        g.fill(path)
        g.clip = clip
    }

    // ---- the two conversions everything goes through ---------------------------------

    private fun pathOf(corners: List<Vec3>, plane: StagePlane): Path2D {
        val path = Path2D.Float()
        corners.forEachIndexed { index, corner ->
            val at = plane.project(corner)
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        path.closePath()
        return path
    }

    /**
     * A colour under a light, through [Tone] — the same multiply
     * `Color.shaded` performs, and for the same reason: a `Color`'s channels
     * are an sRGB *encoding*, and multiplying an encoding is not multiplying
     * light.
     */
    private fun shaded(argb: Int, lit: Lit): AwtColour {
        fun channel(shift: Int, amount: Float): Int {
            val raw = ((argb shr shift) and 0xFF) / 255f
            return (Tone.shade(raw, amount).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return AwtColour(channel(16, lit.red), channel(8, lit.green), channel(0, lit.blue))
    }
}
