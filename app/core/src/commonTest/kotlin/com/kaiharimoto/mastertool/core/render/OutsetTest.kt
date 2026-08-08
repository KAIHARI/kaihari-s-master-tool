package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Claims about growing a shape.
 *
 * The first two are the bug this replaced, written as the thing that was not
 * true: a card's penumbra was half again as wide above and below as it was at
 * the sides, and the guard that was supposed to stop an inset inverting the
 * shape measured the wrong radius by a factor of 1.8.
 */
class OutsetTest {

    /** A card face at the size the reference stage draws one, centred on the origin. */
    private val card = listOf(
        Vec2(-29.5f, -43f), Vec2(29.5f, -43f), Vec2(29.5f, 43f), Vec2(-29.5f, 43f),
    )

    private fun bounds(shape: List<Vec2>) = listOf(
        shape.minOf { it.x }, shape.maxOf { it.x },
        shape.minOf { it.y }, shape.maxOf { it.y },
    )

    @Test
    fun everyEdgeOfACardMovesOutByTheSameAmount() {
        // The whole point. Moving the *corners* out radially — which is what
        // this replaced — moved the long edges by 0.825w and the short ones by
        // 0.566w, a ratio of 1.46 that no light produces.
        val w = 8f
        val (left, right, top, bottom) = bounds(Outset.of(card, w))
        assertEquals(-29.5f - w, left, 1e-3f, "left edge")
        assertEquals(29.5f + w, right, 1e-3f, "right edge")
        assertEquals(-43f - w, top, 1e-3f, "top edge")
        assertEquals(43f + w, bottom, 1e-3f, "bottom edge")
    }

    @Test
    fun aShapeIsNeverTurnedInsideOutByAnInset() {
        // The old guard was `min over corners of the distance to the centre`,
        // which for a rectangle is the half-*diagonal*: 52.1 on a card whose
        // short half-axis is 29.5. It let an inset of 46 through, and an inset
        // of 46 on a shape 59 wide is a bow tie.
        //
        // Stated over the legal range only. Past the inradius the shape is
        // returned untouched — which is the guard doing its job, and would read
        // as "the area went back up" to a monotonicity check that spanned the
        // boundary. That half is `theGuardLetsGoExactly...` below.
        var lastArea = Outset.signedArea(card)
        for (inset in 1..29) {
            val area = Outset.signedArea(Outset.of(card, -inset.toFloat()))
            assertTrue(area > 0f, "inset $inset inverted the shape (area $area)")
            assertTrue(area <= lastArea + 1e-3f, "inset $inset grew the shape")
            lastArea = area
        }
        // And nothing anywhere near the old guard's half-diagonal folds it.
        (30..80).forEach { inset ->
            assertTrue(
                Outset.signedArea(Outset.of(card, -inset.toFloat())) > 0f,
                "inset $inset inverted the shape",
            )
        }
    }

    @Test
    fun theGuardLetsGoExactlyAtTheInradiusAndNotAtTheHalfDiagonal() {
        assertEquals(29.5f, Outset.inradius(card), 1e-3f)
        // Just inside it still shrinks; past it the shape is returned untouched
        // rather than folded through itself.
        assertTrue(Outset.signedArea(Outset.of(card, -29f)) < Outset.signedArea(card))
        assertEquals(card, Outset.of(card, -30f))
    }

    @Test
    fun growingAlwaysAddsAreaAndShrinkingAlwaysRemovesIt() {
        // Stated over a keystoned quad as well as a rectangle, because every
        // shape this is actually asked about has been through a projection.
        val keystoned = listOf(
            Vec2(-20f, -40f), Vec2(34f, -31f), Vec2(28f, 44f), Vec2(-31f, 39f),
        )
        listOf(card, keystoned).forEach { shape ->
            val area = Outset.signedArea(shape)
            for (w in 1..20) {
                assertTrue(Outset.signedArea(Outset.of(shape, w.toFloat())) > area)
                assertTrue(Outset.signedArea(Outset.of(shape, -w * 0.5f)) < area)
            }
        }
    }

    @Test
    fun theOffsetDoesNotCareWhichWayRoundTheCornersWereListed() {
        // The corners arrive from `CardSolid.face`, and which way they wind
        // depends on whether the card is showing its back — which is a fact
        // about the card and must not be a fact about its shadow.
        val reversed = card.reversed()
        val grown = Outset.of(card, 6f)
        val grownReversed = Outset.of(reversed, 6f).reversed()
        grown.indices.forEach { i ->
            assertTrue(
                abs(grown[i].x - grownReversed[i].x) < 1e-3f &&
                    abs(grown[i].y - grownReversed[i].y) < 1e-3f,
                "corner $i: ${grown[i]} vs ${grownReversed[i]}",
            )
        }
    }

    @Test
    fun aQuadSeenAlmostEdgeOnDoesNotGrowAWhisker() {
        // Every card passes through this twice per flip, and an uncapped miter
        // throws the sharp corners out by a multiple of the offset. The cap is
        // what keeps a shadow from sprouting a spike on those two frames.
        val slivers = listOf(
            listOf(Vec2(-40f, -0.4f), Vec2(40f, -0.2f), Vec2(40f, 0.2f), Vec2(-40f, 0.4f)),
            listOf(Vec2(-40f, 0f), Vec2(0f, -0.3f), Vec2(40f, 0f), Vec2(0f, 0.3f)),
        )
        slivers.forEach { sliver ->
            val w = 5f
            val grown = Outset.of(sliver, w)
            grown.indices.forEach { i ->
                val moved = kotlin.math.hypot(
                    grown[i].x - sliver[i].x,
                    grown[i].y - sliver[i].y,
                )
                assertTrue(moved <= w / 0.14f + 1e-2f, "corner $i flew $moved on an offset of $w")
            }
        }
    }

    @Test
    fun zeroLeavesTheShapeAlone() {
        // The rings are drawn from an inset to a spread, and the middle one
        // lands on zero. It has to be the identity or the geometric edge of
        // every shadow moves.
        assertEquals(card, Outset.of(card, 0f))
    }
}
