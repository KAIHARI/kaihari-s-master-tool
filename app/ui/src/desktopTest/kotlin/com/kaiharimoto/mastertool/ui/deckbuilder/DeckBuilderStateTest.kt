package com.kaiharimoto.mastertool.ui.deckbuilder

import com.kaiharimoto.mastertool.core.deck.Breaks
import com.kaiharimoto.mastertool.core.deck.SaveStatus
import com.kaiharimoto.mastertool.core.deck.TidyBy
import com.kaiharimoto.mastertool.core.deck.GroupOdds
import com.kaiharimoto.mastertool.core.deck.Selections
import com.kaiharimoto.mastertool.core.deck.MatChoice
import com.kaiharimoto.mastertool.core.export.Png
import com.kaiharimoto.mastertool.core.layout.GridStep
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.DeckFileAccess
import com.kaiharimoto.mastertool.ui.ImportedFile
import com.kaiharimoto.mastertool.ui.WriteOutcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Test
    fun aMovedGroupStaysSelectedWhereItLanded() = runTest {
        // The one edit that knows exactly where the cards went, so it is the one
        // that can put the selection back -- which is what lets a group be
        // nudged twice without picking it up again.
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        state.select(main, 0)
        state.toggleSelected(main, 1)
        state.moveSelectionTo(main, insertBefore = 6)

        assertEquals(setOf(4, 5), state.selection.indices)
        assertEquals(main, state.selection.section)
    }

    @Test
    fun aGroupCanBeNudgedTwice() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }

        state.select(main, 0)
        state.toggleSelected(main, 1)
        state.moveSelectionTo(main, insertBefore = 6)
        state.moveSelectionTo(main, insertBefore = 0)

        assertEquals(listOf(cards[0].id, cards[1].id), state.deck.main.take(2))
        assertEquals(setOf(0, 1), state.selection.indices)
    }

    // ---- the deck across the table ------------------------------------------

    @Test
    fun anOpponentFileBringsItsOwnSidingPlansWithIt() = runTest {
        // A list downloaded from somebody who plays the deck often arrives with
        // what they actually side, which beats guessing and is already there.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.loadOpponent()
        advanceUntilIdle()

        assertEquals(setOf("Snake-Eye"), state.opponentPlans.keys)
        assertFalse(state.opponentSided)
    }

    @Test
    fun aPlainOpponentFileOffersNothingRatherThanSomethingInvented() = runTest {
        val plain = ImportedFile("meta.ydk", "#main\n14558127\n23434538\n#extra\n!side\n")
        val state = builderState(testDependencies(StubFileAccess(plain)))
        state.loadOpponent()
        advanceUntilIdle()

        assertTrue(state.opponentPlans.isEmpty())
    }

    @Test
    fun theirDeckChangesWhenTheySide() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.loadOpponent()
        advanceUntilIdle()
        val before = state.opponentDeck

        state.sideOpponent(state.opponentPlans.getValue("Snake-Eye"), goingFirst = true)

        assertTrue(state.opponentSided)
        assertTrue(state.opponentDeck != before, "their list is not the one it was")
    }

    @Test
    fun theirHandIsDealtAgainWhenTheirDeckChanges() = runTest {
        // The same trap that made your own side re-deal: a hand already on the
        // table came off the list they were playing a moment ago.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.loadOpponent()
        advanceUntilIdle()
        val side = requireNotNull(state.theirOpening).goingFirst

        state.sideOpponent(state.opponentPlans.getValue("Snake-Eye"), goingFirst = true)

        assertEquals(side, state.theirOpening?.goingFirst, "same side of the die roll")
        assertTrue(
            state.theirOpening!!.cards.all { it in state.opponentDeck.main },
            "dealt from the list they are actually playing",
        )
    }

    @Test
    fun theirListGoesBackForGameOneOfTheNextTrial() = runTest {
        // Leaving them sided would compare your opening list against their
        // game-two list, which is a match that never happens.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        advanceUntilIdle()
        val registered = state.opponentDeck

        state.startRun(trials = 3)
        state.judgeShootout(playable = true)
        state.sideOpponent(state.opponentPlans.getValue("Snake-Eye"), goingFirst = true)
        assertTrue(state.opponentSided)

        // Game two judged, so the next deal is game one of trial two.
        state.judgeShootout(playable = true)

        assertFalse(state.opponentSided)
        assertEquals(registered, state.opponentDeck)
    }

    @Test
    fun endingARunPutsTheirListBackToo() = runTest {
        // Loose judging says nothing about which version of anything it is
        // looking at, so it had better be looking at the one that was loaded.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        advanceUntilIdle()
        val registered = state.opponentDeck

        state.startRun(trials = 3)
        state.judgeShootout(playable = true)
        state.sideOpponent(state.opponentPlans.getValue("Snake-Eye"), goingFirst = true)
        state.endRun()

        assertFalse(state.opponentSided)
        assertEquals(registered, state.opponentDeck)
    }

    @Test
    fun aNewRunStartsAgainstTheirRegisteredList() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        advanceUntilIdle()
        val registered = state.opponentDeck
        state.sideOpponent(state.opponentPlans.getValue("Snake-Eye"), goingFirst = true)

        state.startRun(trials = 3)

        assertFalse(state.opponentSided)
        assertEquals(registered, state.opponentDeck)
    }

    @Test
    fun puttingTheirListBackWithNothingSidedIsHarmless() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.loadOpponent()
        advanceUntilIdle()
        val before = state.opponentDeck

        state.unsideOpponent()

        assertEquals(before, state.opponentDeck)
    }

    // ---- shootout runs -----------------------------------------------------

    @Test
    fun aRunStartsPreSideOnTheFirstTrial() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }

        state.startRun(trials = 3)

        val run = assertNotNull(state.shootoutRun)
        assertEquals(1, run.nextTrial)
        assertFalse(run.nextIsSided, "game one is the registered list")
        assertFalse(state.deckIsSided, "starting a run registers the list it is about")
        assertNotNull(state.yourOpening, "a run deals its first opening immediately")
    }

    @Test
    fun judgingGameOneAsksForTheSidedList() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.startRun(trials = 3)

        state.judgeShootout(playable = true)

        val run = assertNotNull(state.shootoutRun)
        assertEquals(1, run.nextTrial)
        assertEquals(2, run.nextGame)
        assertTrue(run.nextIsSided)
    }

    @Test
    fun sidingKeepsTheRunAndDeckBuildingEndsIt() = runTest {
        val state = builderState()
        val cards = TestPool.many(40)
        cards.forEach { state.addCard(it) }
        state.startRun(trials = 3)
        state.judgeShootout(playable = true)

        state.moveCard(cards[0], DeckSection.MAIN, DeckSection.SIDE)
        assertNotNull(state.shootoutRun, "a swap is the thing the run is measuring")
        assertTrue(state.deckIsSided)

        state.removeOne(cards[1], DeckSection.MAIN)
        assertNull(state.shootoutRun, "taking a card out of the building is not siding")
    }

    @Test
    fun sidingDealsYourOpeningAgainFromTheListYouWillActuallyPlay() = runTest {
        // The hand for game two is dealt the moment game one is judged, which
        // is before anybody sides. Left alone it would be a pre-side hand
        // recorded as post-side, and it would look entirely normal.
        val state = builderState()
        // Sixteen, because the Side deck holds fifteen: everything but one card
        // moves across, and then the opening can only be that one card.
        val cards = TestPool.many(16)
        cards.forEach { state.addCard(it) }
        state.startRun(trials = 3)
        state.judgeShootout(playable = true)
        val dealtBeforeSiding = assertNotNull(state.yourOpening)

        cards.drop(1).forEach { state.moveCard(it, DeckSection.MAIN, DeckSection.SIDE) }

        val now = assertNotNull(state.yourOpening)
        assertEquals(listOf(cards[0].id), now.cards, "dealt from the one card left")
        assertEquals(dealtBeforeSiding.goingFirst, now.goingFirst, "same side of the die roll")
    }

    @Test
    fun theReportKeepsTheTwoVersionsOfTheListApart() = runTest {
        val state = builderState()
        val cards = TestPool.many(40)
        cards.forEach { state.addCard(it) }
        state.startRun(trials = 3)

        state.judgeShootout(playable = false)
        state.moveCard(cards[0], DeckSection.MAIN, DeckSection.SIDE)
        state.judgeShootout(playable = true)

        val report = assertNotNull(state.shootoutRun).report()
        assertEquals(1, report.preSide.total)
        assertEquals(1.0, report.preSide.brickRate)
        assertEquals(1, report.postSide.total)
        assertEquals(0.0, report.postSide.brickRate)
    }

    @Test
    fun forgettingToSideIsRecordedAsWhatItWas() = runTest {
        // The run asks for a sided deck and nothing enforces it. What gets
        // recorded is the deck that dealt the hand, so the report stays true.
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.startRun(trials = 3)

        state.judgeShootout(playable = true)
        state.judgeShootout(playable = true)

        val report = assertNotNull(state.shootoutRun).report()
        assertEquals(2, report.preSide.total)
        assertEquals(0, report.postSide.total)
    }

    @Test
    fun aFinishedRunStopsDealing() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.startRun(trials = 1)

        val run = assertNotNull(state.shootoutRun)
        repeat(run.length) { state.judgeShootout(playable = true) }

        val finished = assertNotNull(state.shootoutRun)
        assertTrue(finished.finished)
        assertEquals(run.length, finished.played)
    }

    @Test
    fun undoingATrialThrowsAwayItsGameOneToo() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.startRun(trials = 3)
        state.judgeShootout(playable = true)
        state.judgeShootout(playable = true)
        state.judgeShootout(playable = false)

        state.undoTrial()

        val run = assertNotNull(state.shootoutRun)
        assertEquals(2, run.played, "only the third game was part of the trial in progress")
        assertEquals(2, run.nextTrial)
    }

    @Test
    fun aVerdictInARunCanBeTakenBack() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.startRun(trials = 3)
        state.judgeShootout(playable = false)

        assertTrue(state.canUndoVerdict)
        state.undoVerdict()

        assertEquals(0, assertNotNull(state.shootoutRun).played)
        assertFalse(state.canUndoVerdict, "one step only")
    }

    // ---- gaps in the arrangement --------------------------------------------

    @Test
    fun aGapIsPutInAndTakenAwayAgain() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        state.toggleBreak(main, 3)
        assertEquals(setOf(3), state.breaksIn(main).before)

        state.toggleBreak(main, 3)
        assertTrue(state.breaksIn(main).isEmpty)
    }

    @Test
    fun aGapCannotBeDrawnAtEitherEndOfASection() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }

        state.toggleBreak(main, 0)
        state.toggleBreak(main, 4)
        state.toggleBreak(main, 9)

        assertTrue(state.breaksIn(main).isEmpty)
    }

    @Test
    fun addingACardNeverDisturbsAGap() = runTest {
        // Which is most of what happens to a deck being built, so it had better
        // be the case that costs nothing.
        val state = builderState()
        val cards = TestPool.many(8)
        cards.take(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.addCard(cards[6])

        assertEquals(setOf(3), state.breaksIn(main).before)
    }

    @Test
    fun cuttingACardFromTheFirstGroupPullsTheGapBack() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.removeOne(cards[0], main)

        assertEquals(setOf(2), state.breaksIn(main).before)
    }

    @Test
    fun undoingAnEditWalksTheGapBackToo() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.removeOne(cards[0], main)
        state.undo()

        assertEquals(setOf(3), state.breaksIn(main).before)
    }

    @Test
    fun aGapBeyondTheEndOfASectionIsNotShown() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.toggleBreak(main, 5)

        cards.drop(1).forEach { state.removeOne(it, main) }

        assertTrue(state.breaksIn(main).isEmpty)
    }

    @Test
    fun theCursorIsWhereAGapGoesFromTheKeyboard() = runTest {
        // The selection is the cursor, as it is for every other keyboard move,
        // so this needs no separate notion of where I am.
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.select(main, 4)

        state.toggleGapAtCursor()

        assertEquals(setOf(4), state.breaksIn(main).before)
    }

    @Test
    fun withSeveralCardsPickedUpTheGapGoesAtTheEndThatMoves() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.select(main, 1)
        state.selectThrough(main, 3)

        state.toggleGapAtCursor()

        assertEquals(setOf(3), state.breaksIn(main).before)
    }

    @Test
    fun aGapFromTheKeyboardWithNothingSelectedDoesNothing() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        state.toggleGapAtCursor()

        assertTrue(state.breaksIn(main).isEmpty)
    }

    @Test
    fun undoingANewDeckBringsTheGapsBackWithTheCards() = runTest {
        // Undo restores the order the cards were in, and an arrangement without
        // its gaps is not the arrangement that was there.
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.newDeck()
        assertTrue(state.breaksIn(main).isEmpty)

        requireNonNull(state.toast?.message)
        state.toast?.undo?.invoke()

        assertEquals(6, state.deck.main.size)
        assertEquals(setOf(3), state.breaksIn(main).before)
    }

    @Test
    fun everyGapInASectionCanBeTakenAwayAtOnce() = runTest {
        // What a sort does to the arrangement it replaced. The tidy and sort
        // paths themselves need a card pool to look cards up in, and this
        // fixture deliberately has none -- that half is held down in :core,
        // where DeckTidy.arrange is asked directly what gaps it draws.
        val state = builderState()
        TestPool.many(8).forEach { state.addCard(it) }
        state.toggleBreak(main, 2)
        state.toggleBreak(main, 5)
        assertEquals(setOf(2, 5), state.breaksIn(main).before)

        state.clearBreaks(main)

        assertTrue(state.breaksIn(main).isEmpty)
    }

    @Test
    fun gapsInOneSectionLeaveTheOthersAlone() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        cards.take(3).forEach { state.moveCard(it, main, DeckSection.SIDE) }
        state.toggleBreak(main, 1)
        state.toggleBreak(DeckSection.SIDE, 2)

        state.clearBreaks(main)

        assertTrue(state.breaksIn(main).isEmpty)
        assertEquals(setOf(2), state.breaksIn(DeckSection.SIDE).before)
    }

    @Test
    fun aDeckWithNoGapsCarriesNothingAboutThem() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        TestPool.many(4).forEach { state.addCard(it) }

        state.exportToFile()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue("arrangement" !in written, written)
    }

    @Test
    fun theGapsGoOutWithTheFileAndComeBackWithIt() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        TestPool.many(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.exportToFile()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue("arrangement" in written, written)

        // Straight back in through the front door.
        val reopened = builderState(testDependencies(StubFileAccess(ImportedFile("d.ydkx", written))))
        reopened.importFromFile()
        advanceUntilIdle()

        assertEquals(Breaks(setOf(3)), reopened.breaksIn(main))
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

    @Test
    fun theSidingSheetGoesOutAsARealPdf() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()

        state.exportSidingSheet()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue(written.startsWith("%PDF"), written.take(40))
        assertTrue(written.trimEnd().endsWith("%%EOF"))
        assertTrue("Snake-Eye" in written, "the matchup the file carries")
        assertTrue(
            written.all { it.code in 0..127 },
            "the writer keeps the whole file inside ASCII, which is what makes " +
                "string length and byte offset the same number",
        )
        assertEquals("application/pdf", files.exportedType)
        assertTrue(requireNonNull(files.exportedName).endsWith(".pdf"))
    }

    @Test
    fun aDeckWithNoPlansWritesNoSheet() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        TestPool.many(4).forEach { state.addCard(it) }

        state.exportSidingSheet()
        advanceUntilIdle()

        assertEquals(null, files.exported, "a file saying nothing is worse than no file")
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

    @Test
    fun aPlanRecordedByMistakeCanBeTakenBackOut() = runTest {
        // Recording had no opposite, and a plan is not harmless to be stuck
        // with: it prints on the sheet and one press applies it to the deck.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        advanceUntilIdle()
        assertTrue("Snake-Eye" in state.sidingPatterns)

        state.forgetSiding("Snake-Eye")

        assertFalse("Snake-Eye" in state.sidingPatterns)
    }

    @Test
    fun forgettingAPlanCanBeTakenBack() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        advanceUntilIdle()

        state.forgetSiding("Snake-Eye")
        state.toast?.undo?.invoke()

        assertTrue("Snake-Eye" in state.sidingPatterns)
    }

    @Test
    fun forgettingAPlanThatWasNeverThereDoesNothing() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.importFromFile()
        advanceUntilIdle()
        val before = state.sidingPatterns

        state.forgetSiding("A deck nobody plays")

        assertEquals(before, state.sidingPatterns)
    }

    @Test
    fun aForgottenPlanIsGoneFromTheExportedFileToo() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        advanceUntilIdle()

        state.forgetSiding("Snake-Eye")
        state.exportToFile()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertFalse("Snake-Eye" in written, written.takeLast(400))
        // And the keys this app has never heard of are still in there.
        assertTrue("somethingThisAppDoesNotKnow" in written)
    }

    @Test
    fun takingBackAnImportPutsTheWholeDeckBack() = runTest {
        // Not only the cards. Undoing an import used to leave the imported
        // file's siding plans, gaps and mat attached to the deck it had just
        // restored, which is a deck that never existed.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(4).forEach { state.addCard(it) }
        val card = state.deck.main.first()
        state.toggleBreak(main, 2)
        state.writeNote(card, "kept")
        state.chooseMat(MatChoice.BAIZE)
        val before = state.deck

        state.importFromFile()
        advanceUntilIdle()
        assertTrue("Snake-Eye" in state.sidingPatterns, "the import brought its plans")

        state.toast?.undo?.invoke()

        assertEquals(before, state.deck)
        assertEquals(setOf(2), state.breaksIn(main).before)
        assertEquals("kept", state.noteOn(card))
        assertEquals(MatChoice.BAIZE, state.mat)
        assertFalse("Snake-Eye" in state.sidingPatterns, "the imported plans came back with it")
    }

    @Test
    fun everythingAboutADeckSurvivesBeingSavedAndOpenedAgain() = runTest {
        // The round trip nothing covered: the gaps, the note on a card and the
        // cloth all live in the payload, and a deck is opened by a different
        // path from the one that puts one back. Both go through `reopen` now,
        // and this is what says so.
        val state = builderState()
        TestPool.many(5).forEach { state.addCard(it) }
        val card = state.deck.main.first()
        state.toggleBreak(main, 3)
        state.writeNote(card, "plays through Ash")
        state.chooseMat(MatChoice.WINE)
        state.updateNotes("cut the third copy")

        state.save()
        advanceUntilIdle()
        val id = requireNonNull(state.deckId)
        val saved = state.deck

        state.newDeck()
        advanceUntilIdle()
        assertEquals(MatChoice.THEME, state.mat, "the new deck is not wearing the old one's cloth")

        state.load(id)
        advanceUntilIdle()

        assertEquals(saved, state.deck)
        assertEquals(setOf(3), state.breaksIn(main).before)
        assertEquals("plays through Ash", state.noteOn(card))
        assertEquals(MatChoice.WINE, state.mat)
        assertEquals("cut the third copy", state.deckNotes)
        assertEquals(SaveStatus.SAVED, state.saveStatus, "reopening is not an edit")
    }

    // ---- what a save has to include ----------------------------------------

    @Test
    fun aGapOnItsOwnIsEnoughToNeedSaving() = runTest {
        // The whole class of bug: the arrangement, the card notes and the mat
        // all ride in the payload, and none of them used to count as a change.
        // A deck whose only edit was a gap reported itself saved and was never
        // written, so the arrangement was the least durable thing in the app.
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        assertEquals(SaveStatus.SAVED, state.saveStatus)

        state.toggleBreak(main, 2)

        assertEquals(SaveStatus.UNSAVED_CHANGES, state.saveStatus)
    }

    @Test
    fun aNoteOnItsOwnIsEnoughToNeedSaving() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.writeNote(state.deck.main.first(), "handtrap")

        assertEquals(SaveStatus.UNSAVED_CHANGES, state.saveStatus)
    }

    @Test
    fun aMatOnItsOwnIsEnoughToNeedSaving() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.chooseMat(MatChoice.WINE)

        assertEquals(SaveStatus.UNSAVED_CHANGES, state.saveStatus)
    }

    @Test
    fun aGapIsWrittenWithoutBeingAsked() = runTest {
        // And the other half: needing a save is no use unless one happens.
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.toggleBreak(main, 2)
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun recordingASidingPlanIsWrittenWithoutBeingAsked() = runTest {
        // The plan goes into the payload, and recording one used to ask for no
        // save at all -- so a plan could sit unwritten until something else was
        // changed, which for a plan recorded at the end of a session is never.
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        advanceUntilIdle()
        state.save()
        advanceUntilIdle()

        state.moveCard(TestPool.maxx, DeckSection.MAIN, DeckSection.SIDE)
        state.moveCard(TestPool.droll, DeckSection.SIDE, DeckSection.MAIN)
        advanceUntilIdle()
        state.recordSiding("Fiendsmith")

        assertEquals(SaveStatus.UNSAVED_CHANGES, state.saveStatus)
        advanceUntilIdle()
        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun aNoteAboutTwoCardsIsFoundFromEither() = runTest {
        val state = builderState()
        listOf(TestPool.ash, TestPool.maxx).forEach { state.addCard(it) }

        state.writePairNote(TestPool.ash.id, TestPool.maxx.id, "both is a hand trap war")

        assertEquals("both is a hand trap war", state.noteOn(TestPool.ash.id, TestPool.maxx.id))
        assertEquals("both is a hand trap war", state.noteOn(TestPool.maxx.id, TestPool.ash.id))
    }

    @Test
    fun theTwoCardsPickedOutAreTheOnesTheNoteIsAbout() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.select(main, 1)
        state.toggleSelected(main, 3)

        state.notePickedPair()

        val target = assertNotNull(state.pairTarget)
        assertEquals(setOf(state.deck.main[1], state.deck.main[3]), setOf(target.first, target.second))
    }

    @Test
    fun oneKeyWritesOnOneCardOrOnTwo() = runTest {
        // One question -- what do I want to remember about what I am holding --
        // and which sheet answers it depends only on how many cards are in hand.
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }

        state.select(main, 1)
        state.noteAtCursor()
        assertEquals(state.deck.main[1], state.noteTarget)
        assertNull(state.pairTarget)

        state.noteTarget = null
        state.select(main, 1)
        state.toggleSelected(main, 2)
        state.noteAtCursor()
        assertNull(state.noteTarget)
        assertNotNull(state.pairTarget)
    }

    @Test
    fun threeCardsHeldIsNotAQuestionThisCanAnswer() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.select(main, 0)
        state.selectThrough(main, 2)

        state.noteAtCursor()

        assertNull(state.noteTarget)
        assertNull(state.pairTarget)
    }

    @Test
    fun pickingUpACardLightsUpWhatItIsNotedWith() = runTest {
        // A note readable only in the sheet it was typed into is a filing
        // cabinet. This is the note showing up in the deck.
        val state = builderState()
        listOf(TestPool.ash, TestPool.maxx, TestPool.nibiru).forEach { state.addCard(it) }
        state.writePairNote(TestPool.ash.id, TestPool.nibiru.id, "both")

        state.select(main, state.deck.main.indexOf(TestPool.ash.id))

        assertEquals(setOf(TestPool.nibiru.id), state.notedWith)
    }

    @Test
    fun aCardWithNoPairsLightsUpNothing() = runTest {
        val state = builderState()
        listOf(TestPool.ash, TestPool.maxx).forEach { state.addCard(it) }
        state.writePairNote(TestPool.ash.id, TestPool.maxx.id, "both")

        state.select(main, state.deck.main.indexOf(TestPool.maxx.id))
        assertEquals(setOf(TestPool.ash.id), state.notedWith, "and from the other end")

        state.clearSelection()
        assertEquals(emptySet(), state.notedWith, "nothing held, nothing lit")
    }

    @Test
    fun holdingTwoCardsLightsUpNothing() = runTest {
        // With two picked out the answer would be about the selection rather
        // than about a card, and a deck lighting up for a range is noise.
        val state = builderState()
        listOf(TestPool.ash, TestPool.maxx, TestPool.nibiru).forEach { state.addCard(it) }
        state.writePairNote(TestPool.ash.id, TestPool.nibiru.id, "both")

        state.select(main, 0)
        state.selectThrough(main, 1)

        assertEquals(emptySet(), state.notedWith)
    }

    @Test
    fun twoCopiesOfOneCardAreNotAPair() = runTest {
        // Two positions, one card. The note could never be written, and a sheet
        // that opens, takes what you type and saves none of it is worse than no
        // sheet at all.
        val state = builderState()
        repeat(2) { state.addCard(TestPool.ash) }
        state.select(main, 0)
        state.toggleSelected(main, 1)

        state.notePickedPair()

        assertNull(state.pairTarget)
    }

    @Test
    fun threeCardsPickedOutIsNotAPair() = runTest {
        // A note about three cards is a note about the deck, which already has
        // somewhere to live.
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.select(main, 0)
        state.selectThrough(main, 2)

        state.notePickedPair()

        assertNull(state.pairTarget)
    }

    @Test
    fun aPairNoteTravelsInTheExportedFileBesideTheCardNotes() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        advanceUntilIdle()
        val cards = state.deck.main.take(2)

        state.writeNote(cards[0], "a card note")
        state.writePairNote(cards[0], cards[1], "a pair note")
        state.exportToFile()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue("a card note" in written, "the card half was destroyed")
        assertTrue("a pair note" in written, "the pair half was destroyed")
        assertTrue("somethingThisAppDoesNotKnow" in written)
    }

    // ---- the cloth the deck lies on ----------------------------------------

    @Test
    fun theMatTravelsInTheExportedFile() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()

        state.chooseMat(MatChoice.WINE)
        state.exportToFile()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue("\"mat\": \"wine\"" in written || "\"mat\":\"wine\"" in written, written.takeLast(500))
        assertTrue("somethingThisAppDoesNotKnow" in written)
    }

    @Test
    fun aDeckNobodyDressedWritesNoMat() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        state.exportToFile()
        advanceUntilIdle()

        assertFalse("\"look\"" in requireNonNull(files.exported))
    }

    @Test
    fun aFreshDeckIsBackOnTheThemesOwnMat() = runTest {
        val state = builderState()
        state.chooseMat(MatChoice.BAIZE)

        state.newDeck()

        assertEquals(MatChoice.THEME, state.mat)
    }

    @Test
    fun takingBackANewDeckBringsBackEverythingThatDescribedTheOldOne() = runTest {
        // Undo restores the cards; a deck is not only its cards. The gaps, the
        // notes and the cloth were what made it that deck rather than a list.
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        val card = state.deck.main.first()
        state.toggleBreak(main, 2)
        state.writeNote(card, "kept")
        state.chooseMat(MatChoice.BAIZE)

        state.newDeck()
        state.toast?.undo?.invoke()

        assertEquals(setOf(2), state.breaksIn(main).before)
        assertEquals("kept", state.noteOn(card))
        assertEquals(MatChoice.BAIZE, state.mat)
    }

    // ---- a word on a card --------------------------------------------------

    @Test
    fun aNoteWrittenOnACardTravelsInTheExportedFile() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()
        val card = state.deck.main.first()

        state.writeNote(card, "only starter that plays through Ash")
        state.exportToFile()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue("only starter that plays through Ash" in written, written.takeLast(400))
        // And the payload rule still holds around it.
        assertTrue("somethingThisAppDoesNotKnow" in written)
    }

    @Test
    fun clearingTheBoxTakesTheNoteAway() = runTest {
        val state = builderState()
        TestPool.many(2).forEach { state.addCard(it) }
        val card = state.deck.main.first()

        state.writeNote(card, "something")
        assertEquals("something", state.noteOn(card))

        state.writeNote(card, "  ")
        assertNull(state.noteOn(card))
    }

    @Test
    fun everyCopyOfACardCarriesTheSameNote() = runTest {
        // By passcode, not by position: cutting one copy must not take the note
        // with it, and the second copy is not a different card.
        val state = builderState()
        val card = TestPool.ash
        repeat(3) { state.addCard(card) }

        state.writeNote(card.id, "handtrap")
        state.removeOne(card, main)

        assertEquals("handtrap", state.noteOn(card.id))
    }

    @Test
    fun aNoteSurvivesTheCardLeavingTheDeckEntirely() = runTest {
        // Taking the last copy out and putting it back happens constantly while
        // building, and a note that did not survive it is a note nobody trusts.
        val state = builderState()
        val card = TestPool.ash
        state.addCard(card)
        state.writeNote(card.id, "kept")

        state.removeOne(card, main)
        state.addCard(card)

        assertEquals("kept", state.noteOn(card.id))
    }

    @Test
    fun aFreshDeckHasNothingWrittenOnIt() = runTest {
        val state = builderState()
        val card = TestPool.ash
        state.addCard(card)
        state.writeNote(card.id, "gone")

        state.newDeck()

        assertNull(state.noteOn(card.id))
    }

    // ---- the sheet a judge reads -------------------------------------------

    @Test
    fun theDecklistGoesOutAsAPdfWithTheCardsCounted() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()

        state.exportDecklistSheet()
        advanceUntilIdle()

        val written = requireNonNull(files.exported)
        assertTrue(written.startsWith("%PDF"), written.take(40))
        assertTrue("MONSTERS" in written && "EXTRA DECK" in written && "SIDE DECK" in written)
        assertTrue("PLAYER" in written, "the field somebody fills in at the venue")
        assertEquals("application/pdf", files.exportedType)
        assertTrue(requireNonNull(files.exportedName).endsWith("decklist.pdf"))
    }

    @Test
    fun aDeckWithNothingInItWritesNoDecklist() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))

        state.exportDecklistSheet()
        advanceUntilIdle()

        assertEquals(null, files.exportedBytes, "a sheet of nothing is worse than no sheet")
    }

    // ---- a picture of the deck ---------------------------------------------

    @Test
    fun aPictureIsWrittenOutUnderTheDecksName() = runTest {
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()

        val png = Png.encode(IntArray(4) { -1 }, 2, 2)
        state.exportPicture(png)
        advanceUntilIdle()

        assertContentEquals(png, files.exportedBytes, "the bytes must arrive untouched")
        assertEquals("image/png", files.exportedType)
        assertTrue(requireNonNull(files.exportedName).endsWith(".png"))
    }

    @Test
    fun aScreenThatCouldNotBeReadWritesNothing() = runTest {
        // The capture returns null before the first frame is drawn, and on any
        // surface a platform declines to read back. A file of no bytes called
        // deck.png is worse than being told it did not work.
        val files = StubFileAccess(sidedFile)
        val state = builderState(testDependencies(files))
        state.importFromFile()

        state.exportPicture(null)
        state.exportPicture(ByteArray(0))
        advanceUntilIdle()

        assertEquals(null, files.exportedBytes)
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

    // ---- the deck across the table -----------------------------------------

    @Test
    fun loadingAnOpponentLeavesYourOwnDeckAlone() = runTest {
        // The one thing this feature must never do. Every other importer in the
        // program replaces the open deck, and this one shares their file picker.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        state.addCard(TestPool.ash)
        val mine = state.deck

        state.loadOpponent()

        assertEquals(mine, state.deck)
        assertEquals(4, state.opponentDeck.main.size)
        assertEquals("lab", state.opponentName)
    }

    @Test
    fun bothSidesAreDealtAndTheyCannotBothGoFirst() = runTest {
        // One of them going first means the other is going second; two five-card
        // hands would be a matchup that cannot happen.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()

        state.dealShootout(goingFirst = true)

        assertEquals(5, state.yourOpening?.size)
        assertEquals(true, state.yourOpening?.goingFirst)
        assertEquals(false, state.theirOpening?.goingFirst)
    }

    @Test
    fun theOtherSideOfTheChoiceIsTheOtherHandSize() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()

        state.dealShootout(goingFirst = false)

        assertEquals(6, state.yourOpening?.size)
        assertEquals(false, state.youGoFirst)
        assertEquals(true, state.theirOpening?.goingFirst)
    }

    @Test
    fun dealingWithNoOpponentLoadedIsNotAnError() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }

        state.dealShootout(goingFirst = true)

        assertEquals(5, state.yourOpening?.size)
        assertEquals(0, state.theirOpening?.size, "an empty deck deals nothing")
    }

    @Test
    fun anOpponentLoadDealsSoThePanelIsNeverEmpty() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }

        state.loadOpponent()

        assertTrue((state.theirOpening?.size ?: 0) > 0)
    }

    @Test
    fun judgingAShootoutHandCountsItAndDealsTheNext() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        state.dealShootout(goingFirst = true)

        state.judgeShootout(playable = false)
        state.judgeShootout(playable = true)

        assertEquals(2, state.matchup.goingFirst.total)
        assertEquals(1, state.matchup.goingFirst.bricks)
        assertEquals(0, state.matchup.goingSecond.total, "the other side saw no hands")
        assertTrue(state.yourOpening != null, "judging deals the next hand")
    }

    @Test
    fun aHandIsRecordedAgainstTheSideItWasDealtFor() = runTest {
        // Not against whichever chip is selected when the verdict is given.
        // Those are the same until somebody switches sides mid-judgement.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()

        state.dealShootout(goingFirst = false)
        state.judgeShootout(playable = false)

        assertEquals(1, state.matchup.goingSecond.total)
        assertEquals(0, state.matchup.goingFirst.total)
    }

    @Test
    fun changingTheMainDeckThrowsTheRecordAway() = runTest {
        // A brick rate measured over one forty is not a fact about a different
        // forty, and siding mid-shootout -- which is the whole point of a
        // shootout -- changes it by design.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        state.judgeShootout(playable = true)
        assertEquals(1, state.matchup.total)

        state.addCard(TestPool.ash)

        assertEquals(0, state.matchup.total)
    }

    @Test
    fun theTestHandTallyGoesTheSameWay() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.dealTestHand(goingFirst = true)
        state.judgeTestHand(playable = false)
        assertEquals(1, state.handTally.total)

        state.addCard(TestPool.ash)

        assertEquals(0, state.handTally.total)
    }

    @Test
    fun anExtraDeckEditLeavesTheRecordAlone() = runTest {
        // Openings are dealt from the Main deck. An Extra deck change is not a
        // change to the question being asked.
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.dealTestHand(goingFirst = true)
        state.judgeTestHand(playable = true)

        state.addCard(TestPool.ash, DeckSection.SIDE)

        assertEquals(1, state.handTally.total)
    }

    @Test
    fun aVerdictCanBeTakenBackOnce() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()

        state.judgeShootout(playable = false)
        assertEquals(1, state.matchup.total)
        assertTrue(state.canUndoVerdict)

        state.undoVerdict()

        assertEquals(0, state.matchup.total)
        assertFalse(state.canUndoVerdict, "one step, and the offer goes with it")
    }

    @Test
    fun thereIsNothingToTakeBackBeforeAVerdict() = runTest {
        val state = builderState()

        assertFalse(state.canUndoVerdict)
        state.undoVerdict()
        assertEquals(0, state.matchup.total)
    }

    @Test
    fun resettingTheRecordAlsoDropsTheOfferToUndo() = runTest {
        // Otherwise undo restores a record from before a reset somebody asked
        // for, which is the opposite of what they asked for.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        state.judgeShootout(playable = true)

        state.resetMatchup()

        assertFalse(state.canUndoVerdict)
    }

    @Test
    fun eitherSideCanDrawOneMore() = runTest {
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        state.dealShootout(goingFirst = true)
        val yours = state.yourOpening!!.size

        state.drawOneMoreShootout(yours = true)

        assertEquals(yours + 1, state.yourOpening?.size)
    }

    @Test
    fun drawingFromAnExhaustedDeckChangesNothing() = runTest {
        // The opponent list in this file is four cards, so going second they
        // have already drawn all of it. Running out is a real answer, and the
        // panel offers the draw only while there is something under the hand.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        state.dealShootout(goingFirst = true)
        val theirs = state.theirOpening!!

        state.drawOneMoreShootout(yours = false)

        assertTrue(theirs.remaining.isEmpty(), "the fixture is meant to be exhausted")
        assertEquals(theirs, state.theirOpening)
    }

    @Test
    fun changingOpponentStartsTheRecordAgain() = runTest {
        // A record against one list says nothing about another, and a tally that
        // quietly carried over would be worse than no tally at all.
        val state = builderState(testDependencies(StubFileAccess(sidedFile)))
        TestPool.many(40).forEach { state.addCard(it) }
        state.loadOpponent()
        state.judgeShootout(playable = false)

        state.loadOpponent()

        assertEquals(0, state.matchup.total)
    }

    @Test
    fun judgingWithNothingDealtDoesNothing() = runTest {
        val state = builderState()

        state.judgeShootout(playable = true)

        assertEquals(0, state.matchup.total)
    }

    // ---- showing what happened ---------------------------------------------

    @Test
    fun addingACardAsksForItToBeShown() = runTest {
        val state = builderState()

        state.addCard(TestPool.ash)

        assertEquals(0, state.revealRequest?.position)
        assertEquals(TestPool.ash.id, state.revealRequest?.cardId)
        assertFalse(state.revealRequest?.flash ?: true, "an add is not a search result")
    }

    @Test
    fun itIsTheCopyJustAddedThatIsShown() = runTest {
        // Adding a third Ash Blossom and scrolling to the first is an answer to
        // a question nobody asked.
        val state = builderState()
        repeat(3) { state.addCard(TestPool.ash) }

        assertEquals(2, state.revealRequest?.position)
    }

    @Test
    fun aRejectedAddShowsNothing() = runTest {
        // The fourth copy is refused, and pointing at the third one as though
        // something had happened would be a lie.
        val state = builderState()
        repeat(3) { state.addCard(TestPool.ash) }
        state.addCard(TestPool.droll)
        val before = state.revealRequest

        state.addCard(TestPool.ash)

        assertEquals(before?.id, state.revealRequest?.id)
    }

    @Test
    fun theDeckCheckStillFlashesWhatItNames() = runTest {
        val state = builderState()
        state.addCard(TestPool.ash)

        state.reveal(main, TestPool.ash.id)

        assertTrue(state.revealRequest?.flash ?: false)
    }

    // ---- naming a pile -----------------------------------------------------

    @Test
    fun aPileIsOnlyNameableOnceThereAreGapsToMakeOneWith() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        assertNull(state.pileAt(main, 2), "with no gaps, this pile is the section")

        state.toggleBreak(main, 3)

        val pile = assertNotNull(state.pileAt(main, 4))
        assertEquals(3, pile.start)
        assertEquals(3, pile.size)
    }

    @Test
    fun whatYouCalledAPileBeatsWhatItIsMadeOf() = runTest {
        // The derived name says what a pile *is* and can never be wrong, because
        // nobody wrote it. A typed one says what it is *for*, which is the thing
        // only its owner knows.
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.namePile(main, 3, "The engine")

        assertEquals("The engine", state.groupNames[1])
    }

    @Test
    fun andEmptyingItGivesTheReadingBack() = runTest {
        // Clearing the box is the delete, and what comes back is not nothing --
        // here it is null only because this fixture's pool knows none of these
        // cards, which is the same reason a real one would say "Monsters".
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)
        state.namePile(main, 3, "The engine")

        state.namePile(main, 3, "  ")

        assertNull(state.groupNames[1])
    }

    @Test
    fun aNameFollowsItsOwnPileWhenTheDeckAboveItChanges() = runTest {
        // The property the whole design turns on. Cut a card out of the first
        // pile and the second starts one earlier -- a name keyed to the old
        // number would now be sitting on a card in the first.
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.toggleBreak(main, 3)
        state.namePile(main, 3, "The engine")

        state.removeAt(cards[0], main, 0)

        assertEquals("The engine", state.groupNames[1])
        assertEquals(2, assertNotNull(state.pileAt(main, 3)).start)
    }

    // ---- what covers the builder -------------------------------------------

    /**
     * Opens one layer.
     *
     * Exhaustive over [Overlay], which the compiler enforces — so a new layer
     * fails to compile here until somebody says how it opens, and this test
     * cannot quietly stop covering all of them.
     */
    private fun DeckBuilderState.show(overlay: Overlay) {
        when (overlay) {
            Overlay.EGG -> eggVisible = true
            Overlay.SHOWCASE -> showcaseVisible = true
            Overlay.INSPECTOR -> inspect(listOf(TestPool.ash), 0)
            Overlay.HELP -> helpVisible = true
            Overlay.CARD_NOTE -> noteTarget = TestPool.ash.id
            Overlay.PAIR_NOTE -> pairTarget = TestPool.ash.id to TestPool.maxx.id
            Overlay.MAT -> matVisible = true
            Overlay.FILTERS -> filtersVisible = true
            Overlay.STATS -> statsVisible = true
            Overlay.SIDING -> sidingVisible = true
            Overlay.TEST_HAND -> testHandVisible = true
            Overlay.SHOOTOUT -> shootoutVisible = true
            Overlay.NOTES -> notesVisible = true
            Overlay.ISSUES -> issuesVisible = true
            Overlay.HISTORY -> historyVisible = true
            Overlay.PASTE -> pasteVisible = true
            Overlay.PILE_NAME -> pileTarget = Pile(main, 0, "", 3)
        }
    }

    @Test
    fun everyLayerCoversTheBuilderAndEscapeClosesIt() = runTest {
        // Two lists used to say this — what silences the shortcuts, and what
        // Escape closes — and nothing made them agree. A layer missing from the
        // first leaks keys through to the builder underneath; one missing from
        // the second cannot be closed by Escape at all.
        val state = builderState()

        Overlay.entries.forEach { overlay ->
            state.show(overlay)
            assertTrue(state.isOpen(overlay), "$overlay did not open")
            assertTrue(state.anyOverlayOpen, "$overlay does not count as covering the builder")

            assertTrue(state.dismissTopOverlay(), "$overlay could not be dismissed")
            assertFalse(state.isOpen(overlay), "$overlay stayed open")
            assertFalse(state.anyOverlayOpen, "something else was left open by $overlay")
        }
    }

    @Test
    fun theTopmostLayerLeavesFirst() = runTest {
        val state = builderState()
        state.statsVisible = true
        state.eggVisible = true

        state.dismissTopOverlay()

        assertFalse(state.eggVisible, "the egg covers everything, so it leaves first")
        assertTrue(state.statsVisible)
    }

    @Test
    fun dismissingNothingSaysSo() = runTest {
        // The caller falls through to the selection and then the search box on
        // a false, so a lie here would eat the Escape that should clear those.
        assertFalse(builderState().dismissTopOverlay())
    }

    // ---- saving ------------------------------------------------------------

    @Test
    fun anEmptyDeckSaysNothingAboutBeingSaved() = runTest {
        // A program that says "unsaved" about an empty screen is crying wolf
        // before anything has happened.
        assertEquals(SaveStatus.UNTOUCHED, builderState().saveStatus)
    }

    @Test
    fun workThatHasNeverBeenSavedSaysSo() = runTest {
        val state = builderState()
        state.addCard(TestPool.ash)

        assertEquals(SaveStatus.NEVER_SAVED, state.saveStatus)
    }

    @Test
    fun savingClearsTheWarning() = runTest {
        val state = builderState()
        state.addCard(TestPool.ash)
        state.save()

        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun anEditAfterSavingIsWrittenWithoutBeingAskedTo() = runTest {
        // The whole point: the first save is manual, and every one after it is
        // not.
        val deps = testDependencies()
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.save()

        state.addCard(TestPool.maxx)
        advanceUntilIdle()

        val stored = deps.deckRepository.byId(requireNonNull(state.deckId))
        assertEquals(
            listOf(TestPool.ash.id, TestPool.maxx.id),
            stored?.entry?.deck?.main,
            "the second card never reached the database",
        )
        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun renamingIsWrittenTheSameWay() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.save()

        state.rename("Snake-Eye")
        advanceUntilIdle()

        assertEquals("Snake-Eye", deps.deckRepository.byId(requireNonNull(state.deckId))?.entry?.name)
    }

    @Test
    fun aDeckNobodyEverSavedIsNeverWrittenOnItsOwn() = runTest {
        // Autosaving everything fills a library with Untitled Decks somebody
        // then has to go and delete, which is worse than asking once.
        val deps = testDependencies()
        val state = builderState(deps)

        state.addCard(TestPool.ash)
        advanceUntilIdle()

        assertTrue(deps.deckRepository.all().isEmpty())
        assertEquals(SaveStatus.NEVER_SAVED, state.saveStatus)
    }

    @Test
    fun startingANewDeckCannotOverwriteTheOneThatWasOpen() = runTest {
        // The trap this whole ordering exists for. Assigning `deck` schedules a
        // save, so clearing the deck before letting go of its id would write an
        // empty list into the row of a deck somebody had just saved.
        val deps = testDependencies()
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.addCard(TestPool.maxx)
        state.save()
        val savedId = requireNonNull(state.deckId)

        state.newDeck()
        advanceUntilIdle()

        assertEquals(
            2,
            deps.deckRepository.byId(savedId)?.entry?.deck?.main?.size,
            "the saved deck was replaced by the new empty one",
        )
    }

    @Test
    fun openingAnotherDeckCannotOverwriteTheOneThatWasOpen() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.save()
        val first = requireNonNull(state.deckId)

        state.newDeck()
        state.addCard(TestPool.maxx)
        state.addCard(TestPool.nibiru)
        state.save()

        state.load(first)
        advanceUntilIdle()

        assertEquals(listOf(TestPool.ash.id), state.deck.main)
        assertEquals(SaveStatus.SAVED, state.saveStatus, "a freshly opened deck is not dirty")
    }

    @Test
    fun importingDoesNotWriteOverTheOpenDeck() = runTest {
        val deps = testDependencies(StubFileAccess(sidedFile))
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.save()
        val savedId = requireNonNull(state.deckId)

        state.importFromFile()
        advanceUntilIdle()

        assertEquals(listOf(TestPool.ash.id), deps.deckRepository.byId(savedId)?.entry?.deck?.main)
        assertEquals(SaveStatus.NEVER_SAVED, state.saveStatus, "an import is a new deck")
    }

    @Test
    fun notesSurviveEverySaveAfterTheFirst() = runTest {
        // The hole this closes: `save` defaults the notes column to the empty
        // string, so every save from the builder was quietly clearing whatever
        // had been written there.
        val deps = testDependencies()
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.updateNotes("side out Nibiru on the draw")
        state.save()

        state.addCard(TestPool.maxx)
        advanceUntilIdle()

        assertEquals(
            "side out Nibiru on the draw",
            deps.deckRepository.byId(requireNonNull(state.deckId))?.entry?.notes,
        )
    }

    @Test
    fun notesComeBackWithTheDeck() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        state.addCard(TestPool.ash)
        state.updateNotes("Kashtira is the bad one")
        state.save()
        val id = requireNonNull(state.deckId)

        state.newDeck()
        assertEquals("", state.deckNotes, "a new deck starts with no notes")

        state.load(id)
        assertEquals("Kashtira is the bad one", state.deckNotes)
        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun writingANoteIsAnUnsavedChange() = runTest {
        val state = builderState()
        state.addCard(TestPool.ash)
        state.save()

        state.updateNotes("something")

        assertEquals(SaveStatus.UNSAVED_CHANGES, state.saveStatus)
    }

    // ---- the cursor --------------------------------------------------------

    @Test
    fun theFirstArrowPutsTheCursorDownRatherThanMovingIt() = runTest {
        // Otherwise the first press appears to do nothing, and the second one
        // looks like the first press was the thing that failed.
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        state.moveCursor(GridStep.RIGHT, columns = 3, extend = false)

        assertEquals(setOf(0), state.selection.indices)
        assertEquals(main, state.selection.section)
    }

    @Test
    fun arrowsWalkTheGrid() = runTest {
        val state = builderState()
        TestPool.many(7).forEach { state.addCard(it) }
        state.select(main, 1)

        state.moveCursor(GridStep.DOWN, columns = 3, extend = false)
        assertEquals(setOf(4), state.selection.indices)

        state.moveCursor(GridStep.LEFT, columns = 3, extend = false)
        assertEquals(setOf(3), state.selection.indices)

        state.moveCursor(GridStep.UP, columns = 3, extend = false)
        assertEquals(setOf(0), state.selection.indices)
    }

    @Test
    fun anEdgeLeavesTheCursorWhereItIs() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.select(main, 0)

        state.moveCursor(GridStep.UP, columns = 3, extend = false)

        assertEquals(setOf(0), state.selection.indices)
    }

    @Test
    fun holdingShiftGrowsTheSelectionOneCardAtATime() = runTest {
        // The derived focus is what makes the second press reach further than
        // the first instead of measuring from the anchor again.
        val state = builderState()
        TestPool.many(8).forEach { state.addCard(it) }
        state.select(main, 2)

        repeat(3) { state.moveCursor(GridStep.RIGHT, columns = 4, extend = true) }

        assertEquals((2..5).toSet(), state.selection.indices)
    }

    @Test
    fun carryingMovesTheCardsAndKeepsHoldOfThem() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.select(main, 0)

        state.carrySelection(GridStep.RIGHT, columns = 3)

        assertEquals(cards[1].id, state.deck.main[0])
        assertEquals(cards[0].id, state.deck.main[1])
        assertEquals(setOf(1), state.selection.indices, "a carried card has to stay held")
    }

    @Test
    fun aCarriedCardCanBeWalkedAcrossThePane() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.select(main, 0)

        repeat(3) { state.carrySelection(GridStep.RIGHT, columns = 3) }

        assertEquals(3, state.deck.main.indexOf(cards[0].id))
    }

    @Test
    fun carryingAGroupKeepsItsOrder() = runTest {
        val state = builderState()
        val cards = TestPool.many(6)
        cards.forEach { state.addCard(it) }
        state.select(main, 0)
        state.selectThrough(main, 1)

        state.carrySelection(GridStep.RIGHT, columns = 3)

        assertEquals(listOf(cards[2].id, cards[0].id, cards[1].id), state.deck.main.take(3))
        assertEquals(setOf(1, 2), state.selection.indices)
    }

    @Test
    fun carryingIntoTheEdgeDoesNothingAtAll() = runTest {
        // Clamped, so the deck is unchanged -- and an unchanged deck must not go
        // on the undo stack, or holding the key down fills it with nothing.
        val state = builderState()
        val cards = TestPool.many(4)
        cards.forEach { state.addCard(it) }
        state.select(main, 3)
        val before = state.deck.main

        repeat(3) { state.carrySelection(GridStep.RIGHT, columns = 2) }
        assertEquals(before, state.deck.main)

        state.undo()
        assertEquals(
            before.dropLast(1),
            state.deck.main,
            "undo should reach the last card added, not three no-op carries",
        )
    }

    @Test
    fun carryingWithNothingHeldDoesNothing() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        val before = state.deck.main

        state.carrySelection(GridStep.DOWN, columns = 2)

        assertEquals(before, state.deck.main)
    }

    @Test
    fun theCursorAsksForTheCardItLandedOnToBeShown() = runTest {
        val state = builderState()
        TestPool.many(9).forEach { state.addCard(it) }
        state.select(main, 0)

        state.moveCursor(GridStep.DOWN, columns = 3, extend = false)
        val first = state.reveal

        state.moveCursor(GridStep.DOWN, columns = 3, extend = false)
        val second = state.reveal

        assertEquals(3, first?.index)
        assertEquals(6, second?.index)
        assertTrue((second?.serial ?: 0L) > (first?.serial ?: 0L), "a repeat request has to look new")
    }

    // ---- tidying -----------------------------------------------------------

    @Test
    fun gatheringCopiesMovesOnlyTheStrayCopy() = runTest {
        // The pool is empty here, and that is deliberate: gathering copies is the
        // one tidy that needs to know nothing about the cards, so it has to work
        // on a deck imported before the database finished downloading.
        val state = builderState()
        state.addCard(TestPool.ash)
        state.addCard(TestPool.imperm)
        state.addCard(TestPool.ash)

        state.tidySection(main, TidyBy.COPIES)

        assertEquals(
            listOf(TestPool.ash.id, TestPool.ash.id, TestPool.imperm.id),
            state.deck.main,
        )
    }

    @Test
    fun tidyingAnAlreadyTidySectionDoesNothingToUndo() = runTest {
        // Pressing it twice must not put a no-op on the undo stack, or the undo
        // after it appears to do nothing.
        val state = builderState()
        state.addCard(TestPool.ash)
        state.addCard(TestPool.imperm)
        state.addCard(TestPool.ash)

        state.tidySection(main, TidyBy.COPIES)
        val tidied = state.deck.main
        state.tidySection(main, TidyBy.COPIES)
        state.undo()

        assertEquals(
            listOf(TestPool.ash.id, TestPool.imperm.id, TestPool.ash.id),
            state.deck.main,
            "undo should reach past the second press, which changed nothing",
        )
        assertEquals(3, tidied.size)
    }

    @Test
    fun aTidyIsUndoable() = runTest {
        val state = builderState()
        state.addCard(TestPool.ash)
        state.addCard(TestPool.imperm)
        state.addCard(TestPool.ash)
        val before = state.deck.main

        state.tidySection(main, TidyBy.COPIES)
        state.undo()

        assertEquals(before, state.deck.main)
    }

    @Test
    fun aTidyNeverChangesWhatIsInTheDeck() = runTest {
        val state = builderState()
        val cards = TestPool.many(9)
        cards.forEach { state.addCard(it) }
        cards.take(3).forEach { state.addCard(it) }
        val before = state.deck.main.sortedBy { it.value }

        TidyBy.entries.forEach { mode ->
            state.tidySection(main, mode)
            assertEquals(before, state.deck.main.sortedBy { it.value }, "$mode changed the deck")
        }
    }

    // ---- keeping a version -------------------------------------------------

    @Test
    fun keepingOneOnADeckThatWasNeverSavedSaysSo() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(40).forEach { state.addCard(it) }

        state.keepThisOne("regionals")
        advanceUntilIdle()

        assertTrue(
            requireNonNull(state.toast?.message).startsWith("Save the deck first"),
            "there is nowhere to keep it, and the program does not save a deck by itself",
        )
        assertNull(state.deckId)
    }

    @Test
    fun keepingOneRecordsTheDeckUnderItsName() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(40).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.keepThisOne("regionals")
        advanceUntilIdle()

        val kept = deps.deckRepository.versions(requireNonNull(state.deckId)).single()
        assertEquals("regionals", kept.name)
        assertEquals(state.deck, kept.deck)
    }

    @Test
    fun whatIsKeptIsWhatIsOnScreenNotWhatWasLastWritten() = runTest {
        // The autosave sits for a second and a half. Without writing first, the
        // version kept here would be the deck as it was before the last few
        // cards went in -- nearly right, and nobody would ever catch it.
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(40).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        val extra = TestPool.many(3)
        extra.forEach { state.addCard(it) }
        state.keepThisOne("regionals")
        advanceUntilIdle()

        val id = requireNonNull(state.deckId)
        assertEquals(state.deck, deps.deckRepository.versions(id).single().deck)
        assertEquals(43, deps.deckRepository.versions(id).single().deck.main.size)
    }

    @Test
    fun aVersionCanBeKeptWithoutBeingNamed() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(40).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.keepThisOne(null)
        advanceUntilIdle()

        assertNull(deps.deckRepository.versions(requireNonNull(state.deckId)).single().name)
    }

    @Test
    fun keepingTwiceOverWithoutEditingIsOneVersion() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(40).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.keepThisOne("regionals")
        advanceUntilIdle()
        state.keepThisOne("the one I actually played")
        advanceUntilIdle()

        val kept = deps.deckRepository.versions(requireNonNull(state.deckId))
        assertEquals(1, kept.size, "one moment, one version")
        assertEquals("the one I actually played", kept.single().name)
    }

    // ---- what a group is called --------------------------------------------

    // What a group is *called* is `GroupNamingTest` in :core, against real card
    // shapes. It cannot be checked here: a name is read off the card pool, the
    // harness deliberately never seeds one -- `addCard` takes the card itself --
    // and `index` has a private setter, which is right. So these are about the
    // wiring, and about the case an empty pool makes easy to reach.

    @Test
    fun aGroupOfCardsThePoolHasNeverHeardOfIsNotNamed() = runTest {
        // Decks arrive from other programs carrying passcodes this database does
        // not have. Calling that group "Monsters" would be a guess.
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        assertEquals(listOf(null, null), state.groupNames)
    }

    @Test
    fun aSectionWithNoGapsIsOneGroup() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }

        assertEquals(1, state.groupNames.size)
    }

    @Test
    fun theNamesLineUpWithTheOddsTheyLabel() = runTest {
        // They are index-aligned by construction and by nothing else, so a
        // change to either side has to keep the lengths equal or the panel puts
        // one group's name on another group's row.
        val state = builderState()
        TestPool.many(12).forEach { state.addCard(it) }
        listOf(3, 7, 9).forEach { state.toggleBreak(main, it) }

        assertEquals(
            GroupOdds.forGroups(state.breaksIn(main), state.deck.main.size).size,
            state.groupNames.size,
        )
    }

    @Test
    fun theNamesFollowTheSectionTheStatisticsAreShowing() = runTest {
        val state = builderState()
        TestPool.many(6).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)

        state.statsSection = DeckSection.SIDE

        assertEquals(emptyList(), state.groupNames, "an empty side deck has no groups")
    }

    // ---- picking up a whole pile -------------------------------------------

    @Test
    fun pickingUpAGroupTakesEveryCardBetweenTheGaps() = runTest {
        val state = builderState()
        TestPool.many(9).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)
        state.toggleBreak(main, 6)

        state.select(main, 4)
        state.selectGroupAtCursor()

        assertEquals(setOf(3, 4, 5), state.selection.indices)
        assertEquals(main, state.selection.section)
    }

    @Test
    fun aSectionWithNoGapsPicksUpAllOfIt() = runTest {
        // One group, which is what no gaps means everywhere else in the program.
        val state = builderState()
        TestPool.many(5).forEach { state.addCard(it) }
        state.select(main, 2)

        state.selectGroupAtCursor()

        assertEquals(setOf(0, 1, 2, 3, 4), state.selection.indices)
    }

    @Test
    fun theCursorStaysAtTheEndYourHandWasAt() = runTest {
        // The focus is derived from the anchor, so picking a pile up must not
        // move the cursor across it -- the next arrow key would jump.
        val state = builderState()
        TestPool.many(9).forEach { state.addCard(it) }
        state.toggleBreak(main, 3)
        state.toggleBreak(main, 6)

        state.select(main, 3)
        state.selectGroupAtCursor()
        assertEquals(3, Selections.focusOf(state.selection))

        state.select(main, 5)
        state.selectGroupAtCursor()
        assertEquals(5, Selections.focusOf(state.selection))
    }

    @Test
    fun pickingUpAGroupWithNothingSelectedDoesNothing() = runTest {
        val state = builderState()
        TestPool.many(5).forEach { state.addCard(it) }

        state.selectGroupAtCursor()

        assertTrue(state.selection.isEmpty)
    }

    @Test
    fun aGroupPickedUpFromACardsOwnMenuNeedsNoCursor() = runTest {
        val state = builderState()
        TestPool.many(9).forEach { state.addCard(it) }
        state.toggleBreak(main, 6)

        state.selectGroupAt(main, 7)

        assertEquals(setOf(6, 7, 8), state.selection.indices)
    }

    @Test
    fun pickingUpAGroupThatIsNotThereLeavesTheSelectionAlone() = runTest {
        val state = builderState()
        TestPool.many(4).forEach { state.addCard(it) }
        state.select(main, 1)
        val before = state.selection

        state.selectGroupAt(main, 40)

        assertEquals(before, state.selection)
    }

    // ---- putting one deck down to pick another up --------------------------

    @Test
    fun puttingADeckDownWritesWhatWasStillPendingOnIt() = runTest {
        // The autosave sits for a second and a half, and every path that swaps
        // the open deck used to cancel it rather than write it. This one goes
        // through `newDeck`, which was the first to prove it: the edit was gone
        // before `load` was ever reached.
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(5).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val first = requireNonNull(state.deckId)

        state.removeOne(TestPool.many(5).first(), main)
        val edited = state.deck

        state.newDeck()
        TestPool.many(3).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val second = requireNonNull(state.deckId)

        state.load(first)
        advanceUntilIdle()

        assertEquals(edited, state.deck, "the pending edit reached the disk")
        assertEquals(edited, assertNotNull(deps.deckRepository.byId(first)).entry.deck)
        assertNotNull(deps.deckRepository.byId(second))
    }

    @Test
    fun openingADeckOffersBackTheOneThatWasNeverSaved() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val saved = requireNonNull(state.deckId)

        state.newDeck()
        TestPool.many(6).forEach { state.addCard(it) }
        state.rename("Work in progress")
        state.chooseMat(MatChoice.WINE)
        val unsaved = state.deck

        state.load(saved)
        advanceUntilIdle()
        assertEquals(4, state.deck.main.size, "the saved deck opened")

        assertNotNull(state.toast?.undo, "there was something to lose").invoke()

        assertEquals(unsaved, state.deck)
        assertEquals("Work in progress", state.deckName)
        assertEquals(MatChoice.WINE, state.mat, "and everything that came with it")
        assertNull(state.deckId, "still never saved, which is what it was")
    }

    @Test
    fun openingADeckSaysNothingWhenThereIsNothingToLose() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val first = requireNonNull(state.deckId)

        state.newDeck()
        TestPool.many(3).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        // `toast` cannot be cleared from here and should not be: what is being
        // asked is whether opening raised a *new* one, which the id answers.
        val before = state.toast?.id
        state.load(first)
        advanceUntilIdle()

        assertEquals(before, state.toast?.id, "a saved deck is on disk either way")
    }

    @Test
    fun openingADeckOverAnEmptyOneSaysNothing() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(4).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val first = requireNonNull(state.deckId)

        state.newDeck()
        val before = state.toast?.id

        state.load(first)
        advanceUntilIdle()

        assertEquals(before, state.toast?.id, "an empty deck is not work")
    }

    @Test
    fun theHistoryCannotBeOpenedOnADeckThatWasNeverSaved() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }

        state.showHistory()
        advanceUntilIdle()

        assertFalse(state.historyVisible)
        assertTrue(requireNonNull(state.toast?.message).startsWith("Save the deck first"))
    }

    @Test
    fun openingTheHistoryWritesWhatIsPendingFirst() = runTest {
        // The list is read off what is *stored*, so the top of it would claim the
        // deck as it stands is a second and a half out of date.
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(40).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        val extra = TestPool.many(3)
        extra.forEach { state.addCard(it) }
        state.showHistory()
        advanceUntilIdle()

        assertTrue(state.historyVisible)
        val id = requireNonNull(state.deckId)
        assertEquals(state.deck, assertNotNull(deps.deckRepository.byId(id)).entry.deck)
    }

    @Test
    fun theHistoryClosesLikeEveryOtherSheet() = runTest {
        val state = builderState()
        TestPool.many(40).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        state.showHistory()
        advanceUntilIdle()

        state.close(Overlay.HISTORY)

        assertFalse(state.isOpen(Overlay.HISTORY))
    }

    @Test
    fun openingTheDeckYouAlreadyHaveOpenShowsYourLatestWork() = runTest {
        // The read used to happen before the write that letting go starts, so
        // this came back as the deck from before the pending edit -- and then
        // the disk and the screen disagreed about a deck nobody had touched.
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(5).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val id = requireNonNull(state.deckId)

        state.removeOne(TestPool.many(5).first(), main)
        val edited = state.deck

        state.load(id)
        advanceUntilIdle()

        assertEquals(edited, state.deck, "the screen kept the edit")
        assertEquals(edited, assertNotNull(deps.deckRepository.byId(id)).entry.deck, "so did the disk")
    }

    @Test
    fun openingADeckThatIsNoLongerThereKeepsTheOneYouHave() = runTest {
        // Letting go happens first now, so a miss has to take it back.
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(5).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()
        val id = requireNonNull(state.deckId)
        val had = state.deck

        state.load("no such deck")
        advanceUntilIdle()

        assertEquals(had, state.deck)
        assertEquals(id, state.deckId, "still the deck it was, and still saved")
        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun undoingANewDeckGivesTheSavedDeckBackAsASavedDeck() = runTest {
        // Not just the cards: the deck's identity. Undoing used to hand back a
        // list that had never been saved, and the next save wrote a second copy
        // beside the original rather than back into it.
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(5).forEach { state.addCard(it) }
        state.rename("Snake-Eye")
        state.save()
        advanceUntilIdle()
        val id = requireNonNull(state.deckId)
        val saved = state.deck

        state.newDeck()
        state.toast?.undo?.invoke()
        advanceUntilIdle()

        assertEquals(saved, state.deck)
        assertEquals(id, state.deckId, "the same deck, not a nameless copy of it")
        assertEquals(SaveStatus.SAVED, state.saveStatus)
    }

    @Test
    fun undoingANewDeckDoesNotLeaveASecondDeckBehind() = runTest {
        val deps = testDependencies()
        val state = builderState(deps)
        TestPool.many(5).forEach { state.addCard(it) }
        state.save()
        advanceUntilIdle()

        state.newDeck()
        state.toast?.undo?.invoke()
        advanceUntilIdle()

        state.addCard(TestPool.many(6).last())
        state.save()
        advanceUntilIdle()

        assertEquals(1, deps.deckRepository.all().size, "one deck, saved twice")
    }

    @Test
    fun sharingSaysSoEitherWay() = runTest {
        // Silent before. On the tablet the share sheet is its own answer; on the
        // desktop there is no sheet, so a press that opened a folder behind the
        // window looked exactly like a press that did nothing.
        val state = builderState()
        TestPool.many(5).forEach { state.addCard(it) }
        state.rename("Snake-Eye")

        state.shareDeck()
        advanceUntilIdle()

        assertEquals("Shared Snake-Eye.ydk.", requireNonNull(state.toast?.message))
    }

    @Test
    fun aWriteThatFailedSaysSoAndACancelledOneDoesNot() = runTest {
        // The two ways of not writing want opposite things said about them.
        val failing = object : DeckFileAccess by NoFileAccess {
            override suspend fun export(name: String, bytes: ByteArray, mimeType: String) =
                WriteOutcome.FAILED
        }
        val state = builderState(testDependencies(failing))
        TestPool.many(5).forEach { state.addCard(it) }
        state.rename("Snake-Eye")

        state.exportToFile()
        advanceUntilIdle()
        assertEquals("Could not write Snake-Eye.ydk.", requireNonNull(state.toast?.message))

        val cancelling = builderState()
        TestPool.many(5).forEach { cancelling.addCard(it) }
        val before = cancelling.toast?.id

        cancelling.exportToFile()
        advanceUntilIdle()

        assertEquals(before, cancelling.toast?.id, "dismissing a picker is not news")
    }

    // ---- the pool ----------------------------------------------------------

    @Test
    fun thePoolIsNotCalledEmptyBeforeItHasBeenRead() = runTest {
        // The search pane offers to download the card database when the index is
        // empty, and an index nobody has opened yet is empty in exactly the same
        // way as one that was never downloaded. Reading thirteen thousand cards
        // out of SQLite is not instant, so that offer was being made to somebody
        // already holding all of them.
        val state = builderState()
        assertFalse(state.poolRead, "nothing has been read until start() has run")

        state.start()
        advanceUntilIdle()

        // Still empty afterwards -- this fixture has no cards in it, and that is
        // the point: the flag is about having looked, not about having found.
        assertTrue(state.poolRead)
        assertEquals(0, state.index.size)
    }

    @Test
    fun andNothingIsCalledIllegalUntilThereIsSomethingToCheckItAgainst() = runTest {
        // The validator is right to call a passcode it cannot resolve an error.
        // Every passcode is unresolvable to an index nobody has opened, so the
        // verdict on a perfectly good deck, on every cold launch, was sixty
        // errors in red saying the card database had never heard of any of it.
        val state = builderState()
        TestPool.many(5).forEach { state.addCard(it) }

        assertNull(state.validation, "no verdict before the pool has been read")

        state.start()
        advanceUntilIdle()

        assertNotNull(state.validation, "and a verdict once it has")
    }

    private fun requireNonNull(value: String?): String =
        value ?: error("nothing was exported")
}
