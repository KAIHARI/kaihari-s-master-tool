package com.kaiharimoto.mastertool.ui.table

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.board.BoardState
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.FieldZone
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random

/**
 * The card currently picked up, if any.
 *
 * Picking a card up and then choosing where it goes is two taps rather than one
 * drag, and that is deliberate for now. The board is seventeen places a card
 * can land, several of them a thumb's width apart, and a drag that misses is a
 * card somewhere you did not put it — the one thing this app refuses to do
 * elsewhere. Two taps are also the only gesture that works identically under a
 * finger and a mouse, which is the standing rule for every gesture here.
 */
sealed interface Held {
    data class InHand(val index: Int) : Held
    data class OnField(val zone: FieldZone) : Held
    data class InPile(val pile: BoardSlot, val index: Int) : Held

    /** Where it came from, for the "put it back" tap. */
    val origin: BoardSlot?
        get() = when (this) {
            is OnField -> BoardSlot.Zone(zone)
            is InPile -> pile
            is InHand -> null
        }
}

/**
 * One side of a duel table, being played with.
 *
 * All the rules of what is *physically* possible already live in `BoardState`,
 * where they are pure and tested; this holds one of those and a stack of the
 * ones before it. Undo costs nothing because every move returns a whole new
 * board — the same reason the deck builder's undo is a list of decks.
 *
 * What it deliberately does not hold is a rules engine. A move that is
 * geometrically impossible — into an occupied zone, off an empty deck — comes
 * back null and nothing happens. A move that is *illegal* happens, because a
 * table lets you do illegal things and a player is the one who knows.
 */
class DuelTableState(
    main: List<CardId>,
    extra: List<CardId>,
    seed: Long = 1L,
    handSize: Int = OPENING_HAND,
) {

    /**
     * Opened, not empty.
     *
     * A table you have to tap the deck five times to sit down at is a table
     * nobody goldfishes on, and goldfishing is the whole reason to have one.
     */
    var board by mutableStateOf(dealt(main, extra, seed, handSize))
        private set

    var held by mutableStateOf<Held?>(null)
        private set

    /** The pile whose contents are open for reading, if any. */
    var openPile by mutableStateOf<BoardSlot?>(null)

    private val past = ArrayDeque<BoardState>()
    private val future = ArrayDeque<BoardState>()

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val opening = main to extra

    /**
     * Runs a move, or does nothing if the board says it cannot happen.
     *
     * Returns whether anything changed, so the caller knows whether to make a
     * sound — a click for a move that did not happen is worse than silence.
     */
    fun move(transform: (BoardState) -> BoardState?): Boolean {
        val next = transform(board) ?: return false
        if (next == board) return false
        past.addLast(board)
        while (past.size > HISTORY) past.removeFirst()
        future.clear()
        board = next
        stamp()
        return true
    }

    fun undo() {
        val previous = past.removeLastOrNull() ?: return
        future.addLast(board)
        board = previous
        held = null
        stamp()
    }

    fun redo() {
        val next = future.removeLastOrNull() ?: return
        past.addLast(board)
        board = next
        held = null
        stamp()
    }

    private fun stamp() {
        canUndo = past.isNotEmpty()
        canRedo = future.isNotEmpty()
    }

    /**
     * A fresh shuffle and a fresh opener. Undoable like anything else.
     *
     * Which is the point of it being a move rather than a reconstruction: you
     * can deal ten hands looking for the one you want to practise and still
     * walk back to the one three deals ago that was interesting.
     */
    fun restart(handSize: Int = OPENING_HAND, seed: Long = Random.nextLong()) {
        move { dealt(opening.first, opening.second, seed, handSize) }
        held = null
    }

    // ---- picking a card up and putting it down ------------------------------

    fun pick(what: Held?) {
        held = if (held == what) null else what
        if (held != null) openPile = null
    }

    fun drop() {
        held = null
    }

    /**
     * Puts the held card in [slot], if that means anything.
     *
     * Every combination that is a real move is one line here, and every one
     * that is not falls through to false — dropping a graveyard card onto the
     * graveyard, playing a hand card into the deck pile, and so on.
     */
    fun place(slot: BoardSlot, position: CardPosition = CardPosition.FACE_UP_ATK): Boolean {
        val what = held ?: return false
        val target = (slot as? BoardSlot.Zone)?.zone

        val done = when {
            // Onto the field, from wherever it was.
            target != null -> when (what) {
                is Held.InHand -> move { it.playFromHand(what.index, target, position) }
                is Held.OnField -> move { it.moveOnField(what.zone, target) }
                is Held.InPile -> when (what.pile) {
                    BoardSlot.ExtraDeck -> move { it.playFromExtra(what.index, target, position) }
                    BoardSlot.Graveyard ->
                        move { it.playFromGraveyard(what.index, target, position) }
                    else -> false
                }
            }

            // Off the field, into a pile.
            what is Held.OnField -> when (slot) {
                BoardSlot.Graveyard -> move { it.sendToGraveyard(what.zone) }
                BoardSlot.Banished -> move { it.banishFrom(what.zone) }
                else -> false
            }

            // Out of the hand, back onto the deck.
            what is Held.InHand && slot == BoardSlot.Deck ->
                move { it.handToDeckTop(what.index) }

            else -> false
        }

        if (done) held = null
        return done
    }

    /** True when [slot] is somewhere the held card could actually go. */
    fun accepts(slot: BoardSlot): Boolean {
        val what = held ?: return false
        return when (slot) {
            is BoardSlot.Zone -> board.at(slot.zone) == null &&
                !(what is Held.OnField && what.zone == slot.zone)
            BoardSlot.Graveyard, BoardSlot.Banished -> what is Held.OnField
            BoardSlot.Deck -> what is Held.InHand
            else -> false
        }
    }

    companion object {
        /** Going first. The other five-or-six decision belongs to the goal sheet. */
        const val OPENING_HAND = 5

        /**
         * Deep, because a combo is fifteen moves and the reason to undo one is
         * usually that you noticed something several steps back.
         */
        private const val HISTORY = 200

        private fun dealt(
            main: List<CardId>,
            extra: List<CardId>,
            seed: Long,
            handSize: Int,
        ): BoardState {
            var board = BoardState.setUp(main, extra).shuffleDeck(seed)
            repeat(handSize) { board = board.draw() ?: return board }
            return board
        }
    }
}
