package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Format
import kotlinx.serialization.Serializable

@Serializable
enum class SortMode(val displayName: String) {
    /** Whatever order the cards were put in. The order a `.ydk` round trips. */
    MANUAL("Manual"),

    /** Monsters by level, then Spells, then Traps — the usual decklist order. */
    TYPE("Type"),

    NAME("Name"),
    LEVEL("Level"),
    ATK("ATK"),
    BANLIST("Banlist"),
}

/**
 * Reorders a section.
 *
 * Sorting is an edit, not a view: a decklist's stored order is exactly what gets
 * written back out to `.ydk`, so a sort that only changed what was on screen
 * would leave the file disagreeing with the deck the whole time. Being an edit
 * also means it goes on the undo stack like everything else.
 *
 * Two properties this must never lose, both covered by tests:
 * the result is a permutation of the input — sorting can never add, drop or
 * substitute a card — and cards the database cannot resolve keep their relative
 * order at the end rather than being dropped or throwing.
 */
object DeckSorter {

    fun sort(
        ids: List<CardId>,
        cards: (CardId) -> Card?,
        mode: SortMode,
        format: Format = Format.TCG,
    ): List<CardId> {
        if (mode == SortMode.MANUAL || ids.size < 2) return ids

        val known = mutableListOf<Pair<CardId, Card>>()
        val unknown = mutableListOf<CardId>()
        ids.forEach { id ->
            val card = cards(id)
            if (card == null) unknown += id else known += id to card
        }

        // `sortedWith` is stable, so equal cards keep the order they were put in
        // and repeated sorts do not shuffle duplicates around.
        return known.sortedWith(comparator(mode, format)).map { it.first } + unknown
    }

    private fun comparator(mode: SortMode, format: Format): Comparator<Pair<CardId, Card>> =
        when (mode) {
            SortMode.TYPE -> compareBy<Pair<CardId, Card>> { categoryRank(it.second) }
                .thenByDescending { it.second.level ?: -1 }
                .thenBy { it.second.name }

            SortMode.NAME -> compareBy { it.second.name }

            // Absent stats sort last rather than as zero: a Link monster has no
            // level and no DEF, and it is not "the lowest one".
            SortMode.LEVEL -> compareByDescending<Pair<CardId, Card>> { it.second.level ?: -1 }
                .thenBy { it.second.name }

            SortMode.ATK -> compareByDescending<Pair<CardId, Card>> { it.second.atk ?: -1 }
                .thenBy { it.second.name }

            // Forbidden first: the cards a deck check will stop on, at the top.
            SortMode.BANLIST -> compareBy<Pair<CardId, Card>> {
                it.second.banStatus(format).maxCopies
            }.thenBy { it.second.name }

            SortMode.MANUAL -> compareBy { 0 }
        }

    private fun categoryRank(card: Card): Int = when (card.category) {
        CardCategory.MONSTER -> 0
        CardCategory.SPELL -> 1
        CardCategory.TRAP -> 2
        CardCategory.OTHER -> 3
    }
}
