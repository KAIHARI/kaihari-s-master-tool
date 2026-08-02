package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.board.toMat
import com.kaiharimoto.mastertool.core.board.toPixels
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.mat.MatEvent
import com.kaiharimoto.mastertool.core.mat.MatGestureMachine
import com.kaiharimoto.mastertool.core.mat.Touch
import com.kaiharimoto.mastertool.core.mat.TouchFrame
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.ui.fx.Feedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import kotlin.math.abs

/**
 * The only part of the gesture system that knows what Compose is.
 *
 * It does three things and nothing else: turn a `PointerEvent` into a
 * [TouchFrame] in the mat's own coordinates, hand that to the arbiter, and
 * carry out whatever the arbiter says happened. Every rule about what a gesture
 * *means* lives in `MatGestureMachine`, in core, where it is tested — because
 * disambiguating four gestures that all start identically is exactly the part
 * that is otherwise only verifiable by hand on a tablet.
 *
 * One `pointerInput`, over the whole stage. Per-card detectors are what let one
 * finger start a drag on one card while a second starts a separate drag on
 * another, and no amount of consumption fixes that after the fact.
 *
 * Coordinates are unprojected here rather than relying on Compose to invert the
 * tilted layer's matrix, so twist angles are angles on the felt rather than
 * angles on the glass, and the arithmetic is the same `StagePlane` the renderer
 * uses.
 */
/**
 * What both of the mat's clocks report to.
 *
 * The arbiter is driven from two places — pointer events, and the frame loop
 * that gives a motionless finger a way to become a long press — and both of
 * them produce events that have to be carried out against *the same* memory of
 * what the press landed on. Left inside the `pointerInput` closure, that memory
 * is unreachable from the frame loop, and every gesture the clock decides
 * (peek, the two-finger menu, a hand left resting on the mat) is computed and
 * then dropped on the floor.
 *
 * [layout] and [onMenu] are refreshed by the composable rather than captured,
 * because this outlives any one composition of it.
 */
@Stable
internal class MatPilot(
    private val machine: MatGestureMachine,
    private val play: PlayState,
    private val feedback: Feedback,
    var layout: BoardLayout,
) {
    var onMenu: (DragOrigin) -> Unit = {}

    /** What the press landed on. The one thing that has to survive both clocks. */
    private var grabbed: DragOrigin? = null

    /** Whether the carried card is going *under* what it is over, not on top. */
    private var attaching = false

    /** True while the secondary mouse button is down, so the menu opens once. */
    private var secondaryDown = false

    fun frame(frame: TouchFrame) = carryOut(machine.onFrame(frame))

    fun tick(timeMillis: Long) = carryOut(machine.onTick(timeMillis))

    /**
     * The mouse's way of asking for the card menu.
     *
     * On touch that is the two-finger hold. A mouse has no second finger, and
     * holding the one button still is already spoken for by the peek, so the
     * secondary button is the whole of the pointer idiom — without it the menu
     * is simply unreachable on a desktop.
     */
    fun secondary(down: Boolean, at: Vec2) {
        if (down == secondaryDown) return
        secondaryDown = down
        if (!down) return

        machine.cancel()
        whatIsUnder(play.field, layout, at)?.let(onMenu)
    }

    private fun carryOut(events: List<MatEvent>) {
        events.forEach { event ->
            when (event) {
                is MatEvent.Pressed, is MatEvent.Dropped -> attaching = false
                is MatEvent.Dwelled -> attaching = true
                // Only movement worth the name undoes it. A card held still is
                // still being held still through the jitter of a real finger.
                is MatEvent.Moved -> if (event.delta.length > ROUSE) attaching = false
                else -> Unit
            }
            grabbed = handle(event, grabbed, play, layout, feedback, onMenu, attaching)
        }
    }

    private companion object {
        /** Mat pixels of travel in one frame that count as having moved on. */
        const val ROUSE = 2.5f
    }
}

