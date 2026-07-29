package com.kaiharimoto.mastertool.ui.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kaiharimoto.mastertool.core.board.Board
import com.kaiharimoto.mastertool.core.board.DragTrail
import com.kaiharimoto.mastertool.core.board.BoardLayout
import com.kaiharimoto.mastertool.core.board.Placement
import com.kaiharimoto.mastertool.core.board.TableState
import com.kaiharimoto.mastertool.core.board.ZoneId
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Milliseconds since the process started, for reading a gesture.
 *
 * Monotonic rather than wall-clock: the only thing ever asked of it is the
 * difference between two moments a few hundred milliseconds apart, and a clock
 * that can be adjusted underneath that would turn a flick into a hold.
 */
private val started = TimeSource.Monotonic.markNow()

private val SystemClock: () -> Long = { started.elapsedNow().inWholeMilliseconds }

/**
 * Where a card being moved came from, which is what decides what moving it means.
 *
 * One type for four quite different actions — playing, moving, summoning and
 * reviving — because from the player's side they are one action: pick the card
 * up, put it somewhere. Keeping them apart in the model and together at the
 * fingertips is the whole trick.
 */
sealed interface DragOrigin {
    data class Hand(val index: Int) : DragOrigin
    data class OnBoard(val zone: ZoneId) : DragOrigin

    /** The Extra deck, by position in it. */
    data class Extra(val index: Int) : DragOrigin

    /** A pile on the board — graveyard, banished — by position in it. */
    data class Pile(val zone: ZoneId, val index: Int) : DragOrigin
}

/** The four stacks that are worth looking through rather than only counting. */
enum class Pile(val label: String, val shortLabel: String) {
    DECK("Deck", "DECK"),
    EXTRA("Extra deck", "EXTRA"),
    GRAVEYARD("Graveyard", "GY"),
    BANISHED("Banished", "BANISH"),
}

data class BoardDrag(val origin: DragOrigin, val id: CardId, val faceDown: Boolean = false)

/**
 * A board you can put cards on, and take them back off.
 *
 * The reason this exists is the question the deck builder cannot answer: not
 * "does the list open" but "does the opening *do* anything" — whether five cards
 * actually assemble into a board. Nobody works that out by reading a decklist;
 * they lay it out.
 *
 * Undo is a stack of whole tables rather than a stack of inverse operations,
 * which is what [TableState] being one value buys. The original needed a command
 * factory for this; a table is a few dozen cards and copying one is free.
 */
class SandboxState(private val nowMs: () -> Long = SystemClock) {

    var table by mutableStateOf(TableState())
        private set

    /**
     * What is picked up, if anything.
     *
     * Tapping picks up and tapping a zone puts down, which is the whole
     * interaction for anybody not using a mouse — and it is the same held card
     * whether it came from the hand, the Extra deck or the graveyard.
     */
    var held by mutableStateOf<DragOrigin?>(null)
        private set

    /** Which card in the fan is picked up, if the held card is one of them. */
    val heldInHand: Int? get() = (held as? DragOrigin.Hand)?.index

    /** Which stack is open for looking through, if any. */
    var openPile by mutableStateOf<Pile?>(null)

    /** Which zone the last card landed in, for a moment of confirmation. */
    var justPlaced by mutableStateOf<ZoneId?>(null)
        private set

    // ---- dragging ----------------------------------------------------------

    /** The card in the air, if one is. */
    var drag by mutableStateOf<BoardDrag?>(null)
        private set

    /** Where it is, in the board's own coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** The zone the card would land in, for lighting it up before release. */
    val over: ZoneId? get() = if (drag == null) null else zoneAt(pointer)

    private var trail = DragTrail.EMPTY
    private val zoneBounds = mutableMapOf<ZoneId, Rect>()

    fun registerZone(zone: ZoneId, bounds: Rect) {
        zoneBounds[zone] = bounds
    }

