package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot

/**
 * Where a card being dragged came from.
 *
 * Needed because the same destination means different things depending on the
 * journey: a card dropped on the graveyard leaves the mat if it was on the mat,
 * leaves the hand if it was in the hand, and is a no-op if it was already in the
 * graveyard. The intent says where; this says from where.
 */
sealed interface DragOrigin {
    data class Hand(val index: Int) : DragOrigin
    data class Mat(val id: Int) : DragOrigin
    data class Pile(val pile: BoardSlot, val index: Int) : DragOrigin

    /**
     * A card underneath one on the mat: the id of the card on top, and how far
     * down [PlayField.under] the one you mean is.
     *
     * The one place on this table where a card had no name. `Mat` names the top
     * of a stack and nothing else, so everything buried in one — the rest of a
     * pile, and an Xyz monster's materials — was unreachable by any gesture,
     * which for an Xyz monster means you could not see what it was made of.
     *
     * Index 1 and up, always: index 0 of that list is the card on top, and that
     * card already has a name.
     */
    data class Buried(val under: Int, val index: Int) : DragOrigin
}

/**
 * Turning "what letting go would do" into the thing it does.
 *
 * The other half of [DropTargets], and pure for the same reason: the indicator,
 * the release and the test are then three readings of one function rather than
 * three implementations that agree most of the time. Every combination that is
 * not a real move returns null and nothing happens — dropping a graveyard card
 * onto the graveyard, sending a hand card to a zone it cannot reach, dragging
 * a card onto itself.
 */
object DropCommit {

    fun commit(
        field: PlayField,
        from: DragOrigin,
        intent: DropIntent,
        position: CardPosition = CardPosition.FACE_UP_ATK,
    ): PlayField? = when (intent) {
        // Somewhere on the mat, either exactly where you let go or pulled into
        // a zone. The two differ only in the point, which the resolver already
        // worked out, so they share every line below.
        is DropIntent.Free -> field.land(from, intent.at, position)
        is DropIntent.Zone -> field.land(from, intent.at, position)

        is DropIntent.Stack -> field.pileOnto(from, intent.onto, position)
        is DropIntent.Attach -> field.tuckUnder(from, intent.onto, position)

        // The hand, at the place in it the finger was pointing at. Every branch
        // takes the index, including the one from the hand itself — which used
        // to be `null`, "a card in your hand cannot go to your hand", true of
        // the destination and false of the gesture: moving a card *within* your
        // hand is the commonest thing anybody does to one.
        is DropIntent.Hand -> when (from) {
            is DragOrigin.Mat -> field.toHand(from.id, intent.at)
            is DragOrigin.Hand -> field.reorderHand(from.index, intent.at)
            is DragOrigin.Pile -> field.movePile(from) { card -> handInsert(card, intent.at) }
            is DragOrigin.Buried -> field.moveBuried(from) { card -> handInsert(card, intent.at) }
        }

        DropIntent.Graveyard -> when (from) {
            is DragOrigin.Mat -> field.toGraveyard(from.id)
            is DragOrigin.Hand -> field.handToGraveyard(from.index)
            is DragOrigin.Pile -> field.movePile(from) { card ->
                copy(graveyard = listOf(card) + graveyard)
            }
            is DragOrigin.Buried -> field.moveBuried(from) { card ->
                copy(graveyard = listOf(card) + graveyard)
            }
        }

        DropIntent.Banish -> when (from) {
            is DragOrigin.Mat -> field.toBanish(from.id)
            is DragOrigin.Hand -> field.handToBanish(from.index)
            is DragOrigin.Pile -> field.movePile(from) { card ->
                copy(banished = listOf(card) + banished)
            }
            is DragOrigin.Buried -> field.moveBuried(from) { card ->
                copy(banished = listOf(card) + banished)
            }
        }

        DropIntent.Deck -> when (from) {
            is DragOrigin.Mat -> field.toDeckTop(from.id)
            is DragOrigin.Hand -> field.handToDeckTop(from.index)
            is DragOrigin.Pile -> field.movePile(from) { card ->
                copy(deck = listOf(card) + deck)
            }
            is DragOrigin.Buried -> field.moveBuried(from) { card ->
                copy(deck = listOf(card) + deck)
            }
        }

        DropIntent.ExtraDeck -> when (from) {
            is DragOrigin.Mat -> field.toExtraDeck(from.id)
            is DragOrigin.Pile -> field.movePile(from) { card ->
                copy(extraDeck = listOf(card) + extraDeck)
            }
            is DragOrigin.Buried -> field.moveBuried(from) { card ->
                copy(extraDeck = listOf(card) + extraDeck)
            }
            is DragOrigin.Hand -> null
        }

        DropIntent.Cancel -> null
    }

