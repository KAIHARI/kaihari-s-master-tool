package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropCommit
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.DropTargets
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.board.toMat
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random

/** A card in the air, and what will happen to it when the finger lets go. */
data class Carry(
    val from: DragOrigin,
    /** The instance being carried, so the resolver can refuse to stack it on itself. */
    val id: Int,
    val at: MatPoint,
    val intent: DropIntent,
    /** True while the gesture means "tuck underneath" rather than "put on top". */
    val attaching: Boolean = false,
    /** Quarter turns applied by a live twist, uncommitted. */
    val quarterTurns: Int = 0,
    /** Turned over in the air, so it is *set* rather than flipped after landing. */
    val faceDown: Boolean = false,
    /** Whether the whole pile is being carried rather than the top card. */
    val whole: Boolean = false,
)

/**
 * A freeform table being played on.
 *
 * All the rules about what is physically possible live in [PlayField], and all
 * the rules about what a release means live in `DropTargets` and `DropCommit`,
 * both of them pure and tested. This holds one field, the stack of fields
 * before it, and whatever is currently in the air.
 */
class PlayState(main: List<CardId>, extra: List<CardId>, seed: Long = 1L) {

    var field by mutableStateOf(dealt(main, extra, seed, OPENING_HAND))
        private set

    var carry by mutableStateOf<Carry?>(null)
        private set

    /** What the last release did, for a moment, so the table can say so. */
    var announcement by mutableStateOf<String?>(null)

    /**
     * What is being looked at without being moved.
     *
     * A held finger on a card asks "what is this", which on a table you answer
     * by tilting it toward you rather than by picking it up and putting it back.
     * Nothing about the field changes, which is why this is not a [move].
     */
    var peeking by mutableStateOf<DragOrigin?>(null)
        private set

    fun peek(at: DragOrigin?) {
        peeking = at
    }

    private val past = ArrayDeque<PlayField>()
    private val future = ArrayDeque<PlayField>()
    private val opening = main to extra

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    fun move(transform: (PlayField) -> PlayField?): Boolean {
        val next = transform(field) ?: return false
        if (next == field) return false
        past.addLast(field)
        while (past.size > HISTORY) past.removeFirst()
        future.clear()
        field = next
        stamp()
        return true
    }

    fun undo() {
        val previous = past.removeLastOrNull() ?: return
        future.addLast(field)
        field = previous
        carry = null
        stamp()
    }

    fun redo() {
        val next = future.removeLastOrNull() ?: return
        past.addLast(field)
        field = next
        carry = null
        stamp()
    }

    private fun stamp() {
        canUndo = past.isNotEmpty()
        canRedo = future.isNotEmpty()
    }

    fun restart(seed: Long = Random.nextLong()) {
        move { dealt(opening.first, opening.second, seed, OPENING_HAND) }
        carry = null
    }

    // ---- carrying a card ------------------------------------------------------

    /** Picks something up. The intent is resolved immediately so the indicator is never blank. */
    fun lift(from: DragOrigin, at: MatPoint, layout: BoardLayout, whole: Boolean = false) {
        val id = when (from) {
            is DragOrigin.Mat -> from.id
            else -> NO_CARD
        }
        carry = Carry(
            from = from,
            id = id,
            at = at,
            intent = DropTargets.resolve(at, id.takeIf { it != NO_CARD }, field, layout, null),
            whole = whole,
            // Picked up as it lay. A set card slid across the mat is still set
            // when it lands, and only a deliberate turn changes that.
            faceDown = from is DragOrigin.Mat && field.placed(from.id)?.faceUp == false,
        )
    }

    /** Moves what is being carried, and re-decides what letting go would do. */
    fun carryTo(at: MatPoint, layout: BoardLayout, attaching: Boolean = false) {
        val held = carry ?: return
        carry = held.copy(
            at = at,
            attaching = attaching,
            intent = DropTargets.resolve(
                point = at,
                dragged = held.id.takeIf { it != NO_CARD },
                field = field,
                layout = layout,
                previous = held.intent,
                attaching = attaching,
            ),
        )
    }

    fun twistCarry(quarterTurns: Int) {
        carry = carry?.copy(quarterTurns = quarterTurns)
    }

    /**
     * Turns the carried card over before it lands.
     *
     * Setting a card is one motion in the hand, not put-down-then-flip, and it
     * is the only way a card ever reaches the mat already face-down — the
     * alternative shows the table a card the player meant to hide.
     */
    fun turnCarry(): Boolean {
        val held = carry ?: return false
        carry = held.copy(faceDown = !held.faceDown)
        return true
    }

    /**
     * Lets go.
     *
     * Whatever the indicator was showing is what happens, because the indicator
     * *is* the intent — there is no second decision made at release time that
     * could disagree with what the user was told.
     */
    fun release(): Boolean {
        val held = carry ?: return false
        carry = null

        val turned = held.quarterTurns % 2 != 0
        val position = when {
            held.faceDown && turned -> CardPosition.FACE_DOWN_DEF
            held.faceDown -> CardPosition.FACE_DOWN_ATK
            turned -> CardPosition.FACE_UP_DEF
            else -> CardPosition.FACE_UP_ATK
        }

        val done = move { DropCommit.commit(it, held.from, held.intent, position) }
        if (done && held.intent !is DropIntent.Free) announcement = held.intent.label
        return done
    }

    fun cancelCarry() {
        carry = null
    }

    /** Where the finger is, in mat fractions, from a point on the stage. */
    fun matPoint(layout: BoardLayout, x: Float, y: Float): MatPoint = layout.toMat(x to y)

    companion object {
        const val OPENING_HAND = 5
        private const val HISTORY = 200

        /** Not a real instance id; nothing on the mat is being carried. */
        const val NO_CARD = -1

        private fun dealt(
            main: List<CardId>,
            extra: List<CardId>,
            seed: Long,
            handSize: Int,
        ): PlayField {
            var field = PlayField.setUp(main, extra).shuffleDeck(seed)
            repeat(handSize) { field = field.draw() ?: return field }
            return field
        }
    }
}
