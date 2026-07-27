package com.kaiharimoto.mastertool.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeckSection(val displayName: String) {
    MAIN("Main"),
    EXTRA("Extra"),
    SIDE("Side");

    /** Inclusive size bounds enforced for tournament legality. */
    val minSize: Int get() = if (this == MAIN) 40 else 0
    val maxSize: Int get() = if (this == MAIN) 60 else 15
}

/**
 * A decklist.
 *
 * Sections are ordered multisets: a card appearing three times is stored three
 * times. Order is preserved because players arrange decklists deliberately and
 * expect an import/export round trip to give the file back unchanged.
 */
@Serializable
data class Deck(
    val main: List<CardId> = emptyList(),
    val extra: List<CardId> = emptyList(),
    val side: List<CardId> = emptyList(),
) {
    operator fun get(section: DeckSection): List<CardId> = when (section) {
        DeckSection.MAIN -> main
        DeckSection.EXTRA -> extra
        DeckSection.SIDE -> side
    }

    fun with(section: DeckSection, cards: List<CardId>): Deck = when (section) {
        DeckSection.MAIN -> copy(main = cards)
        DeckSection.EXTRA -> copy(extra = cards)
        DeckSection.SIDE -> copy(side = cards)
    }

    /** Total copies of [id] across every section, which is what banlists count. */
    fun copiesOf(id: CardId): Int =
        main.count { it == id } + extra.count { it == id } + side.count { it == id }

    val totalCards: Int get() = main.size + extra.size + side.size

    val isEmpty: Boolean get() = totalCards == 0

    companion object {
        val EMPTY = Deck()

        /** Maximum copies of any single card allowed by the base game rules. */
        const val MAX_COPIES = 3
    }
}

/** A saved deck plus the metadata the library screen needs to display it. */
@Serializable
data class DeckEntry(
    val id: String,
    val name: String,
    val deck: Deck,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val notes: String = "",
)
