package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The map that takes a card's own rectangle to the four corners it really has.
 *
 * ## Why a card needs one
 *
 * A card lies in a plane. The camera maps that plane onto the screen's plane,
 * and a projective map between two planes is a **homography** — eight degrees
 * of freedom, fixed by four points. Nothing smaller reaches it.
 *
 * A `graphicsLayer` offers three Euler angles and a `cameraDistance`, which is
 * four numbers, and it spends them the wrong way: a layer is itself a
 * projective *2-D* map whose divide is forced through the layer's own centre,
 * and whatever it produces is then pasted **flat, at z = 0**, into its parent.
 * So a card given `rotationX` comes out as a picture lying in the table rather
 * than as a card standing out of it — the far end and the near end both want to
 * be larger than the middle (one is higher off the felt, the other further down
 * the tilted plane, and both are therefore nearer the lens) and a divide about
 * the centre has to shrink whichever end it does not magnify. That was worth
 * about five per cent on a leaned hand card, and the *visible* half of it was
 * that the canvas drew the same card's edges from its true corners, a few
 * pixels away from its own picture.
 *
 * Four corners, one map, no divide left over. The corners come from the same
 * `CardSolid.face` → `StagePlane.flatten` call the renderer already makes for
 * the card's body and its shadow, so the picture and the geometry stop being
 * two calculations that have to be kept in agreement and become one.
 *
 * ## What this deliberately is not
 *
 * It is not a [Mat3]. That type's contract is that it is a *rotation*, built
 * only by [Rot3] and read only by `Rot3.eulerOf`; a projective map is defined
 * only up to scale, carries a divide, and has no Euler angles to be pulled
 * apart into. Putting one in the other would falsify a comment that is true.
 *
 * And it knows nothing about `StagePlane`. It takes four points that have
 * already been flattened, which keeps `core/render` free of a dependency on
 * `core/layout` and — more to the point — leaves the flattening at the call
 * site, where it is the *same expression* the edges use. That sharing is the
 * whole fix; hiding it in here would give it somewhere to drift to.
 */
