package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * The kinds of place a card can be on a board.
 *
 * Split into single zones and piles because that is the only distinction the
 * board itself has to enforce. Everything else about a zone — where it sits,
 * what it looks like, whether it is yours — belongs to whatever is drawing it.
 */
enum class ZoneKind {
    MONSTER,
    SPELL_TRAP,
    FIELD,
    EXTRA_MONSTER,
    GRAVEYARD,
    BANISHED,
    DECK,
    EXTRA_DECK;

    /**
     * How many cards fit.
     *
     * The four piles are unbounded on purpose. A graveyard with a limit is a
     * graveyard that will one day refuse a card in the middle of a combo, and
     * this is a sandbox — the rules it enforces are the ones that stop the board
     * becoming nonsense, not the ones a judge would.
     */
    val capacity: Int
        get() = when (this) {
            MONSTER, SPELL_TRAP, FIELD, EXTRA_MONSTER -> 1
            GRAVEYARD, BANISHED, DECK, EXTRA_DECK -> Int.MAX_VALUE
        }

    /** A stack of cards rather than a place one card sits. */
    val isPile: Boolean
        get() = when (this) {
            MONSTER, SPELL_TRAP, FIELD, EXTRA_MONSTER -> false
            GRAVEYARD, BANISHED, DECK, EXTRA_DECK -> true
        }
}

data class ZoneId(val kind: ZoneKind, val index: Int = 0)

/**
 * How a card sits.
 *
 * Three, not four. Face-down attack position does not exist in the game, and a
 * fourth value nothing can produce is a value every `when` has to handle
 * forever.
 */
enum class Placement {
    ATTACK,
    DEFENSE,

    /** Face-down: a set monster lies in defence, a set spell or trap just lies. */
    SET;

    /** The next position, for a board where tapping a card turns it. */
    fun turned(): Placement = when (this) {
        ATTACK -> DEFENSE
        DEFENSE -> SET
        SET -> ATTACK
    }
}

/**
 * A card on the board, or something standing in for one.
 *
 * [token] because plenty of decks make them and a board that cannot show one
 * cannot show the turn. A token has no passcode and no art — it is a thing on
 * the table that occupies a zone — so it carries a reserved id rather than a
 * real one, and everything that looks a card up has to notice.
 */
data class PlacedCard(
    val id: CardId,
    val placement: Placement = Placement.ATTACK,
    val token: Boolean = false,
) {
    companion object {
        /** Not a passcode any card has, and never looked up. */
        val TOKEN_ID = CardId(0)

        fun token(placement: Placement = Placement.ATTACK): PlacedCard =
            PlacedCard(TOKEN_ID, placement, token = true)
    }
}

/**
 * A board, as a value.
 *
 * Every operation returns a new one, which is what makes undo a stack of boards
 * rather than a stack of inverse operations — the thing the original's sandbox
 * spent a command factory on. A board is small: forty cards at the very worst,
 * and in practice a dozen.
 *
 * Zones that hold nothing are absent from the map rather than present and empty,
 * so two boards that look the same are equal.
 */
data class Board(private val contents: Map<ZoneId, List<PlacedCard>> = emptyMap()) {

    operator fun get(zone: ZoneId): List<PlacedCard> = contents[zone].orEmpty()

    val isEmpty: Boolean get() = contents.isEmpty()

    /** Every card on the board, for counting and for looking something up. */
    val cards: List<PlacedCard> get() = contents.values.flatten()

    fun occupied(zone: ZoneId): Boolean = this[zone].isNotEmpty()

    /**
     * Puts a card in a zone, or returns null if it does not fit.
     *
     * Null rather than an exception because a full zone is an ordinary outcome —
     * it is what happens when a drag ends over a monster zone that already has a
     * monster in it, which is most of the drags in a busy turn.
     */
    fun place(zone: ZoneId, card: PlacedCard): Board? {
        val existing = this[zone]
        if (existing.size >= zone.kind.capacity) return null
        return withZone(zone, existing + card)
    }

    /**
     * Moves the top card of one zone to another, if it fits.
     *
     * A token sent to a pile simply stops existing, which is the rule and also
     * the only sensible thing: a graveyard with a token in it is a graveyard
     * that would let you bring the token back.
     */
    fun move(from: ZoneId, to: ZoneId): Board? {
        if (from == to) return this
        val card = this[from].lastOrNull() ?: return null
        val lifted = withZone(from, this[from].dropLast(1))
        if (card.token && to.kind.isPile) return lifted
        return lifted.place(to, card)
    }

    /** Turns the top card of a zone to a position. */
    fun turn(zone: ZoneId, placement: Placement): Board {
        val existing = this[zone]
        val top = existing.lastOrNull() ?: return this
        return withZone(zone, existing.dropLast(1) + top.copy(placement = placement))
    }

    /** Takes the top card off a zone. */
    fun lift(zone: ZoneId): Board = withZone(zone, this[zone].dropLast(1))

    /**
     * Takes one card out of the middle of a pile.
     *
     * Which is most of what happens to a graveyard: nothing in this game
     * retrieves the card on top, it retrieves the one you want.
     */
    fun removeAt(zone: ZoneId, index: Int): Board? {
        val existing = this[zone]
        if (index !in existing.indices) return null
        return withZone(zone, existing.filterIndexed { i, _ -> i != index })
    }

    fun clear(zone: ZoneId): Board = withZone(zone, emptyList())

    private fun withZone(zone: ZoneId, cards: List<PlacedCard>): Board =
        Board(if (cards.isEmpty()) contents - zone else contents + (zone to cards))

    companion object {
        val EMPTY = Board()
    }
}

/**
 * Where the zones are, in the order they are drawn.
 *
 * Only counts and order live here. Pixels are the drawing layer's problem, and a
 * board that knew its own size in dp would be a board that could not be shown at
 * two sizes.
 */
object BoardLayout {
    const val MONSTER_ZONES = 5
    const val SPELL_TRAP_ZONES = 5

    /** Shared between both players in the real game, and drawn between them. */
    const val EXTRA_MONSTER_ZONES = 2

    val monsterRow: List<ZoneId> = row(ZoneKind.MONSTER, MONSTER_ZONES)
    val spellTrapRow: List<ZoneId> = row(ZoneKind.SPELL_TRAP, SPELL_TRAP_ZONES)
    val extraMonsterRow: List<ZoneId> = row(ZoneKind.EXTRA_MONSTER, EXTRA_MONSTER_ZONES)

    val field = ZoneId(ZoneKind.FIELD)
    val graveyard = ZoneId(ZoneKind.GRAVEYARD)
    val banished = ZoneId(ZoneKind.BANISHED)
    val deck = ZoneId(ZoneKind.DECK)
    val extraDeck = ZoneId(ZoneKind.EXTRA_DECK)

    /** The piles down the side, top to bottom. */
    val piles: List<ZoneId> = listOf(deck, extraDeck, graveyard, banished)

    private fun row(kind: ZoneKind, count: Int) = (0 until count).map { ZoneId(kind, it) }
}
