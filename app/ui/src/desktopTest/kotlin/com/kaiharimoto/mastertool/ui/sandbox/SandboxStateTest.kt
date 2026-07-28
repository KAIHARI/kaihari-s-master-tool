package com.kaiharimoto.mastertool.ui.sandbox

import com.kaiharimoto.mastertool.core.board.BoardLayout
import com.kaiharimoto.mastertool.core.board.Placement
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandboxStateTest {

    private val cards = (1..20).map { CardId(900_000 + it) }
    private val deck = Deck(main = cards, extra = emptyList(), side = emptyList())
    private val zone = BoardLayout.monsterRow[0]

    private fun open(): SandboxState = SandboxState().also { it.open(deck, Random(4)) }

    @Test
    fun openingDealsAHandAndNothingElse() {
        val state = open()

        assertEquals(5, state.table.hand.size)
        assertTrue(state.board.isEmpty)
        assertFalse(state.canUndo, "there is nothing to go back to yet")
    }

    @Test
    fun aCardGoesFromTheHandToTheBoard() {
        val state = open()
        val card = state.table.hand[0]

        assertTrue(state.play(0, zone, Placement.DEFENSE))

        assertEquals(4, state.table.hand.size)
        assertEquals(card, state.board[zone].single().id)
        assertEquals(Placement.DEFENSE, state.board[zone].single().placement)
    }

    @Test
    fun aFullZoneRefusesAndChangesNothing() {
        val state = open()
        state.play(0, zone, Placement.ATTACK)
        val before = state.table

        assertFalse(state.play(0, zone, Placement.ATTACK))
        assertEquals(before, state.table)
    }

    @Test
    fun playingPutsTheHeldCardDown() {
        val state = open()
        state.hold(2)

        state.play(2, zone, Placement.ATTACK)

        assertNull(state.heldInHand, "the card is on the board now, not in the hand")
    }

    @Test
    fun holdingTheSameCardIsHowYouPutItBack() {
        val state = open()

        state.hold(1)
        assertEquals(1, state.heldInHand)
        state.hold(null)
        assertNull(state.heldInHand)
    }

    @Test
    fun tappingACardTurnsItRoundAndRoundAgain() {
        val state = open()
        state.play(0, zone, Placement.ATTACK)

        state.turn(zone)
        assertEquals(Placement.DEFENSE, state.board[zone].single().placement)
        state.turn(zone)
        assertEquals(Placement.SET, state.board[zone].single().placement)
        state.turn(zone)
        assertEquals(Placement.ATTACK, state.board[zone].single().placement)
    }

    @Test
    fun undoPutsTheWholeTableBack() {
        val state = open()
        val before = state.table

        state.play(0, zone, Placement.ATTACK)
        assertTrue(state.canUndo)
        state.undo()

        assertEquals(before, state.table, "hand, board and deck all go back together")
        assertFalse(state.canUndo)
    }

    @Test
    fun undoWalksBackThroughSeveralMoves() {
        val state = open()
        val opening = state.table

        state.play(0, BoardLayout.monsterRow[0], Placement.ATTACK)
        state.play(0, BoardLayout.monsterRow[1], Placement.DEFENSE)
        state.draw()

        repeat(3) { state.undo() }

        assertEquals(opening, state.table)
    }

    @Test
    fun undoingNothingIsHarmless() {
        val state = open()
        val before = state.table

        state.undo()

        assertEquals(before, state.table)
    }

    @Test
    fun aRefusedMoveIsNotWorthUndoing() {
        // The trap: a rejected drop that still pushed a snapshot would make the
        // next undo do nothing at all, which reads as undo being broken.
        val state = open()
        state.play(0, zone, Placement.ATTACK)

        assertFalse(state.play(0, zone, Placement.ATTACK))
        state.undo()

        assertTrue(state.board.isEmpty)
    }

    @Test
    fun sweepingClearsTheBoardAndKeepsTheDeck() {
        val state = open()
        state.play(0, zone, Placement.ATTACK)
        val library = state.table.library

        state.clearBoard()

        assertTrue(state.board.isEmpty)
        assertEquals(library, state.table.library)
        assertTrue(state.canUndo, "a sweep is the thing you most want back")
    }

    @Test
    fun aCardCanBeSentAwayAndTakenBack() {
        val state = open()
        state.play(0, zone, Placement.ATTACK)

        state.sendToGraveyard(zone)
        assertTrue(state.board[zone].isEmpty())
        assertEquals(1, state.board[BoardLayout.graveyard].size)

        state.undo()
        assertEquals(1, state.board[zone].size)
    }

    @Test
    fun drawingRunsOutRatherThanFailing() {
        val state = open()

        repeat(30) { state.draw() }

        assertTrue(state.table.library.isEmpty())
        assertEquals(cards.size, state.table.hand.size)
    }
}
