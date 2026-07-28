package com.kaiharimoto.mastertool.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutTableTest {

    private val builder = ShortcutContext()
    private val typing = ShortcutContext(textInputFocused = true)
    private val inspecting = ShortcutContext(inspectorOpen = true, overlayOpen = true)
    private val sheetOpen = ShortcutContext(overlayOpen = true)

    @Test
    fun resolvesAPlainKey() {
        assertEquals(ShortcutAction.TOGGLE_STATS, ShortcutTable.resolve(KeyChord("s"), builder))
    }

    @Test
    fun modifiersArePartOfTheChord() {
        assertEquals(
            ShortcutAction.SAVE,
            ShortcutTable.resolve(KeyChord("s", ctrl = true), builder),
        )
        assertEquals(
            ShortcutAction.UNDO,
            ShortcutTable.resolve(KeyChord("z", ctrl = true), builder),
        )
        assertEquals(
            ShortcutAction.REDO,
            ShortcutTable.resolve(KeyChord("z", ctrl = true, shift = true), builder),
        )
    }

    @Test
    fun anUnboundChordResolvesToNothing() {
        assertNull(ShortcutTable.resolve(KeyChord("q"), builder))
        assertNull(ShortcutTable.resolve(KeyChord("s", alt = true), builder))
    }

    // Typing must not trigger anything. This is the guard that stops "side"
    // opening the statistics panel and the deck check on its way past.

    @Test
    fun unmodifiedKeysDoNothingWhileTyping() {
        assertNull(ShortcutTable.resolve(KeyChord("s"), typing))
        assertNull(ShortcutTable.resolve(KeyChord("i"), typing))
        assertNull(ShortcutTable.resolve(KeyChord("1"), typing))
        assertNull(ShortcutTable.resolve(KeyChord("slash"), typing))
    }

    @Test
    fun escapeStillWorksWhileTyping() {
        assertEquals(ShortcutAction.DISMISS, ShortcutTable.resolve(KeyChord("escape"), typing))
    }

    @Test
    fun savingAndSearchingStillWorkWhileTyping() {
        assertEquals(
            ShortcutAction.SAVE,
            ShortcutTable.resolve(KeyChord("s", ctrl = true), typing),
        )
        assertEquals(
            ShortcutAction.FOCUS_SEARCH,
            ShortcutTable.resolve(KeyChord("f", ctrl = true), typing),
        )
    }

    @Test
    fun undoIsSuppressedWhileTyping() {
        // Renaming is not a deck edit, so undo here would revert a card change
        // rather than the text in front of the caret.
        assertNull(ShortcutTable.resolve(KeyChord("z", ctrl = true), typing))
    }

    // Scopes.

    @Test
    fun builderShortcutsAreOffWhileSomethingIsCoveringIt() {
        assertNull(ShortcutTable.resolve(KeyChord("s"), sheetOpen))
        assertNull(ShortcutTable.resolve(KeyChord("1"), sheetOpen))
    }

    @Test
    fun escapeReachesOverAnOpenSheet() {
        assertEquals(ShortcutAction.DISMISS, ShortcutTable.resolve(KeyChord("escape"), sheetOpen))
    }

    @Test
    fun arrowsOnlyPageTheInspectorWhileItIsOpen() {
        assertEquals(
            ShortcutAction.PREVIOUS_CARD,
            ShortcutTable.resolve(KeyChord("left"), inspecting),
        )
        assertEquals(ShortcutAction.NEXT_CARD, ShortcutTable.resolve(KeyChord("right"), inspecting))
    }

    @Test
    fun theSameArrowMovesTheCursorWhenTheInspectorIsShut() {
        // Two meanings for one key, told apart by scope alone. The inspector
        // entries are declared first, which is the whole mechanism -- so this
        // pair is really a test that declaration order still decides.
        assertEquals(ShortcutAction.CURSOR_LEFT, ShortcutTable.resolve(KeyChord("left"), builder))
        assertEquals(ShortcutAction.CURSOR_DOWN, ShortcutTable.resolve(KeyChord("down"), builder))
    }

    @Test
    fun modifiersSayWhatToDoWithTheCursor() {
        assertEquals(
            ShortcutAction.EXTEND_RIGHT,
            ShortcutTable.resolve(KeyChord("right", shift = true), builder),
        )
        assertEquals(
            ShortcutAction.CARRY_UP,
            ShortcutTable.resolve(KeyChord("up", ctrl = true), builder),
        )
    }

    @Test
    fun searchCanBeReachedTwoWays() {
        assertEquals(ShortcutAction.FOCUS_SEARCH, ShortcutTable.resolve(KeyChord("slash"), builder))
        assertEquals(
            ShortcutAction.FOCUS_SEARCH,
            ShortcutTable.resolve(KeyChord("f", ctrl = true), builder),
        )
    }

    @Test
    fun helpIsShiftSlash() {
        assertEquals(
            ShortcutAction.TOGGLE_HELP,
            ShortcutTable.resolve(KeyChord("slash", shift = true), builder),
        )
    }

    // Table hygiene.

    @Test
    fun noTwoShortcutsClaimTheSameChordInTheSameScope() {
        val seen = mutableSetOf<Pair<KeyChord, ShortcutScope>>()
        ShortcutTable.all.forEach { shortcut ->
            assertTrue(
                seen.add(shortcut.chord to shortcut.scope),
                "${shortcut.chord.label} is bound twice in ${shortcut.scope}",
            )
        }
    }

    @Test
    fun everyShortcutCanActuallyFire() {
        // A binding no context can reach is a binding nobody will ever discover.
        val contexts = listOf(builder, typing, inspecting, sheetOpen)
        ShortcutTable.all.forEach { shortcut ->
            assertTrue(
                contexts.any { ShortcutTable.resolve(shortcut.chord, it) == shortcut.action },
                "${shortcut.chord.label} (${shortcut.action}) is unreachable",
            )
        }
    }

    @Test
    fun everyShortcutIsEitherDescribedOrOneOfADescribedSet() {
        // The help sheet renders this table, so a blank description is a binding
        // that goes unmentioned. That is allowed for exactly one reason: the
        // three siblings of each arrow row, which are covered by the row their
        // lead entry prints. Anything else with a blank description is a binding
        // somebody added and forgot to explain.
        val describedShapes = ShortcutTable.all
            .filter { it.inHelp }
            .map { Triple(it.chord.ctrl, it.chord.shift, it.scope) }
            .toSet()

        ShortcutTable.all.filterNot { it.inHelp }.forEach { shortcut ->
            assertTrue(
                Triple(shortcut.chord.ctrl, shortcut.chord.shift, shortcut.scope) in describedShapes,
                "${shortcut.chord.label} is bound but nothing in the help sheet mentions it",
            )
        }
    }

    @Test
    fun everyActionIsReachable() {
        // The other half of the same worry: an action added to the enum and
        // never bound is a feature that exists and cannot be used.
        val bound = ShortcutTable.all.map { it.action }.toSet()
        val unreachable = ShortcutAction.entries.filterNot { it in bound }

        assertTrue(unreachable.isEmpty(), "nothing fires these: $unreachable")
    }

    @Test
    fun chordsReadAsSomethingAHumanRecognises() {
        assertEquals("Ctrl+Shift+Z", KeyChord("z", ctrl = true, shift = true).label)
        assertEquals("Esc", KeyChord("escape").label)
        assertEquals("/", KeyChord("slash").label)
        assertEquals("←", KeyChord("left").label)
        assertEquals("S", KeyChord("s").label)
    }

    @Test
    fun everyActionIsBoundToSomething() {
        val bound = ShortcutTable.all.map { it.action }.toSet()
        assertEquals(ShortcutAction.entries.toSet(), bound)
    }

    @Test
    fun resolvingIsPureAndRepeatable() {
        val chord = KeyChord("s")
        assertNotNull(ShortcutTable.resolve(chord, builder))
        assertEquals(
            ShortcutTable.resolve(chord, builder),
            ShortcutTable.resolve(chord, builder),
        )
    }
}
