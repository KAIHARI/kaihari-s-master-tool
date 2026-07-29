package com.kaiharimoto.mastertool.core.deck

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A shootout run, kept.
 *
 * A run answers *is my side deck doing anything against this deck* — and until
 * now the answer lived in memory until the app closed. Which quietly made the
 * feature about an evening rather than about a deck: forty hands is a big enough
 * sample to say something, and forty hands is also more than anybody sits down
 * and does at once. Three sittings of fifteen is the realistic shape, and three
 * sittings of fifteen used to be three separate things that each said *not
 * enough hands to say* and then vanished.
 *
 * Kept in the deck's own `#ydkx-extended` payload rather than in a table of its
 * own: the results are about this deck, they travel with it, and a new table
 * would need a migration for something that is a handful of integers.
 */
data class TestingRecord(
    /** The deck tested against, as the plan names it. Blank for an untitled run. */
    val matchup: String,
    val preSide: MatchupRecord,
    val postSide: MatchupRecord,
    val atEpochMs: Long,
) {
    val total: Int get() = preSide.total + postSide.total

    /** The same reading a single run gives, over whatever has been kept. */
    fun report(): ShootoutReport = ShootoutReport(preSide, postSide)
}

/**
 * Reads and writes kept runs inside the `#ydkx-extended` payload.
 *
 * Merged rather than replaced, like every other key this program touches there.
 */
object TestingCodec {

    private const val TESTING = "testing"

    fun read(extended: JsonObject?): List<TestingRecord> {
        val kept = extended?.get(TESTING) as? JsonArray ?: return emptyList()

        return kept.mapNotNull { entry ->
            val row = entry as? JsonObject ?: return@mapNotNull null
            val pre = record(row["pre"]) ?: return@mapNotNull null
            val post = record(row["post"]) ?: return@mapNotNull null
            // A run with nothing in it is not a run. Written by nothing here,
            // and dropped rather than trusted when a file carries one.
            if (pre.total + post.total == 0) return@mapNotNull null

            TestingRecord(
                matchup = (row["vs"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                preSide = pre,
                postSide = post,
                atEpochMs = (row["at"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
    }

    fun write(extended: JsonObject?, runs: List<TestingRecord>): JsonObject {
        val rest = (extended ?: JsonObject(emptyMap())).filterKeys { it != TESTING }
        if (runs.isEmpty()) return JsonObject(rest)

        val written = JsonArray(
            // Oldest first, so a file read by eye reads forwards in time and two
            // saves of the same runs produce the same bytes.
            runs.sortedBy { it.atEpochMs }.map { run ->
                JsonObject(
                    buildMap {
                        if (run.matchup.isNotBlank()) put("vs", JsonPrimitive(run.matchup))
                        put("at", JsonPrimitive(run.atEpochMs))
                        put("pre", record(run.preSide))
                        put("post", record(run.postSide))
                    },
                )
            },
        )
        return JsonObject(rest + (TESTING to written))
    }

    /** `[playable, bricks]` per side of the die roll: four integers, no field names. */
    private fun record(value: JsonElement?): MatchupRecord? {
        val row = value as? JsonObject ?: return null
        return MatchupRecord(
            goingFirst = tally(row["first"]) ?: return null,
            goingSecond = tally(row["second"]) ?: return null,
        )
    }

    private fun record(record: MatchupRecord): JsonObject = JsonObject(
        mapOf("first" to tally(record.goingFirst), "second" to tally(record.goingSecond)),
    )

    private fun tally(value: JsonElement?): HandTally? {
        val pair = value as? JsonArray ?: return null
        if (pair.size != 2) return null
        val playable = (pair[0] as? JsonPrimitive)?.intOrNull ?: return null
        val bricks = (pair[1] as? JsonPrimitive)?.intOrNull ?: return null
        if (playable < 0 || bricks < 0) return null
        return HandTally(playable, bricks)
    }

    private fun tally(tally: HandTally): JsonArray =
        JsonArray(listOf(JsonPrimitive(tally.playable), JsonPrimitive(tally.bricks)))
}

/**
 * Everything kept against one opponent, added up.
 *
 * The point of keeping runs at all: three sittings of fifteen hands is one
 * answer, not three shrugs.
 */
object Testing {

    fun againstEach(runs: List<TestingRecord>): Map<String, ShootoutReport> =
        runs.groupBy { it.matchup }
            .mapValues { (_, against) ->
                ShootoutReport(
                    preSide = against.fold(MatchupRecord()) { all, run -> all + run.preSide },
                    postSide = against.fold(MatchupRecord()) { all, run -> all + run.postSide },
                )
            }

    /** Everything ever tested with this deck, whoever it was against. */
    fun everything(runs: List<TestingRecord>): ShootoutReport = ShootoutReport(
        preSide = runs.fold(MatchupRecord()) { all, run -> all + run.preSide },
        postSide = runs.fold(MatchupRecord()) { all, run -> all + run.postSide },
    )
}
