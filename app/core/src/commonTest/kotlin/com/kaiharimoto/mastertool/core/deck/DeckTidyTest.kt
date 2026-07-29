package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals

class DeckTidyTest {

    private val ash = monster(1, "Ash Blossom & Joyous Spring", level = 3, archetype = null)
    private val maxx = monster(2, "Maxx \"C\"", level = 2, archetype = null)
    private val kashtira = monster(3, "Kashtira Fenrir", level = 7, archetype = "Kashtira")
    private val riseheart = monster(4, "Kashtira Riseheart", level = 4, archetype = "Kashtira")
    private val pot = spell(5, "Pot of Prosperity")
    private val imperm = trap(6, "Infinite Impermanence")

    private val pool = listOf(ash, maxx, kashtira, riseheart, pot, imperm).associateBy { it.id }
    private val lookup: (CardId) -> Card? = { pool[it] }

    private fun tidy(ids: List<CardId>, mode: TidyBy) = DeckTidy.apply(ids, mode, lookup)

    private fun names(ids: List<CardId>) = ids.map { pool.getValue(it).name }

    @Test
    fun cardTypesLandInDecklistOrder() {
        val start = listOf(imperm.id, ash.id, pot.id, maxx.id)

        assertEquals(
            listOf(ash.id, maxx.id, pot.id, imperm.id),
            tidy(start, TidyBy.CATEGORY),
        )
    }

    @Test
    fun theMonstersKeepTheOrderTheyWereInsideTheirGroup() {
        // The whole point. Grouping by type must not also alphabetise, re-level
        // or otherwise re-sort the monsters -- somebody arranged those.
        val start = listOf(kashtira.id, imperm.id, ash.id, maxx.id, riseheart.id)

        assertEquals(
            listOf("Kashtira Fenrir", "Ash Blossom & Joyous Spring", "Maxx \"C\"", "Kashtira Riseheart"),
            names(tidy(start, TidyBy.CATEGORY)).take(4),
        )
    }

    @Test
    fun archetypesStayWhereTheyWerePut() {
        // An engine deliberately placed at the front stays at the front:
        // alphabetising the archetypes would undo that decision silently.
        val start = listOf(kashtira.id, ash.id, riseheart.id, maxx.id)

        assertEquals(listOf(kashtira.id, riseheart.id, ash.id, maxx.id), tidy(start, TidyBy.ARCHETYPE))
    }

    @Test
    fun cardsWithNoArchetypeGoLastInTheirOwnOrder() {
        val start = listOf(maxx.id, kashtira.id, ash.id)

        assertEquals(listOf(kashtira.id, maxx.id, ash.id), tidy(start, TidyBy.ARCHETYPE))
    }

    @Test
    fun gatheringCopiesLeavesEverythingElseAlone() {
        val start = listOf(ash.id, pot.id, ash.id, imperm.id, maxx.id)

        assertEquals(
            listOf(ash.id, ash.id, pot.id, imperm.id, maxx.id),
            tidy(start, TidyBy.COPIES),
        )
    }

    @Test
    fun aCardThePoolHasNeverHeardOfIsNotAnError() {
        // Decks are imported before the pool finishes downloading, and a tidy
        // that dropped the unknown cards would be a data loss dressed as a
        // convenience.
        val stranger = CardId(999)
        val start = listOf(stranger, pot.id, ash.id)

        TidyBy.entries.forEach { mode ->
            assertEquals(
                start.sortedBy { it.value },
                tidy(start, mode).sortedBy { it.value },
                "$mode lost a card",
            )
        }
        assertEquals(stranger, tidy(start, TidyBy.CATEGORY).last())
    }

    @Test
    fun everyTidyIsIdempotent() {
        val start = listOf(imperm.id, kashtira.id, ash.id, pot.id, ash.id, riseheart.id, maxx.id)

        TidyBy.entries.forEach { mode ->
            val once = tidy(start, mode)
            assertEquals(once, tidy(once, mode), "$mode moved cards on a second press")
        }
    }

    @Test
    fun everyTidyIsAPermutation() {
        val start = listOf(imperm.id, kashtira.id, ash.id, pot.id, ash.id, riseheart.id, maxx.id)

        TidyBy.entries.forEach { mode ->
            assertEquals(
                start.sortedBy { it.value },
                tidy(start, mode).sortedBy { it.value },
                "$mode changed the deck",
            )
        }
    }

    @Test
    fun anEmptySectionTidiesToNothing() {
        TidyBy.entries.forEach { mode ->
            assertEquals(emptyList(), tidy(emptyList(), mode))
        }
    }

    private fun monster(id: Int, name: String, level: Int, archetype: String?) = Card(
        id = CardId(id),
        name = name,
        type = "Effect Monster",
        frameType = "effect",
        level = level,
        archetype = archetype,
    )

    private fun spell(id: Int, name: String) =
        Card(id = CardId(id), name = name, type = "Spell Card", frameType = "spell")

    private fun trap(id: Int, name: String) =
        Card(id = CardId(id), name = name, type = "Trap Card", frameType = "trap")
    // ---- the gaps a tidy leaves --------------------------------------------