    fun zoneAt(point: Offset): ZoneId? =
        zoneBounds.entries.firstOrNull { it.value.contains(point) }?.key

    fun startDrag(origin: DragOrigin, id: CardId, at: Offset, faceDown: Boolean = false) {
        drag = BoardDrag(origin, id, faceDown)
        pointer = at
        trail = DragTrail.EMPTY.at(at.x, at.y, nowMs())
        held = origin
    }

    fun dragBy(delta: Offset) {
        if (drag == null) return
        pointer += delta
        trail = trail.at(pointer.x, pointer.y, nowMs())
    }

    /**
     * Lets go.
     *
     * The gesture decides the position — see `DragTrail` — so nothing here has
     * to ask, and a card that lands the wrong way round is one tap from being
     * turned. Released over nothing, or over a zone that will not take it, the
     * card goes back where it came from: a drag that fails should cost what a
     * drag that was never started cost.
     */
    fun endDrag(): Boolean {
        val active = drag ?: return false
        val zone = zoneAt(pointer)
        val placement = trail.placementOnRelease(nowMs())
        drag = null
        trail = DragTrail.EMPTY
        held = null
        if (zone == null) return false
        return put(active.origin, zone, placement)
    }

    fun cancelDrag() {
        drag = null
        trail = DragTrail.EMPTY
        held = null
    }

    /**
     * Puts down whatever is picked up, for the tap-tap way of doing it.
     *
     * The gesture cannot say anything here — there was no gesture — so it lands
     * in attack and one tap turns it, which is the honest fallback rather than
     * an invented default.
     */
    fun placeHeld(zone: ZoneId, placement: Placement = Placement.ATTACK): Boolean {
        val origin = held ?: return false
        val landed = put(origin, zone, placement)
        if (landed) held = null
        return landed
    }

    private fun put(origin: DragOrigin, zone: ZoneId, placement: Placement): Boolean =
        when (origin) {
            is DragOrigin.Hand -> play(origin.index, zone, placement)
            is DragOrigin.OnBoard -> move(origin.zone, zone)
            is DragOrigin.Extra -> summon(origin.index, zone, placement)
            is DragOrigin.Pile -> raise(origin.zone, origin.index, zone, placement)
        }

    private var history by mutableStateOf<List<TableState>>(emptyList())

    /**
     * The list this table was dealt from, so it can be dealt again.
     *
     * Kept here rather than asked for again, because "shuffle up and go again"
     * is the most common thing to want on a board and walking back to the deck
     * builder to ask for it would be absurd.
     */
    private var dealtFrom by mutableStateOf(Deck.EMPTY)

    val canRedeal: Boolean get() = dealtFrom.main.isNotEmpty()

    /** Shuffles up and deals again from the same list. */
    fun redeal() {
        if (!canRedeal) return
        open(dealtFrom)
    }

    val canUndo: Boolean get() = history.isNotEmpty()

    val board: Board get() = table.board

    /**
     * Shuffles a decklist out onto the table.
     *
     * [hand] is the opening to lay it out around, for the moment a test hand
     * turns out to be interesting and the question stops being "would I keep
     * this" and becomes "what does it do". Null deals a fresh one.
     */
    fun open(deck: Deck, hand: List<CardId>? = null, random: Random = Random.Default) {
        dealtFrom = deck
        history = emptyList()
        held = null
        openPile = null
        justPlaced = null
        table = if (hand == null) {
            TableState.from(deck, random)
        } else {
            TableState.holding(deck, hand, random)
        }
    }

    fun draw() = change { it.draw() }

    /**
     * Puts the held card down.
     *
     * The gesture decides [placement] — see `DropGesture`. Returns whether it
     * went, so a rejected drop can be shown rather than silently ignored.
     */
    fun play(handIndex: Int, zone: ZoneId, placement: Placement): Boolean {
        val next = table.play(handIndex, zone, placement) ?: return false
        push()
        table = next
        held = null
        justPlaced = zone
        return true
    }

