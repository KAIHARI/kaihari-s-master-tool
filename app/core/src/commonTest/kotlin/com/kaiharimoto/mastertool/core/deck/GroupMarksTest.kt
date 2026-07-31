package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupMarksTest {

    private fun group(id: String, name: String, order: Int) = DeckGroup(id, name, 0, order)

    @Test
    fun everyPresetGetsItsOwnMark() {
        // The eight roles are the common case, and they must all be distinct —
        // Engine and Extender both start with E, which is why the mark is two
        // letters rather than one.
        val groups = GroupPresets.all.mapIndexed { i, preset -> group("g$i", preset.name, i) }
        val marks = GroupMarks.marks(groups)

        assertEquals(
            listOf("EN", "ST", "EX", "NE", "BR", "HA", "BE", "OU"),
            groups.map { marks.getValue(it.id) },
        )
    }

    @Test
    fun twoWordsGiveTheirInitials() {
        assertEquals("NE", GroupMarks.mark("Non-engine"))
        assertEquals("BB", GroupMarks.mark("board breakers"))
        assertEquals("GS", GroupMarks.mark("Going second"))
    }

    @Test
    fun oneWordGivesItsFirstTwoLetters() {
        assertEquals("EN", GroupMarks.mark("Engine"))
        assertEquals("HA", GroupMarks.mark("handtrap"))
        assertEquals("X", GroupMarks.mark("x"))
    }

    @Test
    fun anUnnamedGroupStillGetsAMark() {
        assertEquals("G", GroupMarks.mark(""))
        assertEquals("G", GroupMarks.mark("   "))
        assertEquals("G", GroupMarks.mark("---"))
    }

    @Test
    fun aCollisionTakesTheNextLetterThatTellsThemApart() {
        // Brick and Breaker both want BR. The loser becomes BE, which still
        // reads as the word, rather than B2, which reads as a serial number.
        val groups = listOf(group("a", "Brick", 0), group("b", "Breaker", 1))
        val marks = GroupMarks.marks(groups)

        assertEquals("BR", marks.getValue("a"))
        assertEquals("BE", marks.getValue("b"))
    }

    @Test
    fun identicalNamesFallBackToACounter() {
        val groups = listOf(
            group("a", "Engine", 0),
            group("b", "Engine", 1),
            group("c", "Engine", 2),
        )
        val marks = GroupMarks.marks(groups)

        assertEquals("EN", marks.getValue("a"))
        assertEquals(3, marks.values.toSet().size)
        assertTrue(marks.values.all { it.length == 2 })
    }

    @Test
    fun marksAreAlwaysDistinctAndShort() {
        val groups = (0 until 12).map { group("g$it", "Group", it) }
        val marks = GroupMarks.marks(groups)

        assertEquals(12, marks.values.toSet().size)
        assertTrue(marks.values.all { it.length in 1..2 })
        assertTrue(marks.values.all { mark -> mark.all { it.isLetterOrDigit() } })
    }
}
