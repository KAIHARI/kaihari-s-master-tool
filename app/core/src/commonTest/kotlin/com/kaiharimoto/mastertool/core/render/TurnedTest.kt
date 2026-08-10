package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a lathe owes the rest of the app.
 *
 * Almost none of this is about how a turned solid looks. What is here instead
 * are the four things every other file assumes about a `List<Face>` without ever
 * saying so: that the normals point out, that the normal a face carries is the
 * normal its corners actually have, that a closed solid is closed, and that
 * posing it moves every vertex the same way. Each of those is invisible until
 * something is drawn inside out, at which point it is the only explanation.
 */
class TurnedTest {

    private val standing = Pose3(position = Vec3(800f, 300f, 0f))

    /** Three poses, because a normal that is only right upright is not a normal. */
    private val poses = listOf(
        standing,
        standing.copy(rotZ = 37f),
        standing.copy(rotZ = -113f, rotX = 24f, rotY = 11f),
    )

    /** A lamp: a foot, a cove, a stem, and a shade that flares. */
    private val lamp = listOf(
        Ring(radius = 40f, height = 0f),
        Ring(radius = 40f, height = 6f),
        Ring(radius = 14f, height = 14f),
        Ring(radius = 8f, height = 90f),
        Ring(radius = 30f, height = 100f),
        Ring(radius = 58f, height = 150f),
    )

    /** The puzzle: a square base at the top, hanging to a small flat. */
    private val pyramid = listOf(
        Ring(radius = 4f, height = 0f),
        Ring(radius = 55f, height = 120f),
    )

    /** And the case the arithmetic has to survive: a true point. */
    private val cone = listOf(
        Ring(radius = 0f, height = 0f),
        Ring(radius = 50f, height = 90f),
    )

    /** Something that bulges and is still convex, to keep the lamp honest. */
    private val barrel = listOf(
        Ring(radius = 30f, height = 0f),
        Ring(radius = 45f, height = 40f),
        Ring(radius = 30f, height = 80f),
    )

    private val everyProfile = listOf(lamp, pyramid, cone, barrel)

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-3f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    /**
     * A polygon's area times its normal, in one vector.
     *
     * The honest way to ask a face what direction it is really pointing, and the
     * only one that works for a triangle and a thirty-two-sided disc with the
     * same expression. Half the sum of the cross products round the loop.
     */
    private fun vectorArea(face: Face): Vec3 {
        var sum = Vec3.Zero
        val corners = face.corners
        corners.indices.forEach { index ->
            val a = corners[index]
            val b = corners[(index + 1) % corners.size]
            sum += (a cross b)
        }
        return sum * 0.5f
    }

    private fun centreOf(faces: List<Face>): Vec3 {
        val all = faces.flatMap { it.corners }
        return all.fold(Vec3.Zero) { sum, corner -> sum + corner } / all.size.toFloat()
    }

    // ---- the normals --------------------------------------------------------------

    /**
     * Asked only of the convex ones, and that is not a weaker test — it is the
     * only shape of solid the question means anything about. "Points away from
     * the middle" is a statement about a solid you can see every part of from
     * its own centre, and a lamp with a cove above its foot is not one: the
     * underside of that cove genuinely faces back toward the middle. The claim
     * that holds for every profile is two tests down, and it is stronger.
     */
    @Test
    fun everyNormalPointsOutOfASolidThatHasAnOutside() {
        listOf(pyramid, cone, barrel).forEach { profile ->
            poses.forEach { pose ->
                val faces = Turned.solid(pose, profile, sides = 16)
                val middle = centreOf(faces)
                faces.forEach { face ->
                    val outward = face.normal dot (face.centre - middle)
                    assertTrue(
                        outward > 0f,
                        "a face of $profile at $pose points inward: $outward",
                    )
                }
            }
        }
    }

    /**
     * The claim the whole file exists for.
     *
     * A band's normal is *solved* from the profile rather than crossed from the
     * corners, which is what keeps an apex exact — and which is also a promise
     * that can quietly be broken by a term nobody can see. It is short by
     * `cos(pi / sides)` in its radial half if the chord factor is dropped: 0.7°
     * at twenty sides, 4.4° at eight, and invisible in a picture until a
     * highlight lands a facet away from where it should.
     */
    @Test
    fun theNormalAFaceCarriesIsTheNormalItsCornersHave() {
        listOf(6, 9, 16, 32).forEach { sides ->
            everyProfile.forEach { profile ->
                poses.forEach { pose ->
                    Turned.solid(pose, profile, sides).forEach { face ->
                        val measured = vectorArea(face)
                        assertTrue(
                            measured.length > 1e-3f,
                            "a face of $profile at $sides sides has no area",
                        )
                        val agreement = measured.normalised() dot face.normal
                        assertClose(
                            1f,
                            agreement,
                            tolerance = 1e-4f,
                            note = "$sides sides, face at ${face.centre}:",
                        )
                    }
                }
            }
        }
    }

