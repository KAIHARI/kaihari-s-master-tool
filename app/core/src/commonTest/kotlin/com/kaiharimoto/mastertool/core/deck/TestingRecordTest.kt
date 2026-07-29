package com.kaiharimoto.mastertool.core.deck

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestingRecordTest {

    private fun run(
        vs: String,
        at: Long,
        preFirst: HandTally = HandTally(4, 1),
        preSecond: HandTally = HandTally(3, 2),
        postFirst: HandTally = HandTally(5, 0),
        postSecond: HandTally = HandTally(4, 1),
    ) = TestingRecord(
        matchup = vs,
        preSide = MatchupRecord(preFirst, preSecond),
        postSide = MatchupRecord(postFirst, postSecond),
        atEpochMs = at,
    )

    @Test
    fun aRunSurvivesBeingWrittenAndReadBack() {
        val runs = listOf(run("Ryzeal", 1_000L), run("Snake-Eye", 2_000L))

        val written = TestingCodec.write(null, runs)

        assertEquals(runs, TestingCodec.read(written))
    }

    @Test
    fun theRestOfThePayloadIsLeftAlone() {
        // The desktop tool writes keys this program has never heard of, and they
        // have to survive a round trip through it.
        val theirs = Json.parseToJsonElement(
            """{"sidingPatterns":{"a":1},"somethingNew":[1,2,3]}""",
        ) as JsonObject

        val written = TestingCodec.write(theirs, listOf(run("Ryzeal", 1L)))

        assertEquals(theirs["somethingNew"], written["somethingNew"])
        assertEquals(theirs["sidingPatterns"], written["sidingPatterns"])
    }

    @Test
    fun keepingNothingTakesTheKeyWithIt() {
        val written = TestingCodec.write(null, listOf(run("Ryzeal", 1L)))

        val cleared = TestingCodec.write(written, emptyList())

        assertTrue("testing" !in cleared, "a deck nobody tested carries nothing")
        assertTrue(TestingCodec.read(cleared).isEmpty())
    }

    @Test
    fun theSameRunsWriteTheSameBytes() {
        // Oldest first, so a file read by eye reads forwards in time and two
        // saves of the same thing do not look like a change.
        val runs = listOf(run("B", 2L), run("A", 1L))

        assertEquals(
            TestingCodec.write(null, runs).toString(),
            TestingCodec.write(null, runs.reversed()).toString(),
        )
    }

    @Test
    fun aRunWithNoHandsInItIsNotARun() {
        val empty = """{"testing":[{"vs":"Ryzeal","at":1,"pre":{"first":[0,0],"second":[0,0]},""" +
            """"post":{"first":[0,0],"second":[0,0]}}]}"""

        assertTrue(TestingCodec.read(Json.parseToJsonElement(empty) as JsonObject).isEmpty())
    }

    @Test
    fun rubbishInThePayloadIsDroppedRatherThanTrusted() {
        val bad = """{"testing":[{"vs":"X","at":1,"pre":{"first":[1]},"post":{}},{"nope":true},7]}"""

        assertTrue(TestingCodec.read(Json.parseToJsonElement(bad) as JsonObject).isEmpty())
    }

    @Test
    fun aFileWithNoTestingInItReadsAsNone() {
        assertTrue(TestingCodec.read(null).isEmpty())
        assertTrue(TestingCodec.read(JsonObject(emptyMap())).isEmpty())
    }

    @Test
    fun anUntitledRunKeepsItsNumbersAndLosesNothingElse() {
        val runs = listOf(run("", 5L))

        val back = TestingCodec.read(TestingCodec.write(null, runs))

        assertEquals(runs, back)
    }

    // ---- reading them together ---------------------------------------------

    @Test
    fun threeSittingsAgainstOneDeckAreOneAnswer() {
        // The whole reason for keeping them. Fifteen hands three times is a
        // sample; three separate fifteens is three shrugs.
        val runs = List(3) { run("Ryzeal", it + 1L) }

        val report = Testing.againstEach(runs).getValue("Ryzeal")

        assertEquals(30, report.preSide.total)
        assertEquals(30, report.postSide.total)
        assertEquals(60, report.total)
    }

    @Test
    fun differentOpponentsAreNotAddedTogether() {
        val runs = listOf(run("Ryzeal", 1L), run("Snake-Eye", 2L))

        val each = Testing.againstEach(runs)

        assertEquals(setOf("Ryzeal", "Snake-Eye"), each.keys)
        // Twenty, not ten: a run is ten hands off the registered list and ten
        // off the sided one, and the report holds both halves.
        assertEquals(20, each.getValue("Ryzeal").total)
    }

    @Test
    fun andEverythingEverTestedAddsUpAcrossAllOfThem() {
        val runs = listOf(run("Ryzeal", 1L), run("Snake-Eye", 2L), run("Ryzeal", 3L))

        assertEquals(60, Testing.everything(runs).total)
    }

    @Test
    fun anEmptyShelfOfRunsSaysNothing() {
        assertTrue(Testing.againstEach(emptyList()).isEmpty())
        assertEquals(0, Testing.everything(emptyList()).total)
    }
}
