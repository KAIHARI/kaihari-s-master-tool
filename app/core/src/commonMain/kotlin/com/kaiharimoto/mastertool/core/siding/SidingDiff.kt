package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck

/**
 * Works out what somebody just did to their deck.
 *
 * The other half of siding, and the one that matches how a plan is actually
 * arrived at. Nobody writes a swap down and then performs it; they move cards
 * between the Main and Side decks until the matchup feels right, and *that* is
 * the plan. Recording it afterwards is a diff, not a form.
 *
 * Deliberately by count rather than by position. Two decks that hold the same
 * cards in a different order are the same decklist as far as siding is
 * concerned — the arrangement is a separate thing, and a plan that recorded
 * "moved card seven to slot twelve" would be recording the wrong edit.
 */
object SidingDiff {

    /**
     * The swap that turns [before] into [after].
     *
     * `out` is everything the Main deck lost and `in` is everything it gained,
     * both listed one entry per copy — because "two of three" and "all three"
     * are different decisions, and a count would flatten them.
     *
     * Only the Main deck is compared. Cards that left it went to the Side deck by
     * definition, and a swap describing both sides would say the same thing
     * twice and be able to contradict itself.
     */
    fun between(before: Deck, after: Deck): SidingSwap {
        val had = before.main.counted()
        val has = after.main.counted()

        val out = mutableListOf<CardId>()
        val cardsIn = mutableListOf<CardId>()

        (had.keys + has.keys).forEach { card ->
            val change = (has[card] ?: 0) - (had[card] ?: 0)
            when {
                change > 0 -> repeat(change) { cardsIn += card }
                change < 0 -> repeat(-change) { out += card }
            }
        }

        // In the order the deck itself holds them, so a recorded plan reads the
        // way the deck reads rather than in whatever order a hash map produced.
        return SidingSwap(
            cardsIn = cardsIn.sortedBy { after.main.indexOf(it) },
            cardsOut = out.sortedBy { before.main.indexOf(it) },
        )
    }

    /**
     * Whether one deck became the other by siding rather than by being edited.
     *
     * The distinction is not cosmetic — it decides whether a record of how a
     * deck opens is still about the deck in front of you. Siding moves cards
     * across the line between Main, Side and Extra; it never brings in a card
     * you did not register, and it never takes one out of the building. So if
     * the sixty are the same sixty, every hand dealt so far is still a hand
     * dealt from *this* list, correctly labelled as pre- or post-side. If the
     * sixty changed, you were deck building, and the record is about something
     * that no longer exists.
     *
     * By count and across all three sections, because that is the only way to
     * tell the two apart: moving a card from the Side deck to the Main deck and
     * adding a card you did not bring look identical from the Main deck alone.
     */
    fun isSwap(before: Deck, after: Deck): Boolean = before.pool() == after.pool()

    private fun Deck.pool(): Map<CardId, Int> = (main + side + extra).counted()

    private fun List<CardId>.counted(): Map<CardId, Int> =
        groupingBy { it }.eachCount()
}