    /**
     * A closed polyhedron's faces sum to no area at all.
     *
     * The divergence theorem, and the cheapest complete audit of a mesh there
     * is: it goes non-zero if a cap is missing, if a band is skipped, if a
     * normal is inverted, or if two rings disagree about how many sides they
     * have. Scaled against the solid's own surface area so the threshold means
     * the same thing for a finial and for a shade.
     */
    @Test
    fun aCappedProfileIsAClosedSolid() {
        everyProfile.forEach { profile ->
            poses.forEach { pose ->
                val faces = Turned.solid(pose, profile, sides = 20)
                val total = faces.fold(Vec3.Zero) { sum, face -> sum + vectorArea(face) }
                val surface = faces.sumOf { vectorArea(it).length.toDouble() }.toFloat()
                assertTrue(
                    total.length < surface * 1e-4f,
                    "$profile at $pose leaks: ${total.length} against a surface of $surface",
                )
            }
        }
    }

    /**
     * The claim that holds for a lamp as well as for a cone, and the one that
     * says *which way* the normals point rather than only that they agree with
     * each other.
     *
     * The divergence theorem over a closed surface of flat faces: a face's
     * `x · n` is the same at every point on it, because that is what a plane
     * is, so the whole integral is `(corner · normal) · area` and the volume is
     * a third of the sum. It comes out **positive exactly when the normals face
     * out**, and it is asked with the normal each face is carrying rather than
     * with the winding — so this is a test of the answer the renderer will
     * actually be given.
     *
     * And the number it should come to is not a fixture. An `n`-sided band
     * between two rings is the frustum of an `n`-sided pyramid, whose volume is
     * `h · k · (r0² + r0·r1 + r1²) / 3` for `k = (n / 2) · sin(2π / n)` — so the
     * profile predicts the volume, and a band that is silently missing shows up
     * here as a shortfall rather than as nothing at all.
     */
    @Test
    fun theNormalsFaceOutAndTheSolidHoldsWhatTheProfileSaysItDoes() {
        val sides = 18
        val area = sides / 2f * sin(2f * PI.toFloat() / sides)
        everyProfile.forEach { profile ->
            poses.forEach { pose ->
                val faces = Turned.solid(pose, profile, sides)
                val enclosed = faces.sumOf { face ->
                    val outward = face.corners.first() dot face.normal
                    (outward * vectorArea(face).length / 3f).toDouble()
                }.toFloat()

                val predicted = (0 until profile.size - 1).sumOf { band ->
                    val low = profile[band]
                    val high = profile[band + 1]
                    val span = high.height - low.height
                    val radii = low.radius * low.radius +
                        low.radius * high.radius +
                        high.radius * high.radius
                    (span * area * radii / 3f).toDouble()
                }.toFloat()

                assertTrue(enclosed > 0f, "$profile at $pose is inside out: $enclosed")
                assertClose(
                    1f,
                    enclosed / predicted,
                    tolerance = 1e-3f,
                    note = "$profile holds $enclosed against $predicted —",
                )
            }
        }
    }

    // ---- the shape ----------------------------------------------------------------

    @Test
    fun theProfileIsTheSilhouette() {
        val faces = Turned.solid(standing, lamp, sides = 12)
        faces.flatMap { it.corners }.forEach { corner ->
            val out = hypot(corner.x - standing.position.x, corner.y - standing.position.y)
            val up = corner.z - standing.position.z
            assertTrue(
                lamp.any { abs(it.radius - out) < 1e-2f && abs(it.height - up) < 1e-2f },
                "a vertex at radius $out height $up is on no ring of the profile",
            )
        }
    }

    @Test
    fun anApexIsOnePointAndItsBandIsTriangles() {
        val faces = Turned.solid(standing, cone, sides = 10)
        val bands = faces.filter { it.corners.size == 3 }
        assertEquals(10, bands.size, "a cone on ten sides has ten triangles")
        // One cap, at the wide end. The point end has nothing to close.
        assertEquals(11, faces.size, "and no cap over the point")

        val apex = Vec3(standing.position.x, standing.position.y, standing.position.z)
        bands.forEach { band ->
            assertTrue(
                band.corners.any { (it - apex).length < 1e-3f },
                "a triangle of the cone does not touch the point",
            )
        }
    }

    /**
     * The truncation that is not a truncation.
     *
     * A cone cut off a hair below its point has the same sides as the cone, so
     * the exactness at an apex is a claim that can be checked rather than
     * asserted: take the point away and nothing about the surface moves.
     */
    @Test
    fun aPointIsTheLimitOfASmallFlatAndNotASpecialCase() {
        val sharp = Turned.solid(standing, cone, sides = 12)
        val blunt = Turned.solid(
            standing,
            listOf(Ring(radius = 0.05f, height = 0.09f), cone[1]),
            sides = 12,
        )
        val sharpBands = sharp.filter { it.corners.size == 3 }
        val bluntBands = blunt.filter { it.corners.size == 4 }
        assertEquals(sharpBands.size, bluntBands.size)
        sharpBands.indices.forEach { index ->
            val agreement = sharpBands[index].normal dot bluntBands[index].normal
            assertClose(1f, agreement, tolerance = 1e-5f, note = "band $index:")
        }
    }

