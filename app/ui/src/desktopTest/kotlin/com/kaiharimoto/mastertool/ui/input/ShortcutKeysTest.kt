package com.kaiharimoto.mastertool.ui.input

import com.kaiharimoto.mastertool.core.input.ShortcutTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seam between the shortcut table and the keyboard.
 *
 * `:core` says which chords exist and `:ui` turns a key event into one, and
 * nothing makes those agree. A binding whose key is missing from the map is not
 * a compile error — it is a shortcut that silently does nothing, discovered by
 * pressing it and getting no response, which is indistinguishable from having
 * misread the help sheet.
 */
class ShortcutKeysTest {

    @Test
    fun everyBoundChordHasAKeyThatCanProduceIt() {
        val known = CHORD_KEYS.values.toSet()
        val bound = ShortcutTable.all.map { it.chord.key }.distinct()

        val unreachable = bound.filterNot { it in known }
        assertTrue(
            unreachable.isEmpty(),
            "these shortcuts can never fire: $unreachable",
        )
    }

    @Test
    fun noTwoKeysProduceTheSameChord() {
        // Two Key entries mapping to one name would make a binding fire from a
        // key the help sheet never mentions.
        assertEquals(CHORD_KEYS.values.size, CHORD_KEYS.values.toSet().size)
    }

    @Test
    fun nothingIsMappedThatNothingUses() {
        // Not a failure, but worth seeing: a name here that no shortcut binds is
        // either a leftover or a binding somebody forgot to add.
        val bound = ShortcutTable.all.map { it.chord.key }.toSet()
        val unused = CHORD_KEYS.values.filterNot { it in bound }

        assertTrue(unused.isEmpty(), "mapped but never bound: $unused")
    }
}
