package com.kaiharimoto.mastertool.ui.deckbuilder

import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.ImportedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

/**
 * The state holder, driven directly.
 *
 * Everything here was written without a compiler, in an environment where this
 * module does not build — so these are the first tests that say the behaviour
 * is right rather than merely that it compiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckBuilderStateTest {

    private val main = DeckSection.MAIN

    // ---- selection ---------------------------------------------------------

    @Test
    fun selectingBuildsAGroupAndClearingEndsIt() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }

        state.select(main, 0)
        state.toggleSelected(main, 2)
        assertEquals(2, state.selection.size)

        state.clearSelection()
        assertTrue(state.selection.isEmpty)
    }

    @Test
    fun aRangeCoversEverythingBetween() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        state.select(main, 1)
        state.selectThrough(main, 4)

        assertEquals(setOf(1, 2, 3, 4), state.selection.indices)
    }

    @Test
    fun aBlockIsARectangle() = runTest {
        val state = builderState()
        TestPool.many(12).forEach { state.addCard(it) }

        state.select(main, 0)
        state.selectBlockThrough(main, index = 5, columns = 4)

        // Columns 0..1 over rows 0..1.
        assertEquals(setOf(0, 1, 4, 5), state.selection.indices)
    }

    @Test
    fun aBlockCannotSelectPositionsThatDoNotExist() = runTest {
        val state = builderState()
        TestPool.many(5).forEach { state.addCard(it) }

        state.select(main, 0)
        state.selectBlockThrough(main, index = 4, columns = 4)

        assertTrue(state.selection.indices.all { it < 5 })
    }

    @Test
    fun editingTheDeckDropsTheSelection() = runTest {
        // Enforced by the deck's own setter rather than at each call site, so
        // this is the test that the enforcement actually reaches all of them.
        val state = builderState()
        val cards = TestPool.many(4)
        cards.forEach { state.addCard(it) }
        state.select(main, 1)
        state.selectThrough(main, 3)

        state.removeOne(cards.first(), main)

        assertTrue(state.selection.isEmpty, "a stale selection would move the wrong cards")
    }

    @Test
    fun aGroupOnlyTravelsWhenThereIsMoreThanOneOfIt() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }

        state.select(main, 1)
        assertFalse(state.dragCarriesSelection(main, 1), "one card is not a group")

        state.toggleSelected(main, 2)
        assertTrue(state.dragCarriesSelection(main, 1))
        assertFalse(state.dragCarriesSelection(main, 3), "a card outside the group travels alone")
    }

    @Test
    fun movingAGroupKeepsEveryCard() = runTest {
        val state = builderState()
        listOf(TestPool.ash, TestPool.maxx, TestPool.nibiru, TestPool.droll)
            .forEach { state.addCard(it) }
        val before = state.deck.main

        state.select(main, 0)
        state.toggleSelected(main, 1)
        state.moveSelectionTo(main, insertBefore = 4)

        assertEquals(before.sortedBy { it.value }, state.deck.main.sortedBy { it.value })
        assertEquals(listOf(TestPool.ash.id, TestPool.maxx.id), state.deck.main.takeLast(2))
    }

    @Test
    fun movingAGroupIsOneUndo() = runTest {
        val state = builderState()
        listOf(TestPool.ash, TestPool.maxx, TestPool.nibiru).forEach { state.addCard(it) }
        val before = state.deck.main

        state.select(main, 0)
        state.toggleSelected(main, 1)
        state.moveSelectionTo(main, insertBefore = 3)
        assertTrue(state.deck.main != before)

        state.undo()
        assertEquals(before, state.deck.main)
    }

    // ---- siding ------------------------------------------------------------

    private val sidedFile = ImportedFile(
        name = "lab.ydkx",
        content = """
            #main
            14558127
            23434538
            23434538
            27204311
            #extra
            !side
            94145021
            94145021
            #ydkx-extended
            {
              "version": "1.0",
              "somethingThisAppDoesNotKnow": 42,
              "sidingPatterns": {
                "Snake-Eye": {
                  "deckName": "Snake-Eye",
                  "backgroundColor": "linear-gradient(135deg, #ea580c 0%, #c2410c 100%)",
                  "thumbnails": ["14558127"],
                  "goingFirst": { "in": ["94145021", "94145021"], "out": ["23434538", "23434538"] },
                  "goingSecond": { "in": ["94145021"], "out": ["27204311"] }
                }
              }
            }
        """.trimIndent(),
    )

    @Test
    fun importingAYdkxBringsItsPlansWithIt() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()

        assertEquals(setOf("Snake-Eye"), state.sidingPatterns.keys)
    }

    @Test
    fun sidingSwapsTheDeckOverAndUndoPutsItBack() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        val before = state.deck

        state.applySiding(state.sidingPatterns.getValue("Snake-Eye"), goingFirst = true)

        assertEquals(0, state.deck.main.count { it == TestPool.maxx.id })
        assertEquals(2, state.deck.main.count { it == TestPool.droll.id })
        assertEquals(before.main.size, state.deck.main.size, "a balanced swap keeps the size")

        state.undo()
        assertEquals(before, state.deck)
    }

    @Test
    fun sidingClosesThePanelItWasAppliedFrom() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        state.sidingVisible = true

        state.applySiding(state.sidingPatterns.getValue("Snake-Eye"), goingFirst = false)

        assertFalse(state.sidingVisible)
    }

    @Test
    fun anExportedDeckStillCarriesWhatThisAppDoesNotUnderstand() = runTest {
        // The README's promise, from the outside: import, side, export, and the
        // desktop tool's own keys are still in the file.
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        state.applySiding(state.sidingPatterns.getValue("Snake-Eye"), goingFirst = true)
        state.exportToFile()

        val written = requireNonNull(files.exported)
        assertTrue(written.contains("somethingThisAppDoesNotKnow"), written)
        assertTrue(written.contains("backgroundColor"), written)
        assertTrue(written.contains("#ydkx-extended"), written)
    }

    // ---- recording a plan --------------------------------------------------

    @Test
    fun aFreshlyImportedDeckHasNothingPending() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()

        assertTrue(state.pendingSwap.isEmpty)
    }

    @Test
    fun swappingByHandShowsUpAsAPendingPlan() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()

        // Exactly what a player does: move a card out, bring one in.
        state.moveCard(TestPool.maxx, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)

        assertEquals(listOf(TestPool.droll.id), state.pendingSwap.cardsIn)
        assertEquals(listOf(TestPool.maxx.id), state.pendingSwap.cardsOut)
    }

    @Test
    fun recordingKeepsThePlanAndClearsThePanel() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        state.moveCard(TestPool.maxx, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)

        state.sidingVisible = true
        state.recordingGoingFirst = false
        state.recordSiding("Branded Despia")

        val saved = state.sidingPatterns.getValue("Branded Despia")
        assertEquals(listOf(TestPool.droll.id), saved.goingSecond.cardsIn)
        assertTrue(saved.goingFirst.isEmpty, "only the half being recorded is written")
        assertFalse(state.sidingVisible)
    }

    @Test
    fun bothHalvesOfAMatchupCanBeRecordedSeparately() = runTest {
        // Captured in two sittings, which is how it actually happens.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()

        state.moveCard(TestPool.maxx, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)
        state.recordingGoingFirst = true
        state.recordSiding("Ryzeal")

        state.resetToRegistered()
        state.moveCard(TestPool.nibiru, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)
        state.recordingGoingFirst = false
        state.recordSiding("Ryzeal")

        val saved = state.sidingPatterns.getValue("Ryzeal")
        assertEquals(listOf(TestPool.maxx.id), saved.goingFirst.cardsOut)
        assertEquals(listOf(TestPool.nibiru.id), saved.goingSecond.cardsOut)
    }

    @Test
    fun aRecordedPlanCanBeAppliedBack() = runTest {
        // The point of recording: what was captured has to reproduce what was
        // done, from the registered list.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        state.moveCard(TestPool.maxx, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)
        val sided = state.deck

        state.recordSiding("Snake-Eye Mirror")
        state.resetToRegistered()
        state.applySiding(state.sidingPatterns.getValue("Snake-Eye Mirror"), goingFirst = true)

        assertEquals(
            sided.main.sortedBy { it.value },
            state.deck.main.sortedBy { it.value },
        )
    }

    @Test
    fun recordingNothingRecordsNothing() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()

        state.recordSiding("Nothing Happened")

        assertTrue("Nothing Happened" !in state.sidingPatterns.keys)
    }

    @Test
    fun aRecordedPlanTravelsInTheExportedFile() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        state.moveCard(TestPool.maxx, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)
        state.recordSiding("Fiendsmith")
        state.exportToFile()

        val written = requireNonNull(files.exported)
        assertTrue(written.contains("Fiendsmith"), written)
        // And the desktop's own keys are still there beside it.
        assertTrue(written.contains("somethingThisAppDoesNotKnow"), written)
    }

    // ---- test hands --------------------------------------------------------

    @Test
    fun dealingTakesFiveOrSixOffTheTop() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }

        state.dealTestHand(goingFirst = true)
        assertEquals(5, state.testHand!!.size)

        state.dealTestHand(goingFirst = false)
        assertEquals(6, state.testHand!!.size)
    }

    @Test
    fun aDealtHandOnlyHoldsCardsFromTheDeck() = runTest {
        val state = builderState()
        val cards = TestPool.many(40)
        cards.forEach { state.addCard(it) }
        state.dealTestHand(goingFirst = true)

        val ids = cards.map { it.id }.toSet()
        assertTrue(state.testHand!!.cards.all { it in ids })
    }

    @Test
    fun judgingCountsAndDealsTheNextHand() = runTest {
        // The loop: look, judge, next. Anything that made you press deal again
        // would halve how many hands you actually look at.
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.dealTestHand(goingFirst = true)

        state.judgeTestHand(playable = true)
        state.judgeTestHand(playable = false)

        assertEquals(1, state.handTally.playable)
        assertEquals(1, state.handTally.bricks)
        assertEquals(0.5, state.handTally.brickRate!!, 0.0001)
        assertTrue(state.testHand != null, "a new hand should already be waiting")
    }

    @Test
    fun judgingWithNoHandDoesNothing() = runTest {
        val state = builderState()
        state.judgeTestHand(playable = false)

        assertEquals(0, state.handTally.total)
    }

    @Test
    fun drawingOneAddsToTheHandFromUnderneath() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.dealTestHand(goingFirst = true)

        state.drawOneMore()

        assertEquals(6, state.testHand!!.size)
    }

    @Test
    fun theTallySurvivesReshufflingAndIsClearedByHand() = runTest {
        // It only means something over a run of hands, so a reshuffle must not
        // quietly reset it.
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.dealTestHand(goingFirst = true)
        state.judgeTestHand(playable = false)

        state.dealTestHand(goingFirst = true)
        assertEquals(1, state.handTally.total)

        state.resetHandTally()
        assertEquals(0, state.handTally.total)
    }

    @Test
    fun anEmptyDeckDealsNothingRatherThanFailing() = runTest {
        val state = builderState()
        state.dealTestHand(goingFirst = true)

        assertEquals(0, state.testHand!!.size)
    }

    // ---- the deal ----------------------------------------------------------

    @Test
    fun addingACardIsNotADeal() = runTest {
        // The panes deal on this changing. Re-dealing forty cards because a
        // forty-first arrived would be a party trick.
        val state = builderState()
        val before = state.dealSerial

        state.addCard(TestPool.ash)
        state.removeOne(TestPool.ash, main)

        assertEquals(before, state.dealSerial)
    }

    @Test
    fun importingADeckIsADeal() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        val before = state.dealSerial

        state.importFromFile()

        assertTrue(state.dealSerial > before)
    }

    private fun requireNonNull(value: String?): String =
        value ?: error("nothing was exported")
}
