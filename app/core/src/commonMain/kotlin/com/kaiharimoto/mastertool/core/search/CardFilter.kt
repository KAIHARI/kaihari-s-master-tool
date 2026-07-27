package com.kaiharimoto.mastertool.core.search

import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.Format

/**
 * The quick-filter state.
 *
 * Every set is OR within itself and AND across fields, which is how filter chips
 * are read intuitively: "DARK or LIGHT, and a Monster, and level 4".
 * An empty set means the field is not filtering at all.
 */
data class CardFilter(
    val categories: Set<CardCategory> = emptySet(),
    val attributes: Set<Attribute> = emptySet(),
    val races: Set<String> = emptySet(),
    val levels: Set<Int> = emptySet(),
    val archetypes: Set<String> = emptySet(),
    val banStatuses: Set<BanStatus> = emptySet(),
    val atkRange: IntRange? = null,
    val defRange: IntRange? = null,
    /** null shows both, true shows only Extra Deck cards, false only main-deck. */
    val extraDeckOnly: Boolean? = null,
    val format: Format = Format.TCG,
) {
    val isActive: Boolean
        get() = categories.isNotEmpty() || attributes.isNotEmpty() || races.isNotEmpty() ||
            levels.isNotEmpty() || archetypes.isNotEmpty() || banStatuses.isNotEmpty() ||
            atkRange != null || defRange != null || extraDeckOnly != null

    /** Number of distinct facets in use, for the "Filters (3)" badge. */
    val activeFacetCount: Int
        get() = listOf(
            categories.isNotEmpty(), attributes.isNotEmpty(), races.isNotEmpty(),
            levels.isNotEmpty(), archetypes.isNotEmpty(), banStatuses.isNotEmpty(),
            atkRange != null, defRange != null, extraDeckOnly != null,
        ).count { it }

    fun matches(card: Card): Boolean {
        if (categories.isNotEmpty() && card.category !in categories) return false
        if (attributes.isNotEmpty() && card.attribute !in attributes) return false
        if (races.isNotEmpty() && card.race !in races) return false
        if (levels.isNotEmpty() && card.level !in levels) return false
        if (archetypes.isNotEmpty() && card.archetype !in archetypes) return false
        if (banStatuses.isNotEmpty() && card.banStatus(format) !in banStatuses) return false
        if (extraDeckOnly != null && card.isExtraDeck != extraDeckOnly) return false

        // A monster with no ATK/DEF (Link monsters have no DEF) cannot satisfy a
        // range filter, so treat a missing value as excluded rather than as zero.
        atkRange?.let { range -> if (card.atk == null || card.atk !in range) return false }
        defRange?.let { range -> if (card.def == null || card.def !in range) return false }

        return true
    }

    companion object {
        val NONE = CardFilter()
    }
}
