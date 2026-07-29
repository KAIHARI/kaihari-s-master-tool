package com.kaiharimoto.mastertool.ui.sandbox

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

    private fun open(): SandboxState = SandboxState().also { it.open(deck, random = Random(4)) }

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
        state.hold(DragOrigin.Hand(2))

        state.play(2, zone, Placement.ATTACK)

        assertNull(state.heldInHand, "the card is on the board now, not in the hand")
    }

    @Test
    fun holdingTheSameCardIsHowYouPutItBack() {
        val state = open()

        state.hold(DragOrigin.Hand(1))
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

/**
 * Letting go, which is where the whole idea lives.
 *
 * The clock is handed in so a flick and a hold are the same three lines apart:
 * a test that had to actually wait three hundred milliseconds to check a hold
 * would be a test nobody runs.
 */
class SandboxDragTest {

    private val cards = (1..20).map { CardId(900_000 + it) }
    private val deck = Deck(main = cards, extra = emptyList(), side = emptyList())
    private val zone = BoardLayout.monsterRow[0]
    private val elsewhere = BoardLayout.monsterRow[3]

    private var now = 1_000L

    /** The zone sits at 100,100 and is 70 by 100; nothing else is on the table. */
    private fun laidOut(): SandboxState = SandboxState { now }.also {
        it.open(deck, random = Random(9))
        it.registerZone(zone, Rect(100f, 100f, 170f, 200f))
        it.registerZone(elsewhere, Rect(400f, 100f, 470f, 200f))
    }

    private fun SandboxState.carryTo(target: Offset, steps: Int = 6) {
        val from = pointer
        repeat(steps) {
            now += 16
            dragBy((target - from) / steps.toFloat())
        }
    }

    @Test
    fun aQuickDropStandsTheCardUp() {
        val state = laidOut()
        val card = state.table.hand[0]

        state.startDrag(DragOrigin.Hand(0), card, Offset(120f, 500f))
        state.carryTo(Offset(130f, 150f))
        now += 16
        assertTrue(state.endDrag())

        assertEquals(Placement.ATTACK, state.board[zone].single().placement)
        assertEquals(card, state.board[zone].single().id)
    }

    @Test
    fun aFlickLaysItDown() {
        val state = laidOut()

        state.startDrag(DragOrigin.Hand(0), state.table.hand[0], Offset(120f, 400f))
        state.carryTo(Offset(105f, 150f))
        // The flick itself: sideways, fast, right at the end.
        repeat(3) {
            now += 16
            state.dragBy(Offset(20f, 1f))
        }
        now += 16
        assertTrue(state.endDrag())

        assertEquals(Placement.DEFENSE, state.board[zone].single().placement)
    }

    @Test
    fun holdingBeforeLettingGoSetsIt() {
        val state = laidOut()

        state.startDrag(DragOrigin.Hand(0), state.table.hand[0], Offset(120f, 400f))
        state.carryTo(Offset(130f, 150f))
        // The hand stops. Nothing is reported while it is still, which is the
        // whole difficulty -- the silence is the hold.
        now += 400
        assertTrue(state.endDrag())

        assertEquals(Placement.SET, state.board[zone].single().placement)
    }

    @Test
    fun theZoneUnderTheCardIsKnownBeforeItLands() {
        val state = laidOut()

        state.startDrag(DragOrigin.Hand(0), state.table.hand[0], Offset(120f, 400f))
        assertNull(state.over, "nothing under it out here")

        state.carryTo(Offset(130f, 150f))
        assertEquals(zone, state.over)
    }

    @Test
    fun lettingGoOverNothingCostsNothing() {
        val state = laidOut()
        val before = state.table

        state.startDrag(DragOrigin.Hand(0), state.table.hand[0], Offset(120f, 400f))
        state.carryTo(Offset(300f, 600f))
        now += 16
        assertFalse(state.endDrag())

        assertEquals(before, state.table, "the card goes back where it came from")
        assertFalse(state.canUndo, "and there is nothing to undo")
        assertNull(state.heldInHand)
    }

    @Test
    fun aCardAlreadyDownCanBeMoved() {
        val state = laidOut()
        state.play(0, zone, Placement.ATTACK)
        val card = state.board[zone].single().id

        state.startDrag(DragOrigin.OnBoard(zone), card, Offset(130f, 150f))
        state.carryTo(Offset(430f, 150f))
        now += 16
        assertTrue(state.endDrag())

        assertTrue(state.board[zone].isEmpty())
        assertEquals(card, state.board[elsewhere].single().id)
    }

    @Test
    fun aCancelledDragPutsEverythingBack() {
        val state = laidOut()
        val before = state.table

        state.startDrag(DragOrigin.Hand(1), state.table.hand[1], Offset(120f, 400f))
        state.carryTo(Offset(130f, 150f))
        state.cancelDrag()

        assertEquals(before, state.table)
        assertNull(state.drag)
        assertNull(state.heldInHand)
        assertNull(state.over)
    }

    @Test
    fun movementWithNothingInTheAirIsIgnored() {
        val state = laidOut()

        state.dragBy(Offset(50f, 50f))

        assertEquals(Offset.Zero, state.pointer)
    }
}

/**
 * The stacks: looking through them, and taking something out.
 *
 * Everything a modern turn is made of that never passed through the opening
 * hand -- what comes out of the Extra deck, what comes back out of the
 * graveyard, what a searcher finds.
 */
class SandboxPileTest {

    private val main = (1..20).map { CardId(900_000 + it) }
    private val extra = (1..3).map { CardId(700_000 + it) }
    private val deck = Deck(main = main, extra = extra, side = emptyList())
    private val zone = BoardLayout.monsterRow[0]

    private fun open(): SandboxState = SandboxState().also { it.open(deck, random = Random(2)) }

    @Test
    fun theDeckReadsFromTheTopDown() {
        // The top of the deck is the only part of it anybody has an opinion
        // about, so it is the part they should see first.
        val state = open()

        assertEquals(state.table.library.last(), state.contentsOf(Pile.DECK).first())
        assertEquals(state.table.library.size, state.contentsOf(Pile.DECK).size)
    }

    @Test
    fun searchingTakesTheCardThatWasPointedAt() {
        val state = open()
        val wanted = state.contentsOf(Pile.DECK)[6]

        assertTrue(state.takeToHand(Pile.DECK, 6))

        assertTrue(wanted in state.table.hand)
        assertTrue(wanted !in state.table.library)
        assertTrue(state.canUndo, "a search is an edit like any other")
    }

    @Test
    fun theExtraDeckIsShownAsItIs() {
        val state = open()

        assertEquals(extra, state.contentsOf(Pile.EXTRA))
    }

    @Test
    fun anExtraDeckMonsterIsPickedUpAndPutDown() {
        val state = open()

        state.holdFromPile(Pile.EXTRA, 1)
        assertEquals(DragOrigin.Extra(1), state.held)
        assertNull(state.openPile, "picking one up closes the stack you picked it from")

        assertTrue(state.placeHeld(zone))

        assertEquals(extra[1], state.board[zone].single().id)
        assertEquals(listOf(extra[0], extra[2]), state.table.extra)
        assertNull(state.held)
    }

    @Test
    fun nothingIsSummonedStraightOutOfTheDeck() {
        val state = open()

        state.holdFromPile(Pile.DECK, 0)

        assertNull(state.held)
        assertFalse(state.takeToHand(Pile.EXTRA, 0), "and the Extra deck is not searched to hand")
    }

    @Test
    fun aCardComesBackOutOfTheGraveyard() {
        val state = open()
        state.discard(0)
        state.discard(0)
        val wanted = state.contentsOf(Pile.GRAVEYARD)[0]

        state.holdFromPile(Pile.GRAVEYARD, 0)
        assertTrue(state.placeHeld(zone, Placement.DEFENSE))

        assertEquals(wanted, state.board[zone].single().id)
        assertEquals(Placement.DEFENSE, state.board[zone].single().placement)
        assertEquals(1, state.contentsOf(Pile.GRAVEYARD).size)
    }

    @Test
    fun aCardIsRetrievedFromTheGraveyardToTheHand() {
        val state = open()
        state.discard(0)
        val wanted = state.contentsOf(Pile.GRAVEYARD)[0]
        val handSize = state.table.hand.size

        assertTrue(state.takeToHand(Pile.GRAVEYARD, 0))

        assertEquals(handSize + 1, state.table.hand.size)
        assertTrue(wanted in state.table.hand)
        assertTrue(state.contentsOf(Pile.GRAVEYARD).isEmpty())
    }

    @Test
    fun aRefusedPlacementKeepsTheCardPickedUp() {
        // The zone is full. Losing the held card here would mean finding it
        // again in a stack, which is the opposite of what a refusal should cost.
        val state = open()
        state.play(0, zone, Placement.ATTACK)

        state.holdFromPile(Pile.EXTRA, 0)
        assertFalse(state.placeHeld(zone))

        assertEquals(DragOrigin.Extra(0), state.held)
        assertEquals(3, state.table.extra.size)
    }

    @Test
    fun puttingDownWithNothingPickedUpDoesNothing() {
        val state = open()

        assertFalse(state.placeHeld(zone))
        assertTrue(state.board.isEmpty)
    }

    @Test
    fun undoTakesBackASummonAndTheExtraDeckWithIt() {
        val state = open()
        state.holdFromPile(Pile.EXTRA, 0)
        state.placeHeld(zone)

        state.undo()

        assertTrue(state.board.isEmpty)
        assertEquals(extra, state.table.extra)
        assertNull(state.held, "and nothing is left picked up")
    }

    @Test
    fun aBoardCanBeOpenedAroundAHandYouAlreadyHave() {
        val state = SandboxState()
        val hand = listOf(main[0], main[1], main[2])

        state.open(deck, hand, Random(3))

        assertEquals(hand, state.table.hand)
        assertEquals(main.size - hand.size, state.table.library.size)
        assertFalse(state.canUndo, "it is a fresh table, not an edit to one")
    }

    @Test
    fun shufflingUpDealsAgainFromTheSameList() {
        val state = open()
        state.play(0, zone, Placement.ATTACK)

        assertTrue(state.canRedeal)
        state.redeal()

        assertTrue(state.board.isEmpty)
        assertEquals(5, state.table.hand.size)
        assertFalse(state.canUndo, "and there is no going back to the last table")
    }

    @Test
    fun anEmptyTableHasNothingToShuffle() {
        assertFalse(SandboxState().canRedeal)
    }

    @Test
    fun aPileKnowsWhetherItIsSomewhereToDropACard() {
        assertEquals(BoardLayout.graveyard, Pile.GRAVEYARD.zone)
        assertEquals(BoardLayout.banished, Pile.BANISHED.zone)
        assertNull(Pile.DECK.zone, "a card put back into the deck has no known position")
        assertNull(Pile.EXTRA.zone)
    }

    @Test
    fun aCardIsDraggedFromTheHandToTheGraveyard() {
        val state = open()
        val card = state.table.hand[0]

        assertTrue(state.play(0, BoardLayout.graveyard, Placement.ATTACK))

        assertEquals(card, state.contentsOf(Pile.GRAVEYARD).single())
    }

    @Test
    fun aCardIsBanishedOutOfTheGraveyard() {
        val state = open()
        state.discard(0)
        val card = state.contentsOf(Pile.GRAVEYARD).single()

        state.holdFromPile(Pile.GRAVEYARD, 0)
        assertTrue(state.placeHeld(BoardLayout.banished))

        assertTrue(state.contentsOf(Pile.GRAVEYARD).isEmpty())
        assertEquals(card, state.contentsOf(Pile.BANISHED).single())
    }

    @Test
    fun aMonsterOnTheBoardIsSentToTheGraveyard() {
        val state = open()
        state.play(0, zone, Placement.ATTACK)
        val card = state.board[zone].single().id

        state.sendToGraveyard(zone)

        assertEquals(card, state.contentsOf(Pile.GRAVEYARD).single())
        assertTrue(state.board[zone].isEmpty())
    }

    @Test
    fun theFieldSpellZoneIsAZoneLikeAnyOther() {
        // A card sits in it, one at a time, and turns like the rest -- which is
        // why it is drawn as a zone on the mat and not as a stack beside it.
        val state = open()
        val card = state.table.hand[0]

        assertTrue(state.play(0, BoardLayout.field, Placement.ATTACK))

        assertEquals(card, state.board[BoardLayout.field].single().id)
        assertFalse(state.play(0, BoardLayout.field, Placement.ATTACK), "one at a time")

        state.turn(BoardLayout.field)
        assertEquals(Placement.DEFENSE, state.board[BoardLayout.field].single().placement)
    }

    @Test
    fun aTokenIsHeldAndPlacedLikeAnythingElse() {
        // Nothing extra had to be built for it, which is the point of there
        // being one idea of "picked up".
        val state = open()
        val handSize = state.table.hand.size

        state.hold(DragOrigin.Token)
        assertTrue(state.placeHeld(zone, Placement.DEFENSE))

        assertTrue(state.board[zone].single().token)
        assertEquals(Placement.DEFENSE, state.board[zone].single().placement)
        assertEquals(handSize, state.table.hand.size, "it came from nowhere")
        assertNull(state.held)
    }

    @Test
    fun aTokenSentAwayLeavesNothingBehind() {
        val state = open()
        state.makeToken(zone)

        state.sendToGraveyard(zone)

        assertTrue(state.board.isEmpty)
        assertTrue(state.contentsOf(Pile.GRAVEYARD).isEmpty())
    }

    @Test
    fun makingATokenCanBeUndone() {
        val state = open()

        state.makeToken(zone)
        assertTrue(state.canUndo)
        state.undo()

        assertTrue(state.board.isEmpty)
    }

    @Test
    fun aTokenTurnsWhereItLies() {
        val state = open()
        state.makeToken(zone)

        state.turn(zone)

        assertEquals(Placement.DEFENSE, state.board[zone].single().placement)
        assertTrue(state.board[zone].single().token, "and is still a token")
    }
}
