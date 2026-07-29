package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck

/**
 * What a deck's kept versions say about the deck.
 *
 * The history exists so you can go back to a list you registered, and it shows
 * what changed between one version and the next. Read across all of them at once
 * it answers a different question, and a better one:
 *
 * **Which cards have never left, and which keep coming and going.**
 *
 * The first set is the deck. The second is the part you are still deciding — and
 * a player who can see the two apart knows where the next test should be aimed
 * without having to remember six weeks of edits.
 *
 * Presence anywhere counts, Main or Side or Extra. A card that moved between
 * sections is still a card you kept; the question here is what you *own*, not
 * where it sat that week.
 */
object DeckLife {

    /** One card, and how many of the kept versions had it. */
    data class Standing(val id: CardId, val kept: Int, val of: Int) {
        val always: Boolean get() = of > 0 && kept == of
    }

    /**
     * Versions needed before any of this means anything.
     *
     * Two is a coincidence with a sample size of two, the same floor the siding
     * habits use and for the same reason.
     */
    const val ENOUGH = 3

    /** Every card any version held, steadiest first. */
    fun across(versions: List<Deck>): List<Standing> {
        if (versions.isEmpty()) return emptyList()

        val counts = HashMap<CardId, Int>()
        versions.forEach { deck ->
            (deck.main + deck.extra + deck.side).distinct().forEach {
                counts[it] = (counts[it] ?: 0) + 1
            }
        }

        return counts.entries
            .map { (id, kept) -> Standing(id, kept, versions.size) }
            .sortedWith(compareByDescending<Standing> { it.kept }.thenBy { it.id.value })
    }

    /** The cards that have been there the whole time. */
    fun core(versions: List<Deck>): List<CardId> {
        if (versions.size < ENOUGH) return emptyList()
        return across(versions).filter { it.always }.map { it.id }
    }

    /** And the ones that have come and gone, which is where the deck is still being decided. */
    fun changing(versions: List<Deck>): List<CardId> {
        if (versions.size < ENOUGH) return emptyList()
        return across(versions).filterNot { it.always }.map { it.id }
    }
}
