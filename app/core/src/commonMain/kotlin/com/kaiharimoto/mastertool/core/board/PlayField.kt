package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
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
 * It began as the sibling of a zone-indexed `BoardState` — five monster zones,
 * five spell/trap, one card each — which is the game's own geometry and exactly
 * right for reading a board back. What that one could not express is a card at
 * an arbitrary point, or two cards on one square, and bending it into doing so
 * would have cost the thing it was good at. Both existed for a while, behind two
 * screens; the zone board is gone and this is the only table now.
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

    /**
     * Every physical card on the mat — for counting, not for drawing.
     *
     * Stacks *and* materials. A card tucked under a monster as material is
     * still a card on the table, and leaving it out of the census made
     * attaching one look like losing one, which an exhaustive test noticed
     * before a person did.
     */
    val onMat: List<BoardCard>
        get() = mat.flatMap { placed -> placed.pile.flatMap { listOf(it) + it.materials } }

    /**
     * The pile a slot names, for anything that has to treat all four alike.
     *
     * There were four copies of this `when` before there was one — in the drop
     * resolver, in the committer, in the seat builder and in the hit test — and
     * the fan would have made it five. A zone is not a pile and never holds one.
     */
    fun pile(slot: BoardSlot): List<BoardCard> = when (slot) {
        BoardSlot.Deck -> deck
        BoardSlot.ExtraDeck -> extraDeck
        BoardSlot.Graveyard -> graveyard
        BoardSlot.Banished -> banished
        is BoardSlot.Zone -> emptyList()
    }

    /**
     * Everything [id] has under it, top card first.
     *
     * The pile it is the top of, and then whatever is attached to it as
     * material. Two different relationships — one is *resting on*, the other is
     * *part of* — and they are listed together because a hand reaching into a
     * stack does not distinguish them: what you want to know is what is under
     * this card, and for an Xyz monster the answer is its materials.
     *
     * Index 0 is [id] itself, so the list reads as "this card and what is beneath
     * it" and an index into it is an index into the whole object.
     */
    fun under(id: Int): List<BoardCard> {
        val placed = placed(id) ?: return emptyList()
        return placed.pile + placed.card.materials
    }

    /**
     * Pulls the card [index] deep in [id]'s stack out onto the mat at [at].
     *
     * The move nothing could ask for. `unstack` could take the card directly
     * beneath one, one at a time, and `detachMaterial` could send the
     * bottom-most material to the graveyard — so the third card down of a pile,
     * and any material that was not the last one, were cards on the table that
     * no operation could name.
     *
     * Index 0 is the top card, which is already a card on the mat and is simply
     * moved; that keeps a fanned-out stack's first card behaving like every
     * other card rather than being a special case at the call site.
     */
    fun takeFromUnder(id: Int, index: Int, at: MatPoint, position: CardPosition): PlayField? {
        if (index == 0) return moveOnMat(id, at)
        val (without, card) = liftFromUnder(id, index) ?: return null
        return without.place(card, at, position)
    }

    /**
     * The field without the card [index] deep in [id]'s stack, and that card.
     *
     * Materials come off with nothing attached to them — a material is a plain
     * card that happens to be under a monster — and a card taken out of the pile
     * keeps whatever was attached to *it*, because that is a different object
     * that happened to be resting in the same place.
     */
    internal fun liftFromUnder(id: Int, index: Int): Pair<PlayField, BoardCard>? {
        val placed = placed(id) ?: return null
        if (index <= 0) return null

        val buried = placed.beneath
        if (index <= buried.size) {
            val card = buried[index - 1]
            val left = copy(mat = mat.replace(id) { it.copy(beneath = buried.remove(index - 1)) })
            return left to card
        }

        val materials = placed.card.materials
        val at = index - buried.size - 1
        val card = materials.getOrNull(at) ?: return null
        val left = copy(
            mat = mat.replace(id) {
                it.copy(card = it.card.copy(materials = materials.remove(at)))
            },
        )
        return left to card
    }

    /**
     * The cards a fan opened on [what] spreads out, in the order they spread.
     *
     * One function so the hit test and the renderer cannot disagree about which
     * card is third from the left — and so that a pile and a stack on the mat
     * are the same feature rather than two that look alike. A hand is not
     * something you search; you can already see all of it.
     */
    /**
     * The card an origin names, wherever on the table it is.
     *
     * The one place a `DragOrigin` is turned back into the card it points at, so
     * that anything wanting to *read* one — the reader a held finger opens, the
     * name a tap announces — asks the same question the gestures do and cannot
     * end up looking at a different card from the one under the finger.
     *
     * Null for an index that has run off the end of its pile, which happens: a
     * gesture is a memory of what the press landed on, and the board can change
     * underneath it.
     */
    fun cardAt(what: DragOrigin): BoardCard? = when (what) {
        is DragOrigin.Mat -> placed(what.id)?.card
        is DragOrigin.Hand -> hand.getOrNull(what.index)
        is DragOrigin.Pile -> pile(what.pile).getOrNull(what.index)
        is DragOrigin.Buried -> under(what.under).getOrNull(what.index)
    }

    /**
     * The origin that names [instanceId] *now*, given one that named it before.
     *
     * A `DragOrigin` into the hand or a pile is an **index**, and an index is
     * only true of the board it was taken from. One hand releasing a card
     * renumbers everything below it, so with two hands in the same pile the
     * second gesture is holding a memory that has quietly come to mean the card
     * next door — and it would drop *that* one, correctly, silently, and wrong.
     *
     * This used to be `stillHolds`, a boolean, and the release turned `false`
     * into a refusal: the card left the air, nothing was committed, and it was
     * drawn back where it started. That is safe and it is also the whole of
     * kai's third report — *"I'm trying to take 2+ cards out of the deck at the
     * same time and the hold works but when I let one go the other card I'm
     * holding goes back into the deck."* Refusing was never the only safe
     * answer, because the card's own identity was already being carried: a
     * gesture that knows **which card** it is holding does not need the index to
     * have survived, it needs to be told where that card is now.
     *
     * Rebased **within the same place**, never across one. A card looked for in
     * the pile it came out of and not found has genuinely left — the other hand
     * drew it, milled it, played it — and there is no honest repair for that, so
     * this still answers null and the release still puts the card back. Widening
     * the search to the whole table would turn "play this card off my deck" into
     * "play this card out of my hand", which is the same piece of cardboard and
     * a different move.
     *
     * The four places are four coordinate systems and each is rebased in its own:
     *
     * - **Mat** carries the instance id outright, so there is nothing to
     *   re-number. It answers null only when the card has stopped being a *top*
     *   card, which means the other hand stacked something on it — and dragging
     *   it then is no longer the move the user started, because the top of a
     *   stack takes the stack with it.
     * - **Hand** and **Pile** are plain lookups in the list they name.
     * - **Buried** is a lookup in [under], and that is exactly right without a
     *   special case: `under` is the top card, then what is beneath it, then its
     *   materials, in that order, and `liftFromUnder` branches on the same
     *   boundary. So an index found by identity always lands on the side the
     *   card is on *now*, which is the side it should come off.
     */
    fun rebase(what: DragOrigin, instanceId: Int): DragOrigin? = when (what) {
        is DragOrigin.Mat -> what.takeIf { placed(it.id)?.card?.instanceId == instanceId }
        is DragOrigin.Hand -> hand.indexOfFirst { it.instanceId == instanceId }
            .takeIf { it >= 0 }?.let { DragOrigin.Hand(it) }
        is DragOrigin.Pile -> pile(what.pile).indexOfFirst { it.instanceId == instanceId }
            .takeIf { it >= 0 }?.let { DragOrigin.Pile(what.pile, it) }
        is DragOrigin.Buried -> under(what.under).indexOfFirst { it.instanceId == instanceId }
            .takeIf { it >= 0 }?.let { DragOrigin.Buried(what.under, it) }
    }

    /** Whether an origin still names the card it named when it was picked up. */
    fun stillHolds(what: DragOrigin, instanceId: Int): Boolean =
        cardAt(what)?.instanceId == instanceId

    fun fanOf(what: DragOrigin): List<BoardCard> = when (what) {
        is DragOrigin.Pile -> pile(what.pile)
        // What is *under* the card, and not the card. A monster stays where it
        // is while you look through what it is made of — which is the honest
        // picture, and it also settles a question the alternative could not
        // answer: the top of a stack takes the stack with it when it leaves, so
        // a spread with the monster in it would have one card that behaved
        // differently from the rest and no way to say why.
        is DragOrigin.Mat -> under(what.id).drop(1)
        is DragOrigin.Buried -> under(what.under).drop(1)
        is DragOrigin.Hand -> emptyList()
    }

    // ---- the deck -----------------------------------------------------------

    fun shuffleDeck(seed: Long): PlayField = copy(deck = deck.riffled(seed))

    /**
     * And the extra deck, which by the rules never needs it.
     *
     * It is public information in a real game, so its order carries nothing and
     * shuffling it changes nothing anybody could know. It is here because the
     * extra deck can be fanned out and searched like any other pile, and a
     * search that leaves a pile in the order you left it in is a search that has
     * quietly told you where everything is — which matters not at all against an
     * opponent and matters when the person you are practising against is you.
     */
    fun shuffleExtraDeck(seed: Long): PlayField = copy(extraDeck = extraDeck.riffled(seed))

    /**
     * Fisher-Yates, written out rather than `shuffled(Random(seed))`.
     *
     * The stdlib's shuffle is free to change its algorithm between Kotlin
     * versions and between platforms, and this app has two of those. A seed that
     * produced one deal on a tablet and another on a desktop would make every
     * bug report about an opening hand unreproducible.
     */
    private fun List<BoardCard>.riffled(seed: Long): List<BoardCard> {
        val shuffled = toMutableList()
        val random = Random(seed)
        for (i in shuffled.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val swap = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = swap
        }
        return shuffled
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

    /**
     * Slides a card — and its pile — to a new point, and brings it to the front.
     *
     * [position] is how it is lying when it gets there, and **null means "the way
     * it already was"**, which is what a plain slide across the felt is. Trailing
     * and defaulted, so every caller that has no opinion keeps the behaviour this
     * had before it could be told one.
     *
     * It could not be told one for two releases, and that is the whole of *"a
     * card set onto the field comes out vertical"*: `SetPosition` solved the
     * landing correctly, the card turned over and lay down in the air where you
     * could see it, and then `DropCommit` called this — which moved the card and
     * silently dropped the answer. Setting worked from the hand and from a pile,
     * which is why it looked intermittent rather than broken.
     */
    fun moveOnMat(id: Int, to: MatPoint, position: CardPosition? = null): PlayField? {
        val card = placed(id) ?: return null
        val landed = if (position == null) card.card else card.card.copy(position = position)
        return copy(mat = mat.without(id) + card.copy(at = to.clamped(), card = landed))
    }

    /**
     * Drops one card onto another so the two become a pile.
     *
     * The moved card lands on top, carrying anything that was already under it,
     * because that is what happens when you put a pile down on a pile. Dropping
     * a card onto itself, or onto a card already in its own pile, is not a move.
     */
    fun stackOnto(id: Int, onto: Int, position: CardPosition? = null): PlayField? {
        if (id == onto) return null
        val moving = placed(id) ?: return null
        val target = placed(onto) ?: return null
        // Null is "as it was", exactly as in [moveOnMat] — and for the same
        // reason: a card set face-down onto another card is still being set.
        val landed = if (position == null) moving.card else moving.card.copy(position = position)

        return copy(
            mat = mat.without(id).without(onto) +
                moving.copy(
                    at = target.at,
                    card = landed,
                    beneath = moving.beneath + target.pile,
                ),
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

    /**
     * Back to the hand, at [at] if the gesture named a place in it.
     *
     * Null means the end, which is what every caller that is not a drag wants:
     * a card returned by a menu item has not been aimed anywhere.
     */
    fun toHand(id: Int, at: Int? = null): PlayField? {
        val (field, cards) = lift(id) ?: return null
        val arriving = cards.map { it.faceUp() }
        val where = (at ?: field.hand.size).coerceIn(0, field.hand.size)
        return field.copy(hand = field.hand.take(where) + arriving + field.hand.drop(where))
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

    /**
     * Moves the card at [from] to the gap at [to], so a hand can be arranged.
     *
     * [to] counts gaps in the hand *as it stands*, before the card leaves it —
     * zero is before everything, `hand.size` after everything — which is what a
     * finger is pointing at and what makes both no-ops fall out of the same
     * comparison: dropping a card back in its own place, and dropping it in the
     * gap immediately after itself, are the same hand.
     *
     * Arranging your hand is the most ordinary thing anybody does with one, and
     * it was the one thing this table could not do at all. It is a real move and
     * goes on the undo stack like any other, because it is a thing you can get
     * wrong with a slip of a finger and want back.
     */
    fun reorderHand(from: Int, to: Int): PlayField? {
        val card = hand.getOrNull(from) ?: return null
        if (to !in 0..hand.size) return null
        if (to == from || to == from + 1) return null

        val without = hand.remove(from)
        val at = if (to > from) to - 1 else to
        return copy(hand = without.take(at) + card + without.drop(at))
    }

    /** Puts an arriving card into the hand at [at], which may be its end. */
    internal fun handInsert(card: BoardCard, at: Int): PlayField {
        val where = at.coerceIn(0, hand.size)
        return copy(hand = hand.take(where) + card + hand.drop(where))
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

/**
 * The card [index] along a fan opened on this origin, named so it can be moved.
 *
 * The join between "where my finger is in the spread" and "which card that is",
 * and the only place that mapping exists. A pile indexes straight through. A
 * stack's index zero is the card on top, which is already a card on the mat and
 * already has a name — everything below it is [DragOrigin.Buried].
 */
fun DragOrigin.fanCardAt(index: Int): DragOrigin? = when (this) {
    is DragOrigin.Pile -> DragOrigin.Pile(pile, index)
    // Offset by one, because a stack's spread leaves the card on top out of it
    // and `under` counts from the card itself. Every card in the spread is
    // therefore buried, and every one of them behaves the same way.
    is DragOrigin.Mat -> DragOrigin.Buried(id, index + 1)
    is DragOrigin.Buried -> DragOrigin.Buried(under, index + 1)
    is DragOrigin.Hand -> null
}

/** What a fan opened on this origin is *about*, so two names for it compare equal. */
val DragOrigin.fanSource: DragOrigin
    get() = when (this) {
        is DragOrigin.Pile -> DragOrigin.Pile(pile, 0)
        is DragOrigin.Mat -> this
        is DragOrigin.Buried -> DragOrigin.Mat(under)
        is DragOrigin.Hand -> this
    }

/**
 * Whether a card taken from here came out of the spread that is currently open.
 *
 * One line, and it exists because the version written inline at the call site was
 * `from.pile == fan` — a `BoardSlot` compared against a `DragOrigin`. Nothing in
 * either type makes that a compile error, both are sealed interfaces and a class
 * could in principle implement both, so it compiled and was **always false**: for
 * three releases you could search your deck, drag a card out of the spread onto
 * the board, and the deck stayed fanned across the table behind it.
 *
 * A named function in core is the difference between that and a red build, which
 * is the whole argument for [fanSource] existing in the first place — "so two
 * names for it compare equal" is no use to anybody if the comparison is written
 * out by hand somewhere else.
 */
fun DragOrigin.cameFrom(fan: DragOrigin?): Boolean = fan != null && fanSource == fan
