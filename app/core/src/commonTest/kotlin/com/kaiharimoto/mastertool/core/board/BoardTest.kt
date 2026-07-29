package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardTest {

    private val ash = PlacedCard(CardId(14558127))
    private val maxx = PlacedCard(CardId(23434538))
    private val zone = BoardLayout.monsterRow[0]
    private val other = BoardLayout.monsterRow[1]

    @Test
    fun anEmptyBoardHasNothingAnywhere() {
        assertTrue(Board.EMPTY.isEmpty)
        assertEquals(emptyList(), Board.EMPTY[zone])
        assertFalse(Board.EMPTY.occupied(zone))
    }

    @Test
    fun aCardGoesInAZone() {
        val board = assertNotNull(Board.EMPTY.place(zone, ash))

        assertEquals(listOf(ash), board[zone])
        assertTrue(board.occupied(zone))
        assertFalse(board.occupied(other))
    }

    @Test
    fun aMonsterZoneHoldsOneMonster() {
        val board = assertNotNull(Board.EMPTY.place(zone, ash))

        assertNull(board.place(zone, maxx), "a full zone refuses rather than stacking")
    }

    @Test
    fun aGraveyardTakesEverything() {
        var board = Board.EMPTY
        repeat(40) { board = assertNotNull(board.place(BoardLayout.graveyard, ash)) }

        assertEquals(40, board[BoardLayout.graveyard].size)
    }

    @Test
    fun aBoardThatLooksEmptyIsEqualToAnEmptyOne() {
        // Zones are absent rather than present and empty, so putting a card down
        // and picking it up again leaves no trace to compare against.
        val board = assertNotNull(Board.EMPTY.place(zone, ash)).lift(zone)

        assertEquals(Board.EMPTY, board)
        assertTrue(board.isEmpty)
    }

    @Test
    fun movingTakesTheTopCardAcross() {
        val board = assertNotNull(Board.EMPTY.place(zone, ash))
            .let { assertNotNull(it.move(zone, BoardLayout.graveyard)) }

        assertFalse(board.occupied(zone))
        assertEquals(listOf(ash), board[BoardLayout.graveyard])
    }

    @Test
    fun movingOntoAFullZoneLeavesBothWhereTheyWere() {
        val board = assertNotNull(assertNotNull(Board.EMPTY.place(zone, ash)).place(other, maxx))

        assertNull(board.move(zone, other))
    }

    @Test
    fun movingAnEmptyZoneIsRefused() {
        assertNull(Board.EMPTY.move(zone, other))
    }

    @Test
    fun movingAZoneOntoItselfChangesNothing() {
        val board = assertNotNull(Board.EMPTY.place(zone, ash))

        assertEquals(board, board.move(zone, zone))
    }

    @Test
    fun turningChangesOnlyTheTopCard() {
        var board = Board.EMPTY
        board = assertNotNull(board.place(BoardLayout.graveyard, ash))
        board = assertNotNull(board.place(BoardLayout.graveyard, maxx))

        board = board.turn(BoardLayout.graveyard, Placement.SET)

        assertEquals(
            listOf(ash, maxx.copy(placement = Placement.SET)),
            board[BoardLayout.graveyard],
        )
    }

    @Test
    fun turningAnEmptyZoneIsHarmless() {
        assertEquals(Board.EMPTY, Board.EMPTY.turn(zone, Placement.SET))
    }

    @Test
    fun positionsGoRoundInACircle() {
        assertEquals(Placement.DEFENSE, Placement.ATTACK.turned())
        assertEquals(Placement.SET, Placement.DEFENSE.turned())
        assertEquals(Placement.ATTACK, Placement.SET.turned())
    }

    @Test
    fun theBoardHasTheZonesTheGameHas() {
        assertEquals(5, BoardLayout.monsterRow.size)
        assertEquals(5, BoardLayout.spellTrapRow.size)
        assertEquals(2, BoardLayout.extraMonsterRow.size)
        assertEquals(
            BoardLayout.monsterRow.toSet().size,
            BoardLayout.monsterRow.size,
            "the zones in a row are distinct",
        )
    }

    @Test
    fun theTwoHalvesOfTheMatAreDifferentPlaces() {
        // The bug this exists to stop: one set of ids drawn twice put every card
        // played in front of you across from you as well.
        assertTrue(BoardLayout.monsterRow.none { it in BoardLayout.theirMonsterRow })
        assertTrue(BoardLayout.spellTrapRow.none { it in BoardLayout.theirSpellTrapRow })
        assertTrue(BoardLayout.field != BoardLayout.theirField)
    }

    @Test
    fun aCardOnOneSideIsNotOnTheOther() {
        val mine = BoardLayout.monsterRow[0]
        val theirs = BoardLayout.theirMonsterRow[0]

        val board = assertNotNull(Board.EMPTY.place(mine, ash))

        assertEquals(listOf(ash), board[mine])
        assertTrue(board[theirs].isEmpty())
        assertNotNull(board.place(theirs, maxx), "their zone is free")
    }

    @Test
    fun theSharedZonesBelongToNeitherHalf() {
        // Which is what makes them the crease the board folds on.
        assertTrue(BoardLayout.extraMonsterRow.all { it.mine })
        assertEquals(2, BoardLayout.extraMonsterRow.toSet().size)
    }
}

