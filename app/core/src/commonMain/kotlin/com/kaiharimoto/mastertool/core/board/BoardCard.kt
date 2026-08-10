package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * What a card *is* on a table, and where the rules say it may sit.
 *
 * The vocabulary rather than a board. There used to be a `BoardState` here — the
 * zone-board's whole domain, a pure `(BoardState) -> BoardState?` per move — and
 * it went with the screen that was its only reader: `PlayField` had replaced it
 * everywhere that mattered, and two boards is two answers to "where is that
 * card". What survived is the part neither board could do without.
 *
 * [FieldZone] is still here and still means what it meant, because
 * `BoardLayout` names the ten zones with it: the freeform stage draws them,
 * snaps to them, and simply does not insist that a card be inside one.
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
