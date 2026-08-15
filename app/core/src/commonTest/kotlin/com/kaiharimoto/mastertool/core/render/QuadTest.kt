package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Four cross products, and the four ways they can be wrong.
 *
 * A hit test is the one piece of geometry a person notices immediately and can
 * never quite describe — "it didn't pick up the card" — so the claims here are
 * about the properties rather than about examples: both windings, edges count,
 * and a shape that is not a quad is not a hit.
 */
class QuadTest {

    private val square = listOf(
        Vec2(0f, 0f), Vec2(10f, 0f), Vec2(10f, 10f), Vec2(0f, 10f),
    )

    @Test
    fun theInsideIsInsideAndTheOutsideIsNot() {
        assertTrue(Quad.contains(square, 5f, 5f))
        assertTrue(Quad.contains(square, 0.1f, 9.9f))
        assertTrue(!Quad.contains(square, -0.1f, 5f))
        assertTrue(!Quad.contains(square, 5f, 10.1f))
        assertTrue(!Quad.contains(square, 20f, 20f))
    }

    /**
     * Either winding, because `flatten` is a projection.
     *
     * `CardSolid.face` hands back its corners in one order, but a card seen from
     * the other side of the table comes back through the projection wound the
     * other way. A test that assumed one of them would work until somebody
     * walked round the table, which is the kind of bug this stage specialises in.
     */
    @Test
    fun itDoesNotCareWhichWayRoundTheCornersCame() {
        val other = square.reversed()
        listOf(square, other).forEach { quad ->
            assertTrue(Quad.contains(quad, 5f, 5f), "the middle of $quad")
            assertTrue(!Quad.contains(quad, 50f, 50f), "far outside $quad")
        }
    }

    /** A card's own edge belongs to the card. */
    @Test
    fun theEdgeCountsAsInside() {
        assertTrue(Quad.contains(square, 0f, 5f), "left edge")
        assertTrue(Quad.contains(square, 10f, 5f), "right edge")
        assertTrue(Quad.contains(square, 5f, 0f), "top edge")
        assertTrue(Quad.contains(square, 0f, 0f), "a corner")
    }

    /**
     * A leaned card's real shape: sheared and shorter, which is what `flatten`
     * makes of a card standing up off the felt.
     */
    @Test
    fun aShearedQuadIsStillAnswerable() {
        val leaned = listOf(
            Vec2(2f, 0f), Vec2(12f, 0f), Vec2(10f, 6f), Vec2(0f, 6f),
        )
        assertTrue(Quad.contains(leaned, 6f, 3f), "the middle")
        assertTrue(Quad.contains(leaned, 11f, 1f), "inside the lean")
        assertTrue(!Quad.contains(leaned, 1f, 1f), "outside the lean")
        assertTrue(!Quad.contains(leaned, 6f, 7f), "below it")
    }

    @Test
    fun anythingThatIsNotFourCornersIsNotAQuad() {
        assertTrue(!Quad.contains(emptyList(), 0f, 0f))
        assertTrue(!Quad.contains(square.take(3), 5f, 5f))
        assertTrue(!Quad.contains(square + Vec2(5f, 5f), 5f, 5f))
    }

    /**
     * A degenerate quad — every corner the same point — swallows nothing except
     * that point.
     *
     * It arises for real: `StagePlane.flatten` collapses a card seen exactly
     * edge-on, and a hit test that answered "yes" everywhere at that moment
     * would grab a card nobody can see.
     */
    @Test
    fun aCollapsedQuadIsNotTheWholeTable() {
        val flat = List(4) { Vec2(3f, 3f) }
        assertTrue(!Quad.contains(flat, 50f, 50f))
        assertTrue(!Quad.contains(flat, 3f, 4f))
    }
}
