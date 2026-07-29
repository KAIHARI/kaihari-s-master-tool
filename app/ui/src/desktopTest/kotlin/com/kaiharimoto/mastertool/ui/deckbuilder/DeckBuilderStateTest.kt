package com.kaiharimoto.mastertool.ui.deckbuilder

import com.kaiharimoto.mastertool.core.deck.Breaks
import com.kaiharimoto.mastertool.core.deck.SaveStatus
import com.kaiharimoto.mastertool.core.deck.TidyBy
import com.kaiharimoto.mastertool.core.layout.GridStep
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.ImportedFile
import kotlin.test.Test
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
            Overlay.FILTERS -> filtersVisible = true
            Overlay.STATS -> statsVisible = true
            Overlay.SIDING -> sidingVisible = true
            Overlay.TEST_HAND -> testHandVisible = true
            Overlay.SHOOTOUT -> shootoutVisible = true
            Overlay.NOTES -> notesVisible = true
            Overlay.ISSUES -> issuesVisible = true
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

    private fun requireNonNull(value: String?): String =
        value ?: error("nothing was exported")
}
