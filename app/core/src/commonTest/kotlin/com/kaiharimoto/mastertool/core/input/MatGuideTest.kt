package com.kaiharimoto.mastertool.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guide is only worth having if it cannot be wrong.
 *
 * A control list written by hand beside the code it describes is wrong within
 * two changes, and a wrong guide is worse than no guide: it is the difference
 * between a user who does not know a gesture and a user who has been told the
 * wrong one and concludes the table is broken. So the guide is data, and these
 * are the properties that keep the data honest.
 */
class MatGuideTest {

    @Test
    fun everyGestureHasBothIdioms() {
        // The standing rule — every gesture ships with a touch idiom and a
        // pointer one — said in the one place a test can reach it. A blank
        // pointer column is a gesture a desktop user cannot make, and that has
        // never once been a decision somebody took: it is what happens when a
        // touch gesture is added and nobody looks at the other column.
        MatGuide.all.forEach {
            assertTrue(it.what.isNotBlank(), "a gesture with no name")
            assertTrue(it.touch.isNotBlank(), "'${it.what}' has no touch idiom")
            assertTrue(it.pointer.isNotBlank(), "'${it.what}' has no pointer idiom")
        }
    }

    @Test
    fun everyConsoleIdiomIsActuallyWrittenDown() {
        // `button` is optional because the 3DS port is behind the tablet, and a
        // gesture with no console idiom *yet* is a fact rather than a fault. A
        // blank one is neither: it is a column somebody started filling in and
        // stopped, and it renders as a gesture the console claims to have and
        // does not name.
        //
        // What holds the strings themselves to `mt_input.h` is not here — it
        // cannot be, since this module knows nothing about the port. The golden
        // vectors export this column and `3ds/test/mt_test.c` asserts every
        // button named here is one that table carries.
        MatGuide.all.forEach {
            val button = it.button ?: return@forEach
            assertTrue(button.isNotBlank(), "'${it.what}' has an empty console idiom")
            assertEquals(button.trim(), button, "'${it.what}' pads its console idiom")
        }
    }

    @Test
    fun everyHeadingHasSomethingUnderIt() {
        // The guide renders by walking the groups rather than by naming them,
        // which is what stops a group from going missing — and which means an
        // empty group renders as a heading with nothing beneath it.
        GestureGroup.entries.forEach {
            assertTrue(MatGuide.of(it).isNotEmpty(), "${it.heading} is an empty heading")
        }
    }

    @Test
    fun noGestureIsListedTwice() {
        val names = MatGuide.all.map { it.what }
        assertEquals(names.distinct().size, names.size, "a gesture is described twice: $names")
    }

    @Test
    fun theKeysTheGuideNamesAreKeysTheTableBinds() {
        // The guide and the shortcut list are shown on the same screen, one
        // above the other. They are allowed to describe the same action in
        // different words; they are not allowed to name a key nobody bound.
        val bound = ShortcutTable.all
            .filter { it.scope == ShortcutScope.PLAY }
            .map { it.chord.label }
            .toSet()

        MatGuide.all.mapNotNull { it.key }.forEach { named ->
            // Several seats on one line, which is how they read on the bar.
            named.split("·").map { it.trim() }.forEach {
                assertTrue(it in bound, "the guide names $it, which no play shortcut binds")
            }
        }
    }

    @Test
    fun thereIsAWayToOpenTheGuideWithoutTouchingTheScreen() {
        // The button on the bar is the touch half. Without this the guide would
        // itself be the one thing on the stage that breaks the rule it exists
        // to document.
        assertTrue(
            ShortcutTable.all.any {
                it.action == ShortcutAction.PLAY_GUIDE && it.scope == ShortcutScope.PLAY
            },
            "no shortcut opens the play guide",
        )
    }

    @Test
    fun theCameraIsExplainedFirst() {
        // Ordering as a claim rather than an accident. A table you can orbit
        // creates exactly one question it never answers by itself — "how do I
        // get back to where I was" — and the guide opens with the answer.
        assertEquals(GestureGroup.CAMERA, GestureGroup.entries.first())
    }
}
