package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private var next = 900_000

private fun snakeEye(name: String) =
    TestCards.monster(id = next++, name = name, archetype = "Snake-Eye")

private fun fiendsmith(name: String) =
    TestCards.monster(id = next++, name = name, archetype = "Fiendsmith")

private fun plain(name: String) = TestCards.monster(id = next++, name = name)

private fun spell(name: String) = TestCards.pot.copy(name = name)

private fun trap(name: String) = TestCards.infiniteImpermanence.copy(name = name)

private fun copies(card: Card, n: Int) = List(n) { card }

class GroupNamingTest {

    @Test
    fun anEmptyGroupIsNotCalledAnything() {
        assertNull(GroupNaming.nameOf(emptyList()))
    }

    @Test
    fun anArchetypeThatIsMostOfTheGroupNamesIt() {
        val group = listOf(
            snakeEye("Snake-Eye Ash"),
            snakeEye("Snake-Eye Oak"),
            snakeEye("Snake-Eye Diabellstar"),
            plain("Called by the Grave"),
        )

        assertEquals("Snake-Eye", GroupNaming.nameOf(group))
    }

    @Test
    fun exactlyHalfIsEnough() {
        val group = listOf(snakeEye("Ash"), snakeEye("Oak"), plain("a"), plain("b"))

        assertEquals("Snake-Eye", GroupNaming.nameOf(group))
    }

    @Test
    fun aMixtureIsNotCalledByItsLargestPart() {
        // Two of five is a group with some Snake-Eye cards in it, not a
        // Snake-Eye group, and a nearly-true label is one nobody keeps reading.
        // Mixed kinds too, so nothing else picks the name up.
        val group = listOf(
            snakeEye("Ash"),
            snakeEye("Oak"),
            spell("Called by the Grave"),
            spell("Bonfire"),
            trap("Infinite Impermanence"),
        )

        assertNull(GroupNaming.nameOf(group))
    }

    @Test
    fun anArchetypeBelowTheShareStillLeavesTheKindOfCard() {
        // Two of five Snake-Eye is not a Snake-Eye group, but five monsters are
        // still five monsters -- the weaker true thing rather than nothing.
        val group = listOf(snakeEye("Ash"), snakeEye("Oak"), plain("a"), plain("b"), plain("c"))

        assertEquals("Monsters", GroupNaming.nameOf(group))
    }

    @Test
    fun oneCardNeverNamesItsOwnGroup() {
        // Otherwise every single-card group between two gaps would be named
        // after whatever archetype that one card happens to belong to.
        assertNull(GroupNaming.nameOf(listOf(snakeEye("Ash"))))
    }

    @Test
    fun theBiggerArchetypeWins() {
        val group = listOf(
            snakeEye("Ash"),
            snakeEye("Oak"),
            snakeEye("Diabellstar"),
            fiendsmith("Engraver"),
            fiendsmith("Requiem"),
        )

        assertEquals("Snake-Eye", GroupNaming.nameOf(group))
    }

    @Test
    fun aTieBetweenArchetypesSettlesTheSameWayEveryTime() {
        // A map has no order, and a label that changed between two draws of the
        // same panel would read as the deck changing.
        val group = listOf(snakeEye("Ash"), snakeEye("Oak"), fiendsmith("A"), fiendsmith("B"))

        assertEquals(GroupNaming.nameOf(group), GroupNaming.nameOf(group.reversed()))
        assertEquals("Fiendsmith", GroupNaming.nameOf(group), "alphabetical breaks the tie")
    }

    @Test
    fun aGroupThatIsAllOneKindOfCardIsCalledThat() {
        assertEquals("Spells", GroupNaming.nameOf(listOf(spell("Pot"), spell("Called by"))))
        assertEquals("Traps", GroupNaming.nameOf(listOf(trap("Imperm"), trap("Evenly"))))
        assertEquals("Monsters", GroupNaming.nameOf(listOf(plain("a"), plain("b"), plain("c"))))
    }

    @Test
    fun anArchetypeBeatsTheKindOfCard() {
        // "Snake-Eye" says more than "Monsters", and both are true.
        val group = listOf(snakeEye("Ash"), snakeEye("Oak"), snakeEye("Diabellstar"))

        assertEquals("Snake-Eye", GroupNaming.nameOf(group))
    }

    @Test
    fun spellsAndTrapsTogetherAreNeither() {
        val group = listOf(spell("Pot"), spell("Called by"), trap("Imperm"))

        // Falls through to the copy count, and there are no repeats here.
        assertNull(GroupNaming.nameOf(group))
    }

    @Test
    fun theKindOfCardBeatsACopyCount() {
        // "Monsters" is about the whole group and "Bonfire x3" is about part of
        // it, so the copy count only speaks when nothing better can.
        val group = copies(plain("Bonfire"), 3) + plain("a") + plain("b")

        assertEquals("Monsters", GroupNaming.nameOf(group))
    }

    @Test
    fun theCopyCountIsTheLastResort() {
        val group = copies(spell("Bonfire"), 3) + trap("Imperm")

        assertEquals("Bonfire ×3", GroupNaming.nameOf(group))
    }

    @Test
    fun oneOfEachIsNotAGroupAboutAnything() {
        val group = listOf(spell("Pot"), trap("Imperm"), plain("Ash"))

        assertNull(GroupNaming.nameOf(group))
    }

    @Test
    fun aBlankArchetypeIsNoArchetype() {
        // The database sends empty strings, and "" is not a name.
        val group = List(4) { TestCards.monster(id = next++, name = "card $it", archetype = "  ") }

        assertEquals("Monsters", GroupNaming.nameOf(group), "falls through to the kind")
    }

    @Test
    fun theKindOfCardHasNoWordForOther() {
        // OTHER is a real category and not a word anybody wants on a row, so a
        // group of them falls through like any other mixture.
        val group = listOf(TestCards.token, TestCards.token.copy(name = "Ojama Token"))

        assertNull(GroupNaming.nameOf(group))
    }
}
