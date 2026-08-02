package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random

/**
 * Where something sits on the mat, as a fraction of the mat's own size.
 *
 * Not pixels. A board is a thing you build over several minutes and then rotate
 * the tablet, or resize the window, or hand to someone else — and a board stored
 * in pixels is a board that falls apart when any of that happens. Fractions
 * survive all of it and make the model resolution-independent, which is also
 * what lets it be tested without a screen.
 *
 * The point is the card's *centre*, because everything a hand does to a card —
 * dropping it, turning it, stacking on it — is about the middle of it, and a
 * corner-anchored card rotates out from under your finger.
 */
data class MatPoint(val x: Float, val y: Float) {
    /** Kept on the mat: a card cannot be dropped off the edge of the world. */
    fun clamped(margin: Float = 0f) = MatPoint(
        x = x.coerceIn(margin, 1f - margin),
        y = y.coerceIn(margin, 1f - margin),
    )

    companion object {
        val Centre = MatPoint(0.5f, 0.5f)
    }
}

/**
 * One card on the mat, and whatever is underneath it.
 *
 * [beneath] is the pile this card is the top of, nearest first. A stack is
 * modelled as belonging to its top card rather than as a separate object
 * because that is what it is physically: you see the top one, and the rest are
 * under it. Moving the top card moves the pile.
 *
 * Face-up and turned are read off [BoardCard.position] rather than stored
 * again, so there is exactly one answer to "which way is this card facing"
 * anywhere in the app.
 */
data class PlacedCard(
    val card: BoardCard,
    val at: MatPoint,
    val beneath: List<BoardCard> = emptyList(),
) {
    val id: Int get() = card.instanceId
    val faceUp: Boolean get() = card.position.faceUp
    val turned: Boolean get() =
        card.position == CardPosition.FACE_UP_DEF || card.position == CardPosition.FACE_DOWN_DEF

    /** How many cards are in this pile, the top one included. */
    val depth: Int get() = beneath.size + 1

    /** The whole pile, top first — what leaves together when it leaves. */
    val pile: List<BoardCard> get() = listOf(card) + beneath
}

/**
 * A duel table you can put cards down on anywhere.
 *
 * The sibling of [BoardState], not a replacement for it. That one is
 * zone-indexed — five monster zones, five spell/trap, one card each — which is
 * the game's own geometry and exactly right for reading a board back. It cannot
 * express a card at an arbitrary point, or two cards on one square, and bending
 * it into doing so would have cost the thing it is good at.
 *
 * So this holds the same piles, in the same order, with the same rules about
 * what is physically possible, and swaps the zone arrays for a list of cards at
 * points. The zones do not disappear — they become somewhere a card is *pulled
 * toward* when you let go near one, which is `DropTargets`' job, not this type's.
 *
 * Every operation returns null when the move is physically impossible and a new
 * field otherwise, so undo is a list of these and costs nothing.
 */
