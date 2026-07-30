package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random

/**
 * The duel table's domain: one side of a board, as geometry.
 *
 * A sandbox, not a rules engine — like a real table, the model enforces what
 * is *physically* coherent (a zone holds one card, the graveyard keeps its
 * order, a draw comes off the top) and the player enforces the game. Every
 * operation is a pure `(BoardState) -> BoardState?` returning null when the
 * move is geometrically impossible, so the UI can grey things out and undo is
 * a history of immutable states, exactly the pattern the deck builder uses.
 */

enum class CardPosition {
    FACE_UP_ATK,
    FACE_UP_DEF,
    FACE_DOWN_DEF,
    /** Rare, but real cards produce it; the table should not argue. */
    FACE_DOWN_ATK,
    ;

    val faceUp: Boolean get() = this == FACE_UP_ATK || this == FACE_UP_DEF
}

enum class DuelPhase(val label: String) {
    DRAW("Draw"),
    STANDBY("Standby"),
    MAIN1("Main 1"),
    BATTLE("Battle"),
    MAIN2("Main 2"),
    END("End"),
    ;

    fun next(): DuelPhase = entries[(ordinal + 1) % entries.size]
}

/** Where a card can sit on the field proper. */
sealed interface FieldZone {
    data class Monster(val index: Int) : FieldZone // 0..4
    data class ExtraMonster(val index: Int) : FieldZone // 0..1
    data class SpellTrap(val index: Int) : FieldZone // 0..4
    data object FieldSpell : FieldZone
}

/** One physical card on the table. */
data class BoardCard(
    val instanceId: Int,
    val cardId: CardId,
    val position: CardPosition = CardPosition.FACE_UP_ATK,
    /** Xyz materials, bottom-most last. Plain cards ride along face-up. */
    val materials: List<BoardCard> = emptyList(),
    val counters: Int = 0,
)

