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
    fun partitionKeepsDeckOrderWithinGroupsAndShowsEmptyGroups() {
        val section = listOf(ash, fiend, ash, maxx, CardId(111))
        val blocks = groups().upsert(DeckGroup("g-empty", "Board breakers", 5, 2))
            .partition(section)

        assertEquals(listOf("g-engine", "g-traps", "g-empty", null), blocks.map { it.group?.id })
        assertEquals(listOf(fiend), blocks[0].ids)
        // Both Ash copies, in deck order, then Maxx.
        assertEquals(listOf(ash, ash, maxx), blocks[1].ids)
        assertTrue(blocks[2].ids.isEmpty())
        assertEquals(listOf(CardId(111)), blocks[3].ids)
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

    // ---- breakdown flattening & drop maths ---------------------------------

    @Test
    fun flattenGivesEveryCopyItsRawIndex() {
        val section = listOf(ash, fiend, ash)
        val entries = DeckBreakdown.flatten(section, groups())

        val cards = entries.filterIsInstance<BreakdownEntry.CardEntry>()
        // Engine block first: fiend at raw index 1. Then both Ash copies, raw 0 and 2.
        assertEquals(listOf(1, 0, 2), cards.map { it.rawIndex })
        assertEquals(
            listOf("g-engine", "g-traps", "g-traps"),
            cards.map { it.groupId },
        )
    }

    @Test
    fun droppingOnAHeaderJoinsTheGroupItOpens() {
        val entries = DeckBreakdown.flatten(listOf(ash, fiend), groups())
        // entries: [Header(engine), fiend, Header(traps), ash, Header(null)]
        assertEquals("g-engine", DeckBreakdown.dropGroup(entries, 0))
        assertEquals("g-traps", DeckBreakdown.dropGroup(entries, 2))
        assertEquals("g-traps", DeckBreakdown.dropGroup(entries, 3))
        // Past the end lands in the final (Ungrouped) block.
        assertNull(DeckBreakdown.dropGroup(entries, entries.size))
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