@Composable
internal fun MatInput(
    pilot: MatPilot,
    machine: MatGestureMachine,
    layout: BoardLayout,
    stage: StagePlane,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(layout, stage) {
                awaitPointerEventScope {
                    while (true) {
                        // Main, not Initial: this is the deepest interactive node
                        // on the stage, and consuming here is what stops an
                        // ancestor pager or drawer from stealing a drag.
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        fun onFelt(change: PointerInputChange): Touch {
                            val onPlane = stage.unproject(change.position.x, change.position.y)
                            return Touch(change.id.value, Vec2(onPlane.x, onPlane.y))
                        }

                        // The mouse's right button, before the gesture machine
                        // sees anything: it is a request for a menu, never the
                        // start of a drag.
                        val secondary = event.buttons.isSecondaryPressed
                        event.changes.firstOrNull()?.let {
                            pilot.secondary(secondary, onFelt(it).at)
                        }
                        if (secondary) {
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        val frame = TouchFrame(
                            timeMillis = event.changes.firstOrNull()?.uptimeMillis ?: 0L,
                            touches = event.changes.filter { it.pressed }.map(::onFelt),
                            // Every pointer the event carried, including any that
                            // went up inside it: Android batches, and a brisk
                            // two-finger tap can arrive with both already lifted.
                            seen = event.changes.size,
                            // And where those lifted ones were when they left, or
                            // a fully batched tap has nothing to flip *at*.
                            released = event.changes.filterNot { it.pressed }.map(::onFelt),
                        )

                        pilot.frame(frame)

                        if (machine.phase.locked) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    )
}

/**
 * Carries out one thing the arbiter says happened.
 *
 * Returns what is currently grabbed, which only [MatEvent.Pressed] changes —
 * the press is where the stage looks under the finger, and everything after it
 * acts on what was found there.
 */
private fun handle(
    event: MatEvent,
    grabbed: DragOrigin?,
    play: PlayState,
    layout: BoardLayout,
    feedback: Feedback,
    onMenu: (DragOrigin) -> Unit,
    attaching: Boolean,
): DragOrigin? {
    fun mat(at: Vec2): MatPoint = layout.toMat(at.x to at.y)

    when (event) {
        is MatEvent.Pressed -> return whatIsUnder(play.field, layout, event.at)

        is MatEvent.Tapped -> {
            when (val what = grabbed) {
                is DragOrigin.Pile -> when (what.pile) {
                    // The deck is drawn from; every other pile is read.
                    BoardSlot.Deck ->
                        if (play.move { it.draw() }) feedback.play(SoundEffect.DEAL)
                    else -> onMenu(what)
                }
                is DragOrigin.Mat ->
                    if (play.move { it.bringToFront(what.id) }) feedback.play(SoundEffect.LIFT)
                else -> Unit
            }
        }

        is MatEvent.LiftedCard -> {
            grabbed?.let {
                play.lift(it, mat(event.at), layout, whole = false)
                feedback.play(SoundEffect.LIFT)
            }
        }

        is MatEvent.LiftedStack -> {
            grabbed?.let {
                play.lift(it, mat(event.at), layout, whole = true)
                feedback.play(SoundEffect.LIFT)
            }
        }

        is MatEvent.Moved -> play.carryTo(mat(event.at), layout, attaching)

        // The card has been held still over another one long enough to mean it
        // is going underneath. Re-resolving with the same point is what changes
        // the indicator from "stack" to "attach", so the user is told before
        // they let go rather than after.
        is MatEvent.Dwelled -> {
            if (play.carry != null) {
                play.carryTo(mat(event.at), layout, attaching = true)
                feedback.play(SoundEffect.LIFT)
            }
        }

        is MatEvent.Dropped -> {
            if (play.carry != null && play.release()) feedback.play(SoundEffect.SNAP)
        }

        is MatEvent.Flipped -> {
            val what = grabbed
            when {
                // Mid-carry, the card turns in the air and lands set. Putting
                // it down face-up and flipping it after would have shown the
                // table the one card the player meant to hide.
                play.carry != null -> if (play.turnCarry()) feedback.play(SoundEffect.SLIDE)
                what is DragOrigin.Mat ->
                    if (play.move { it.flip(what.id) }) feedback.play(SoundEffect.SLIDE)
                else -> Unit
            }
        }

        is MatEvent.Twisting -> play.twistCarry(event.quarterTurns)

        is MatEvent.Detent -> feedback.play(SoundEffect.LIFT)

        is MatEvent.TwistCommitted -> {
            val what = grabbed
            if (play.carry != null) {
                play.release()
            } else if (what is DragOrigin.Mat && event.quarterTurns % 2 != 0) {
                if (play.move { it.rotate(what.id) }) feedback.play(SoundEffect.SLIDE)
            }
        }

        is MatEvent.MenuRequested -> grabbed?.let(onMenu)

        MatEvent.Cancelled -> play.cancelCarry()

        // Looking, not moving: the card rises and turns toward the reader and
        // the field is untouched, which is what a held finger on a table means.
        is MatEvent.PeekBegan -> grabbed?.let { what ->
            // Everything is peekable except the deck. The graveyard, the
            // banished pile and the extra deck are all open information you are
            // allowed to read; the top of your own deck is the one card a
            // goldfish is only honest without.
            val deck = what is DragOrigin.Pile && what.pile == BoardSlot.Deck
            if (!deck) {
                play.peek(what)
                feedback.play(SoundEffect.LIFT)
            }
        }

        MatEvent.PeekEnded -> play.peek(null)
    }

    return grabbed
}

/**
 * What is under the finger, in mat-plane pixels.
 *
 * Order matters and is the same order the eye uses: the topmost card on the mat
 * first — `mat` is ordered back to front, so the search runs backwards — then
 * the hand, then the bare piles. A pile is only reachable when no card is
 * sitting on it, which is right: the card you can see is the card you meant.
 */
private fun whatIsUnder(field: PlayField, layout: BoardLayout, at: Vec2): DragOrigin? {
    val halfWidth = layout.cardWidth / 2f
    val halfHeight = layout.cardHeight / 2f

    fun covers(centre: Pair<Float, Float>): Boolean =
        abs(at.x - centre.first) <= halfWidth && abs(at.y - centre.second) <= halfHeight

    for (placed in field.mat.asReversed()) {
        if (covers(layout.toPixels(placed.at))) return DragOrigin.Mat(placed.id)
    }

    field.hand.indices.reversed().forEach { index ->
        val point = handPointFor(layout, index, field.hand.size)
        if (covers(layout.toPixels(point))) return DragOrigin.Hand(index)
    }

    listOf(
        BoardSlot.Deck to field.deck.size,
        BoardSlot.ExtraDeck to field.extraDeck.size,
        BoardSlot.Graveyard to field.graveyard.size,
        BoardSlot.Banished to field.banished.size,
    ).forEach { (slot, count) ->
        val rect = layout[slot] ?: return@forEach
        if (count > 0 && rect.holds(at.x, at.y)) return DragOrigin.Pile(slot, 0)
    }

    return null
}
