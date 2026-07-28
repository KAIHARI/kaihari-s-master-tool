package com.kaiharimoto.mastertool.core.deck

/**
 * How a deck opened against one other deck, kept separately for each side of
 * the die roll.
 *
 * One tally across a whole run answers the wrong question. The decision a player
 * actually makes at the start of a match is *whether to go first*, and against
 * some decks the answer is the opposite of the usual one — a list that bricks
 * going first and functions going second is exactly the list whose owner needs
 * to be told, because the default is to choose first without thinking.
 *
 * So the two are never mixed, and [preference] turns them into the answer rather
 * than leaving two percentages side by side to be compared by eye.
 */
data class MatchupRecord(
    val goingFirst: HandTally = HandTally(),
    val goingSecond: HandTally = HandTally(),
) {
    val total: Int get() = goingFirst.total + goingSecond.total

    /**
     * Both sides of the die roll as one rate.
     *
     * Only ever the answer to a question that is not about the die roll — how
     * one version of a list compares to another, say. Anything choosing a side
     * has to read the halves.
     */
    val brickRate: Double? get() = (goingFirst + goingSecond).brickRate

    fun judged(wentFirst: Boolean, playable: Boolean): MatchupRecord =
        if (wentFirst) {
            copy(goingFirst = goingFirst.judged(playable))
        } else {
            copy(goingSecond = goingSecond.judged(playable))
        }

    /**
     * Which side to choose, or null when there is not enough to say.
     *
     * Null is the important case and the one a naive comparison gets wrong: two
     * hands each is not evidence, and a program that answers anyway will
     * confidently recommend going second off a single unlucky opening. It also
     * returns null on a tie, because "it does not matter" is a real answer and
     * dressing it as a preference would be inventing one.
     */
    fun preference(minimumEach: Int = MINIMUM_EACH): Preference? {
        if (goingFirst.total < minimumEach || goingSecond.total < minimumEach) return null

        val first = goingFirst.brickRate ?: return null
        val second = goingSecond.brickRate ?: return null

        return when {
            first < second -> Preference.FIRST
            second < first -> Preference.SECOND
            else -> null
        }
    }

    operator fun plus(other: MatchupRecord): MatchupRecord =
        MatchupRecord(goingFirst + other.goingFirst, goingSecond + other.goingSecond)

    companion object {
        /**
         * Hands needed on each side before the question is worth answering.
         *
         * Five is not statistics; it is the point past which somebody would have
         * formed an opinion anyway, and the program keeping quiet until then is
         * the honest version of that.
         */
        const val MINIMUM_EACH = 5
    }
}

enum class Preference { FIRST, SECOND }
