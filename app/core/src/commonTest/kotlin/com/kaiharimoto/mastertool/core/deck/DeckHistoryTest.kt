package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val HOUR = 60L * 60 * 1000

private fun mark(at: Long, name: String? = null, id: String = "v$at") = VersionMark(id, at, name)

private fun kept(versions: List<VersionMark>, limit: Int): List<Long> {
    val gone = DeckHistory.surplus(versions, limit).map { it.id }.toSet()
    return versions.filter { it.id !in gone }.map { it.atEpochMs }.sorted()
}

class KeepsACopyTest {

    @Test
    fun aDeckBeingSavedForTheFirstTimeKeepsNothing() {
        // There is nothing to keep. The copy is always of what was already there.
        assertFalse(DeckHistory.keepsACopy(lastEditedAtEpochMs = null, nowEpochMs = 9 * HOUR))
    }

    @Test
    fun anEveningOfBuildingIsOneVersionAndNotOneVersionPerEdit() {
        // Autosave writes constantly. A timeline of every edit is not a record.
        val start = 100 * HOUR

        assertFalse(DeckHistory.keepsACopy(start, start))
        assertFalse(DeckHistory.keepsACopy(start, start + 1))
        assertFalse(DeckHistory.keepsACopy(start, start + 5 * HOUR))
    }

    @Test
    fun comingBackToADeckKeepsWhatYouLeftIt() {
        val lastNight = 100 * HOUR

        assertTrue(DeckHistory.keepsACopy(lastNight, lastNight + 6 * HOUR), "exactly the spacing")
        assertTrue(DeckHistory.keepsACopy(lastNight, lastNight + 14 * HOUR))
        assertTrue(DeckHistory.keepsACopy(lastNight, lastNight + 400 * HOUR))
    }

    @Test
    fun aClockThatWentBackwardsDoesNotKeepACopy() {
        // Timezone changes and clock corrections both do this, and neither one
        // means the deck stood still for anything.
        assertFalse(DeckHistory.keepsACopy(100 * HOUR, 90 * HOUR))
    }
}

class SurplusTest {

    @Test
    fun aDeckUnderTheLimitLetsGoOfNothing() {
        val versions = (0L until 12L).map { mark(it * HOUR) }

        assertEquals(emptyList(), DeckHistory.surplus(versions, limit = 12))
    }

    @Test
    fun bothEndsSurvive() {
        // The oldest is the deck as it first stood, which is usually the version
        // worth the most -- dropping the far end turns a history into "the last
        // few days" no matter how long the deck has been around.
        val versions = (0L..10L).map { mark(it) }

        val out = kept(versions, limit = 5)

        assertEquals(0L, out.first())
        assertEquals(10L, out.last())
    }

    @Test
    fun whatIsLeftIsSpreadAcrossTheDecksLife() {
        val versions = (0L..10L).map { mark(it) }

        assertEquals(listOf(0L, 4L, 6L, 8L, 10L), kept(versions, limit = 5))
    }

    @Test
    fun aBurstOfWorkInOneSittingCollapsesAndTheSpreadOutOnesStay() {
        // The case the rule exists for: five versions from one evening say one
        // thing between them, and the three from months apart say three.
        val versions = listOf(0L, 1L, 2L, 3L, 4L, 100L, 200L, 300L).map(::mark)

        assertEquals(listOf(0L, 100L, 200L, 300L), kept(versions, limit = 4))
    }

    @Test
    fun aNamedVersionIsNeverDropped() {
        val versions = listOf(mark(0), mark(1), mark(2, "regionals"), mark(3), mark(4))

        assertEquals(listOf(0L, 2L, 4L), kept(versions, limit = 3))
    }

    @Test
    fun namingMoreVersionsThanTheLimitKeepsThemAll() {
        // Somebody saying they want these outranks a number this file picked.
        val versions = listOf(mark(0), mark(1, "a"), mark(2, "b"), mark(3, "c"), mark(4))

        assertEquals(emptyList(), DeckHistory.surplus(versions, limit = 3))
    }

    @Test
    fun theLimitCannotThinBelowTwoBecauseNeitherEndIsACandidate() {
        val versions = (0L..4L).map { mark(it) }

        assertEquals(listOf(0L, 4L), kept(versions, limit = 0))
    }

    @Test
    fun versionsArrivingOutOfOrderAreThinnedTheSame() {
        // Rows come back from a database in whatever order the query gave them.
        val ordered = (0L..10L).map { mark(it) }

        assertEquals(kept(ordered, 5), kept(ordered.reversed(), 5))
    }

    @Test
    fun thinningIsStable() {
        val versions = (0L..10L).map { mark(it) }

        assertEquals(
            DeckHistory.surplus(versions, 5).map { it.id }.toSet(),
            DeckHistory.surplus(versions, 5).map { it.id }.toSet(),
        )
    }

    @Test
    fun thinningLeavesExactlyTheLimit() {
        val versions = (0L until 40L).map { mark(it * HOUR) }

        assertEquals(DeckHistory.LIMIT, kept(versions, DeckHistory.LIMIT).size)
        assertEquals(40 - DeckHistory.LIMIT, DeckHistory.surplus(versions, DeckHistory.LIMIT).size)
    }

    @Test
    fun aVersionKeptAtTheSameMomentAsAnotherIsStillOnlyOneOfThem() {
        // Two rows with the same stamp make a zero-width span, which is the
        // smallest there is -- so one of them goes first, which is right.
        val versions = listOf(mark(0), mark(50, id = "a"), mark(50, id = "b"), mark(100))

        assertEquals(listOf(0L, 50L, 100L), kept(versions, limit = 3))
    }
}
