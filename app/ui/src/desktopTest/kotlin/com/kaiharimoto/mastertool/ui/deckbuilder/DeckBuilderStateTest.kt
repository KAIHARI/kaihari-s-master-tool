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
