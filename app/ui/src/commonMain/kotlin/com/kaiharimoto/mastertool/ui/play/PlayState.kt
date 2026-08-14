package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropCommit
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.DropTargets
import com.kaiharimoto.mastertool.core.board.fanSource
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.board.SetPosition
import com.kaiharimoto.mastertool.core.board.toMat
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.tune.StageTuning
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
    /**
     * How the card will be lying when it lands.
     *
     * Solved by `SetPosition` every time anything about the carry changes, and
     * held here for exactly the reason [intent] is: the pose in the air and the
     * release both read it, so what you can see cannot disagree with what
     * happens. A set monster turns sideways *while you are still holding it*,
     * as it crosses into a monster zone, which is the honest way to say that the
     * table knows what kind of card it is.
     */
    val position: CardPosition = CardPosition.FACE_UP_ATK,
    /**
     * Where this card was lying in the open spread it was taken out of, on the
     * felt — null unless it came out of a fan that is still open.
     *
     * Fixed at the lift and not touched again, because it is a fact about where
     * the card *was*. The screen works it out, since it is the half of the
     * question that is projection: a spread floats above the felt, so the slot's
     * own coordinates have to be flattened back down to the plane the finger is
     * unprojected onto before the two can be compared at all.
     */
    val cameOutOf: MatPoint? = null,
) {
    /** Whether the card is lying sideways, for the pose that draws it. */
    val turned: Boolean get() =
        position == CardPosition.FACE_UP_DEF || position == CardPosition.FACE_DOWN_DEF
}

/**
 * A freeform table being played on.
 *
 * All the rules about what is physically possible live in [PlayField], and all
 * the rules about what a release means live in `DropTargets` and `DropCommit`,
 * both of them pure and tested. This holds one field, the stack of fields
 * before it, and whatever is currently in the air.
 */
