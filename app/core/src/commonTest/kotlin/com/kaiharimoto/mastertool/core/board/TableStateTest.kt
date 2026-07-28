package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableStateTest {

    private val cards = (1..12).map { CardId(900_000 + it) }
    private val zone = BoardLayout.monsterRow[0]

    private fun table(handSize: Int = 3) =
        TableState(library = cards).draw(handSize)

    @Test
    fun drawingTakesFromTheTop() {
        val table = TableState(library = cards).draw(2)

        assertEquals(listOf(cards.last(), cards[cards.size - 2]), table.hand)
        assertEquals(cards.size - 2, table.library.size)
    }

    @Test
    fun drawingMoreThanIsLeftTakesWhatThereIs() {
        // Decking out is a thing that happens, not an error.
        val table = TableState(library = cards.take(2)).draw(5)

        assertEquals(2, table.hand.size)
        assertTrue(table.library.isEmpty())
    }

    @Test
    fun drawingFromNothingChangesNothing() {
        val table = TableState()

        assertEquals(table, table.draw(3))
    }

    @Test
    fun playingMovesACardOutOfTheHand() {
        val before = table()
        val played = assertNotNull(before.play(0, zone, Placement.ATTACK))

        assertEquals(before.hand.size - 1, played.hand.size)
        assertEquals(before.hand.drop(1), played.hand)
        assertEquals(
            listOf(PlacedCard(before.hand[0], Placement.ATTACK)),
            played.board[zone],
        )
    }

    @Test
    fun playingFromTheMiddleOfTheHandKeepsTheRestInOrder() {
        val before = table(handSize = 4)
        val played = assertNotNull(before.play(1, zone, Placement.SET))

        assertEquals(listOf(before.hand[0], before.hand[2], before.hand[3]), played.hand)
    }

    @Test
    fun playingIntoAFullZoneIsRefused() {
        val table = assertNotNull(table().play(0, zone, Placement.ATTACK))

        assertNull(table.play(0, zone, Placement.ATTACK))
    }

    @Test
    fun playingACardThatIsNotInHandIsRefused() {
        assertNull(table().play(9, zone, Placement.ATTACK))
        assertNull(table().play(-1, zone, Placement.ATTACK))
    }

    @Test
    fun theGestureDecidesHowItLands() {
        val before = table()
        val flicked = assertNotNull(
            before.play(0, zone, DropGesture.placement(dx = 44f, dy = 6f, heldMs = 0)),
        )

        assertEquals(Placement.DEFENSE, flicked.board[zone].single().placement)
    }

    @Test
    fun aCardCanGoStraightToAPile() {
        val before = table()
        val after = assertNotNull(before.sendFromHand(0, BoardLayout.graveyard))

        assertEquals(listOf(PlacedCard(before.hand[0])), after.board[BoardLayout.graveyard])
        assertEquals(before.hand.size - 1, after.hand.size)
    }

    @Test
    fun aCardCanComeBackToTheHand() {
        val before = table()
        val played = assertNotNull(before.play(0, zone, Placement.DEFENSE))

        val bounced = assertNotNull(played.toHand(zone))

        assertEquals(before.hand.size, bounced.hand.size)
        assertTrue(bounced.board.isEmpty)
    }

    @Test
    fun bouncingAnEmptyZoneIsRefused() {
        assertNull(table().toHand(zone))
    }

    @Test
    fun aDeckDealsAnOpeningHandAndKeepsTheExtraAside() {
        val deck = Deck(
            main = cards,
            extra = listOf(CardId(1), CardId(2)),
            side = listOf(CardId(3)),
        )

        val table = TableState.from(deck, Random(7))

        assertEquals(TableState.OPENING_HAND, table.hand.size)
        assertEquals(cards.size - TableState.OPENING_HAND, table.library.size)
        assertEquals(listOf(CardId(1), CardId(2)), table.extra)
        assertTrue(
            table.library.none { it in table.extra },
            "the Extra deck is not something you draw",
        )
    }

    @Test
    fun theSameSeedDealsTheSameTable() {
        val deck = Deck(main = cards, extra = emptyList(), side = emptyList())

        assertEquals(TableState.from(deck, Random(3)), TableState.from(deck, Random(3)))
    }

    @Test
    fun everyCardIsSomewhereAfterAnyMove() {
        // The property worth having: cards do not appear and do not vanish.
        val deck = Deck(main = cards, extra = emptyList(), side = emptyList())
        val start = TableState.from(deck, Random(11))

        val after = assertNotNull(start.play(2, zone, Placement.ATTACK))
            .let { assertNotNull(it.sendFromHand(0, BoardLayout.graveyard)) }
            .let { assertNotNull(it.toHand(zone)) }

        val everywhere = after.hand + after.library + after.board.cards.map { it.id }
        assertEquals(cards.sortedBy { it.value }, everywhere.sortedBy { it.value })
    }
}
