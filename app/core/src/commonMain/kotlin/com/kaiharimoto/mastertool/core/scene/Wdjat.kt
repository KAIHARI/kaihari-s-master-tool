package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Eye of Wdjat, in a unit square.
 *
 * The one thing that makes the object on the left of the desk *the Millennium
 * Puzzle* rather than a gold pyramid. Everything else about it — the inverted
 * pyramid, the small flat at the point, the ring at the top — is shared with any
 * number of pendants; the eye is not.
 *
 * ## Why it is in core, and why it is polygons
 *
 * It is drawn onto a **facet of a solid**, which means it has to go through the
 * same projection the facet's own outline does or it will slide off the face as
 * the camera turns. `Homography.squareToQuad` already does exactly that for a
 * card's art — four flattened corners in, a perspective-correct map out — so
 * what this has to be is a drawing in the unit square, and where it has to live
 * is somewhere both the drawing and a test can reach.
 *
 * Polygons rather than curves because the seam has no shape language: `:ui`
 * holds the `Path` type and `:core` may not depend on it, so the choice is
 * either a path model invented here and translated there, or points. At the size
 * this is drawn — about a hundred pixels across a facet — [SMOOTH] segments per
 * curve is under a third of a pixel of chord error, and a point is a point on
 * every platform.
 *
 * ## The six parts, and the fractions they were named for
 *
 * A wedjat is six marks, and in Ancient Egyptian arithmetic each one *was* a
 * fraction of the hekat: the eye's outer half 1/2, the pupil 1/4, the brow 1/8,
 * the inner half 1/16, the curl 1/32, the teardrop 1/64. They come to 63/64, and
 * Thoth is said to supply the last. That is not decoration on this comment: the
 * six parts below are exactly those six marks, and the order they are listed in
 * is the order of the fractions.
 */
object Wdjat {

    /** Segments per curve. A third of a pixel of chord at the size this is drawn. */
    const val SMOOTH = 24

    /**
     * How much of the facet the eye leaves bare, at each edge.
     *
     * A mark that runs to the edges of a face reads as a texture the face is
     * wrapped in; one with a margin round it reads as a mark cast *into* it.
     */
    const val MARGIN = 0.07f

    /**
     * The parts, in the unit square, ready for a `Path` and a homography.
     *
     * Each is a closed ring of points, wound in one direction, to be filled. In
     * the square's own frame: **+x to the right, +y down**, which is the frame
     * `Homography.squareToQuad` maps and the frame the whole app draws in.
     */
    fun parts(): List<List<Vec2>> {
        val drawn = listOf(upperLid(), pupil(), brow(), lowerLid(), curl(), teardrop())
        // Fitted rather than placed. The six marks are laid out in whatever
        // frame reads best while they are being drawn, and the whole drawing is
        // then measured and dropped into the square — so moving the curl out a
        // little cannot quietly push the teardrop off the facet, which is what a
        // hand-tuned span did the first time it was changed.
        //
        // One scale for both axes, because the eye is a shape and not a fill:
        // fitting x and y separately would stretch it to whatever the patch on
        // the flank happens to be, and that patch is a trapezoid.
        val all = drawn.flatten()
        val left = all.minOf { it.x }
        val right = all.maxOf { it.x }
        val top = all.minOf { it.y }
        val bottom = all.maxOf { it.y }
        val span = maxOf(right - left, bottom - top)
        if (span <= 0f) return drawn
        val scale = (1f - 2f * MARGIN) / span
        val offsetX = 0.5f - (left + right) * 0.5f * scale
        val offsetY = 0.5f - (top + bottom) * 0.5f * scale
        return drawn.map { part ->
            part.map { Vec2(it.x * scale + offsetX, it.y * scale + offsetY) }
        }
    }

    // ---- the six marks ---------------------------------------------------------------

    /** 1/2 — the long lid over the eye, thick at the outer corner. */
    private fun upperLid(): List<Vec2> = ribbon(
        spine = curve(Vec2(-0.46f, 0.06f), Vec2(-0.18f, -0.20f), Vec2(0.34f, -0.16f)),
        from = 0.030f,
        to = 0.075f,
    )

    /** 1/4 — the pupil. */
    private fun pupil(): List<Vec2> = disc(Vec2(-0.04f, 0.02f), 0.115f)

