package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridRegionTest {

    private fun ringsOf(cells: List<Int>, columns: Int) = GridRegion.outline(cells, columns)

    @Test
    fun oneCellIsARectangle() {
        val ring = ringsOf(listOf(11), columns = 10).single()

        assertEquals(4, ring.corners.size)
        assertEquals(
            setOf(
                GridPoint(1, 1), GridPoint(2, 1),
                GridPoint(2, 2), GridPoint(1, 2),
            ),
            ring.corners.toSet(),
        )
        assertFalse(ring.isHole)
    }

    @Test
    fun aRunOfCellsIsOneRectangleNotFive() {
        // The whole point: five cards side by side are one shape with four
        // corners, not five outlines stacked against each other.
        val ring = ringsOf(listOf(0, 1, 2, 3, 4), columns = 10).single()

        assertEquals(4, ring.corners.size)
        assertEquals(0, ring.corners.minOf { it.x })
        assertEquals(5, ring.corners.maxOf { it.x })
        assertEquals(1, ring.corners.maxOf { it.y })
    }

    @Test
    fun cardsStackedInAColumnMergeVertically() {
        // Cards directly above one another share an edge, so a group that runs
        // down the grid is one shape too — the case that made the old per-row
        // plates look like unrelated fragments.
        val ring = ringsOf(listOf(3, 13, 23), columns = 10).single()

        assertEquals(4, ring.corners.size)
        assertEquals(3, ring.corners.minOf { it.x })
        assertEquals(4, ring.corners.maxOf { it.x })
        assertEquals(0, ring.corners.minOf { it.y })
        assertEquals(3, ring.corners.maxOf { it.y })
    }

    @Test
    fun aWrappedRunSplitsWhenItsRowsDoNotOverlap() {
        // Eight cards from index 6: columns 6-9 on the first row, 0-3 on the
        // second. Nothing overlaps vertically, so this is honestly two shapes —
        // the same way a wrapped text selection is drawn as two bands, and the
        // reason a renderer must not assume a range is ever one ring.
        val rings = ringsOf((6..13).toList(), columns = 10)

        assertEquals(2, rings.size)
        assertTrue(rings.all { it.corners.size == 4 })
    }

    @Test
    fun aRunLongerThanARowBecomesOneSteppedRing() {
        // Fourteen cards from index 6 reach into a third row, so the middle row
        // is full and bridges the two ends: one ring, stepped at both ends.
        val rings = ringsOf((6..19).toList(), columns = 10)

        // Six corners: both ends are stepped, and the right edge runs straight
        // down because both rows reach the last column.
        val outer = rings.single { !it.isHole }
        assertEquals(6, outer.corners.size)
        assertEquals(0, outer.corners.minOf { it.x })
        assertEquals(10, outer.corners.maxOf { it.x })
    }

    @Test
    fun scatteredCardsAreOneRingEach() {
        // The complaint case: six handtraps at arbitrary indices. Each is its
        // own clean rectangle; none of them is a fragment of something else.
        val cells = listOf(3, 7, 12, 19, 25, 33)
        val rings = ringsOf(cells, columns = 10)

        assertEquals(6, rings.size)
        assertTrue(rings.all { it.corners.size == 4 })
        assertTrue(rings.none { it.isHole })
    }

    @Test
    fun cellsTouchingOnlyAtACornerStaySeparate() {
        // A diagonal touch traced as one ring pinches to a point, which reads as
        // a drawing mistake. Two rings, two shapes.
        val rings = ringsOf(listOf(0, 11), columns = 10)

        assertEquals(2, rings.size)
        assertTrue(rings.all { it.corners.size == 4 })
    }

    @Test
    fun aRingAroundAGapReportsTheHole() {
        // Eight cells around one empty middle: an outer ring and a hole, and the
        // caller can tell which is which by winding.
        val block = listOf(0, 1, 2, 10, 12, 20, 21, 22)
        val rings = ringsOf(block, columns = 10)

        assertEquals(2, rings.size)
        assertEquals(1, rings.count { it.isHole })
        val outer = rings.first { !it.isHole }
        assertEquals(4, outer.corners.size)
    }

    @Test
    fun anLShapeKeepsItsInsideCorner() {
        // Three cells in an L: six corners, one of them concave. A caller
        // rounding corners needs both kinds to survive tracing.
        val ring = ringsOf(listOf(0, 10, 11), columns = 10).single()

        assertEquals(6, ring.corners.size)
    }

    @Test
    fun connectivityUsesEdgesNotCorners() {
        assertTrue(GridRegion.isConnected(listOf(0, 1, 2), columns = 10))
        assertTrue(GridRegion.isConnected(listOf(0, 10, 20), columns = 10))
        // A wrap only connects when the rows overlap in columns.
        assertFalse(GridRegion.isConnected(listOf(6, 7, 8, 9, 10, 11), columns = 10))
        assertTrue(GridRegion.isConnected(listOf(8, 9, 18, 19), columns = 10))
        assertFalse(GridRegion.isConnected(listOf(0, 11), columns = 10))
        assertFalse(GridRegion.isConnected(listOf(3, 7, 12), columns = 10))
        assertTrue(GridRegion.isConnected(emptyList(), columns = 10))
    }

    @Test
    fun anEmptySelectionDrawsNothing() {
        assertTrue(ringsOf(emptyList(), columns = 10).isEmpty())
    }

    @Test
    fun everyRingClosesAndWindsTheSameWay() {
        // Whatever the shape, an outer boundary winds one way and a hole the
        // other — the invariant the renderer relies on to fill correctly.
        val messy = listOf(0, 1, 2, 3, 11, 12, 20, 22, 24, 30, 31, 32, 33, 34)
        val rings = ringsOf(messy, columns = 10)

        assertTrue(rings.isNotEmpty())
        rings.forEach { ring ->
            assertTrue(ring.corners.size >= 4)
            assertTrue(ring.signedArea2 != 0)
        }
    }
}