    /** A card arriving on the mat, from wherever it was. */
    private fun PlayField.land(
        from: DragOrigin,
        at: MatPoint,
        position: CardPosition,
    ): PlayField? = when (from) {
        is DragOrigin.Hand -> playFromHand(from.index, at, position)
        is DragOrigin.Mat -> moveOnMat(from.id, at)
        is DragOrigin.Pile -> when (from.pile) {
            BoardSlot.Deck -> playFromDeck(from.index, at, position)
            BoardSlot.ExtraDeck -> playFromExtra(from.index, at, position)
            BoardSlot.Graveyard -> playFromGraveyard(from.index, at, position)
            BoardSlot.Banished -> playFromBanished(from.index, at, position)
            is BoardSlot.Zone -> null
        }
        is DragOrigin.Buried -> takeFromUnder(from.under, from.index, at, position)
    }

    /**
     * A card landing on another card.
     *
     * Anything not already on the mat has to arrive there first — it is put
     * down on top of its target and then stacked, which is both what the hand
     * does and what keeps this to one code path.
     */
    private fun PlayField.pileOnto(
        from: DragOrigin,
        onto: Int,
        position: CardPosition,
    ): PlayField? {
        val target = placed(onto) ?: return null
        if (from is DragOrigin.Mat) return stackOnto(from.id, onto)

        val arrived = land(from, target.at, position) ?: return null
        val landed = arrived.mat.lastOrNull()?.id ?: return null
        return arrived.stackOnto(landed, onto)
    }

    private fun PlayField.tuckUnder(
        from: DragOrigin,
        onto: Int,
        position: CardPosition,
    ): PlayField? {
        val target = placed(onto) ?: return null
        if (from is DragOrigin.Mat) return attachAsMaterial(from.id, onto)

        val arrived = land(from, target.at, position) ?: return null
        val landed = arrived.mat.lastOrNull()?.id ?: return null
        return arrived.attachAsMaterial(landed, onto)
    }

    /**
     * A card lifted out of one pile and put somewhere else by [arrive].
     *
     * Moving between piles is a real thing you do — a banished card back to the
     * graveyard, a graveyard card back onto the deck — and every one of those is
     * the same two steps, so they are written once. Dropping a pile onto itself
     * is not a move and falls out for free: the card is removed and put back in
     * the same place, which compares equal and is rejected upstream.
     */
    private fun PlayField.movePile(
        from: DragOrigin.Pile,
        arrive: PlayField.(BoardCard) -> PlayField,
    ): PlayField? {
        val (without, card) = lifted(from) ?: return null
        val landed = without.arrive(card)
        return if (landed == this) null else landed
    }

    /**
     * A card dug out from under another one and put somewhere else by [arrive].
     *
     * The same two steps [movePile] takes and the same reason for writing them
     * once — but the lifting is [PlayField.liftFromUnder]'s job, because taking
     * a card out of a stack on the mat has to decide whether it came from the
     * pile or from the top card's materials, and that is a fact about the board
     * rather than about the drag.
     */
    private fun PlayField.moveBuried(
        from: DragOrigin.Buried,
        arrive: PlayField.(BoardCard) -> PlayField,
    ): PlayField? {
        val (without, card) = liftFromUnder(from.under, from.index) ?: return null
        val landed = without.arrive(card)
        return if (landed == this) null else landed
    }

    /** The field without [from]'s card, and that card, ready to go elsewhere. */
    private fun PlayField.lifted(from: DragOrigin.Pile): Pair<PlayField, BoardCard>? =
        when (from.pile) {
            BoardSlot.Deck -> deck.getOrNull(from.index)
                ?.let { copy(deck = deck.minusAt(from.index)) to it }
            BoardSlot.ExtraDeck -> extraDeck.getOrNull(from.index)
                ?.let { copy(extraDeck = extraDeck.minusAt(from.index)) to it }
            BoardSlot.Graveyard -> graveyard.getOrNull(from.index)
                ?.let { copy(graveyard = graveyard.minusAt(from.index)) to it }
            BoardSlot.Banished -> banished.getOrNull(from.index)
                ?.let { copy(banished = banished.minusAt(from.index)) to it }
            // A zone is not a pile.
            is BoardSlot.Zone -> null
        }
}

/**
 * The list without the item at [index].
 *
 * Named rather than written inline, and deliberately not called `drop`: a local
 * `List<T>.drop(index)` shadows the standard one, so its own `drop(index + 1)`
 * resolves back to itself and recurses until the stack runs out.
 */
private fun <T> List<T>.minusAt(index: Int): List<T> =
    if (index !in indices) this else take(index) + drop(index + 1)
