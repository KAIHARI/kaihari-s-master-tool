package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckLensTest {

    private fun card(id: Int, archetype: String? = null) = Card(
        id = CardId(id),
        name = "Card $id",
        type = "Effect Monster",
        frameType = "effect",
        archetype = archetype,
    )

    private val index = listOf(
        card(1, "Snake-Eye"),
        card(2, "Snake-Eye"),
        card(3, "Snake-Eye"),
        card(4, "Fiendsmith"),
        card(5, "Fiendsmith"),
        card(6, "Kashtira"),
        card(7, null),
        card(8, null),
    ).associateBy { it.id }

    private val cards: (CardId) -> Card? = index::get

    private fun deck(vararg ids: Int) = ids.map(::CardId)

    // ---- the quiet position -------------------------------------------------

    @Test
    fun theDeckLensColoursNothing() {
        val keying = DeckLenses.key(Lens.DECK, deck(1, 2, 3), cards, DeckGroups.EMPTY)

        assertTrue(keying.keys.isEmpty())
        assertEquals(3, keying.keyOfCell.size)
        assertEquals(3, keying.unclaimed)
        assertTrue(keying.isEmpty)
    }

    // ---- roles --------------------------------------------------------------

    @Test
    fun rolesKeepsAnEmptyGroupVisible() {
        // The group has to survive being empty or there is no way back into it
        // to put the first card in.
        val groups = DeckGroups.EMPTY
            .upsert(DeckGroup("g1", "Engine", color = 2, order = 0))
            .upsert(DeckGroup("g2", "Handtraps", color = 5, order = 1))
            .assign(CardId(1), "g1")

        val keying = DeckLenses.key(Lens.ROLES, deck(1, 2, 3), cards, groups)

        assertEquals(listOf("g1", "g2"), keying.keyOrder)
        assertEquals(1, keying.countOf("g1"))
        assertEquals(0, keying.countOf("g2"))
        assertEquals(2, keying.unclaimed)
    }

    @Test
    fun rolesMarksAreTheGroupMarks() {
        val groups = DeckGroups.EMPTY
            .upsert(DeckGroup("g1", "Brick", color = 0, order = 0))
            .upsert(DeckGroup("g2", "Breaker", color = 1, order = 1))

        val keying = DeckLenses.key(Lens.ROLES, deck(1), cards, groups)

        assertEquals("BR", keying.keyById("g1")?.mark)
        // Distinct within the lens, by the same collision rule as everywhere.
        assertEquals("BE", keying.keyById("g2")?.mark)
    }

    @Test
    fun everyCopyOfACardTakesItsGroup() {
        // Assignment is per passcode; three copies of one card is one decision.
        val groups = DeckGroups.EMPTY
            .upsert(DeckGroup("g1", "Engine", color = 2, order = 0))
            .assign(CardId(1), "g1")

        val keying = DeckLenses.key(Lens.ROLES, deck(1, 1, 1, 2), cards, groups)

        assertContentEquals(listOf("g1", "g1", "g1", null), keying.keyOfCell)
    }

    // ---- archetype ----------------------------------------------------------

    @Test
    fun archetypesRankByHowMuchOfTheDeckTheyAre() {
        val keying = DeckLenses.key(
            Lens.ARCHETYPE,
            deck(6, 4, 5, 1, 2, 3),
            cards,
            DeckGroups.EMPTY,
        )

        assertEquals(listOf("Snake-Eye", "Fiendsmith", "Kashtira"), keying.keyOrder)
        assertEquals(3, keying.countOf("Snake-Eye"))
        assertEquals(0, keying.unclaimed)
    }

    @Test
    fun cardsWithNoArchetypeAreLeftBare() {
        // Which is the reading: what is not on the ramp is the non-engine.
        val keying = DeckLenses.key(Lens.ARCHETYPE, deck(1, 7, 8), cards, DeckGroups.EMPTY)

        assertEquals(listOf("Snake-Eye"), keying.keyOrder)
        assertNull(keying.keyAt(1))
        assertEquals(2, keying.unclaimed)
    }

    @Test
    fun onlyTheFourLargestArchetypesAreColoured() {
        val many = (1..6).map { card(100 + it, "Theme $it") }
        val pool = (index + many.associateBy { it.id })
        // Theme 1 four copies, Theme 2 three, … so the ranking is unambiguous.
        val section = (1..6).flatMap { theme -> List(7 - theme) { CardId(100 + theme) } }

        val keying = DeckLenses.key(Lens.ARCHETYPE, section, pool::get, DeckGroups.EMPTY)

        assertEquals(DeckLenses.MAX_ARCHETYPES, keying.keys.size)
        assertEquals(listOf("Theme 1", "Theme 2", "Theme 3", "Theme 4"), keying.keyOrder)
        // Themes 5 and 6 are two cards and one card: a splash, left bare.
        assertEquals(3, keying.unclaimed)
    }

    @Test
    fun tiedArchetypesAlwaysPaintTheSameWayRound() {
        // A reading that reshuffles itself between sessions teaches nothing.
        val pool = listOf(card(21, "Zebra"), card(22, "Anteater")).associateBy { it.id }
        val keying = DeckLenses.key(Lens.ARCHETYPE, deck(21, 22), pool::get, DeckGroups.EMPTY)

        assertEquals(listOf("Anteater", "Zebra"), keying.keyOrder)
    }

    @Test
    fun archetypeHuesAvoidRedAndAmber() {
        val keying = DeckLenses.key(Lens.ARCHETYPE, deck(1, 4, 6), cards, DeckGroups.EMPTY)

        keying.keys.forEach { key ->
            val hue = (key.paint as KeyPaint.Hue).prismIndex
            // 0 and 1 mean illegal and warning everywhere else in the app.
            assertTrue(hue >= 2, "archetype took hue $hue")
        }
    }

    // ---- copies -------------------------------------------------------------

    @Test
    fun copiesCountsWithinTheSectionBeingDrawn() {
        val keying = DeckLenses.key(
            Lens.COPIES,
            deck(1, 1, 1, 2, 2, 3),
            cards,
            DeckGroups.EMPTY,
        )

        assertContentEquals(
            listOf("c3", "c3", "c3", "c2", "c2", "c1"),
            keying.keyOfCell,
        )
        assertEquals(0, keying.unclaimed)
    }

    @Test
    fun onlyTheCopyCountsPresentGetKeys() {
        // A deck of threes should not be told about a Singletons key it has none of.
        val keying = DeckLenses.key(Lens.COPIES, deck(1, 1, 1, 2, 2, 2), cards, DeckGroups.EMPTY)

        assertEquals(listOf("c3"), keying.keyOrder)
        assertEquals("×3", keying.keyById("c3")?.mark)
    }

    @Test
    fun singletonsAreTheBrightest() {
        val keying = DeckLenses.key(Lens.COPIES, deck(1, 2, 2, 3, 3, 3), cards, DeckGroups.EMPTY)

        val one = (keying.keyById("c1")!!.paint as KeyPaint.Grey).luminance
        val two = (keying.keyById("c2")!!.paint as KeyPaint.Grey).luminance
        val three = (keying.keyById("c3")!!.paint as KeyPaint.Grey).luminance

        assertTrue(one > two && two > three, "$one $two $three")
    }

    @Test
    fun aFourthCopyIsTheValidatorsProblem() {
        // Illegal, and the deck says so elsewhere; this lens must not fall over.
        val keying = DeckLenses.key(Lens.COPIES, deck(1, 1, 1, 1), cards, DeckGroups.EMPTY)

        assertContentEquals(List(4) { "c3" }, keying.keyOfCell)
    }

    // ---- every lens, every deck ---------------------------------------------

    @Test
    fun everyLensKeysOneEntryPerCard() {
        val section = deck(1, 2, 3, 4, 5, 6, 7, 8)
        val groups = DeckGroups.EMPTY
            .upsert(DeckGroup("g1", "Engine", color = 2, order = 0))
            .assign(CardId(1), "g1")

        Lens.entries.forEach { lens ->
            val keying = DeckLenses.key(lens, section, cards, groups)
            assertEquals(section.size, keying.keyOfCell.size, "$lens")
            // Every key a cell names is a key the bar can show.
            keying.keyOfCell.filterNotNull().distinct().forEach { id ->
                assertTrue(id in keying.keyOrder, "$lens lost key $id")
            }
        }
    }

    @Test
    fun everyLensSurvivesAnEmptyDeck() {
        Lens.entries.forEach { lens ->
            val keying = DeckLenses.key(lens, emptyList(), cards, DeckGroups.EMPTY)
            assertTrue(keying.keyOfCell.isEmpty(), "$lens")
            assertTrue(keying.isEmpty, "$lens")
        }
    }

    @Test
    fun theLensRingIsAClosedCycle() {
        assertEquals(Lens.ROLES, Lens.DECK.next())
        assertEquals(Lens.DECK, Lens.COPIES.next())
        assertEquals(Lens.COPIES, Lens.DECK.previous())
        Lens.entries.forEach { assertEquals(it, it.next().previous()) }
    }

    @Test
    fun onlyRolesIsSomethingYouCanEdit() {
        assertTrue(Lens.ROLES.isEditable)
        assertTrue(Lens.entries.filter { it.isEditable } == listOf(Lens.ROLES))
    }
}
