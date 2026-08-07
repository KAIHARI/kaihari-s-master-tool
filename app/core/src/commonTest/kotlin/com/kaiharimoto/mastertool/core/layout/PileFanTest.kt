package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PileFanTest {

    // A board about the size the fitter solves for on a landscape tablet.
    private val cardWidth = 120f
    private val cardHeight = 175f
    private val field = Slot(left = 100f, top = 60f, width = 1400f, height = 620f)

    private fun spread(count: Int, over: Slot = field) =
        PileFan.spread(count, over, cardWidth, cardHeight)

    // ---- every card is somewhere, and somewhere on the table ------------------------

    @Test
    fun everyCardInThePileGetsAPlace() {
        // The property the whole feature rests on: a search that quietly dropped
        // the last four cards of the deck would be a search that lies.
        val fan = spread(40)

        assertEquals(40, fan.cards.size)
        assertEquals((0 until 40).toList(), fan.cards.map { it.index })
    }

    @Test
    fun theSpreadStaysOnTheBoardItWasGiven() {
        listOf(1, 5, 15, 40, 60).forEach { count ->
            val fan = spread(count)
            fan.cards.forEach {
                assertTrue(
                    it.x - cardWidth / 2f >= field.left - 0.5f &&
                        it.x + cardWidth / 2f <= field.right + 0.5f,
                    "$count cards: one at ${it.x} runs off the side of the board",
                )
                assertTrue(
                    it.y - cardHeight / 2f >= field.top - 0.5f &&
                        it.y + cardHeight / 2f <= field.bottom + 0.5f,
                    "$count cards: one at ${it.y} runs off the top or bottom",
                )
            }
        }
    }

    @Test
    fun theCardsAreNeverResized() {
        // Not tested here so much as *stated* here: a fan has no card size in it
        // at all, because the only free variables are the step and the number of
        // rows. A search shows you the cards that are on the table.
        val fan = spread(40)

        assertTrue(fan.step > 0f)
        assertTrue(fan.step <= cardWidth, "a step wider than a card is not a fan")
    }

    // ---- what a fan looks like ------------------------------------------------------

    @Test
    fun aSmallPileIsOneRowOfSlightlyOverlappingCards() {
        // A graveyard of six, an extra deck of fifteen: both are one row, and
        // both overlap, because a pile that has just been spread out by a hand
        // does not become a row of separate cards with gaps between them.
        listOf(6, 15).forEach { count ->
            val fan = spread(count)
            assertEquals(1, fan.rows, "$count cards should need one row")
            assertTrue(
                fan.step < cardWidth,
                "$count cards should overlap: step ${fan.step} of $cardWidth",
            )
        }
    }

    @Test
    fun aDeckBreaksIntoRowsRatherThanShrinkingToASliver() {
        // Forty cards will not go across a board at a legible step, so the fan
        // wraps. The alternative — one row of forty — leaves eight pixels of
        // each card showing, which is a colour, not a card.
        val fan = spread(40)

        assertTrue(fan.rows > 1, "forty cards do not fit in one row")
        assertTrue(
            fan.step > cardWidth * 0.3f,
            "and having wrapped, each card shows enough to be recognised: ${fan.step}",
        )
    }

    @Test
    fun rowsOverlapEachOtherToo() {
        val fan = spread(40)
        val rows = fan.cards.map { it.y }.distinct().sorted()

        assertTrue(rows.size >= 2)
        assertTrue(
            rows[1] - rows[0] < cardHeight,
            "rows sit on each other rather than in a grid: ${rows[1] - rows[0]} of $cardHeight",
        )
    }

    @Test
    fun aShortLastRowIsCentredUnderTheOneAboveIt() {
        // 25 cards over two rows leaves a ragged second row, and a ragged row
        // pushed to the left reads as a layout accident rather than as a pile.
        val fan = spread(25)
        val byRow = fan.cards.groupBy { it.row }
        if (byRow.size < 2) return

        byRow.values.forEach { row ->
            val middle = (row.first().x + row.last().x) / 2f
            assertTrue(
                abs(middle - field.centerX) < 0.5f,
                "every row is centred on the board: $middle against ${field.centerX}",
            )
        }
    }

    @Test
    fun aPileOfOneIsJustTheCardInTheMiddle() {
        val fan = spread(1)

        assertEquals(1, fan.cards.size)
        assertTrue(abs(fan.cards.single().x - field.centerX) < 0.5f)
        assertTrue(abs(fan.cards.single().y - field.centerY) < 0.5f)
    }

    @Test
    fun anEmptyPileSpreadsIntoNothing() {
        val fan = spread(0)

        assertTrue(fan.cards.isEmpty())
        assertEquals(0, fan.rows)
    }

    // ---- pointing at one ------------------------------------------------------------

    @Test
    fun theCardUnderYourFingerIsTheOneOnTop() {
        // A fan overlaps, so two cards cover the same pixel and only one of them
        // is the one you can see. Later cards are drawn over earlier ones, so
        // the later one wins — the same rule the mat itself is hit-tested by,
        // and getting it backwards means every finger lands one card to the left.
        val fan = spread(20)
        val first = fan.cards[0]
        val second = fan.cards[1]

        // A point inside both: just past the left edge of the second card.
        val overlap = second.x - cardWidth / 2f + 2f
        assertTrue(overlap < first.x + cardWidth / 2f, "the two really do overlap")

        assertEquals(
            1,
            PileFan.cardAt(fan, overlap, first.y, cardWidth, cardHeight),
        )
    }

    @Test
    fun everyCardCanBeReachedAtItsOwnCentre() {
        // Which is the claim that the fan is *searchable*: if a card in the
        // middle of a spread deck has no point that selects it, the pile might
        // as well not have been spread.
        val fan = spread(40)

        fan.cards.forEach {
            assertEquals(
                it.index,
                PileFan.cardAt(fan, it.x, it.y, cardWidth, cardHeight),
                "card ${it.index} cannot be picked at its own centre",
            )
        }
    }

    @Test
    fun theGapsBetweenTheCardsAreNotACard() {
        // The fan's own backdrop, which is what closes it. Well outside the
        // block, still inside the board.
        val fan = spread(6)

        assertNull(PileFan.cardAt(fan, field.left + 1f, field.top + 1f, cardWidth, cardHeight))
    }

    @Test
    fun theBoundsCoverEveryCardAndNothingMore() {
        // What the input layer treats as "inside the fan", so it has to hold
        // every card: a press on a card outside the bounds would be read as a
        // press on the felt and start turning the table instead.
        val fan = spread(40)

        fan.cards.forEach {
            assertTrue(
                fan.bounds.contains(it.x, it.y),
                "card ${it.index} at ${it.x},${it.y} is outside ${fan.bounds}",
            )
        }
        assertTrue(fan.bounds.width <= field.width + 0.5f)
        assertTrue(fan.bounds.height <= field.height + 0.5f)
    }

    @Test
    fun aVeryLargePileSqueezesRatherThanRunningOffTheTable() {
        // Sixty cards on a short board. Something has to give, and it is the
        // step: the cards stay their own size and stay on the felt.
        val cramped = Slot(left = 100f, top = 60f, width = 900f, height = 300f)
        val fan = PileFan.spread(60, cramped, cardWidth, cardHeight)

        assertEquals(60, fan.cards.size)
        assertNotNull(PileFan.cardAt(fan, fan.cards.last().x, fan.cards.last().y, cardWidth, cardHeight))
        fan.cards.forEach {
            assertTrue(
                it.x - cardWidth / 2f >= cramped.left - 0.5f &&
                    it.x + cardWidth / 2f <= cramped.right + 0.5f,
                "even squeezed, nothing leaves the board: ${it.x}",
            )
        }
    }
}
