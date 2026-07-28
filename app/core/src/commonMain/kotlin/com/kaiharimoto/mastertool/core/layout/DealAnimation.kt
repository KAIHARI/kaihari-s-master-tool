package com.kaiharimoto.mastertool.core.layout

/**
 * Dealing a deck onto the table.
 *
 * Opening a saved list put forty cards on screen at once, which is the one
 * moment in this app that could not happen at a table and the one worth
 * spending a frame budget on. Cards arrive in a wave instead.
 *
 * One animation drives all of them. Each card works out its own progress from
 * its index rather than owning a timer, because forty `Animatable`s to show a
 * quarter-second effect is forty coroutines and forty invalidating layers for
 * something the arithmetic already knows.
 */
object DealAnimation {

    /**
     * How much of the run is spent starting cards, against how long each card
     * takes once it has started.
     *
     * At 1 the last card only begins as the run ends, and the deal reads as a
     * ripple with no cards in flight. At 0 they all move together and it is not
     * a deal at all. Two thirds leaves several in the air at once, which is what
     * dealing looks like.
     */
    const val STAGGER = 0.66f

    /**
     * Progress of the card at [index], for a run that is [overall] complete.
     *
     * Returns 1 for a card that has landed and 0 for one that has not been dealt
     * yet, so a caller can skip drawing effects for both.
     */
    fun progressFor(index: Int, count: Int, overall: Float): Float {
        if (count <= 0 || index < 0) return 1f
        val run = overall.coerceIn(0f, 1f)

        // A single card has nothing to stagger against and simply animates.
        val start = if (count <= 1) 0f else STAGGER * index.toFloat() / (count - 1)
        val span = 1f - STAGGER

        // Guards a STAGGER of exactly 1, where every card would divide by zero.
        if (span <= 0f) return if (run >= start) 1f else 0f

        return ((run - start) / span).coerceIn(0f, 1f)
    }

    /**
     * How far above its place a card still is, as a fraction of its own height.
     *
     * Dropped rather than faded in from nowhere: a card that arrives by becoming
     * opaque has not come from anywhere, and the whole point is that it was put
     * there.
     */
    fun riseFor(progress: Float): Float = (1f - progress.coerceIn(0f, 1f)) * RISE

    const val RISE = 0.35f
}
