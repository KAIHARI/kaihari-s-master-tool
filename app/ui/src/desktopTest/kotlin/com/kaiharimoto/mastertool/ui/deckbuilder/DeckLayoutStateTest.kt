package com.kaiharimoto.mastertool.ui.deckbuilder

import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.prefs.SectionPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The shape of the room, rather than the deck in it.
 *
 * This holder carries the only real arithmetic in the UI layer that is not
 * already in `:core` — panes trading weight across a divider — and it had no
 * tests at all. It is also the one place where getting it wrong is invisible
 * rather than obvious: a resize that quietly shrinks the pane on the far side of
 * the drag looks like the drag being imprecise, not like a bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckLayoutStateTest {

    private val main = DeckSection.MAIN
    private val extra = DeckSection.EXTRA
    private val side = DeckSection.SIDE

    private fun TestScope.layoutState(): DeckLayoutState =
        DeckLayoutState(
            testDependencies().preferencesRepository,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        ).apply { deckColumnHeightPx = 900f }

    // ---- collapsing --------------------------------------------------------

    @Test
    fun focusingASectionCollapsesTheOtherTwo() = runTest {
        val layout = layoutState()

        layout.focusSection(main)

        assertFalse(layout.preferences[main].collapsed)
        assertTrue(layout.preferences[extra].collapsed)
        assertTrue(layout.preferences[side].collapsed)
    }

    @Test
    fun focusingTheAlreadyFocusedSectionPutsThemAllBack() = runTest {
        // Otherwise the key that gives the Main deck the column has no way back
        // except pressing two other keys.
        val layout = layoutState()

        layout.focusSection(main)
        layout.focusSection(main)

        assertTrue(DeckSection.entries.none { layout.preferences[it].collapsed })
    }

    @Test
    fun focusingADifferentSectionMovesTheFocus() = runTest {
        val layout = layoutState()

        layout.focusSection(main)
        layout.focusSection(side)

        assertTrue(layout.preferences[main].collapsed)
        assertFalse(layout.preferences[side].collapsed)
    }

    @Test
    fun collapsingIsItsOwnToggle() = runTest {
        val layout = layoutState()
        val before = layout.preferences[extra].collapsed

        layout.toggleCollapsed(extra)
        assertEquals(!before, layout.preferences[extra].collapsed)

        layout.toggleCollapsed(extra)
        assertEquals(before, layout.preferences[extra].collapsed)
    }

    // ---- trading weight across a divider -----------------------------------

    @Test
    fun aResizeTradesWeightSoTheTotalStaysPut() = runTest {
        // What makes a three-pane resize feel like moving one boundary rather
        // than rescaling the column: whatever one pane gains, its neighbour
        // loses, and the third never moves.
        val layout = layoutState()
        val before = DeckSection.entries.sumOf { layout.preferences[it].weight.toDouble() }
        val untouched = layout.preferences[side].weight

        layout.resizePanes(main, extra, deltaPx = 60f)

        val after = DeckSection.entries.sumOf { layout.preferences[it].weight.toDouble() }
        assertEquals(before, after, 0.0001)
        assertEquals(untouched, layout.preferences[side].weight)
    }

    @Test
    fun draggingDownGrowsTheTopPaneAndShrinksTheOneBelow() = runTest {
        val layout = layoutState()
        val topBefore = layout.preferences[main].weight
        val bottomBefore = layout.preferences[extra].weight

        layout.resizePanes(main, extra, deltaPx = 60f)

        assertTrue(layout.preferences[main].weight > topBefore)
        assertTrue(layout.preferences[extra].weight < bottomBefore)
    }

    @Test
    fun draggingUpShrinksIt() = runTest {
        val layout = layoutState()
        val before = layout.preferences[main].weight

        layout.resizePanes(main, extra, deltaPx = -60f)

        assertTrue(layout.preferences[main].weight < before)
    }

    @Test
    fun aResizeWithNoRoomToReportDoesNothing() = runTest {
        // The height arrives from a layout pass, so the first frames have none.
        val layout = layoutState()
        layout.deckColumnHeightPx = 0f
        val before = layout.preferences

        layout.resizePanes(main, extra, deltaPx = 200f)

        assertEquals(before, layout.preferences)
    }

    @Test
    fun aPaneCannotBeDraggedPastItsMinimum() = runTest {
        // And crucially, when it stops, the *other* pane stops too. Applying a
        // delta the top pane refused would shrink the bottom one past its own
        // limit to pay for space nobody received.
        val layout = layoutState()
        val before = DeckSection.entries.sumOf { layout.preferences[it].weight.toDouble() }

        repeat(40) { layout.resizePanes(main, extra, deltaPx = -200f) }

        assertTrue(layout.preferences[main].weight >= SectionPreferences.MIN_WEIGHT)
        assertTrue(layout.preferences[extra].weight >= SectionPreferences.MIN_WEIGHT)
        assertEquals(
            before,
            DeckSection.entries.sumOf { layout.preferences[it].weight.toDouble() },
            0.0001,
        )
    }

    @Test
    fun resizingRepeatedlyNeverLosesOrInventsWeight() = runTest {
        val layout = layoutState()
        val before = DeckSection.entries.sumOf { layout.preferences[it].weight.toDouble() }

        listOf(40f, -90f, 15f, -5f, 120f).forEach {
            layout.resizePanes(main, extra, it)
            layout.resizePanes(extra, side, -it)
        }

        assertEquals(
            before,
            DeckSection.entries.sumOf { layout.preferences[it].weight.toDouble() },
            0.0001,
        )
    }

    // ---- how wide a grid actually is ---------------------------------------

    @Test
    fun aPaneThatHasNotMeasuredFallsBackToWhatIsStored() = runTest {
        val layout = layoutState()

        assertEquals(layout.preferences[main].columns, layout.columnsOn(main))
    }

    @Test
    fun aMeasuredPaneReportsWhatItSettledOn() = runTest {
        // The stored preference can say "size yourself", in which case only the
        // composable that laid the grid out knows the answer.
        val layout = layoutState()

        layout.noteDisplayedColumns(main, 11)

        assertEquals(11, layout.columnsOn(main))
    }

    @Test
    fun takingTheDensityControlIsTakingOver() = runTest {
        val layout = layoutState()

        layout.setColumns(main, 9)

        assertEquals(9, layout.preferences[main].columns)
        assertFalse(layout.preferences[main].autoFit, "reaching for it is the decision")
    }

    @Test
    fun draggingADividerPinsBothPanesAtWhatTheyWereShowing() = runTest {
        // Otherwise the cards re-flow to a new column count mid-drag, so the
        // drag changes how many cards are in a row instead of how big they are.
        val layout = layoutState()
        layout.noteDisplayedColumns(main, 10)
        layout.noteDisplayedColumns(extra, 6)

        layout.resizePanes(main, extra, deltaPx = 40f)

        assertEquals(10, layout.preferences[main].columns)
        assertEquals(6, layout.preferences[extra].columns)
        assertFalse(layout.preferences[main].autoFit)
        assertFalse(layout.preferences[extra].autoFit)
    }

    @Test
    fun pinningDoesNotKeepReapplyingDuringADrag() = runTest {
        // `pin` runs on every frame of a drag. Once a section is manual it has
        // to stop having an opinion, or a mid-drag column count overwrites the
        // one it was pinned at.
        val layout = layoutState()
        layout.noteDisplayedColumns(main, 10)
        layout.resizePanes(main, extra, deltaPx = 10f)

        layout.noteDisplayedColumns(main, 4)
        layout.resizePanes(main, extra, deltaPx = 10f)

        assertEquals(10, layout.preferences[main].columns)
    }

    // ---- the pool's share --------------------------------------------------

    @Test
    fun theSearchPaneTakesItsShareOfTheWindow() = runTest {
        val layout = layoutState()
        layout.builderWidthPx = 1000f
        val before = layout.preferences.searchWeight

        layout.resizeSearchPane(deltaPx = 100f)

        assertTrue(layout.preferences.searchWeight > before)
    }

    @Test
    fun aSearchResizeBeforeTheWindowIsMeasuredDoesNothing() = runTest {
        val layout = layoutState()
        val before = layout.preferences.searchWeight

        layout.resizeSearchPane(deltaPx = 100f)

        assertEquals(before, layout.preferences.searchWeight)
    }

    @Test
    fun sizingThePoolByHandIsRememberedAndFitIsZero() = runTest {
        val layout = layoutState()

        layout.setSearchColumns(7)
        assertEquals(7, layout.preferences.searchColumns)

        layout.setSearchColumns(0)
        assertEquals(0, layout.preferences.searchColumns, "zero means size to fit")

        layout.setSearchColumns(-3)
        assertEquals(0, layout.preferences.searchColumns, "and nothing below it")
    }
}