data class Homography(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float,
) {

    /** Where the point ([x], [y]) lands. The divide happens here and nowhere else. */
    fun map(x: Float, y: Float): Vec2 {
        val w = m20 * x + m21 * y + m22
        val inverse = if (w == 0f) 0f else 1f / w
        return Vec2(
            x = (m00 * x + m01 * y + m02) * inverse,
            y = (m10 * x + m11 * y + m12) * inverse,
        )
    }

    fun map(point: Vec2): Vec2 = map(point.x, point.y)

    /**
     * The map that undoes this one, or null where there is none.
     *
     * ## Why a shader needs it and nothing else does
     *
     * Everything drawn *through* a homography gets its own rectangle for free:
     * a card's picture is drawn inside `withTransform { transform(matrix) }`, so
     * Skia composes the local matrix with the CTM and inverts the total per
     * pixel, and `main(float2 p)` receives card-local coordinates with the
     * divide already done.
     *
     * A pile's ruled side is the exception, and it is the one that matters. It
     * is drawn as a *path in the mat's frame*, because that is where
     * `StagePlane.flatten` puts a face with a height — so a shader colouring it
     * is handed mat pixels and has to recover which card it is standing on. That
     * is this, folded to three `float3` uniforms and eleven instructions:
     *
     * ```
     * float3 q  = uM0 * p.x + uM1 * p.y + uM2;
     * float2 uv = q.xy / q.z;
     * ```
     *
     * ## The adjugate, and why there is no division by the determinant
     *
     * A projective map is defined only up to scale — `map` divides by `w`, so
     * multiplying all nine numbers by a constant changes nothing it returns.
     * The adjugate *is* the inverse times the determinant, so it is already the
     * answer, and dividing by `det` would spend nine divisions to reach a matrix
     * that maps every point to the same place.
     *
     * What the determinant is still needed for is the refusal. Null rather than
     * nine infinities, for the same reason [squareToQuad] hands back null: a
     * caller that has to check `isFinite()` on nine floats will one day forget
     * one, and a NaN is not a coordinate — it passes every clamp and draws
     * nothing.
     *
     * ## The threshold, and the one that looked right and was not
     *
     * It has to be relative: a map built by [rectToQuad] carries screen
     * coordinates around a thousand in its last column and reciprocals of a
     * card's width — a hundredth — in its first two, so the determinant runs
     * over orders of magnitude with the card size and an absolute epsilon is a
     * different tolerance on every stage.
     *
     * The obvious relative measure is the largest element cubed, on the argument
     * that a determinant is a sum of triple products. **It rejects every real
     * card.** That measure is only a scale when the nine numbers share one, and
     * here they emphatically do not: the largest element is the translation, a
     * thousand, so the threshold comes out at `1e-6 · 1e9` = 1000 while the true
     * determinant of a perfectly good card is far below it. Every card in view
     * came back null.
     *
     * Hadamard's inequality is the honest one — `|det| <= ||c0||·||c1||·||c2||`
     * over the columns — so that product is the largest determinant these three
     * columns could possibly have, the ratio is a true fraction in `[0, 1]`, and
     * it is invariant to each column being scaled on its own. Which is exactly
     * what [rectToQuad] does to two of them.
     */
    fun inverse(): Homography? {
        // The adjugate: each entry is the cofactor of the *transposed* position,
        // which is what makes this the inverse rather than the cofactor matrix.
        val a00 = m11 * m22 - m12 * m21
        val a01 = m02 * m21 - m01 * m22
        val a02 = m01 * m12 - m02 * m11
        val a10 = m12 * m20 - m10 * m22
        val a11 = m00 * m22 - m02 * m20
        val a12 = m02 * m10 - m00 * m12
        val a20 = m10 * m21 - m11 * m20
        val a21 = m01 * m20 - m00 * m21
        val a22 = m00 * m11 - m01 * m10

        val det = m00 * a00 + m01 * a10 + m02 * a20
        val hadamard =
            sqrt(m00 * m00 + m10 * m10 + m20 * m20) *
                sqrt(m01 * m01 + m11 * m11 + m21 * m21) *
                sqrt(m02 * m02 + m12 * m12 + m22 * m22)
        if (hadamard == 0f || abs(det) <= DEGENERATE * hadamard) return null

        return Homography(a00, a01, a02, a10, a11, a12, a20, a21, a22)
    }

    /**
     * How fast this map runs, at one point: `sqrt(|det J|)`.
     *
     * The number of destination units one source unit covers, averaged over
     * direction — which is what a texture has to know to stop aliasing. A
     * homography's Jacobian determinant is `det(H) / w³`, so the whole of it is
     * one determinant computed once and a `w` computed per point, and the square
     * root is what turns an area ratio back into a length ratio.
     *
     * The reason it is here rather than in a shader is that `w` is the only part
     * that varies: a caller passing this into SkSL sends [determinant] as a
     * uniform and computes the rest from the `w` it already has. There are no
     * derivatives in AGSL or SkSL on either platform, so every level of detail
     * on this stage is analytic and comes out of core. See `docs/AAA.md` #97.
     *
     * Zero where the point is on the map's own horizon, which is the honest
     * answer: nothing there has a finite size.
     */
    fun scaleAt(x: Float, y: Float): Float {
        val w = m20 * x + m21 * y + m22
        if (w == 0f) return 0f
        val det = determinant()
        return sqrt(abs(det) / abs(w * w * w))
    }

    /** The determinant of these nine numbers, which is [scaleAt]'s only constant. */
    fun determinant(): Float =
        m00 * (m11 * m22 - m12 * m21) -
            m01 * (m10 * m22 - m12 * m20) +
            m02 * (m10 * m21 - m11 * m20)

    /**
     * These nine numbers, written into the sixteen a Compose `Matrix` holds.
     *
     * The layout is not a guess and it is not negotiable, so it lives here
     * rather than in the composable that hands it over. `androidx.compose.ui`'s
     * `Matrix` is a flat `FloatArray(16)` indexed `values[row * 4 + column]`,
     * and its own `map(Offset)` reads
     *
     * ```
     * w  = values[3]·x  + values[7]·y  + values[15]
     * x' = (values[0]·x + values[4]·y  + values[12]) / w
     * y' = (values[1]·x + values[5]·y  + values[13]) / w
     * ```
     *
     * — which is this matrix with the rows down the columns, because Compose
     * maps row vectors. Android's own conversion agrees: `setFrom` copies
     * exactly `values[0], [4], [12], [1], [5], [13], [3], [7], [15]` into an
     * `android.graphics.Matrix`'s nine, **perspective row included**, and
     * requires only that the z terms — indices 2, 6, 8, 9, 11 and 14 — are
     * zero, which they are. So the divide survives the trip to a hardware
     * canvas; the "only a subset of transformations" warning on that conversion
     * is about z, not about perspective.
     *
     * Written into an array the caller owns, because sixty cards allocating a
     * `FloatArray(16)` per frame is garbage for a value that lives for one draw
     * call.
     */
    fun writeComposeMatrix(out: FloatArray) {
        require(out.size == 16) { "a Compose matrix is sixteen floats, not ${out.size}" }

        out[0] = m00; out[4] = m01; out[12] = m02
        out[1] = m10; out[5] = m11; out[13] = m12
        out[3] = m20; out[7] = m21; out[15] = m22

        // The axis nothing here uses, left as the identity — and the six slots
        // Android's conversion insists on finding empty.
        out[2] = 0f; out[6] = 0f; out[8] = 0f; out[9] = 0f
        out[10] = 1f
        out[11] = 0f; out[14] = 0f
    }

    companion object {
        val Identity = Homography(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        /**
         * How near collinear three corners may get before there is no map.
         *
         * Compared against an *area*, so the threshold has to be one too —
         * hence the squared span. An absolute number would be a different
         * tolerance at every card size, which on a stage whose cards resize
         * with the deck is the same as no tolerance at all.
         */
        private const val DEGENERATE = 1e-6f

        /**
         * The unit square onto four corners, clockwise from `(0, 0)`.
         *
         * Heckbert's closed form (*Fundamentals of Texture Mapping and Image
         * Warping*, §2.2), which is worth saying out loud: this needs **no
         * linear solver**. Eight unknowns and eight equations, and the four
         * source points being the corners of the unit square collapses the
         * whole thing to a handful of subtractions and one division. A general
         * solve would be a page of code and a pivoting strategy to get wrong.
         *
         * Null when the four points are too near collinear to determine a map
         * — a card seen exactly edge-on, which every card passes through twice
         * on every flip. Null rather than a matrix full of infinities, because
         * a caller that has to check `isFinite()` on nine floats will one day
         * forget one.
         */
        fun squareToQuad(quad: List<Vec2>): Homography? {
            if (quad.size != 4) return null

            // Solved about the quad's own centre and translated back afterwards.
            // The corners arrive as screen coordinates around a thousand pixels
            // out, where a float's spacing is a ten-thousandth; the sums below
            // are differences of four of them and are *supposed* to come out
            // near zero, which is precisely where that spacing turns into the
            // answer. Centring costs four additions and removes it.
            val centreX = (quad[0].x + quad[1].x + quad[2].x + quad[3].x) / 4f
            val centreY = (quad[0].y + quad[1].y + quad[2].y + quad[3].y) / 4f

            val x0 = quad[0].x - centreX; val y0 = quad[0].y - centreY
            val x1 = quad[1].x - centreX; val y1 = quad[1].y - centreY
            val x2 = quad[2].x - centreX; val y2 = quad[2].y - centreY
            val x3 = quad[3].x - centreX; val y3 = quad[3].y - centreY

            // The two edges meeting at the far corner, and how far the quad is
            // from closing as a parallelogram.
            val dx1 = x1 - x2; val dx2 = x3 - x2; val sumX = x0 - x1 + x2 - x3
            val dy1 = y1 - y2; val dy2 = y3 - y2; val sumY = y0 - y1 + y2 - y3

            val den = dx1 * dy2 - dy1 * dx2
            val span = max(max(abs(dx1), abs(dy1)), max(abs(dx2), abs(dy2)))
            if (span == 0f || abs(den) <= DEGENERATE * span * span) return null

            // A quad that closes exactly is an affine map and is given one, so
            // that a card whose corners really do form a parallelogram is
            // *bit-exactly* a translate, a scale and a shear — no divide it did
            // not previously have. The test is `== 0f` on purpose: a relative
            // epsilon would pick the branch on the last bit of a sine, which is
            // the class of instability `GoldenStageTest` exists to keep out.
            val perspectiveX: Float
            val perspectiveY: Float
            if (sumX == 0f && sumY == 0f) {
                perspectiveX = 0f
                perspectiveY = 0f
            } else {
                perspectiveX = (sumX * dy2 - sumY * dx2) / den
                perspectiveY = (dx1 * sumY - dy1 * sumX) / den
            }

            return Homography(
                m00 = x1 - x0 + perspectiveX * x1 + centreX * perspectiveX,
                m01 = x3 - x0 + perspectiveY * x3 + centreX * perspectiveY,
                m02 = x0 + centreX,
                m10 = y1 - y0 + perspectiveX * y1 + centreY * perspectiveX,
                m11 = y3 - y0 + perspectiveY * y3 + centreY * perspectiveY,
                m12 = y0 + centreY,
                m20 = perspectiveX,
                m21 = perspectiveY,
                m22 = 1f,
            )
        }

        /**
         * A [width] by [height] rectangle onto four corners, clockwise from the
         * top-left — the order `CardSolid.face` hands its corners back in, and
         * the order a Compose layout runs in, which is the coincidence that
         * makes this a drop-in for a card's `graphicsLayer`.
         *
         * The rectangle is folded in by dividing two columns rather than by
         * composing with a scale matrix: the same arithmetic, one fewer object,
         * and no second rounding.
         */
        fun rectToQuad(width: Float, height: Float, quad: List<Vec2>): Homography? {
            if (width <= 0f || height <= 0f) return null
            val unit = squareToQuad(quad) ?: return null
            return unit.copy(
                m00 = unit.m00 / width, m01 = unit.m01 / height,
                m10 = unit.m10 / width, m11 = unit.m11 / height,
                m20 = unit.m20 / width, m21 = unit.m21 / height,
            )
        }
    }
}
