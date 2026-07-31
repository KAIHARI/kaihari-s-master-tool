package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class DeckGroupsTest {

    private val engine = DeckGroup("g-engine", "Engine", color = 0, order = 0)
    private val traps = DeckGroup("g-traps", "Handtraps", color = 3, order = 1)

    private val ash = CardId(14558127)
    private val maxx = CardId(75500286)
    private val fiend = CardId(60764609)

    private fun groups() = DeckGroups.EMPTY
        .upsert(engine)
        .upsert(traps)
        .assign(fiend, "g-engine")
        .assign(ash, "g-traps")
        .assign(maxx, "g-traps")

    @Test
    fun assignmentIsPerPasscodeAndFullyManual() {
        val g = groups()
        assertEquals("g-traps", g.assignments[ash])
        // Assigning to a group that does not exist is a no-op, not a crash.
        assertEquals(g, g.assign(ash, "g-missing"))
        // Clearing returns the card to Ungrouped.
        assertNull(g.assign(ash, null).assignments[ash])
    }

    @Test
    fun removingAGroupFreesItsCards() {
        val g = groups().remove("g-traps")
        assertNull(g.byId("g-traps"))
        assertNull(g.assignments[ash])
        assertEquals("g-engine", g.assignments[fiend])
    }

    @Test
    fun reorderRenumbersDensely() {
        val g = groups().reorder("g-traps", 0)
        assertEquals(listOf("g-traps", "g-engine"), g.ordered().map { it.id })
        assertEquals(listOf(0, 1), g.ordered().map { it.order })
    }

    @Test
    fun pruningDropsDeadAssignments() {
        val g = groups()
            .copy(assignments = groups().assignments + (CardId(999) to "g-gone"))
            .pruned(deckIds = listOf(ash, fiend))

        assertNull(g.assignments[CardId(999)])
        assertNull(g.assignments[maxx]) // not in the deck any more
        assertEquals("g-traps", g.assignments[ash])
    }

    // ---- the breakdown lens ------------------------------------------------

    @Test
    fun theLensLeavesTheDeckInTheOrderItIsStoredIn() {
        val section = listOf(ash, fiend, ash, maxx)
        val slots = DeckBreakdown.slots(section, groups(), columns = 10)

        // One slot per card, in deck order, each pointing back at its position.
        assertEquals(listOf(0, 1, 2, 3), slots.map { it.index })
        assertEquals(
            listOf("g-traps", "g-engine", "g-traps", "g-traps"),
            slots.map { it.groupId },
        )
    }

    @Test
    fun eachUnbrokenRunOfAGroupIsOnePiece() {
        // traps | engine | traps traps
        val slots = DeckBreakdown.slots(listOf(ash, fiend, ash, maxx), groups(), columns = 10)

        assertEquals(listOf(1, 1, 2, 2), slots.map { it.runLength })
        assertEquals(listOf(0, 0, 0, 1), slots.map { it.positionInRun })
        assertEquals(listOf(true, true, true, false), slots.map { it.startsRun })
    }

    @Test
    fun aPieceNeverWrapsToTheNextRow() {
        // Four handtraps across a three-wide grid: two pieces, not one bent one.
        val slots = DeckBreakdown.slots(listOf(ash, ash, maxx, ash), groups(), columns = 3)

        assertEquals(listOf(3, 3, 3, 1), slots.map { it.runLength })
        assertEquals(listOf(0, 1, 2, 0), slots.map { it.positionInRun })
    }

    @Test
    fun ungroupedCardsStillGetASlotSoIndicesLineUp() {
        val section = listOf(ash, CardId(111), CardId(222), fiend)
        val slots = DeckBreakdown.slots(section, groups(), columns = 10)

        assertEquals(section.indices.toList(), slots.map { it.index })
        assertNull(slots[1].groupId)
        // The two ungrouped cards are one run of their own.
        assertEquals(2, slots[1].runLength)
    }

    @Test
    fun assignmentsToDeletedGroupsReadAsUngrouped() {
        val orphaned = groups().copy(groups = groups().groups.filterNot { it.id == "g-traps" })
        val slots = DeckBreakdown.slots(listOf(ash, fiend), orphaned, columns = 10)

        assertNull(slots[0].groupId)
        assertEquals("g-engine", slots[1].groupId)
    }

    // ---- codec -------------------------------------------------------------

    @Test
    fun codecRoundTripsThroughTheExtendedPayload() {
        val stored = StoredGroups(groups(), breakdown = true)
        val written = DeckGroupsCodec.write(extended = null, stored = stored)
        val back = DeckGroupsCodec.read(written)

        assertEquals(stored.breakdown, back.breakdown)
        assertEquals(stored.groups.assignments, back.groups.assignments)
        assertEquals(
            stored.groups.ordered().map { it.id to it.name },
            back.groups.ordered().map { it.id to it.name },
        )
    }

    @Test
    fun codecPreservesEveryOtherKeyVerbatim() {
        // A payload with the legacy tool's keys in it — they must survive
        // untouched, byte for byte.
        val legacy = Json.parseToJsonElement(
            """{"version":"1.0","sidingPatterns":{"vs Snake-Eye":{"deckName":"Snake-Eye"}},
                "notes":{"cards":{},"pairs":{}}}"""
        ) as JsonObject

        val written = DeckGroupsCodec.write(legacy, StoredGroups(groups(), breakdown = false))!!
        assertEquals(legacy["sidingPatterns"], written["sidingPatterns"])
        assertEquals(legacy["notes"], written["notes"])
        assertEquals(legacy["version"], written["version"])

        // And removing the groups again restores the original exactly.
        assertEquals(legacy, DeckGroupsCodec.write(written, StoredGroups.EMPTY))
    }

    @Test
    fun emptyGroupsOnAPlainDeckWriteNothing() {
        assertNull(DeckGroupsCodec.write(extended = null, stored = StoredGroups.EMPTY))
    }

    @Test
    fun codecShrugsOffMalformedShapes() {
        val garbage = buildJsonObject { put("groups", JsonPrimitive("not an object")) }
        assertEquals(StoredGroups.EMPTY, DeckGroupsCodec.read(garbage))
        assertEquals(StoredGroups.EMPTY, DeckGroupsCodec.read(null))
    }
}