    /** 1/8 — the brow, a separate arc above and clear of the lid. */
    private fun brow(): List<Vec2> = ribbon(
        spine = curve(Vec2(-0.44f, -0.16f), Vec2(-0.12f, -0.42f), Vec2(0.36f, -0.34f)),
        from = 0.028f,
        to = 0.055f,
    )

    /** 1/16 — the shorter lid under the eye, closing the almond. */
    private fun lowerLid(): List<Vec2> = ribbon(
        spine = curve(Vec2(-0.46f, 0.06f), Vec2(-0.20f, 0.19f), Vec2(0.20f, 0.12f)),
        from = 0.026f,
        to = 0.050f,
    )

    /**
     * 1/32 — the curl, which is the mark that says *falcon* rather than *eye*.
     *
     * It runs down from under the outer corner and turns back on itself. The
     * spiral is a real one — the radius shrinks as it goes round — because a
     * curl drawn as an arc of constant radius reads as a hook.
     */
    private fun curl(): List<Vec2> {
        val spine = ArrayList<Vec2>(SMOOTH + 1)
        spine += curve(Vec2(0.16f, 0.14f), Vec2(0.30f, 0.30f), Vec2(0.34f, 0.44f))
        repeat(SMOOTH) { step ->
            val t = (step + 1) / SMOOTH.toFloat()
            // Three quarters of a turn, tightening.
            val angle = (-0.25f + 1.05f * t) * 2f * PI.toFloat()
            val radius = 0.145f * (1f - 0.62f * t)
            spine += Vec2(0.30f + radius * cos(angle), 0.545f + radius * sin(angle))
        }
        return ribbon(spine, from = 0.045f, to = 0.020f)
    }

    /** 1/64 — the teardrop, hanging straight down from under the pupil. */
    private fun teardrop(): List<Vec2> = ribbon(
        spine = curve(Vec2(-0.10f, 0.16f), Vec2(-0.16f, 0.34f), Vec2(-0.20f, 0.50f)),
        from = 0.055f,
        to = 0.012f,
    )

    // ---- the drawing tools -----------------------------------------------------------

    /** A quadratic curve, sampled. Quadratic because three points is a shape. */
    private fun curve(a: Vec2, control: Vec2, b: Vec2): List<Vec2> =
        (0..SMOOTH).map { step ->
            val t = step / SMOOTH.toFloat()
            val u = 1f - t
            Vec2(
                x = u * u * a.x + 2f * u * t * control.x + t * t * b.x,
                y = u * u * a.y + 2f * u * t * control.y + t * t * b.y,
            )
        }

    /**
     * A stroke of varying width, as a closed outline.
     *
     * Down one side of the spine and back up the other, offsetting each point
     * along the spine's own normal. The width runs from [from] to [to] over the
     * length, because every mark in a wedjat is tapered and a constant-width one
     * reads as a diagram of the symbol rather than as the symbol.
     */
    private fun ribbon(spine: List<Vec2>, from: Float, to: Float): List<Vec2> {
        if (spine.size < 2) return emptyList()
        val left = ArrayList<Vec2>(spine.size)
        val right = ArrayList<Vec2>(spine.size)
        spine.indices.forEach { index ->
            val ahead = spine[(index + 1).coerceAtMost(spine.size - 1)]
            val behind = spine[(index - 1).coerceAtLeast(0)]
            val run = Vec2(ahead.x - behind.x, ahead.y - behind.y)
            val length = kotlin.math.sqrt(run.x * run.x + run.y * run.y)
            // A spine that doubles back on itself has no direction here; the
            // neighbours do, and taking theirs is better than dropping a point
            // and leaving a notch in the outline.
            val normal =
                if (length < 1e-6f) Vec2(0f, 0f) else Vec2(-run.y / length, run.x / length)
            val half = (from + (to - from) * (index / (spine.size - 1f))) * 0.5f
            left += Vec2(spine[index].x + normal.x * half, spine[index].y + normal.y * half)
            right += Vec2(spine[index].x - normal.x * half, spine[index].y - normal.y * half)
        }
        return left + right.reversed()
    }

    private fun disc(at: Vec2, radius: Float): List<Vec2> =
        (0 until SMOOTH * 2).map { step ->
            val angle = step * 2f * PI.toFloat() / (SMOOTH * 2)
            Vec2(at.x + radius * cos(angle), at.y + radius * sin(angle))
        }

}