class PlayState(
    main: List<CardId>,
    extra: List<CardId>,
    seed: Long = 1L,
    /**
     * Whether a passcode names a monster, asked of whatever knows.
     *
     * A lambda rather than the card index itself, because the one question this
     * class has about a card's *identity* is which way up a set copy of it lies,
     * and handing a table the whole database to answer it would put the card
     * database in the undo stack's line of sight. Defaulted to "no idea", which
     * is what a test with no index has and a real answer in its own right.
     */
    private val isMonster: (CardId) -> Boolean? = { null },
) {

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

    /**
     * What is spread out to be searched, if anything.
     *
     * UI state and not a move, exactly like [peeking]: spreading a pile out to
     * look at it changes nothing about the game, so it must not land on the undo
     * stack. Undoing a search would be undoing having looked.
     *
     * A `DragOrigin` rather than a pile, and that is what makes a stack on the
     * mat searchable by the same feature: whatever the press landed on is what
     * gets spread. It holds no geometry, because where the cards go is
     * `PileFan`'s answer and depends on a board size this class has never needed
     * to know.
     */
    var fanned by mutableStateOf<DragOrigin?>(null)
        private set

    /** Spreads something out. Opening one closes whichever was open. */
    fun fan(what: DragOrigin?) {
        val source = what?.fanSource
        if (source == fanned) return
        if (fanned != null) closeFan()
        fanned = source
    }

    /**
     * Squares it back up, and says whether that shuffled anything.
     *
     * **Closing the deck's fan shuffles the deck**, because you just searched
     * it. It is what the rules say, it costs nothing, and the alternative is a
     * tool that teaches you a habit which loses games. The graveyard and the
     * banished pile are ordered and public, so they close as they were; the
     * extra deck is public too and has a button for when you want one; and a
     * stack on the mat keeps its order, because the order of a stack on a table
     * is a thing you arranged on purpose.
     */
    fun closeFan(): Boolean {
        val what = fanned ?: return false
        fanned = null
        return what == DragOrigin.Pile(BoardSlot.Deck, 0) && shuffle(BoardSlot.Deck)
    }

    /**
     * Shuffles a pile, and says whether anything moved.
     *
     * The seed comes off this table's own stream rather than being derived from
     * the position. It used to be `turn * 31 + deck.size`, written out at three
     * separate call sites — a pure function of the board, so shuffling twice in
     * a row without drawing produced *the identical permutation*, `move` saw a
     * field equal to the one before it, and the second shuffle silently did
     * nothing at all.
     */
    fun shuffle(slot: BoardSlot): Boolean = when (slot) {
        BoardSlot.Deck -> move { it.shuffleDeck(entropy.nextLong()) }
        BoardSlot.ExtraDeck -> move { it.shuffleExtraDeck(entropy.nextLong()) }
        else -> false
    }

    /**
     * Where every shuffle after the deal comes from.
     *
     * Seeded off the session's own seed, so a table opened with a known seed
     * plays out identically twice — which is what makes a reported opening hand
     * something anybody else can reproduce.
     */
    private val entropy = Random(seed)

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
    fun lift(
        from: DragOrigin,
        at: MatPoint,
        layout: BoardLayout,
        cameOutOf: MatPoint? = null,
        /**
         * True when the gesture itself was a set — two fingers — so the card
         * comes up already turned over rather than being flipped after landing.
         */
        faceDown: Boolean = false,
    ) {
        val id = when (from) {
            is DragOrigin.Mat -> from.id
            else -> NO_CARD
        }
        carry = Carry(
            from = from,
            id = id,
            at = at,
            // Deliberately without [cameOutOf]: on the frame a card is lifted it
            // is still exactly where it came from, so passing it here would open
            // every drag out of a spread already reading "Put back".
            intent = DropTargets.resolve(at, id.takeIf { it != NO_CARD }, field, layout, null),
            // Picked up as it lay, unless the gesture itself said otherwise. A
            // set card slid across the mat is still set when it lands, and only
            // a deliberate turn — the two-finger tap, or the two-finger drag
            // that started this — changes that.
            faceDown = faceDown ||
                (from is DragOrigin.Mat && field.placed(from.id)?.faceUp == false),
            cameOutOf = cameOutOf,
        ).settled()
    }

    /** Moves what is being carried, and re-decides what letting go would do. */
    fun carryTo(
        at: MatPoint,
        layout: BoardLayout,
        attaching: Boolean = false,
        handStep: Float = StageTuning.DEFAULT.hand.stepFraction,
    ) {
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
                cameOutOf = held.cameOutOf,
                // Which card of the hand is the one in the air, so the gaps are
                // counted against the row the user can actually see.
                fromHand = (held.from as? DragOrigin.Hand)?.index,
                handStep = handStep,
            ),
        ).settled()
    }

    fun twistCarry(quarterTurns: Int) {
        carry = carry?.copy(quarterTurns = quarterTurns)?.settled()
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
        carry = held.copy(faceDown = !held.faceDown).settled()
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

        val done = move { DropCommit.commit(it, held.from, held.intent, held.position) }
        if (done && held.intent !is DropIntent.Free) announcement = held.intent.label
        return done
    }

    /**
     * The carry with its landing position re-solved.
     *
     * Called after every change to one, so [Carry.position] is to which way up
     * the card lands exactly what [Carry.intent] is to where it lands: decided
     * continuously while it is in the air, drawn from the same value that
     * commits it, and therefore unable to promise one thing and do another.
     */
    private fun Carry.settled(): Carry = copy(
        position = SetPosition.of(
            faceDown = faceDown,
            turned = quarterTurns % 2 != 0,
            intent = intent,
            monster = monsterAt(this),
        ),
    )

    /**
     * Whether the card in the air is a monster, as far as anybody here knows.
     *
     * Null when the card is not in the index — a passcode the database has never
     * heard of, which happens — and null is a real answer rather than a missing
     * one: [SetPosition] sets an unknown card upright, the way it sets a spell,
     * instead of guessing at a defence position for a card it cannot identify.
     */
    private fun monsterAt(held: Carry): Boolean? =
        field.cardAt(held.from)?.let { isMonster(it.cardId) }

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
