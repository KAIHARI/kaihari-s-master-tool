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
        // A graveyard of six, a hand-sized pile of ten: one row each, and both
        // overlap, because a pile that has just been spread out by a hand does
        // not become a row of separate cards with gaps between them.
        listOf(1, 6, 10).forEach { count ->
            val fan = spread(count)
            assertEquals(1, fan.rows, "$count cards should need one row")
            assertTrue(
                fan.step < cardWidth,
                "$count cards should overlap: step ${fan.step} of $cardWidth",
            )
        }
    }

    @Test
    fun aDeckIsFourRowsWhicheverSizeItIs() {
        // kai's number, and the reason a row is capped at ten: at three rows the
        // step is already at its ceiling, so no amount of "is this comfortable"
        // would ever have asked for a fourth. Forty and sixty spread the same
        // way, which is what makes a search of one deck look like a search of
        // any other.
        listOf(40, 45, 60).forEach { count ->
            assertEquals(4, spread(count).rows, "$count cards should spread as four rows")
        }
        // And an extra deck, which is two rows of eight and seven rather than
        // one row of fifteen — ten to a row is the rule that decides it.
        assertEquals(2, spread(15).rows)
    }

    @Test
    fun aFannedCardShowsMoreOfItselfThanItHides() {
        // "Fan out more", measured. Half a card is enough to *recognise* one and
        // not enough to read one: the name band survives and the art, the type
        // line and the numbers are all under the next card along.
        listOf(15, 40, 60).forEach { count ->
            val fan = spread(count)
            assertTrue(
                fan.step > cardWidth * 0.5f,
                "$count cards: each shows only ${fan.step} of $cardWidth",
            )
        }
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
    fun aDeckIsStillFourRowsOnTheBoardsThisActuallyShipsTo() {
        // The rest of this file works on a board of my own invention, which is
        // the right way to test the arithmetic and the wrong way to claim
        // anything about the app: four rows only happen if the field is tall
        // enough to hold them, and `ceilingRows` will quietly return three if it
        // is not. So this asks the real solver, in both rooms — the desk gives a
        // fifth of the height away to the room, which is exactly the case where
        // the fourth row is at risk.
        listOf(
            "tablet, minimal" to BoardLayouter.solve(1600f, 1000f, 59f / 86f),
            "tablet, desk" to BoardLayouter.solve(1600f, 1000f, 59f / 86f, roomAbove = 0.20f),
            "phone, desk" to BoardLayouter.solve(960f, 540f, 59f / 86f, roomAbove = 0.20f),
        ).forEach { (where, layout) ->
            val fan = PileFan.spread(40, layout.field, layout.cardWidth, layout.cardHeight)
            assertEquals(4, fan.rows, "$where: forty cards came out as ${fan.rows} rows")
            fan.cards.forEach {
                assertTrue(
                    it.y - layout.cardHeight / 2f >= layout.field.top - 0.5f &&
                        it.y + layout.cardHeight / 2f <= layout.field.bottom + 0.5f,
                    "$where: a card at ${it.y} is off the field",
                )
            }
        }
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

    // ---- and it makes room for a card being carried over it -------------------------

    @Test
    fun aRowPartsWhereTheCardIsGoingAndLeavesItACardOfClearFelt() {
        // kai's first report, second half: *"the fan needs to dynamically move
        // and make space for wherever the hovered card is going."* A six-card
        // graveyard has three card widths of slack, so the window opens on
        // slack alone and nothing is squeezed.
        val plain = spread(6)
        val at = plain.cards[2].x + 30f
        val parted = PileFan.spread(6, field, cardWidth, cardHeight, FanParting(at, plain.cards[2].y))

        val left = parted.cards.filter { it.x <= at }.maxOf { it.x }
        val right = parted.cards.filter { it.x > at }.minOf { it.x }
        assertTrue(at - left >= cardWidth * 0.6f, "the left side did not move: ${at - left}")
        assertTrue(right - at >= cardWidth * 0.6f, "the right side did not move: ${right - at}")
    }

    @Test
    fun aRowWithNoSlackOpensItsWindowBySqueezingInstead() {
        // The case that needs it most and can afford it least. A board exactly
        // wide enough for ten cards has nothing to slide into, so the side gives
        // up its own overlap instead — which is what a hand does to a fanned
        // pile, and is the whole reason the window is paid for in two
        // currencies rather than one.
        val narrow = Slot(left = 100f, top = 60f, width = 800f, height = 620f)
        val plain = PileFan.spread(40, narrow, cardWidth, cardHeight)
        val row = plain.cards.filter { it.row == 1 }
        assertEquals(
            narrow.width,
            row.maxOf { it.x } - row.minOf { it.x } + cardWidth,
            1f,
            "the fixture has slack after all, so it is testing the other currency",
        )

        val at = row[4].x + plain.step / 2f
        val parted = PileFan.spread(40, narrow, cardWidth, cardHeight, FanParting(at, row[4].y))
        val partedRow = parted.cards.filter { it.row == 1 }

        val left = partedRow.filter { it.x <= at }.maxOf { it.x }
        val right = partedRow.filter { it.x > at }.minOf { it.x }
        assertTrue(right - left > cardWidth, "the window is ${right - left}, under a card")
        assertTrue(
            partedRow.maxOf { it.x } - partedRow.minOf { it.x } <=
                row.maxOf { it.x } - row.minOf { it.x } + 1f,
            "with no slack the row still got wider",
        )
    }

    @Test
    fun aPartedRowNeverLeavesTheAreaItWasSpreadOver() {
        // The invariant under both currencies. Slack is spent first and it is
        // measured against this rectangle, so a window can never push a card off
        // the table however hard it is asked to open.
        listOf(6, 15, 40, 60).forEach { count ->
            listOf(field, Slot(100f, 60f, 800f, 620f)).forEach { over ->
                val plain = PileFan.spread(count, over, cardWidth, cardHeight)
                plain.cards.forEach { probe ->
                    val parted = PileFan.spread(
                        count, over, cardWidth, cardHeight,
                        FanParting(probe.x, probe.y, holding = probe.index),
                    )
                    parted.cards.forEach {
                        assertTrue(
                            it.x - cardWidth / 2f >= over.left - 1f &&
                                it.x + cardWidth / 2f <= over.right + 1f,
                            "$count over ${over.width}: card ${it.index} ran off at ${it.x}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun apartedRowKeepsItsOrderAndKeepsEveryCardFindable() {
        // Two claims that are really one: `cardAt` answers with the *last* card
        // whose footprint covers the point, so a row that reordered itself would
        // hand back the wrong index — silently, and only while something is
        // being carried.
        listOf(6, 15, 40, 60).forEach { count ->
            val plain = spread(count)
            plain.cards.forEach { probe ->
                val parted = PileFan.spread(
                    count, field, cardWidth, cardHeight,
                    FanParting(probe.x + 20f, probe.y, holding = probe.index),
                )
                parted.cards.groupBy { it.row }.forEach { (row, cards) ->
                    cards.zipWithNext { a, b ->
                        assertTrue(
                            b.x > a.x,
                            "$count cards, row $row: ${a.index} at ${a.x} and ${b.index} at ${b.x}",
                        )
                    }
                }
                parted.cards.forEach { card ->
                    assertEquals(
                        card.index,
                        PileFan.cardAt(parted, card.x, card.y, cardWidth, cardHeight),
                        "$count cards: card ${card.index} is not found at its own centre",
                    )
                }
            }
        }
    }

    @Test
    fun theSlotTheCardCameOutOfDoesNotMove() {
        // Because `FanHome` wrote its position down at the lift, on purpose: a
        // put-back target that walked away as you approached it would not be a
        // target. It is also the card in your hand, so it is not drawn and
        // nothing is lost by leaving it be.
        listOf(6, 15, 40).forEach { count ->
            val plain = spread(count)
            plain.cards.forEach { held ->
                val parted = PileFan.spread(
                    count, field, cardWidth, cardHeight,
                    FanParting(held.x + cardWidth * 0.3f, held.y, holding = held.index),
                )
                assertEquals(
                    held.x,
                    parted.cards[held.index].x,
                    1e-3f,
                    "$count cards: slot ${held.index} was pushed aside",
                )
            }
        }
    }

    @Test
    fun aRowTheCardIsNowhereNearDoesNotMoveAtAll() {
        val plain = spread(40)
        val far = plain.cards.first { it.row == 0 }
        val parted = PileFan.spread(40, field, cardWidth, cardHeight, FanParting(far.x, far.y))

        parted.cards.filter { it.row >= 2 }.forEach {
            assertEquals(plain.cards[it.index].x, it.x, 1e-3f, "row ${it.row} moved")
        }
    }

    @Test
    fun theFootprintIsTheOneTheFanHasWhenNothingIsOverIt() {
        // `bounds` is what says where the fan stops and the table starts. A
        // footprint that breathed as the spread got out of the way would let go
        // of the gesture that was making it move.
        val plain = spread(40)
        val parted = PileFan.spread(
            40, field, cardWidth, cardHeight,
            FanParting(plain.cards[15].x, plain.cards[15].y),
        )
        assertEquals(plain.bounds, parted.bounds)
    }

    @Test
    fun withNothingCarriedOverItTheSpreadIsExactlyWhatItAlwaysWas() {
        listOf(1, 6, 15, 40, 60).forEach { count ->
            assertEquals(spread(count), PileFan.spread(count, field, cardWidth, cardHeight, null))
        }
    }
}
