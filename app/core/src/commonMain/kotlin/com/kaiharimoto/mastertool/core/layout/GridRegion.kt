package com.kaiharimoto.mastertool.core.layout

/**
 * A point on the lattice between grid cells, in cell units.
 *
 * (0,0) is the top-left corner of cell (0,0); (1,0) is the top-right corner of
 * that same cell. Cell units rather than pixels because the caller knows the
 * card size and the gutter and this does not — and because integers make the
 * tracing exact, with no float comparisons deciding whether two edges meet.
 */
data class GridPoint(val x: Int, val y: Int)

/**
 * One closed loop of a region's boundary, as its corners in order.
 *
 * The outer boundary of a region is one ring; a hole inside it is another. Both
 * are traced clockwise in screen coordinates (x right, y down), so a caller can
 * tell them apart by signed area: the outer ring encloses positive area, a hole
 * negative.
 */
data class RegionRing(val corners: List<GridPoint>) {

    /** Twice the signed area — positive for an outer ring, negative for a hole. */
    val signedArea2: Int
        get() = corners.indices.sumOf { i ->
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            (a.x * b.y - b.x * a.y)
        }

    val isHole: Boolean get() = signedArea2 < 0
}

/**
 * The outline of a set of grid cells, traced exactly.
 *
 * This is what makes a group of cards read as one shape rather than as a pile
 * of separate highlights. Give it the cells a group occupies and it gives back
 * the boundary of their union: cards that touch — side by side, or one directly
 * above another — share an edge, that edge is interior, and the outline goes
 * around the outside of the whole cluster. Cards that touch nothing come back
 * as their own small ring.
 *
 * Kept in `:core` and in integer cell units because it is pure combinatorics
 * and the fiddly part is the corner cases, not the drawing: a marching-squares
 * pass over floats would have to decide whether two edges meet by comparing
 * them, and at a diagonal touch — two cells meeting only at a corner — that
 * decision is the difference between one figure-eight ring and two clean ones.
 * Here the lattice is integral, so it is decided by construction.
 */
object GridRegion {

    /**
     * Traces [cells] (indices into a [columns]-wide grid) into closed rings.
     *
     * Rings come back with collinear points removed, so a straight run of five
     * cards is four corners and not twelve. Diagonal touches are separated: two
     * cells meeting at a single corner are two rings, because drawing them as
     * one would put a pinch point in the outline that reads as a mistake.
     */
    fun outline(cells: Collection<Int>, columns: Int): List<RegionRing> {
        val width = columns.coerceAtLeast(1)
        val occupied = cells.filter { it >= 0 }.map { it / width to it % width }.toSet()
        if (occupied.isEmpty()) return emptyList()

        // Every edge of every cell whose neighbour is missing, directed so the
        // inside of the region is always on the right. Chaining edges that share
        // a point then walks each boundary in one consistent direction.
        val edges = HashMap<GridPoint, MutableList<GridPoint>>()
        fun edge(from: GridPoint, to: GridPoint) {
            edges.getOrPut(from) { mutableListOf() }.add(to)
        }

        occupied.forEach { (row, col) ->
            val topLeft = GridPoint(col, row)
            val topRight = GridPoint(col + 1, row)
            val bottomRight = GridPoint(col + 1, row + 1)
            val bottomLeft = GridPoint(col, row + 1)

            if ((row - 1) to col !in occupied) edge(topLeft, topRight)
            if (row to (col + 1) !in occupied) edge(topRight, bottomRight)
            if ((row + 1) to col !in occupied) edge(bottomRight, bottomLeft)
            if (row to (col - 1) !in occupied) edge(bottomLeft, topLeft)
        }

        val rings = mutableListOf<RegionRing>()

        while (edges.isNotEmpty()) {
            val start = edges.keys.first()
            var current = start
            var next = edges.getValue(start).removeAt(0)
            if (edges.getValue(start).isEmpty()) edges.remove(start)

            val path = mutableListOf(start)

            while (next != start) {
                path += next
                val outgoing = edges[next]
                    // A boundary that does not close is impossible for a union
                    // of cells; if it ever happened, abandoning the ring is
                    // better than looping forever.
                    ?: break
                // At a lattice point where four edges meet — two cells touching
                // only at a corner — take the turn that keeps the inside on the
                // right, which is the one that turns *away* from the incoming
                // direction. That splits the pinch into two clean rings.
                val chosen = if (outgoing.size == 1) 0 else pickTurn(current, next, outgoing)
                val following = outgoing.removeAt(chosen)
                if (outgoing.isEmpty()) edges.remove(next)
                current = next
                next = following
            }

            if (path.size >= 4) rings += RegionRing(dropCollinear(path))
        }

        return rings
    }

    /**
     * Whether these cells form one connected shape.
     *
     * Connected here means edge-adjacent, the same rule the outline traces by —
     * cells that only touch at a corner are two shapes, and look like two.
     */
    fun isConnected(cells: Collection<Int>, columns: Int): Boolean {
        val width = columns.coerceAtLeast(1)
        val remaining = cells.filter { it >= 0 }.toMutableSet()
        if (remaining.size <= 1) return true

        val frontier = ArrayDeque(listOf(remaining.first()))
        remaining.remove(frontier.first())

        while (frontier.isNotEmpty()) {
            val cell = frontier.removeFirst()
            val row = cell / width
            val col = cell % width
            val neighbours = listOfNotNull(
                (cell - width).takeIf { row > 0 },
                (cell + width),
                (cell - 1).takeIf { col > 0 },
                (cell + 1).takeIf { col < width - 1 },
            )
            neighbours.forEach { neighbour ->
                if (remaining.remove(neighbour)) frontier.addLast(neighbour)
            }
        }

        return remaining.isEmpty()
    }

    /**
     * At a four-way lattice point, the outgoing edge that turns right hardest.
     *
     * Turning as tightly as possible keeps the traced ring hugging the cell it
     * came from, which is what separates two diagonally-touching cells into two
     * rings instead of one crossed figure-eight.
     */
    private fun pickTurn(from: GridPoint, at: GridPoint, options: List<GridPoint>): Int {
        val inX = at.x - from.x
        val inY = at.y - from.y

        // Screen coordinates have y growing downward, so a *positive* cross
        // product is the clockwise turn — the one that keeps hugging the cell
        // this edge came off. Getting this backwards chains the two cells at a
        // diagonal touch into a single crossed ring.
        fun rank(to: GridPoint): Int {
            val outX = to.x - at.x
            val outY = to.y - at.y
            val cross = inX * outY - inY * outX
            return when {
                cross > 0 -> 0 // clockwise: hug the cell we are tracing
                cross == 0 -> 1 // straight on
                else -> 2 // anticlockwise: that is the other cell's boundary
            }
        }

        var best = 0
        options.forEachIndexed { index, option ->
            if (rank(option) < rank(options[best])) best = index
        }
        return best
    }

    /** Corners only: a point with the same direction in and out is not one. */
    private fun dropCollinear(path: List<GridPoint>): List<GridPoint> {
        if (path.size < 3) return path

        val corners = mutableListOf<GridPoint>()
        path.indices.forEach { i ->
            val previous = path[(i - 1 + path.size) % path.size]
            val point = path[i]
            val next = path[(i + 1) % path.size]

            val inX = point.x - previous.x
            val inY = point.y - previous.y
            val outX = next.x - point.x
            val outY = next.y - point.y

            if (inX * outY - inY * outX != 0) corners += point
        }
        return corners
    }
}
