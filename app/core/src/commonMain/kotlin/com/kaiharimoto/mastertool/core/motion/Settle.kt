package com.kaiharimoto.mastertool.core.motion

/**
 * Nothing ever lands square.
 *
 * Every card on the stage currently comes to rest at exactly the angle the
 * layout named, and five of them side by side in a hand therefore read as one
 * printed strip rather than as five objects. Perfect alignment is the tell,
 * every time — a real hand of cards is a degree out here and two pixels over
 * there, and the eye reads that unevenness as *separate things* long before it
 * reads any of the shading.
 *
 * This is `AAA.md` #52, and #47 for the graveyard.
 *
 * ## Why it is a hash and not a random number
 *
 * There are two hundred levels of undo on this stage and a seeded shuffle
 * behind it, and `AAA.md` #38 is explicit that anything which cannot be
 * replayed exactly is a thing that breaks undo. A `Random` drawn at landing
 * time would have to be *stored* on every card, survive the undo stack, survive
 * a card moving between the mat and a pile and back, and still produce the same
 * table. A hash of the card's own instance id needs none of that: it is the
 * same answer forever, for free, from a number the field already carries.
 *
 * It also has to be stable *within* a frame. The pose is recomputed whenever
 * the board changes and read again every frame by the springs; a landing that
 * differed between two of those reads would be a card that shivers.
 *
 * ## Why the amount is a property of how it got there
 *
 * A deck is tapped square against the table and a graveyard is swept into a
 * heap, and drawing both at the same tidiness is what makes a board look
 * printed. So the caller says which of the three it is, and [Care] carries the
 * numbers rather than each call site choosing its own — the failure mode of
 * per-site constants is a stage where the hand is scruffier than the discard
 * pile and nobody can say why.
 */
object Settle {

    /**
     * How square something came to rest: a turn in degrees, and a slip across
     * the felt as a fraction of the card's width.
     *
     * A fraction rather than pixels because the board is re-solved at every
     * window size — a landing measured in pixels would be a millimetre on a
     * tablet and a centimetre on a desktop, and the same board would be tidy on
     * one and strewn on the other.
     */
    data class Landing(val turnDegrees: Float, val slipX: Float, val slipY: Float) {
        companion object {
            /** Dead square. What a carried card gets, because a finger is exact. */
            val Square = Landing(0f, 0f, 0f)
        }
    }

    /**
     * How much trouble was taken putting it there.
     *
     * The three numbers are half-widths: a landing is uniform in
     * `[-degrees, +degrees]` and `[-slip, +slip]`, so [SQUARED] can be out by a
     * quarter of a degree and no more.
     */
    enum class Care(val degrees: Float, val slip: Float) {
        /**
         * A pile tapped square: the deck and the extra deck.
         *
         * Not zero, and that is the point of having a level here at all. A deck
         * squared to the pixel is the one object on the table that could only
         * have been placed by a machine, and its top card is a card like any
         * other. A quarter of a degree is under a pixel across a card's width
         * and still enough to break the ruled edge of the stack under it.
         */
        SQUARED(degrees = 0.25f, slip = 0.003f),

        /**
         * Set down by a hand: a card on the mat, a card in the hand, a card on
         * top of a stack somebody built.
         *
         * `AAA.md` #52 asks for "a degree of rotation and a pixel of offset",
         * which is this.
         */
        PLACED(degrees = 1.1f, slip = 0.010f),

        /**
         * Swept into a heap: the graveyard and the banished pile.
         *
         * Real discard piles are never square, and a perfectly aligned
         * graveyard is the clearest possible statement that nothing here is a
         * real object (`AAA.md` #47). Four degrees is visible at a glance and
         * still lets you read what the top card is, which is the only job the
         * graveyard has.
         */
        THROWN(degrees = 4f, slip = 0.035f),
    }

    /**
     * Where instance [id] came to rest, given how carefully it was put there.
     *
     * Three independent values out of one integer. They have to be independent:
     * the cheap thing to do is to derive the turn and the two slips from one
     * hash by shifting, and the result is a hand where every card that leans
     * left is also low and left, which is a pattern rather than a mess.
     */
    fun of(id: Int, care: Care): Landing = Landing(
        turnDegrees = signed(id, TURN) * care.degrees,
        slipX = signed(id, SLIP_X) * care.slip,
        slipY = signed(id, SLIP_Y) * care.slip,
    )

    // ---- the hash ----------------------------------------------------------------

    /**
     * One number in `[-1, 1)` from an id and a channel.
     *
     * MurmurHash3's 32-bit finalizer, which is the standard answer for turning a
     * counter into something that looks random: it is an avalanche mix, so two
     * consecutive instance ids — which is exactly what a freshly dealt hand is —
     * land nowhere near each other. That property is the whole requirement here
     * and it is the one a cheaper `id * PRIME` shift fails: consecutive ids come
     * out of that in a visible arithmetic progression, and a hand of five would
     * fan out in an even, obviously-drawn ramp.
     *
     * The channel is mixed in at the start rather than at the end so the three
     * values are three different walks rather than three views of one.
     */
    private fun signed(id: Int, channel: Int): Float {
        var h = id * PHI + channel * CHANNEL_STRIDE
        h = h xor (h ushr 16)
        h *= -0x7ee3623b // 0x85ebca6b as a signed Int
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb // 0xc2b2ae35 as a signed Int
        h = h xor (h ushr 16)
        // The top 24 bits, which are the best-mixed, mapped onto [-1, 1).
        return (h ushr 8) / HALF_RANGE - 1f
    }

    /** The golden-ratio odd constant, so ids that differ by one differ a lot. */
    private const val PHI = -0x61c88647 // 0x9e3779b9 as a signed Int

    /** Far enough apart that the three channels never share a neighbourhood. */
    private const val CHANNEL_STRIDE = 0x27d4eb2f

    private const val TURN = 0
    private const val SLIP_X = 1
    private const val SLIP_Y = 2

    /** Half of 2^24, so `(h ushr 8) / this` covers `[0, 2)`. */
    private const val HALF_RANGE = 8_388_608f
}