    // ---- how round is round enough ------------------------------------------------

    @Test
    fun aTurnNeverLeavesAFlatBiggerThanHalfAPixel() {
        var radius = 1f
        while (radius <= 600f) {
            val sides = Turned.sides(radius)
            assertTrue(sides in Turned.MIN_SIDES..Turned.MAX_SIDES, "$radius gave $sides sides")
            if (sides < Turned.MAX_SIDES) {
                val sagitta = radius * (1f - cos(PI.toFloat() / sides))
                assertTrue(
                    sagitta <= Turned.SMOOTH + 1e-4f,
                    "a circle of $radius on $sides sides misses itself by $sagitta",
                )
            }
            radius += 1f
        }
    }

    /**
     * A turn is always centrally symmetric, so its bounding box is centred on
     * the axis. The room sorts, measures and lights every fixture through that
     * box; an odd side count slides it off the foot by the sagitta on one side.
     */
    @Test
    fun aTurnIsTheSameShapeUpsideDownSoItsBoxIsCentred() {
        var radius = 0.5f
        while (radius <= 400f) {
            assertEquals(0, Turned.sides(radius) % 2, "a circle of $radius has an odd turn")
            radius += 0.5f
        }

        val faces = Turned.solid(standing, barrel)
        val widest = 45f
        val here = standing.position
        listOf(
            faces.minOf { face -> face.corners.minOf { it.x } } - here.x to -widest,
            faces.maxOf { face -> face.corners.maxOf { it.x } } - here.x to widest,
            faces.minOf { face -> face.corners.minOf { it.y } } - here.y to -widest,
            faces.maxOf { face -> face.corners.maxOf { it.y } } - here.y to widest,
        ).forEach { (measured, edge) ->
            // Inside the circle it stands for, and by the same amount on both
            // sides — which is the whole claim.
            assertTrue(abs(measured) <= abs(edge) + 1e-3f, "$measured is outside $edge")
            assertTrue(abs(measured) >= abs(edge) - Turned.SMOOTH, "$measured is nowhere near $edge")
        }
    }

    @Test
    fun aThingTooSmallToBeRoundIsNotGivenSidesItCannotShow() {
        assertEquals(Turned.MIN_SIDES, Turned.sides(0f))
        assertEquals(Turned.MIN_SIDES, Turned.sides(Turned.SMOOTH))
        assertTrue(Turned.sides(600f) == Turned.MAX_SIDES)
        // And the count never falls as a thing gets bigger.
        var previous = 0
        var radius = 0f
        while (radius <= 400f) {
            val sides = Turned.sides(radius)
            assertTrue(sides >= previous, "$radius went backwards: $sides after $previous")
            previous = sides
            radius += 0.5f
        }
    }

    // ---- and it is genuinely posed -------------------------------------------------

    /**
     * The one thing `CardSolid.slab` cannot do, which is why this exists.
     *
     * A slab's body hangs down the *stage's* z whatever the pose says, so tipping
     * one leaves the body vertical while the face turns. Every vertex here goes
     * through `Rot3.place`, so a turned solid tipped about x is the tipped solid.
     */
    @Test
    fun tippingItTipsTheWholeSolidAndNotJustItsTop() {
        val upright = Turned.solid(standing, lamp, sides = 8)
        val tipped = Turned.solid(standing.copy(rotX = 30f), lamp, sides = 8)

        upright.indices.forEach { index ->
            upright[index].corners.indices.forEach { corner ->
                val local = upright[index].corners[corner] - standing.position
                val expected = Rot3.rotateX(local, 30f) + standing.position
                val actual = tipped[index].corners[corner]
                assertClose(expected.x, actual.x, tolerance = 1e-2f, note = "x:")
                assertClose(expected.y, actual.y, tolerance = 1e-2f, note = "y:")
                assertClose(expected.z, actual.z, tolerance = 1e-2f, note = "z:")
            }
        }
    }

    @Test
    fun aWholeSegmentOfTurnPutsItBackWhereItWas() {
        val sides = 9
        val before = Turned.solid(standing, lamp, sides)
        val after = Turned.solid(standing.copy(rotZ = 360f / sides), lamp, sides)

        val here = before.flatMap { it.corners }
        val there = after.flatMap { it.corners }
        assertEquals(here.size, there.size)
        here.forEach { corner ->
            assertTrue(
                there.any { (it - corner).length < 1e-2f },
                "a vertex at $corner has nowhere to go after a whole segment of turn",
            )
        }
    }

    @Test
    fun aProfileWithNothingInItDrawsNothing() {
        assertTrue(Turned.solid(standing, emptyList()).isEmpty())
        assertTrue(Turned.solid(standing, listOf(Ring(10f, 0f))).isEmpty())
        assertTrue(Turned.solid(standing, lamp, sides = 2).isEmpty())
    }
}
