package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * Stable identities for the cards in a section.
 *
 * A deck holds duplicates, so a passcode alone does not identify a tile — three
 * copies of Ash Blossom are three separate things on screen. The obvious fix is
 * to key each tile by its position, and that is what this code did.
 *
 * Position is the wrong key for anything that animates. Insert a card at index
 * three and every tile after it gets a new key, so the grid sees thirty-seven
 * cards disappear and thirty-seven different cards appear where they happen to
 * be — nothing moved, as far as it is concerned, and nothing can be animated
 * moving. Keying by *which copy* instead leaves every other tile's identity
 * untouched, and inserting one card becomes one arrival and a lot of cards
 * sliding along.
 *
 * Which copy is which does not matter — the copies are identical — only that the
 * answer is stable and that two tiles never share it.
 */
object DeckKeys {

    /** For each position, how many earlier positions hold the same card. */
    fun occurrences(ids: List<CardId>): List<Int> {
        val seen = mutableMapOf<CardId, Int>()
        return ids.map { id ->
            val count = seen.getOrElse(id) { 0 }
            seen[id] = count + 1
            count
        }
    }
}
