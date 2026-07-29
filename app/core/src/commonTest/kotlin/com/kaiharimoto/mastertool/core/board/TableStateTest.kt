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
        val after = assertNotNull(before.play(0, BoardLayout.graveyard, Placement.ATTACK))

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
            .let { assertNotNull(it.play(0, BoardLayout.graveyard, Placement.ATTACK)) }
            .let { assertNotNull(it.toHand(zone)) }

        val everywhere = after.hand + after.library + after.board.cards.map { it.id }
        assertEquals(cards.sortedBy { it.value }, everywhere.sortedBy { it.value })
    }

    // ---- the Extra deck, and the piles --------------------------------------

    private val extra = listOf(CardId(700_001), CardId(700_002), CardId(700_003))

    @Test
    fun aMonsterComesOutOfTheExtraDeck() {
        val table = TableState(library = cards, extra = extra).draw(3)

        val summoned = assertNotNull(table.summon(1, zone, Placement.ATTACK))

        assertEquals(extra[1], summoned.board[zone].single().id)
        assertEquals(listOf(extra[0], extra[2]), summoned.extra, "and only that one leaves")
        assertEquals(table.hand, summoned.hand, "the hand is not where it came from")
    }

    @Test
    fun summoningAnExtraDeckMonsterThatIsNotThereIsRefused() {
        val table = TableState(extra = extra)

        assertNull(table.summon(7, zone, Placement.ATTACK))
        assertNull(TableState().summon(0, zone, Placement.ATTACK))
    }

    @Test
    fun summoningIntoAFullZoneIsRefusedAndKeepsTheExtraDeckIntact() {
        val table = assertNotNull(TableState(extra = extra).summon(0, zone, Placement.ATTACK))

        assertNull(table.summon(0, zone, Placement.ATTACK))
        assertEquals(2, table.extra.size)
    }

    @Test
    fun aSearcherTakesTheCardItWantedAndLeavesTheRestInOrder() {
        val table = TableState(library = cards)

        val searched = assertNotNull(table.searchLibrary(4))

        assertEquals(listOf(cards[4]), searched.hand)
        assertEquals(cards.filterIndexed { i, _ -> i != 4 }, searched.library)
    }

    @Test
    fun searchingDoesNotDisturbWhatIsOnTop() {
        // The trap: a search that shuffled would quietly ruin anything set up
        // on top of the deck, and nothing on screen would say it had.
        val table = TableState(library = cards)
        val onTop = table.library.last()

        val searched = assertNotNull(table.searchLibrary(0))

        assertEquals(onTop, searched.library.last())
        assertEquals(listOf(onTop), searched.draw().hand.takeLast(1))
    }

    @Test
    fun aCardIsRetrievedFromTheMiddleOfAGraveyard() {
        var table = TableState(library = cards).draw(3)
        repeat(3) { table = assertNotNull(table.play(0, BoardLayout.graveyard, Placement.ATTACK)) }
        val wanted = table.board[BoardLayout.graveyard][1]

        val retrieved = assertNotNull(table.retrieve(BoardLayout.graveyard, 1))

        assertEquals(listOf(wanted.id), retrieved.hand)
        assertEquals(2, retrieved.board[BoardLayout.graveyard].size)
        assertTrue(retrieved.board[BoardLayout.graveyard].none { it == wanted })
    }

    @Test
    fun aCardIsRaisedOutOfTheGraveyardStraightOntoTheBoard() {
        var table = TableState(library = cards).draw(2)
        repeat(2) { table = assertNotNull(table.play(0, BoardLayout.graveyard, Placement.ATTACK)) }
        val wanted = table.board[BoardLayout.graveyard][0]

        val raised = assertNotNull(
            table.raise(BoardLayout.graveyard, 0, zone, Placement.DEFENSE),
        )

        assertEquals(wanted.id, raised.board[zone].single().id)
        assertEquals(Placement.DEFENSE, raised.board[zone].single().placement)
        assertEquals(1, raised.board[BoardLayout.graveyard].size)
        assertTrue(raised.hand.isEmpty(), "it does not pass through the hand")
    }

    @Test
    fun raisingIntoAFullZoneLeavesTheGraveyardAlone() {
        var table = TableState(library = cards).draw(2)
        table = assertNotNull(table.play(0, zone, Placement.ATTACK))
        table = assertNotNull(table.play(0, BoardLayout.graveyard, Placement.ATTACK))

        assertNull(table.raise(BoardLayout.graveyard, 0, zone, Placement.ATTACK))
    }

    @Test
    fun raisingAPileOntoItselfIsRefused() {
        var table = TableState(library = cards).draw(1)
        table = assertNotNull(table.play(0, BoardLayout.graveyard, Placement.ATTACK))

        assertNull(table.raise(BoardLayout.graveyard, 0, BoardLayout.graveyard, Placement.ATTACK))
    }

    @Test
    fun retrievingWhatIsNotThereIsRefused() {
        assertNull(TableState().retrieve(BoardLayout.graveyard, 0))
        assertNull(TableState().raise(BoardLayout.graveyard, 0, zone, Placement.ATTACK))
        assertNull(TableState().searchLibrary(0))
    }

    @Test
    fun theExtraDeckIsStillAccountedForAfterASummon() {
        val deck = Deck(main = cards, extra = extra, side = emptyList())
        val start = TableState.from(deck, Random(5))

        val after = assertNotNull(start.summon(0, zone, Placement.ATTACK))

        val everywhere = after.hand + after.library + after.extra + after.board.cards.map { it.id }
        assertEquals(
            (cards + extra).sortedBy { it.value },
            everywhere.sortedBy { it.value },
        )
    }

    @Test
    fun aDeckCanBeLaidOutAroundAHandYouAlreadyHave() {
        val deck = Deck(main = cards, extra = extra, side = emptyList())
        val hand = listOf(cards[0], cards[5], cards[9])

        val table = TableState.holding(deck, hand, Random(6))

        assertEquals(hand, table.hand, "in the order it was already in")
        assertEquals(cards.size - hand.size, table.library.size)
        assertTrue(hand.none { it in table.library }, "and not still in the deck as well")
        assertEquals(extra, table.extra)
    }

    @Test
    fun onlyOneCopyLeavesTheDeckPerCopyInHand() {
        // Three of a card in the list, one of them in hand: the other two are
        // still in there, and a naive removeAll would take all three.
        val ash = CardId(14558127)
        val deck = Deck(main = List(3) { ash } + cards, extra = emptyList(), side = emptyList())

        val table = TableState.holding(deck, listOf(ash), Random(1))

        assertEquals(2, table.library.count { it == ash })
    }

    @Test
    fun aHandCardThatIsNotInTheDeckIsStillDealt() {
        val deck = Deck(main = cards, extra = emptyList(), side = emptyList())
        val stranger = CardId(111_111)

        val table = TableState.holding(deck, listOf(stranger), Random(1))

        assertEquals(listOf(stranger), table.hand)
        assertEquals(cards.size, table.library.size)
    }

    // ---- tokens -------------------------------------------------------------

    @Test
    fun aTokenComesFromNowhere() {
        val before = table()

        val after = assertNotNull(before.makeToken(zone, Placement.DEFENSE))

        assertTrue(after.board[zone].single().token)
        assertEquals(Placement.DEFENSE, after.board[zone].single().placement)
        assertEquals(before.hand, after.hand, "nothing left the hand")
        assertEquals(before.library, after.library, "nor the deck")
    }

    @Test
    fun aTokenTakesUpAZoneLikeAnythingElse() {
        val table = assertNotNull(table().makeToken(zone))

        assertNull(table.makeToken(zone), "the zone is occupied")
        assertNull(table.play(0, zone, Placement.ATTACK))
    }

    @Test
    fun aTokenSentToTheGraveyardStopsExisting() {
        // A graveyard with a token in it is a graveyard that would let you bring
        // the token back.
        val start = assertNotNull(table().makeToken(zone))

        val after = assertNotNull(start.copy(board = assertNotNull(start.board.move(zone, BoardLayout.graveyard))))

        assertTrue(after.board.isEmpty)
        assertTrue(after.board[BoardLayout.graveyard].isEmpty())
    }

    @Test
    fun aTokenBouncedToTheHandStopsExistingToo() {
        val before = table()
        val start = assertNotNull(before.makeToken(zone))

        val after = assertNotNull(start.toHand(zone))

        assertEquals(before.hand, after.hand, "there is no card to hold")
        assertTrue(after.board.isEmpty)
    }

    @Test
    fun aTokenMovedToAnotherZoneIsStillThere() {
        // Only piles make it vanish. Sliding it along the row does not.
        val start = assertNotNull(table().makeToken(zone, Placement.DEFENSE))
        val elsewhere = BoardLayout.monsterRow[2]

        val moved = assertNotNull(start.board.move(zone, elsewhere))

        assertTrue(moved[elsewhere].single().token)
        assertEquals(Placement.DEFENSE, moved[elsewhere].single().placement)
    }

    @Test
    fun aRealCardIsStillKeptWhenItLeavesTheBoard() {
        val before = table()
        val played = assertNotNull(before.play(0, zone, Placement.ATTACK))

        val bounced = assertNotNull(played.toHand(zone))

        assertEquals(before.hand.size, bounced.hand.size, "a card is not a token")
    }
}
