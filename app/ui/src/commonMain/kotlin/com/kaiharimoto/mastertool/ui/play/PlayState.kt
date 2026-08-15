package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropCommit
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.DropTargets
import com.kaiharimoto.mastertool.core.board.FanHome
import com.kaiharimoto.mastertool.core.board.fanSource
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.board.SetPosition
import com.kaiharimoto.mastertool.core.board.toMat
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.HandFan
import com.kaiharimoto.mastertool.core.layout.HandRow
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlin.random.Random

/** A card in the air, and what will happen to it when the finger lets go. */
data class Carry(
    val from: DragOrigin,
    /** The instance being carried, so the resolver can refuse to stack it on itself. */
    val id: Int,
    /**
     * Which physical card was picked up, whatever it was picked up *out of*.
     *
     * [id] is the mat's instance and is [PlayState.NO_CARD] for anything that
     * was not already on the mat; this is the card itself, and it is what makes
     * the release able to find the card again when the board has moved under
     * this gesture. See `PlayField.rebase` for why that matters at all.
     */
    val holding: Int = PlayState.NO_CARD,
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
     * Where this card was lying in the open spread it was taken out of, and
     * whether it has left — null unless it came out of a fan that is still open.
     *
     * The *place* is fixed at the lift, because it is a fact about where the
     * card was. The screen works it out, since that half of the question is
     * projection: a spread floats above the felt, so the slot's own coordinates
     * are flattened back down to the felt before anything is compared to them.
     * That is the same space [landing] is in — the footprint of a thing drawn in
     * the air — which is what keeps "put it back" aimed at the gap you can see
     * rather than at the finger under it.
     *
     * The *latch* is not fixed: [FanHome.seeing] is fed every landing, so the
     * gap only becomes a target once the card has actually been taken out of it.
     * See [FanHome] for why a slot alone could not mean "put it back".
     */
    val home: FanHome? = null,
    /**
     * Where this card would land if it were let go now, which is **not** [at].
     *
     * [at] is where the finger is and where the card is posed from; a card in
     * the air is drawn up-table of that by the perspective divide, because it is
     * a card in the air. `CarryHeight` has the measurement — most of a card
     * height for one coming out of the hand — and the whole of kai's "a set
     * monster lands in attack position" was that the drop was resolved at the
     * finger while the card he was aiming was drawn here.
     *
     * Stored rather than derived at the release for the reason [intent] is
     * stored: the indicator he is reading and the move he gets have to be the
     * same answer, and an answer computed twice is two answers.
     */
    val landing: MatPoint = at,
) {
    /** Whether the card is lying sideways, for the pose that draws it. */
    val turned: Boolean get() =
        position == CardPosition.FACE_UP_DEF || position == CardPosition.FACE_DOWN_DEF
}

/**
 * A card's effect being announced.
 *
 * [count] is a running number rather than a timestamp, and that is deliberate:
 * a clock in state that the renderer keys off is a clock two runs of `:studio`
 * disagree about, and the whole claim of that tool is that they do not. What the
 * stage needs is only "is this a *new* declaration", and a counter answers that
 * exactly, at no cost, from inside a pure value.
 */