    /** Moves what is already on the board. */
    fun move(from: ZoneId, to: ZoneId): Boolean {
        val next = table.board.move(from, to) ?: return false
        if (next == table.board) return false
        push()
        table = table.copy(board = next)
        justPlaced = to
        return true
    }

    /** Turns a card where it lies: attack, defence, face-down, round again. */
    fun turn(zone: ZoneId) {
        val top = table.board[zone].lastOrNull() ?: return
        change { it.copy(board = it.board.turn(zone, top.placement.turned())) }
    }

    fun sendToGraveyard(zone: ZoneId) {
        move(zone, BoardLayout.graveyard)
    }

    fun returnToHand(zone: ZoneId) = change { it.toHand(zone) }

    fun discard(handIndex: Int) = change { it.sendFromHand(handIndex, BoardLayout.graveyard) }

    fun hold(origin: DragOrigin?) {
        held = origin
    }

    // ---- the stacks --------------------------------------------------------

    /**
     * What is in a stack, in the order it is worth reading.
     *
     * The deck reads from the top down, because the top is the only part of it
     * anybody has an opinion about.
     */
    fun contentsOf(pile: Pile): List<CardId> = when (pile) {
        Pile.DECK -> table.library.asReversed()
        Pile.EXTRA -> table.extra
        Pile.GRAVEYARD -> table.board[BoardLayout.graveyard].map { it.id }
        Pile.BANISHED -> table.board[BoardLayout.banished].map { it.id }
    }

    /** Picks a card out of an open stack, ready to be put somewhere. */
    fun holdFromPile(pile: Pile, index: Int) {
        held = when (pile) {
            Pile.EXTRA -> DragOrigin.Extra(index)
            Pile.GRAVEYARD -> DragOrigin.Pile(BoardLayout.graveyard, index)
            Pile.BANISHED -> DragOrigin.Pile(BoardLayout.banished, index)
            // Nothing is summoned straight out of the deck often enough to be
            // worth a fifth case; searching it to hand and playing it is the
            // same two taps and one fewer thing to explain.
            Pile.DECK -> null
        }
        if (held != null) openPile = null
    }

    /** Takes a card out of an open stack and into the hand. */
    fun takeToHand(pile: Pile, index: Int): Boolean {
        val before = table
        when (pile) {
            Pile.DECK -> change { it.searchLibrary(it.library.size - 1 - index) }
            Pile.EXTRA -> return false
            Pile.GRAVEYARD -> change { it.retrieve(BoardLayout.graveyard, index) }
            Pile.BANISHED -> change { it.retrieve(BoardLayout.banished, index) }
        }
        return table != before
    }

    fun summon(extraIndex: Int, zone: ZoneId, placement: Placement): Boolean {
        val next = table.summon(extraIndex, zone, placement) ?: return false
        push()
        table = next
        justPlaced = zone
        return true
    }

    fun raise(from: ZoneId, index: Int, to: ZoneId, placement: Placement): Boolean {
        val next = table.raise(from, index, to, placement) ?: return false
        push()
        table = next
        justPlaced = to
        return true
    }

    fun undo() {
        val previous = history.lastOrNull() ?: return
        history = history.dropLast(1)
        table = previous
        held = null
        justPlaced = null
    }

    /** Sweeps the table, keeping the decklist that was dealt from. */
    fun clearBoard() = change { it.copy(board = Board.EMPTY) }

    private fun push() {
        // Bounded, because a long session is a long session and none of this is
        // worth a megabyte. Fifty is far past what anybody reaches back through.
        history = (history + table).takeLast(UNDO_DEPTH)
    }

    private fun change(transform: (TableState) -> TableState?) {
        val next = transform(table) ?: return
        if (next == table) return
        push()
        table = next
        justPlaced = null
    }

    private companion object {
        const val UNDO_DEPTH = 50
    }
}