class DropGestureTest {

    @Test
    fun lettingGoStandsTheCardUp() {
        assertEquals(Placement.ATTACK, DropGesture.placement(dx = 0f, dy = 0f, heldMs = 0))
    }

    @Test
    fun aSidewaysFlickLaysItDown() {
        assertEquals(Placement.DEFENSE, DropGesture.placement(dx = 40f, dy = 5f, heldMs = 0))
        assertEquals(
            Placement.DEFENSE,
            DropGesture.placement(dx = -40f, dy = 5f, heldMs = 0),
            "either direction: the card turns the same way",
        )
    }

    @Test
    fun holdingSetsItFaceDown() {
        assertEquals(Placement.SET, DropGesture.placement(dx = 0f, dy = 0f, heldMs = 300))
    }

    @Test
    fun aHeldFlickIsStillASet() {
        // They are asking for the same rotation and differ only in face-up, so
        // there is nothing to resolve -- but the precedence is worth pinning.
        assertEquals(Placement.SET, DropGesture.placement(dx = 60f, dy = 2f, heldMs = 500))
    }

    @Test
    fun carryingACardDownTheBoardIsNotAFlick() {
        // The drag that would have broken this: a card taken from the hand to
        // the spell row travels a long way, and mostly downwards.
        assertFalse(DropGesture.isFlick(dx = 80f, dy = 200f))
        assertEquals(Placement.ATTACK, DropGesture.placement(dx = 80f, dy = 200f, heldMs = 0))
    }

    @Test
    fun aHandThatDidNotQuiteStopIsNotAFlick() {
        assertFalse(DropGesture.isFlick(dx = 15f, dy = 0f))
        assertEquals(Placement.ATTACK, DropGesture.placement(dx = 15f, dy = 0f, heldMs = 0))
    }

    @Test
    fun theRatioIsWhatDecidesNotTheDistance() {
        // Both travel the same distance sideways. Only one is a flick.
        assertTrue(DropGesture.isFlick(dx = 60f, dy = 30f))
        assertFalse(DropGesture.isFlick(dx = 60f, dy = 50f))
    }

    @Test
    fun theHoldIsInclusiveAtItsThreshold() {
        assertEquals(
            Placement.SET,
            DropGesture.placement(0f, 0f, DropGesture.HOLD_MS),
        )
        assertEquals(
            Placement.ATTACK,
            DropGesture.placement(0f, 0f, DropGesture.HOLD_MS - 1),
        )
    }

}
