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

/** Where a card being dragged came from, which decides where it goes back to. */
sealed interface DragOrigin {
    data class Hand(val index: Int) : DragOrigin
    data class OnBoard(val zone: ZoneId) : DragOrigin
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

    /** Which card in hand is picked up, if any. */
    var heldInHand by mutableStateOf<Int?>(null)
        private set

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
        heldInHand = (origin as? DragOrigin.Hand)?.index
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
        if (zone == null) {
            heldInHand = null
            return false
        }

        val landed = when (val origin = active.origin) {
            is DragOrigin.Hand -> play(origin.index, zone, placement)
            is DragOrigin.OnBoard -> move(origin.zone, zone)
        }
        heldInHand = null
        return landed
    }

    fun cancelDrag() {
        drag = null
        trail = DragTrail.EMPTY
        heldInHand = null
    }

    private var history by mutableStateOf<List<TableState>>(emptyList())

    val canUndo: Boolean get() = history.isNotEmpty()

    val board: Board get() = table.board

    /** Shuffles a decklist out and draws an opening hand. */
    fun open(deck: Deck, random: Random = Random.Default) {
        history = emptyList()
        heldInHand = null
        justPlaced = null
        table = TableState.from(deck, random)
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
        heldInHand = null
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

    fun hold(handIndex: Int?) {
        heldInHand = handIndex
    }

    fun undo() {
        val previous = history.lastOrNull() ?: return
        history = history.dropLast(1)
        table = previous
        heldInHand = null
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