/**
 * Which deck was open when the program was last closed.
 *
 * Lives in the preferences document rather than a table of its own, because it
 * is one nullable string and a table for a sentence is a table too many.
 */
class LastDeckTest {

    private fun TestScope.layoutState(): DeckLayoutState =
        DeckLayoutState(
            testDependencies().preferencesRepository,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

    @Test
    fun nothingIsRememberedUntilADeckIsOpened() = runTest {
        assertEquals(null, layoutState().preferences.lastDeckId)
    }

    @Test
    fun theOpenDeckIsRemembered() = runTest {
        val layout = layoutState()

        layout.rememberOpenDeck("snake-eye")

        assertEquals("snake-eye", layout.preferences.lastDeckId)
    }

    @Test
    fun closingToANewDeckForgetsIt() = runTest {
        // A new deck has no id, and reopening the previous one on the next run
        // would be the program deciding what you meant to be working on.
        val layout = layoutState()
        layout.rememberOpenDeck("snake-eye")

        layout.rememberOpenDeck(null)

        assertEquals(null, layout.preferences.lastDeckId)
    }

    @Test
    fun rememberingTheSameDeckAgainChangesNothing() = runTest {
        // It is called on every change of the open deck, so the common case is
        // being told what it already knows.
        val layout = layoutState()
        layout.rememberOpenDeck("snake-eye")
        val before = layout.preferences

        layout.rememberOpenDeck("snake-eye")

        assertEquals(before, layout.preferences)
    }
}
