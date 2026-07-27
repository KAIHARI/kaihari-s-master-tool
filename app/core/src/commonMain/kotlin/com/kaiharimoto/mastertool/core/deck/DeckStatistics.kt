package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection

data class DeckStatistics(
    val monsters: Int,
    val spells: Int,
    val traps: Int,
    val byAttribute: Map<Attribute, Int>,
    val byLevel: Map<Int, Int>,
    val byRace: Map<String, Int>,
    val byArchetype: Map<String, Int>,
    val unknownCards: Int,
) {
    val total: Int get() = monsters + spells + traps

    /**
     * Probability of drawing at least one copy of a card with [copies] copies in
     * a [total]-card deck, in an opening hand of [handSize].
     *
     * Hypergeometric, computed as 1 - P(no copies). Used for the opening-hand
     * readout next to each card in the deck pane.
     */
    fun openingHandOdds(copies: Int, handSize: Int = 5): Double {
        if (copies <= 0 || total <= 0 || handSize <= 0) return 0.0
        if (copies >= total || handSize >= total) return 1.0

        var pNone = 1.0
        for (i in 0 until handSize) {
            pNone *= (total - copies - i).toDouble() / (total - i).toDouble()
            if (pNone <= 0.0) return 1.0
        }
        return 1.0 - pNone
    }

    companion object {
        fun of(
            deck: Deck,
            cards: (CardId) -> Card?,
            section: DeckSection = DeckSection.MAIN,
        ): DeckStatistics {
            var monsters = 0
            var spells = 0
            var traps = 0
            var unknown = 0
            val attributes = mutableMapOf<Attribute, Int>()
            val levels = mutableMapOf<Int, Int>()
            val races = mutableMapOf<String, Int>()
            val archetypes = mutableMapOf<String, Int>()

            deck[section].forEach { id ->
                val card = cards(id)
                if (card == null) {
                    unknown++
                    return@forEach
                }

                when (card.category) {
                    CardCategory.MONSTER -> {
                        monsters++
                        attributes[card.attribute] = (attributes[card.attribute] ?: 0) + 1
                        card.level?.let { levels[it] = (levels[it] ?: 0) + 1 }
                        card.race?.let { races[it] = (races[it] ?: 0) + 1 }
                    }
                    CardCategory.SPELL -> spells++
                    CardCategory.TRAP -> traps++
                    CardCategory.OTHER -> unknown++
                }

                card.archetype?.let { archetypes[it] = (archetypes[it] ?: 0) + 1 }
            }

            return DeckStatistics(
                monsters = monsters,
                spells = spells,
                traps = traps,
                byAttribute = attributes.entries.sortedBy { it.key.ordinal }
                    .associate { it.key to it.value },
                byLevel = levels.entries.sortedBy { it.key }.associate { it.key to it.value },
                byRace = races.entries.sortedByDescending { it.value }
                    .associate { it.key to it.value },
                byArchetype = archetypes.entries.sortedByDescending { it.value }
                    .associate { it.key to it.value },
                unknownCards = unknown,
            )
        }
    }
}