data class Declaration(val id: Int, val cardId: CardId, val count: Int)

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

    /**
     * What is in the air, by the hand holding it.
     *
     * A map rather than one card, because kai's table is played with two hands:
     * *"I should be able to use one finger on two cards to simultaneously drag
     * both"*, and then *"let's expand it to 10 fingers"*. The key is `MatDesk`'s
     * lane — the gesture, not the finger — so a two-finger twist is still one
     * entry and two one-finger drags are two.
     *
     * A map rather than an array of ten nullable slots, which is what the shape
     * would be if the cap were the thing it was sized against. It is not: the
     * cap is `MatDesk.MAX_LANES` and nothing here knows it. The one thing every
     * reader actually wants is [carried], and it is a list.
     */
    var carries by mutableStateOf<Map<Int, Carry>>(emptyMap())
        private set

    /** Everything in the air, for the poses and the drop indicators. */
    val carried: List<Carry> get() = carries.values.toList()

    /**
     * The hand exactly as the stage draws it: cards in the air have no place,
     * and one place is held open wherever a card is about to land.
     *
     * Here rather than at the four sites that need it — the pose, the hit box,
     * the insert index and the indicator — because those four *were* rebuilding
     * it separately, and two of them disagreed. The renderer drew a place for
     * the card in the air; the resolver measured as though the row had closed up
     * behind it. Half a step apart, every time, which is 0.37 of a card width at
     * the shipped tuning and most of the width of the gap being pointed at.
     *
     * Read by identity rather than by `Carry.from`, for the same reason the
     * release rebases: an index into the hand is a memory of the board at the
     * lift, and with ten lanes the board moves under it.
     */
    val handRow: HandRow get() = HandFan.row(
        count = field.hand.size,
        lifted = carries.values.mapNotNullTo(mutableSetOf()) { held ->
            field.hand.indexOfFirst { it.instanceId == held.holding }.takeIf { it >= 0 }
        },
        opening = carries.values.mapNotNull { (it.intent as? DropIntent.Hand)?.at },
    )

    fun carryIn(lane: Int): Carry? = carries[lane]

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
     * The card whose effect was just declared, and when.
     *
     * "I am using this" — the thing you say out loud across a table, and the one
     * thing this stage had no way to say at all. Not a [move]: nothing about the
     * board changed, so it must not land on the undo stack, exactly as [peeking]
     * and [fanned] do not. Undoing a declaration would be undoing having spoken.
     *
     * The stamp is what makes declaring the *same* card twice in a row visible.
     * A declaration is a moment rather than a state, and combo lines are full of
     * a card being used, chained to, and used again; without a counter the
     * second one would set the same value, change nothing, and the table would
     * silently answer only the first.
     */
    var declared by mutableStateOf<Declaration?>(null)
        private set

    /**
     * Says a card's effect out loud, and whether there was a card to say it about.
     *
     * False when the origin names nothing — a gesture is a memory of what the
     * press landed on and the board can change underneath it — so the caller
     * knows whether to make a sound.
     */
    fun declare(what: DragOrigin): Boolean {
        val card = field.cardAt(what) ?: return false
        declared = Declaration(
            id = card.instanceId,
            cardId = card.cardId,
            count = (declared?.count ?: 0) + 1,
        )
        return true
    }

    /** Puts the declared card back down, once the table has finished saying so. */
    fun clearDeclaration() {
        declared = null
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
        carries = emptyMap()
        stamp()
    }

    fun redo() {
        val next = future.removeLastOrNull() ?: return
        past.addLast(field)
        field = next
        carries = emptyMap()
        stamp()
    }

    private fun stamp() {
        canUndo = past.isNotEmpty()
        canRedo = future.isNotEmpty()
    }

    fun restart(seed: Long = Random.nextLong()) {
        move { dealt(opening.first, opening.second, seed, OPENING_HAND) }
        carries = emptyMap()
    }

    // ---- carrying a card ------------------------------------------------------

    /**
     * Picks something up. The intent is resolved immediately so the indicator is
     * never blank.
     *
     * Every one of these takes a [lane] — which hand is doing it — and none of
     * them takes a default. A lane threaded by hand through every call is
     * tedious exactly once; a lane with a default is a call site that silently
     * moves the *other* hand's card, and there is no test that would catch it
     * because both hands are holding perfectly valid cards.
     */
    fun lift(
        lane: Int,
        from: DragOrigin,
        at: MatPoint,
        layout: BoardLayout,
        /**
         * Where letting go here would put it. See [Carry.landing]; it defaults
         * to [at] so a caller with no camera — every test in this file, and the
         * keyboard — gets the flat table it always had.
         */
        landing: MatPoint = at,
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
        carries = carries + (lane to Carry(
            from = from,
            id = id,
            holding = field.cardAt(from)?.instanceId ?: NO_CARD,
            at = at,
            landing = landing,
            // Deliberately without the home: on the frame a card is lifted it is
            // still exactly where it came from. That used to be the only thing
            // stopping every drag out of a spread opening on "Put back", and it
            // is now belt and braces — `FanHome` is not armed until the card has
            // been carried a card's width clear of the slot.
            intent = DropTargets.resolve(landing, id.takeIf { it != NO_CARD }, field, layout, null),
            // Picked up as it lay, unless the gesture itself said otherwise. A
            // set card slid across the mat is still set when it lands, and only
            // a deliberate turn — the two-finger tap, or the two-finger drag
            // that started this — changes that.
            faceDown = faceDown ||
                (from is DragOrigin.Mat && field.placed(from.id)?.faceUp == false),
            // And picked up lying the way it was lying, which is the other half
            // of the same sentence and was missing.
            //
            // `SetPosition` solves the landing from `faceDown` and `turned`
            // alone, so a carry that started at zero quarter turns says "upright"
            // about a card that has been sideways since it was summoned. That
            // did not matter for as long as `DropCommit` threw the answer away
            // for a card already on the mat — the two bugs cancelled, and fixing
            // either one alone would have made a monster in defence stand up
            // every time it was nudged.
            quarterTurns = if (from is DragOrigin.Mat && field.placed(from.id)?.turned == true) 1 else 0,
            home = cameOutOf?.let { FanHome(it) },
        ).settled())
    }

    /** Moves what is being carried, and re-decides what letting go would do. */
    fun carryTo(
        lane: Int,
        at: MatPoint,
        layout: BoardLayout,
        /** Where letting go here would put it. See [Carry.landing]. */
        landing: MatPoint = at,
        attaching: Boolean = false,
        handStep: Float = StageTuning.DEFAULT.hand.stepFraction,
    ) {
        val held = carries[lane] ?: return
        // Fed the landing before it is resolved against, so the frame the card
        // first gets clear of its own slot is the frame "Put back" becomes
        // reachable — rather than one frame later, which is a target that
        // appears behind you.
        val home = held.home?.seeing(landing, layout)
        carries = carries + (lane to held.copy(
            at = at,
            landing = landing,
            attaching = attaching,
            home = home,
            intent = DropTargets.resolve(
                point = landing,
                dragged = held.id.takeIf { it != NO_CARD },
                field = field,
                layout = layout,
                previous = held.intent,
                attaching = attaching,
                home = home,
                // The row the user can actually see, so the gap the finger is
                // over is counted against it rather than against a row of
                // evenly spaced cards that is never drawn.
                hand = handRow,
                handStep = handStep,
            ),
        ).settled())
    }

    fun twistCarry(lane: Int, quarterTurns: Int) {
        val held = carries[lane] ?: return
        carries = carries + (lane to held.copy(quarterTurns = quarterTurns).settled())
    }

    /**
     * Turns the carried card over before it lands.
     *
     * Setting a card is one motion in the hand, not put-down-then-flip, and it
     * is the only way a card ever reaches the mat already face-down — the
     * alternative shows the table a card the player meant to hide.
     */
    fun turnCarry(lane: Int): Boolean {
        val held = carries[lane] ?: return false
        carries = carries + (lane to held.copy(faceDown = !held.faceDown).settled())
        return true
    }

    /**
     * Lets go.
     *
     * Whatever the indicator was showing is what happens, because the indicator
     * *is* the intent — there is no second decision made at release time that
     * could disagree with what the user was told.
     */
    fun release(lane: Int): Boolean {
        val held = carries[lane] ?: return false
        carries = carries - lane

        // The other hand may have renumbered the pile this one is holding an
        // index into, so the origin is asked where its card is *now* rather than
        // taken at its word. `PlayField.rebase` answers null only when the card
        // has genuinely left the place it came from, and then putting it back is
        // still the only safe answer — dropping whatever is at that index now
        // would be moving a card nobody touched.
        val from = if (held.holding == NO_CARD) {
            held.from
        } else {
            field.rebase(held.from, held.holding) ?: return false
        }

        val done = move { DropCommit.commit(it, from, held.intent, held.position) }
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

    /**
     * Set a card that is already lying on the field, where it lies.
     *
     * The **pointer idiom for setting**, and it did not exist. `MatGuide` has
     * advertised *"Set a card face-down → right-click it, then Turn over"* since
     * the set gesture shipped, and "Turn over" is `PlayField.flip`, which
     * deliberately keeps a card upright — so on a mouse, setting a monster
     * produced a face-down card standing up, every time, by design. The touch
     * idiom (two fingers) had a different bug with the same symptom; fixing that
     * one alone would have left this one looking like it.
     *
     * The zone decides, exactly as it does for the gesture: the card's own point
     * is resolved through the same `DropTargets` the drag uses, so a card in a
     * monster zone lies down and one in a spell/trap zone stands up, and neither
     * answer is written twice.
     */
    fun setDown(id: Int, layout: BoardLayout) {
        val placed = field.placed(id) ?: return
        val position = SetPosition.of(
            faceDown = true,
            // Not the card's current rotation: "set" is a statement about how it
            // should end up, and the zone is what says so. A monster already
            // sideways and face up is being *set*, not merely turned over.
            turned = false,
            intent = DropTargets.resolve(placed.at, id, field, layout, null),
            monster = isMonster(placed.card.cardId),
        )
        move { it.setPosition(id, position) }
    }

    fun cancelCarry(lane: Int) {
        carries = carries - lane
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