data class BoardState(
    val monsters: List<BoardCard?>,
    val extraMonsters: List<BoardCard?>,
    val spellTraps: List<BoardCard?>,
    val fieldSpell: BoardCard?,
    val hand: List<BoardCard>,
    /** Index 0 is the top. */
    val deck: List<BoardCard>,
    val extraDeck: List<BoardCard>,
    /** Most recent on top (index 0), the way a real graveyard reads. */
    val graveyard: List<BoardCard>,
    val banished: List<BoardCard>,
    val lifePoints: Int,
    val phase: DuelPhase,
    val turn: Int,
    private val nextInstanceId: Int,
) {
    companion object {
        fun setUp(main: List<CardId>, extra: List<CardId>): BoardState {
            var id = 0
            fun place(cards: List<CardId>) = cards.map { BoardCard(id++, it) }
            return BoardState(
                monsters = List(5) { null },
                extraMonsters = List(2) { null },
                spellTraps = List(5) { null },
                fieldSpell = null,
                hand = emptyList(),
                deck = place(main),
                extraDeck = place(extra),
                graveyard = emptyList(),
                banished = emptyList(),
                lifePoints = 8000,
                phase = DuelPhase.DRAW,
                turn = 1,
                nextInstanceId = id,
            )
        }
    }

    // ---- reads -------------------------------------------------------------

    fun at(zone: FieldZone): BoardCard? = when (zone) {
        is FieldZone.Monster -> monsters.getOrNull(zone.index)
        is FieldZone.ExtraMonster -> extraMonsters.getOrNull(zone.index)
        is FieldZone.SpellTrap -> spellTraps.getOrNull(zone.index)
        FieldZone.FieldSpell -> fieldSpell
    }

    private fun valid(zone: FieldZone): Boolean = when (zone) {
        is FieldZone.Monster -> zone.index in 0..4
        is FieldZone.ExtraMonster -> zone.index in 0..1
        is FieldZone.SpellTrap -> zone.index in 0..4
        FieldZone.FieldSpell -> true
    }

    private fun put(zone: FieldZone, card: BoardCard?): BoardState = when (zone) {
        is FieldZone.Monster ->
            copy(monsters = monsters.toMutableList().also { it[zone.index] = card })
        is FieldZone.ExtraMonster ->
            copy(extraMonsters = extraMonsters.toMutableList().also { it[zone.index] = card })
        is FieldZone.SpellTrap ->
            copy(spellTraps = spellTraps.toMutableList().also { it[zone.index] = card })
        FieldZone.FieldSpell -> copy(fieldSpell = card)
    }

    // ---- moves: null = geometrically impossible ----------------------------

    fun draw(): BoardState? {
        val top = deck.firstOrNull() ?: return null
        return copy(deck = deck.drop(1), hand = hand + top)
    }

    fun shuffleDeck(seed: Long): BoardState =
        copy(deck = deck.shuffled(Random(seed)))

    /** Hand to an empty zone — summon, set, or activate; the position says which. */
    fun playFromHand(handIndex: Int, zone: FieldZone, position: CardPosition): BoardState? {
        val card = hand.getOrNull(handIndex) ?: return null
        if (!valid(zone) || at(zone) != null) return null
        return put(zone, card.copy(position = position))
            .copy(hand = hand.filterIndexed { i, _ -> i != handIndex })
    }

    /** Extra deck to a zone — the fusion/synchro/xyz/link arrival. */
    fun playFromExtra(extraIndex: Int, zone: FieldZone, position: CardPosition): BoardState? {
        val card = extraDeck.getOrNull(extraIndex) ?: return null
        if (!valid(zone) || at(zone) != null) return null
        return put(zone, card.copy(position = position))
            .copy(extraDeck = extraDeck.filterIndexed { i, _ -> i != extraIndex })
    }

    /** Graveyard back to a zone — the revival. Index 0 is the top. */
    fun playFromGraveyard(gyIndex: Int, zone: FieldZone, position: CardPosition): BoardState? {
        val card = graveyard.getOrNull(gyIndex) ?: return null
        if (!valid(zone) || at(zone) != null) return null
        return put(zone, card.copy(position = position))
            .copy(graveyard = graveyard.filterIndexed { i, _ -> i != gyIndex })
    }

    /** Slide a card between two zones on the table. */
    fun moveOnField(from: FieldZone, to: FieldZone): BoardState? {
        val card = at(from) ?: return null
        if (!valid(to) || at(to) != null) return null
        return put(from, null).put(to, card)
    }

    fun changePosition(zone: FieldZone): BoardState? {
        val card = at(zone) ?: return null
        val next = when (card.position) {
            CardPosition.FACE_UP_ATK -> CardPosition.FACE_UP_DEF
            CardPosition.FACE_UP_DEF -> CardPosition.FACE_UP_ATK
            // A face-down card turning over is a flip, not a position change.
            CardPosition.FACE_DOWN_DEF, CardPosition.FACE_DOWN_ATK -> return null
        }
        return put(zone, card.copy(position = next))
    }

    fun flip(zone: FieldZone): BoardState? {
        val card = at(zone) ?: return null
        if (card.position.faceUp) return null
        return put(zone, card.copy(position = CardPosition.FACE_UP_ATK))
    }

    /** To the graveyard, materials riding along — the way an Xyz actually dies. */
    fun sendToGraveyard(zone: FieldZone): BoardState? {
        val card = at(zone) ?: return null
        val released = listOf(card.copy(materials = emptyList(), counters = 0)) + card.materials
        return put(zone, null).copy(graveyard = released + graveyard)
    }

    fun banishFrom(zone: FieldZone, faceDown: Boolean = false): BoardState? {
        val card = at(zone) ?: return null
        val gone = card.copy(
            materials = emptyList(),
            counters = 0,
            position = if (faceDown) CardPosition.FACE_DOWN_ATK else CardPosition.FACE_UP_ATK,
        )
        return put(zone, null).copy(banished = listOf(gone) + card.materials + banished)
    }

    fun returnToHand(zone: FieldZone): BoardState? {
        val card = at(zone) ?: return null
        val freed = card.copy(materials = emptyList(), counters = 0, position = CardPosition.FACE_UP_ATK)
        return put(zone, null).copy(hand = hand + freed + card.materials)
    }

    fun graveyardToHand(gyIndex: Int): BoardState? {
        val card = graveyard.getOrNull(gyIndex) ?: return null
        return copy(
            graveyard = graveyard.filterIndexed { i, _ -> i != gyIndex },
            hand = hand + card,
        )
    }

    fun handToDeckTop(handIndex: Int): BoardState? {
        val card = hand.getOrNull(handIndex) ?: return null
        return copy(hand = hand.filterIndexed { i, _ -> i != handIndex }, deck = listOf(card) + deck)
    }

    fun handToDeckBottom(handIndex: Int): BoardState? {
        val card = hand.getOrNull(handIndex) ?: return null
        return copy(hand = hand.filterIndexed { i, _ -> i != handIndex }, deck = deck + card)
    }

    /** Stack the card at [from] under the card at [target] as Xyz material. */
    fun attachAsMaterial(from: FieldZone, target: FieldZone): BoardState? {
        val material = at(from) ?: return null
        val host = at(target) ?: return null
        if (from == target) return null
        val flattened = material.copy(materials = emptyList())
        return put(from, null).put(
            target,
            host.copy(materials = host.materials + flattened + material.materials),
        )
    }

    /** Detach the bottom-most material to the graveyard — the Xyz cost. */
    fun detachMaterial(zone: FieldZone): BoardState? {
        val host = at(zone) ?: return null
        val material = host.materials.lastOrNull() ?: return null
        return put(zone, host.copy(materials = host.materials.dropLast(1)))
            .copy(graveyard = listOf(material) + graveyard)
    }

    fun addCounter(zone: FieldZone, delta: Int): BoardState? {
        val card = at(zone) ?: return null
        return put(zone, card.copy(counters = (card.counters + delta).coerceAtLeast(0)))
    }

    fun adjustLifePoints(delta: Int): BoardState =
        copy(lifePoints = (lifePoints + delta).coerceAtLeast(0))

    fun nextPhase(): BoardState {
        val next = phase.next()
        return copy(phase = next, turn = if (next == DuelPhase.DRAW) turn + 1 else turn)
    }

    fun endTurn(): BoardState = copy(phase = DuelPhase.DRAW, turn = turn + 1)
}