    @Test
    fun groupingByTypeLeavesAGapBetweenTheGroups() {
        // The space between the groups is what says there are groups. A tidy
        // that brings the monsters together and butts them against the spells
        // has done half the job.
        val start = listOf(imperm.id, ash.id, pot.id, maxx.id)

        val arranged = DeckTidy.arrange(start, TidyBy.CATEGORY, lookup)

        assertEquals(listOf(ash.id, maxx.id, pot.id, imperm.id), arranged.ids)
        assertEquals(setOf(2, 3), arranged.breaks.before)
        assertEquals(
            listOf(listOf("Ash Blossom & Joyous Spring", "Maxx \"C\""), listOf("Pot of Prosperity"), listOf("Infinite Impermanence")),
            arranged.breaks.groups(arranged.ids.size).map { range -> names(arranged.ids.slice(range)) },
        )
    }

    @Test
    fun groupingByArchetypeSeparatesTheEngineFromEverythingElse() {
        val start = listOf(ash.id, kashtira.id, maxx.id, riseheart.id)

        val arranged = DeckTidy.arrange(start, TidyBy.ARCHETYPE, lookup)

        // The Kashtira cards have an archetype and come first in first-appearance
        // order; the two that have none are not a group and go last.
        assertEquals(listOf(kashtira.id, riseheart.id, ash.id, maxx.id), arranged.ids)
        assertEquals(setOf(2), arranged.breaks.before)
    }

    @Test
    fun gatheringCopiesDrawsNoGapsAtAll() {
        // Forty cards would become twenty gaps, which says nothing and looks
        // like a fault.
        val start = listOf(ash.id, maxx.id, ash.id, pot.id, maxx.id)

        val arranged = DeckTidy.arrange(start, TidyBy.COPIES, lookup)

        assertEquals(listOf(ash.id, ash.id, maxx.id, maxx.id, pot.id), arranged.ids)
        assertEquals(Breaks.NONE, arranged.breaks)
    }

    @Test
    fun aTidyOfOneCardHasNothingToSeparate() {
        val arranged = DeckTidy.arrange(listOf(ash.id), TidyBy.CATEGORY, lookup)

        assertEquals(Breaks.NONE, arranged.breaks)
    }

    @Test
    fun theOldTidyStillAgreesWithTheNewOne() {
        // Two ways of asking the same question is how the two come to disagree,
        // so one is written in terms of the other and this says so.
        val start = listOf(imperm.id, kashtira.id, pot.id, ash.id, riseheart.id, maxx.id)

        TidyBy.entries.forEach { mode ->
            assertEquals(
                DeckTidy.apply(start, mode, lookup),
                DeckTidy.arrange(start, mode, lookup).ids,
                mode.name,
            )
        }
    }

}

/**
 * The tidy the Extra deck actually wants.
 *
 * Grouping fifteen Extra deck cards by "card type" tells you they are all
 * monsters. Grouping them by how they are summoned is how the pane is read.
 */
class SummonTidyTest {

    private fun card(id: Int, name: String, frameType: String) =
        Card(id = CardId(id), name = name, type = "Monster", frameType = frameType)

    private val link = card(1, "Accesscode Talker", "link")
    private val xyz = card(2, "Number 41", "xyz")
    private val fusion = card(3, "Mudragon", "fusion")
    private val synchro = card(4, "Baronne", "synchro")
    private val fusionPendulum = card(5, "Beyond the Pendulum", "fusion_pendulum")
    private val ritual = card(6, "Nekroz of Areadbhair", "ritual")
    private val effect = card(7, "Ash Blossom", "effect")

    private val pool = listOf(link, xyz, fusion, synchro, fusionPendulum, ritual, effect)
        .associateBy { it.id }

    private fun tidy(vararg cards: Card) =
        DeckTidy.apply(cards.map { it.id }, TidyBy.SUMMONING) { pool[it] }

    @Test
    fun anExtraDeckComesOutInReadingOrder() {
        assertEquals(
            listOf(fusion.id, synchro.id, xyz.id, link.id),
            tidy(link, xyz, fusion, synchro),
        )
    }

    @Test
    fun aCompoundFrameTakesTheFirstThingItIs() {
        // "fusion_pendulum" is a Fusion monster that also happens to be a
        // Pendulum, and it belongs with the Fusions.
        assertEquals(listOf(fusion.id, fusionPendulum.id, link.id), tidy(link, fusion, fusionPendulum))
    }

    @Test
    fun theMainDeckGetsSomethingUsefulToo() {
        // Not only an Extra deck tidy: Rituals ahead of Effects is a real
        // ordering for a Main deck as well.
        assertEquals(listOf(ritual.id, effect.id), tidy(effect, ritual))
    }

    @Test
    fun aFrameNothingRecognisesGoesLast() {
        val stranger = card(99, "Odd One", "skill")
        val withStranger = pool + (stranger.id to stranger)

        assertEquals(
            listOf(fusion.id, stranger.id),
            DeckTidy.apply(listOf(stranger.id, fusion.id), TidyBy.SUMMONING) { withStranger[it] },
        )
    }

    @Test
    fun itIsStillAStablePartition() {
        val first = card(11, "Link A", "link")
        val second = card(12, "Link B", "link")
        val withLinks = pool + mapOf(first.id to first, second.id to second)
        val start = listOf(second.id, fusion.id, first.id)

        val once = DeckTidy.apply(start, TidyBy.SUMMONING) { withLinks[it] }

        assertEquals(listOf(fusion.id, second.id, first.id), once, "order inside a group survives")
        assertEquals(once, DeckTidy.apply(once, TidyBy.SUMMONING) { withLinks[it] })
    }
}
