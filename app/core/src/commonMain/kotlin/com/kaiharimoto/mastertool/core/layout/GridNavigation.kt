package com.kaiharimoto.mastertool.core.layout

/** Which way an arrow key points. */
enum class GridStep { LEFT, RIGHT, UP, DOWN }

/**
 * Moving through a wrapped grid by arrow key.
 *
 * The list is one-dimensional and the grid is a wrap of it, so left and right
 * are just the neighbouring positions — stepping left from the start of a row
 * lands on the end of the row above, which is what a list does and what anyone
 * arranging cards expects. Up and down are a row's worth at a time.
 *
 * Null means the edge: there is nowhere in this grid to go. That is returned
 * rather than clamping so the caller can decide what an edge means — a cursor
 * stays put, a drag might cross into the next pane — instead of every caller
 * having to detect "it gave me back the same index" and guess why.
 */
object GridNavigation {

    fun step(from: Int, count: Int, columns: Int, step: GridStep): Int? {
        if (count <= 0 || columns <= 0 || from !in 0 until count) return null

        return when (step) {
            GridStep.LEFT -> (from - 1).takeIf { it >= 0 }
            GridStep.RIGHT -> (from + 1).takeIf { it < count }
            GridStep.UP -> (from - columns).takeIf { it >= 0 }

            // The last row is usually short, so stepping down out of a full row
            // has to land on the last card rather than on nothing. Asking
            // whether there *is* a row below — rather than whether the arithmetic
            // stayed in range — is what tells those two cases apart.
            GridStep.DOWN -> {
                val lastRowStart = ((count - 1) / columns) * columns
                if (from >= lastRowStart) null else minOf(from + columns, count - 1)
            }
        }
    }
}
