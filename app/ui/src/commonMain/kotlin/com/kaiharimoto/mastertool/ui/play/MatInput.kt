package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
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
@Composable
internal fun MatInput(
    play: PlayState,
    machine: MatGestureMachine,
    layout: BoardLayout,
    stage: StagePlane,
    feedback: Feedback,
    onMenu: (DragOrigin) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(layout, stage) {
                var grabbed: DragOrigin? = null

                awaitPointerEventScope {
                    while (true) {
                        // Main, not Initial: this is the deepest interactive node
                        // on the stage, and consuming here is what stops an
                        // ancestor pager or drawer from stealing a drag.
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        val touches = event.changes
                            .filter { it.pressed }
                            .map { change ->
                                val onPlane = stage.unproject(change.position.x, change.position.y)
                                Touch(change.id.value, Vec2(onPlane.x, onPlane.y))
                            }

                        val frame = TouchFrame(
                            timeMillis = event.changes.firstOrNull()?.uptimeMillis ?: 0L,
                            touches = touches,
                            // Every pointer the event carried, including any that
                            // went up inside it: Android batches, and a brisk
                            // two-finger tap can arrive with both already lifted.
                            seen = event.changes.size,
                        )

                        machine.onFrame(frame).forEach { matEvent ->
                            grabbed = handle(
                                matEvent,
                                grabbed,
                                play,
                                layout,
                                feedback,
                                onMenu,
                            )
                        }

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

        is MatEvent.Moved -> play.carryTo(mat(event.at), layout)

        is MatEvent.Dropped -> {
            if (play.carry != null && play.release()) feedback.play(SoundEffect.SNAP)
        }

        is MatEvent.Flipped -> {
            val what = grabbed
            if (what is DragOrigin.Mat) {
                if (play.move { it.flip(what.id) }) feedback.play(SoundEffect.SLIDE)
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

        is MatEvent.PeekBegan, MatEvent.PeekEnded -> Unit
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
