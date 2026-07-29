package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidingHabitsTest {

    private val droll = CardId(94145021)
    private val maxx = CardId(23434538)
    private val imperm = CardId(10045474)
    private val nibiru = CardId(27204311)

    private fun plan(name: String, out: List<CardId>, into: List<CardId>) = SidingPattern(
        deckName = name,
        goingFirst = SidingSwap(cardsIn = into, cardsOut = out),
    )

    private val shelf = listOf(
        plan("Snake-Eye", out = List(3) { maxx }, into = List(3) { droll }),
        plan("Ryzeal", out = List(2) { maxx } + imperm, into = List(3) { droll }),
        plan("Fiendsmith", out = List(3) { maxx }, into = List(2) { droll } + nibiru),
    )

    @Test
    fun aCardEveryPlanCutsIsFound() {
        // Three matchups, and all three begin by taking the same card out. One
        // plan at a time that is invisible; across the shelf it is the answer to
        // a question nobody asked the program.
        assertEquals(listOf(maxx), SidingHabits.alwaysCut(shelf))
    }

    @Test
    fun andACardEveryPlanBringsIn() {
        // A Side deck slot arguing to be a Main deck one.
        assertEquals(listOf(droll), SidingHabits.alwaysBroughtIn(shelf))
    }

    @Test
    fun aPlanCountsOnceForACardHoweverManyCopiesItMoves() {
        // The question is how many *matchups* treat a card a certain way. Three
        // copies out is still one matchup that cuts it.
        val habit = SidingHabits.of(shelf).first { it.id == maxx }

        assertEquals(3, habit.cutBy)
    }

    @Test
    fun andOnceHoweverManyHalvesOfTheMatchDoIt() {
        val both = SidingPattern(
            deckName = "Mirror",
            goingFirst = SidingSwap(cardsOut = listOf(maxx)),
            goingSecond = SidingSwap(cardsOut = List(2) { maxx }),
        )

        assertEquals(1, SidingHabits.of(listOf(both)).single { it.id == maxx }.cutBy)
    }

    @Test
    fun twoPlansAgreeingIsNotAHabit() {
        // A sample size of two is a coincidence, and the whole value of this is
        // in what eight matchups say together.
        val two = shelf.take(2)

        assertTrue(SidingHabits.alwaysCut(two).isEmpty())
        assertTrue(SidingHabits.alwaysBroughtIn(two).isEmpty())
    }

    @Test
    fun aCardOnlySomePlansTouchIsNotAHabitEither() {
        assertTrue(imperm !in SidingHabits.alwaysCut(shelf))
        assertTrue(nibiru !in SidingHabits.alwaysBroughtIn(shelf))
    }

    @Test
    fun theMostCutComesFirst() {
        val order = SidingHabits.of(shelf).map { it.id }

        assertEquals(maxx, order.first())
    }

    @Test
    fun nothingAtAllIsNothingAtAll() {
        assertTrue(SidingHabits.of(emptyList()).isEmpty())
        assertTrue(SidingHabits.alwaysCut(emptyList()).isEmpty())
    }

    @Test
    fun aShelfOfEmptyPlansHasNoHabits() {
        val blanks = List(4) { SidingPattern(deckName = "Plan $it") }

        assertTrue(SidingHabits.of(blanks).isEmpty())
        assertTrue(SidingHabits.alwaysCut(blanks).isEmpty())
    }

    @Test
    fun everyCardTheyTouchIsAccountedFor() {
        val seen = SidingHabits.of(shelf).map { it.id }.toSet()

        assertEquals(setOf(maxx, imperm, droll, nibiru), seen)
    }
}
