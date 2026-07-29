package com.kaiharimoto.mastertool.core.library

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.search.TextMatching

/**
 * Finding a saved deck by what is in it.
 *
 * A shelf of decks is remembered by its cards at least as often as by its name.
 * "The one I was testing Droll in", "which of these still plays Maxx C" — the
 * name is what you typed months ago and the cards are what you have been looking
 * at all week. Matching only on the name means the answer is a list of names to
 * open one at a time.
 *
 * Deck names come first regardless of how well a card scored: a deck *called*
 * Snake-Eye is what somebody typing "snake eye" meant, and a deck that merely
 * plays a card by that name is the second answer.
 */
object LibrarySearch {

    /** A card in a deck that answered the query, and how many copies of it. */
    data class CardMatch(val name: String, val copies: Int)

    data class Match<T>(
        val deck: T,
        /** The deck's own name answered the query. */
        val byName: Boolean,
        /** Cards in it that answered the query, best match first. */
        val cards: List<CardMatch>,
    )

    /**
     * Below this many characters the query is matched against deck names alone.
     *
     * Two letters against thirteen thousand card names matches most of them, and
     * a shelf where every deck reports forty hits is a shelf with no search on
     * it. The card search field uses the same floor for its text mode.
     */
    const val MIN_CARD_QUERY = 3

    /**
     * [decks] filtered to those answering [query], in the order described above.
     *
     * A passcode [cardName] cannot resolve is skipped rather than matched on its
     * digits: an unresolved card is one the pool has not loaded, and claiming it
     * does not contain what was typed would be a claim about a card nobody has.
     */
    fun <T> run(
        query: String,
        decks: List<T>,
        nameOf: (T) -> String,
        deckOf: (T) -> Deck,
        cardName: (CardId) -> String?,
    ): List<Match<T>> {
        val needle = TextMatching.normalize(query)
        if (needle.isEmpty()) {
            return decks.map { Match(it, byName = false, cards = emptyList()) }
        }
        val tokens = needle.split(' ').filter { it.isNotEmpty() }

        // Every distinct passcode on the shelf, scored once. Nine decks share
        // most of their handtraps; scoring per deck would rate Ash Blossom nine
        // times per keystroke, and the scorer is the expensive part.
        val scored: Map<CardId, Scored> =
            if (needle.length < MIN_CARD_QUERY) {
                emptyMap()
            } else {
                val out = HashMap<CardId, Scored>()
                val seen = HashSet<CardId>()
                for (item in decks) {
                    val deck = deckOf(item)
                    for (id in deck.main + deck.extra + deck.side) {
                        if (!seen.add(id)) continue
                        val name = cardName(id) ?: continue
                        val score = TextMatching.score(
                            TextMatching.normalize(name),
                            needle,
                            tokens,
                        ) ?: continue
                        out[id] = Scored(name, score)
                    }
                }
                out
            }

        val named = ArrayList<Ranked<T>>()
        val played = ArrayList<Ranked<T>>()

        for (item in decks) {
            val deck = deckOf(item)
            val nameScore = TextMatching.score(
                TextMatching.normalize(nameOf(item)),
                needle,
                tokens,
            )

            val hits = if (scored.isEmpty()) {
                emptyList()
            } else {
                (deck.main + deck.extra + deck.side).distinct()
                    .mapNotNull { id -> scored[id]?.let { it to deck.copiesOf(id) } }
                    .sortedWith(
                        compareByDescending<Pair<Scored, Int>> { it.first.score }
                            .thenByDescending { it.second }
                            .thenBy { it.first.name },
                    )
                    .map { (card, copies) -> CardMatch(card.name, copies) }
            }

            val match = Match(item, byName = nameScore != null, cards = hits)
            when {
                nameScore != null -> named += Ranked(match, nameScore)
                hits.isNotEmpty() -> played += Ranked(match, scored.bestOf(deck))
                else -> Unit
            }
        }

        // Stable, so decks that scored the same stay in the order the shelf was
        // handed over in — which is the order somebody has learnt where things are.
        return (named.sortedByDescending { it.score } + played.sortedByDescending { it.score })
            .map { it.match }
    }

    private fun Map<CardId, Scored>.bestOf(deck: Deck): Int {
        var best = 0
        for (id in deck.main + deck.extra + deck.side) {
            val score = this[id]?.score ?: continue
            if (score > best) best = score
        }
        return best
    }

    private class Scored(val name: String, val score: Int)

    private class Ranked<T>(val match: Match<T>, val score: Int)
}
