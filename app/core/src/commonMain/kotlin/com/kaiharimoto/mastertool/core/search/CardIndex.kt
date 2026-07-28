package com.kaiharimoto.mastertool.core.search

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId

/**
 * A page of search results, plus how many cards matched in total.
 *
 * The count is what lets the UI say "showing 150 of 3,214 matches" honestly.
 * Without it the only number available is the size of the whole pool, which
 * reads as though the other 12,850 cards had been rejected by the query rather
 * than merely not drawn.
 */
data class SearchOutcome(
    val cards: List<Card>,
    val matchCount: Int,
    /**
     * True when nothing was *named* this and these are cards that *say* it.
     *
     * Worth reporting rather than blending in: the results are answering a
     * different question from the one that was asked, and a screen that quietly
     * substitutes one for the other is a screen you stop trusting.
     */
    val matchedText: Boolean = false,
) {
    val truncated: Boolean get() = matchCount > cards.size

    companion object {
        val EMPTY = SearchOutcome(emptyList(), 0)
    }
}

/**
 * An immutable, queryable view over the whole card pool.
 *
 * Built once after the database loads and then only read, so it is safe to share
 * across threads and cheap to hold in a view model.
 */
class CardIndex private constructor(
    val cards: List<Card>,
    private val byId: Map<CardId, Card>,
    private val byNormalizedName: Map<String, Card>,
    private val normalizedNames: List<String>,
) {

    val size: Int get() = cards.size

    /**
     * Resolves a passcode, including alternate artwork passcodes.
     *
     * Deck files exported by other tools frequently reference an alternate
     * printing. Looking those up through the same map is what stops an imported
     * deck from showing holes where perfectly ordinary cards should be.
     */
    fun byId(id: CardId): Card? = byId[id]

    fun byName(name: String): Card? = byNormalizedName[TextMatching.normalize(name)]

    /** Distinct monster types present in the pool, for building filter chips. */
    val races: List<String> by lazy {
        cards.mapNotNull { it.race }.distinct().sorted()
    }

    val archetypes: List<String> by lazy {
        cards.mapNotNull { it.archetype }.distinct().sorted()
    }

    val levels: List<Int> by lazy {
        cards.mapNotNull { it.level }.distinct().sorted()
    }

    /**
     * Ranked search.
     *
     * Filtering happens before scoring so an active filter shrinks the work
     * rather than adding to it, and results are capped so a one-letter query
     * cannot hand the UI 13,000 rows to lay out.
     */
    fun search(
        query: String,
        filter: CardFilter = CardFilter.NONE,
        limit: Int = 120,
    ): SearchOutcome {
        val normalizedQuery = TextMatching.normalize(query)
        val tokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }

        if (normalizedQuery.isEmpty()) {
            // No query to rank by, so rank by name. Browsing a filter has to show
            // the same cards every time rather than whichever ones the API
            // happened to return first.
            val matches = cards.filter(filter::matches)
            return SearchOutcome(
                cards = matches.sortedBy { it.name }.take(limit),
                matchCount = matches.size,
            )
        }

        val hits = ArrayList<ScoredCard>(minOf(limit * 4, 512))
        for (i in cards.indices) {
            val card = cards[i]
            if (!filter.matches(card)) continue
            val score = TextMatching.score(normalizedNames[i], normalizedQuery, tokens) ?: continue
            hits.add(ScoredCard(card, score))
        }

        hits.sortWith(
            compareByDescending<ScoredCard> { it.score }
                .thenBy { it.card.name.length }
                .thenBy { it.card.name }
        )

        // Every match is already in `hits`, so the total costs nothing extra.
        if (hits.isNotEmpty()) {
            return SearchOutcome(cards = hits.take(limit).map { it.card }, matchCount = hits.size)
        }

        return searchText(query, filter, limit)
    }

    /**
     * What the cards say, when nothing is called it.
     *
     * "Which of these banishes?" is a real question about a deck and there was
     * nowhere to ask it. Making it a fallback rather than a mode means it needs
     * no control, no prefix and no explaining — and costs nothing in the common
     * case, because it only runs once a name search has already come back
     * empty, which is exactly when somebody was looking for something else.
     *
     * Deliberately literal. Card text is written in a house style with its own
     * punctuation and the fuzzy scoring that suits a half-remembered *name* would
     * turn "banish" into a hundred cards that merely rhyme with it.
     */
    private fun searchText(query: String, filter: CardFilter, limit: Int): SearchOutcome {
        val needle = query.trim()
        if (needle.length < MIN_TEXT_LENGTH) return SearchOutcome.EMPTY

        val matches = cards.filter { card ->
            filter.matches(card) && card.description.contains(needle, ignoreCase = true)
        }

        return SearchOutcome(
            // By name, so asking the same question twice gets the same answer.
            cards = matches.sortedBy { it.name }.take(limit),
            matchCount = matches.size,
            matchedText = matches.isNotEmpty(),
        )
    }

    private class ScoredCard(val card: Card, val score: Int)

    companion object {
        /**
         * Below this, a text search is noise: every card says "a" and half of
         * them say "in", and a two-letter fallback would fire constantly on the
         * way to typing a name.
         */
        const val MIN_TEXT_LENGTH = 3

        fun build(cards: List<Card>): CardIndex {
            val byId = HashMap<CardId, Card>(cards.size * 2)
            val byName = HashMap<String, Card>(cards.size)
            val normalized = ArrayList<String>(cards.size)

            cards.forEach { card ->
                // Canonical id wins over an alternate id from another card if
                // they ever collide, so register alternates first.
                card.alternateIds.forEach { alt -> if (alt !in byId) byId[alt] = card }
            }
            cards.forEach { card ->
                byId[card.id] = card
                val key = TextMatching.normalize(card.name)
                if (key !in byName) byName[key] = card
                normalized.add(key)
            }

            return CardIndex(cards, byId, byName, normalized)
        }

        val EMPTY = build(emptyList())
    }
}
