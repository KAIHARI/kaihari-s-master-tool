package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck

/**
 * Which few cards stand for a whole deck.
 *
 * A saved deck listed by name alone is a filename, and a player with nine of
 * them is reading nine filenames to find the one they mean. Three card faces and
 * they find it without reading anything — which is how the decks are actually
 * told apart in someone's head.
 *
 * The ranking is copies first, because three copies of a card is the strongest
 * statement a decklist can make about what it is trying to do. Ties go to
 * whichever card was put first, which is not arbitrary: in this program the
 * order is authored, and the front of the Main deck is where the cards somebody
 * cares about end up.
 */
object DeckIdentity {

    const val FACES = 3

    fun faces(deck: Deck, count: Int = FACES): List<CardId> {
        // The Main deck is the deck. An Extra-only list is a stub somebody is
        // partway through, and showing its Link monsters is better than showing
        // nothing at all.
        val source = deck.main.ifEmpty { deck.extra }.ifEmpty { deck.side }
        if (source.isEmpty() || count <= 0) return emptyList()

        val copies = LinkedHashMap<CardId, Int>()
        source.forEach { copies[it] = (copies[it] ?: 0) + 1 }

        // `entries` of a LinkedHashMap is in first-appearance order and
        // `sortedByDescending` is stable, so the tie-break comes for free rather
        // than needing the position carried alongside.
        return copies.entries
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }
    }
}
