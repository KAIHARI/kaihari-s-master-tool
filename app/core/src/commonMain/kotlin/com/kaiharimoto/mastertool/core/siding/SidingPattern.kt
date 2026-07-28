package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * What to take out and what to put in, for one side of a match.
 *
 * Stored **by copy, not by count**: three copies of the same card leaving the
 * deck is that passcode three times. That is how the tool this replaces writes
 * them and how its printed sheets read, one thumbnail per copy — and it is also
 * the honest shape, because siding out two of three is a different decision from
 * siding out all three.
 */
data class SidingSwap(
    val cardsIn: List<CardId> = emptyList(),
    val cardsOut: List<CardId> = emptyList(),
) {
    val isEmpty: Boolean get() = cardsIn.isEmpty() && cardsOut.isEmpty()

    /**
     * Whether the swap leaves the deck the size it found it.
     *
     * Not enforced — a deck may legally end anywhere from 40 to 60 — but it is
     * almost always a mistake, and it is the one thing you cannot see by reading
     * the two lists side by side.
     */
    val isBalanced: Boolean get() = cardsIn.size == cardsOut.size
}

/**
 * A plan for one matchup.
 *
 * Keyed by the opponent's deck rather than by your own, because that is the
 * question being answered at a table: this is what is sitting across from me,
 * so this is what changes. [associatedYdk] links the plan to an opponent's deck
 * file by name, which is what lets importing their list pick the plan out.
 *
 * Only the fields this app understands are modelled. Everything else in the
 * stored object — the desktop tool writes a background gradient, a category,
 * tags, a last-used stamp and the opponent's full decklist — is carried through
 * untouched by [SidingCodec] rather than being redefined here.
 */
data class SidingPattern(
    val deckName: String = "",
    val associatedYdk: String? = null,
    /** Three cards that identify the matchup at a glance. */
    val thumbnails: List<CardId> = emptyList(),
    val goingFirst: SidingSwap = SidingSwap(),
    val goingSecond: SidingSwap = SidingSwap(),
) {
    fun swapFor(goingFirst: Boolean): SidingSwap =
        if (goingFirst) this.goingFirst else goingSecond

    val isEmpty: Boolean get() = goingFirst.isEmpty && goingSecond.isEmpty
}
