package com.kaiharimoto.mastertool.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutTableTest {

    private val builder = ShortcutContext()
    private val typing = ShortcutContext(textInputFocused = true)
    private val inspecting = ShortcutContext(topLayer = ShortcutLayer.INSPECTOR)
    private val sheetOpen = ShortcutContext(topLayer = ShortcutLayer.ISSUES)
    private val playing = ShortcutContext(topLayer = ShortcutLayer.PLAY)

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

    // Toggles. A toggle that can only open is not a toggle, which is what the
    // first release of these bindings shipped as: opening the panel switched the
    // BUILDER scope off, taking the shortcut with it.

    @Test
    fun aToggleStillFiresWhileItsOwnPanelIsOpen() {
        assertEquals(
            ShortcutAction.TOGGLE_STATS,
            ShortcutTable.resolve(KeyChord("s"), ShortcutContext(topLayer = ShortcutLayer.STATS)),
        )
        assertEquals(
            ShortcutAction.TOGGLE_ISSUES,
            ShortcutTable.resolve(KeyChord("i"), ShortcutContext(topLayer = ShortcutLayer.ISSUES)),
        )
        assertEquals(
            ShortcutAction.TOGGLE_FILTERS,
            ShortcutTable.resolve(
                KeyChord("f", ctrl = true, shift = true),
                ShortcutContext(topLayer = ShortcutLayer.FILTERS),
            ),
        )
        assertEquals(
            ShortcutAction.TOGGLE_HELP,
            ShortcutTable.resolve(
                KeyChord("slash", shift = true),
                ShortcutContext(topLayer = ShortcutLayer.HELP),
            ),
        )
    }

    @Test
    fun aToggleDoesNotFireOverSomeoneElsesPanel() {
        // "s" over the issues panel would open statistics *behind* it.
        assertNull(ShortcutTable.resolve(KeyChord("s"), sheetOpen))
        assertNull(
            ShortcutTable.resolve(KeyChord("s"), ShortcutContext(topLayer = ShortcutLayer.HELP)),
        )
    }

    @Test
    fun onlySteppingShortcutsRepeat() {
        // Key repeat walking the inspector or the undo stack is useful; key
        // repeat strobing a panel open and shut is not.
        val repeatable = ShortcutTable.all.filter { it.repeatable }.map { it.action }.toSet()
        assertEquals(
            setOf(
                ShortcutAction.PREVIOUS_CARD,
                ShortcutAction.NEXT_CARD,
                ShortcutAction.UNDO,
                ShortcutAction.REDO,
                // Holding `d` deals: opening five cards on the play stage is
                // five presses otherwise, and drawing is a stepping action in
                // the same sense paging is.
                ShortcutAction.PLAY_DRAW,
            ),
            repeatable,
        )
    }

    @Test
    fun arrowsOnlyPageTheInspectorWhileItIsOpen() {
        assertEquals(
            ShortcutAction.PREVIOUS_CARD,
            ShortcutTable.resolve(KeyChord("left"), inspecting),
        )
        assertEquals(ShortcutAction.NEXT_CARD, ShortcutTable.resolve(KeyChord("right"), inspecting))
        assertNull(ShortcutTable.resolve(KeyChord("left"), builder))
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
        val contexts = listOf(builder, typing, inspecting, sheetOpen, playing)
        ShortcutTable.all.forEach { shortcut ->
            assertTrue(
                contexts.any { ShortcutTable.resolve(shortcut.chord, it) == shortcut.action },
                "${shortcut.chord.label} (${shortcut.action}) is unreachable",
            )
        }
    }

    @Test
    fun theSameLetterMeansDifferentThingsOnTheTableAndInTheBuilder() {
        // The two scopes are never live at once, which is what makes reusing
        // the good letters safe rather than ambiguous.
        assertEquals(ShortcutAction.TOGGLE_STATS, ShortcutTable.resolve(KeyChord("s"), builder))
        assertEquals(ShortcutAction.PLAY_SHUFFLE, ShortcutTable.resolve(KeyChord("s"), playing))
        assertEquals(
            ShortcutAction.TOGGLE_SEARCH_PANE,
            ShortcutTable.resolve(KeyChord("d"), builder),
        )
        assertEquals(ShortcutAction.PLAY_DRAW, ShortcutTable.resolve(KeyChord("d"), playing))
    }

    @Test
    fun theTableOnlyUsesKeysTheHostCanName() {
        // `ShortcutHost.chordName` is an explicit list, and a binding on a key
        // missing from it is a binding that silently never fires — which is
        // exactly what had happened to `d` and `n`. This is the reminder: add a
        // key here and you must name it there too.
        val named = setOf(
            "escape", "slash", "space", "left", "right",
            "1", "2", "3",
            "b", "c", "d", "f", "g", "i", "n", "s", "t", "z",
        )
        val used = ShortcutTable.all.map { it.chord.key }.toSet()
        assertEquals(emptySet(), used - named, "bound to a key the host cannot name")
    }

    @Test
    fun everyShortcutIsDescribedForTheHelpSheet() {
        // The help sheet renders this table, so a blank description is a blank row.
        ShortcutTable.all.forEach { assertTrue(it.description.isNotBlank()) }
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