data class PlayField(
    /** Cards on the mat, back to front. The last one drawn is the top one. */
    val mat: List<PlacedCard> = emptyList(),
    val hand: List<BoardCard> = emptyList(),
    /** Index 0 is the top. */
    val deck: List<BoardCard> = emptyList(),
    val extraDeck: List<BoardCard> = emptyList(),
    /** Most recent on top, the way a real graveyard reads. */
    val graveyard: List<BoardCard> = emptyList(),
    val banished: List<BoardCard> = emptyList(),
    val lifePoints: Int = 8000,
    val phase: DuelPhase = DuelPhase.MAIN1,
    val turn: Int = 1,
) {

    // ---- reads --------------------------------------------------------------

    fun placed(id: Int): PlacedCard? = mat.firstOrNull { it.id == id }

    /** Every card anywhere on the mat, piles included — for counting, not drawing. */
    val onMat: List<BoardCard> get() = mat.flatMap { it.pile }

    // ---- the deck -----------------------------------------------------------

    fun shuffleDeck(seed: Long): PlayField {
        val shuffled = deck.toMutableList()
        val random = Random(seed)
        for (i in shuffled.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val swap = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = swap
        }
        return copy(deck = shuffled)
    }

    fun draw(): PlayField? {
        val top = deck.firstOrNull() ?: return null
        return copy(deck = deck.drop(1), hand = hand + top.faceUp())
    }

    // ---- onto the mat -------------------------------------------------------

    fun playFromHand(index: Int, at: MatPoint, position: CardPosition): PlayField? {
        val card = hand.getOrNull(index) ?: return null
        return copy(hand = hand.remove(index)).place(card, at, position)
    }

    fun playFromExtra(index: Int, at: MatPoint, position: CardPosition): PlayField? {
        val card = extraDeck.getOrNull(index) ?: return null
        return copy(extraDeck = extraDeck.remove(index)).place(card, at, position)
    }

    fun playFromGraveyard(index: Int, at: MatPoint, position: CardPosition): PlayField? {
        val card = graveyard.getOrNull(index) ?: return null
        return copy(graveyard = graveyard.remove(index)).place(card, at, position)
    }

    fun playFromBanished(index: Int, at: MatPoint, position: CardPosition): PlayField? {
        val card = banished.getOrNull(index) ?: return null
        return copy(banished = banished.remove(index)).place(card, at, position)
    }

    fun playFromDeck(index: Int, at: MatPoint, position: CardPosition): PlayField? {
        val card = deck.getOrNull(index) ?: return null
        return copy(deck = deck.remove(index)).place(card, at, position)
    }

    private fun place(card: BoardCard, at: MatPoint, position: CardPosition): PlayField =
        copy(mat = mat + PlacedCard(card.copy(position = position), at.clamped()))

    // ---- moving what is already there ---------------------------------------

    /** Slides a card — and its pile — to a new point, and brings it to the front. */
    fun moveOnMat(id: Int, to: MatPoint): PlayField? {
        val card = placed(id) ?: return null
        return copy(mat = mat.without(id) + card.copy(at = to.clamped()))
    }

    /**
     * Drops one card onto another so the two become a pile.
     *
     * The moved card lands on top, carrying anything that was already under it,
     * because that is what happens when you put a pile down on a pile. Dropping
     * a card onto itself, or onto a card already in its own pile, is not a move.
     */
    fun stackOnto(id: Int, onto: Int): PlayField? {
        if (id == onto) return null
        val moving = placed(id) ?: return null
        val target = placed(onto) ?: return null

        return copy(
            mat = mat.without(id).without(onto) +
                moving.copy(at = target.at, beneath = moving.beneath + target.pile),
        )
    }

    /**
     * Lifts the top card off a pile and puts it down at [at].
     *
     * The one under it becomes the pile's new top and stays where it was, which
     * is what happens when you take the top card off something.
     */
    fun unstack(id: Int, at: MatPoint): PlayField? {
        val pile = placed(id) ?: return null
        val next = pile.beneath.firstOrNull() ?: return null

        return copy(
            mat = mat.without(id) +
                PlacedCard(next, pile.at, pile.beneath.drop(1)) +
                PlacedCard(pile.card, at.clamped()),
        )
    }

    /** Puts a card on top of everything without moving it, for reading a busy mat. */
    fun bringToFront(id: Int): PlayField? {
        val card = placed(id) ?: return null
        if (mat.lastOrNull()?.id == id) return null
        return copy(mat = mat.without(id) + card)
    }

    // ---- which way it faces --------------------------------------------------

    /** Turns a card over, keeping whether it is upright or sideways. */
    fun flip(id: Int): PlayField? = repose(id) { position ->
        when (position) {
            CardPosition.FACE_UP_ATK -> CardPosition.FACE_DOWN_ATK
            CardPosition.FACE_UP_DEF -> CardPosition.FACE_DOWN_DEF
            CardPosition.FACE_DOWN_ATK -> CardPosition.FACE_UP_ATK
            CardPosition.FACE_DOWN_DEF -> CardPosition.FACE_UP_DEF
        }
    }

    /** Turns a card ninety degrees, keeping which way up it is facing. */
    fun rotate(id: Int): PlayField? = repose(id) { position ->
        when (position) {
            CardPosition.FACE_UP_ATK -> CardPosition.FACE_UP_DEF
            CardPosition.FACE_UP_DEF -> CardPosition.FACE_UP_ATK
            CardPosition.FACE_DOWN_ATK -> CardPosition.FACE_DOWN_DEF
            CardPosition.FACE_DOWN_DEF -> CardPosition.FACE_DOWN_ATK
        }
    }

    fun setPosition(id: Int, position: CardPosition): PlayField? = repose(id) { position }

    private fun repose(id: Int, next: (CardPosition) -> CardPosition): PlayField? {
        val card = placed(id) ?: return null
        val position = next(card.card.position)
        if (position == card.card.position) return null
        return copy(mat = mat.replace(id) { it.copy(card = it.card.copy(position = position)) })
    }

    // ---- off the mat ---------------------------------------------------------

    /**
     * Everything that leaves the mat takes its pile with it and arrives clean.
     *
     * Counters and Xyz materials belong to a card *while it is in play*; a card
     * in the graveyard has neither, and carrying them along would quietly
     * resurrect them if it came back.
     */
    private fun lift(id: Int): Pair<PlayField, List<BoardCard>>? {
        val card = placed(id) ?: return null
        val freed = card.pile.flatMap { listOf(it.copy(materials = emptyList(), counters = 0)) + it.materials }
        return copy(mat = mat.without(id)) to freed
    }

    fun toGraveyard(id: Int): PlayField? {
        val (field, cards) = lift(id) ?: return null
        return field.copy(graveyard = cards.map { it.faceUp() } + field.graveyard)
    }

    fun toBanish(id: Int, faceDown: Boolean = false): PlayField? {
        val (field, cards) = lift(id) ?: return null
        val position = if (faceDown) CardPosition.FACE_DOWN_ATK else CardPosition.FACE_UP_ATK
        return field.copy(banished = cards.map { it.copy(position = position) } + field.banished)
    }

    fun toHand(id: Int): PlayField? {
        val (field, cards) = lift(id) ?: return null
        return field.copy(hand = field.hand + cards.map { it.faceUp() })
    }

    fun toDeckTop(id: Int): PlayField? {
        val (field, cards) = lift(id) ?: return null
        return field.copy(deck = cards.map { it.faceUp() } + field.deck)
    }

    fun toDeckBottom(id: Int): PlayField? {
        val (field, cards) = lift(id) ?: return null
        return field.copy(deck = field.deck + cards.map { it.faceUp() })
    }

    /** Back to the extra deck, for a card that came from it. */
    fun toExtraDeck(id: Int): PlayField? {
        val (field, cards) = lift(id) ?: return null
        return field.copy(extraDeck = cards.map { it.faceUp() } + field.extraDeck)
    }

    // ---- out of the hand -----------------------------------------------------

    fun handToDeckTop(index: Int): PlayField? {
        val card = hand.getOrNull(index) ?: return null
        return copy(hand = hand.remove(index), deck = listOf(card) + deck)
    }

    fun handToDeckBottom(index: Int): PlayField? {
        val card = hand.getOrNull(index) ?: return null
        return copy(hand = hand.remove(index), deck = deck + card)
    }

    fun handToGraveyard(index: Int): PlayField? {
        val card = hand.getOrNull(index) ?: return null
        return copy(hand = hand.remove(index), graveyard = listOf(card) + graveyard)
    }

    fun handToBanish(index: Int): PlayField? {
        val card = hand.getOrNull(index) ?: return null
        return copy(hand = hand.remove(index), banished = listOf(card) + banished)
    }

    // ---- counters, life, phases ----------------------------------------------

    fun addCounter(id: Int, delta: Int): PlayField? {
        val card = placed(id) ?: return null
        val counters = (card.card.counters + delta).coerceAtLeast(0)
        if (counters == card.card.counters) return null
        return copy(mat = mat.replace(id) { it.copy(card = it.card.copy(counters = counters)) })
    }

    /**
     * Tucks one card under another as Xyz material.
     *
     * Distinct from stacking: a material rides *with* its monster and leaves
     * with it, where a stack is just two cards in the same place. The physical
     * difference is real — materials are under the card and slightly fanned —
     * and so is the rules difference.
     */
    fun attachAsMaterial(id: Int, onto: Int): PlayField? {
        if (id == onto) return null
        val moving = placed(id) ?: return null
        val target = placed(onto) ?: return null
        val materials = moving.pile.map { it.copy(materials = emptyList(), counters = 0) }

        return copy(
            mat = mat.without(id).replace(onto) {
                it.copy(card = it.card.copy(materials = it.card.materials + materials))
            },
        )
    }

    fun detachMaterial(id: Int): PlayField? {
        val card = placed(id) ?: return null
        val material = card.card.materials.firstOrNull() ?: return null
        return copy(
            mat = mat.replace(id) { it.copy(card = it.card.copy(materials = it.card.materials.drop(1))) },
            graveyard = listOf(material.faceUp()) + graveyard,
        )
    }

    fun adjustLifePoints(delta: Int): PlayField =
        copy(lifePoints = (lifePoints + delta).coerceAtLeast(0))

    fun nextPhase(): PlayField = copy(phase = phase.next())

    fun endTurn(): PlayField = copy(phase = DuelPhase.DRAW, turn = turn + 1)

    companion object {
        /** A shuffled deck, an empty mat, and eight thousand life points. */
        fun setUp(main: List<CardId>, extra: List<CardId>): PlayField {
            var id = 0
            fun deal(cards: List<CardId>) = cards.map { BoardCard(id++, it) }
            return PlayField(deck = deal(main), extraDeck = deal(extra))
        }
    }
}

// ---- small list surgery, named so the operations above read as sentences -----

private fun BoardCard.faceUp() = copy(position = CardPosition.FACE_UP_ATK)

private fun <T> List<T>.remove(index: Int): List<T> =
    if (index !in indices) this else take(index) + drop(index + 1)

private fun List<PlacedCard>.without(id: Int): List<PlacedCard> = filterNot { it.id == id }

private fun List<PlacedCard>.replace(id: Int, change: (PlacedCard) -> PlacedCard): List<PlacedCard> =
    map { if (it.id == id) change(it) else it }
