package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * What a shelf of siding plans says about the deck they side.
 *
 * Nobody writes these down to be counted. They are written one matchup at a
 * time, months apart, each one answering a question about that matchup — and
 * then, taken together, they answer a question about the *deck* that nothing
 * else in this program can:
 *
 * **A card cut in every plan is not in the deck, it is in the way.** If every
 * matchup you have thought about begins by taking a card out, the deck it is in
 * is the one you never play.
 *
 * **And a card brought in against everything belongs in the Main deck.** Fifteen
 * Side deck slots are the scarcest thing in a decklist, and one of them spent on
 * a card that goes in every game is a slot spent buying back a decision you
 * already made.
 *
 * Neither is a rule and this does not enforce either — the point of saying them
 * out loud is that they are invisible one plan at a time and obvious across
 * eight.
 */
object SidingHabits {

    /** One card, and what the plans do with it. */
    data class Habit(
        val id: CardId,
        /** How many plans take it out, counting a plan once however it does. */
        val cutBy: Int,
        /** And how many bring it in. */
        val broughtInBy: Int,
    )

    /**
     * How many plans a habit has to span before it is one.
     *
     * Two is not enough: two plans agreeing is a coincidence with a sample size
     * of two, and the whole value here is in what eight matchups say together.
     */
    const val ENOUGH = 3

    /**
     * Every card the plans touch, most-cut first.
     *
     * A plan counts once for a card however many copies it moves and whichever
     * half of the match does it: this is a question about *how many matchups*
     * treat a card a certain way, and a plan that cuts three copies going first
     * and two going second is still one matchup that cuts it.
     */
    fun of(patterns: Collection<SidingPattern>): List<Habit> {
        val cut = HashMap<CardId, Int>()
        val brought = HashMap<CardId, Int>()

        patterns.forEach { pattern ->
            val outs = pattern.goingFirst.cardsOut.toSet() + pattern.goingSecond.cardsOut
            val ins = pattern.goingFirst.cardsIn.toSet() + pattern.goingSecond.cardsIn
            outs.forEach { cut[it] = (cut[it] ?: 0) + 1 }
            ins.forEach { brought[it] = (brought[it] ?: 0) + 1 }
        }

        return (cut.keys + brought.keys)
            .map { Habit(it, cutBy = cut[it] ?: 0, broughtInBy = brought[it] ?: 0) }
            .sortedWith(
                compareByDescending<Habit> { it.cutBy }
                    .thenByDescending { it.broughtInBy }
                    .thenBy { it.id.value },
            )
    }

    /**
     * Cards every plan takes out.
     *
     * Empty below [ENOUGH] plans, because the claim is about a deck's whole
     * matchup spread and two plans are not one.
     */
    fun alwaysCut(patterns: Collection<SidingPattern>): List<CardId> {
        if (patterns.size < ENOUGH) return emptyList()
        return of(patterns).filter { it.cutBy == patterns.size }.map { it.id }
    }

    /** Cards every plan brings in, which is a Side deck slot arguing to be a Main one. */
    fun alwaysBroughtIn(patterns: Collection<SidingPattern>): List<CardId> {
        if (patterns.size < ENOUGH) return emptyList()
        return of(patterns).filter { it.broughtInBy == patterns.size }.map { it.id }
    }
}
