package com.kaiharimoto.mastertool.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import com.kaiharimoto.mastertool.core.deck.DeckHistory
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val HOUR = 60L * 60 * 1000

/**
 * A deck over months rather than over three calls.
 *
 * Every other test here checks one step. The rules that actually matter are
 * about the shape of a history after a long time: that it never grows without
 * bound, that a version somebody named is still there after forty more sittings,
 * that the far end does not quietly fall off, and that going back is never the
 * thing that loses the present.
 *
 * None of those can fail in three steps, and all of them are what somebody
 * notices six months in.
 */
class DeckHistoryLifeTest {

    private var now = 1_000L * HOUR

    private val database = MasterToolDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { MasterToolDatabase.Schema.create(it) },
    )

    private val decks = DeckRepository(database, clock = { now })

    /** One sitting: a handful of edits, then a gap before the next one. */
    private suspend fun sitting(day: Int, cards: Int) {
        repeat(3) { edit ->
            decks.save("d", "Snake-Eye", Deck(main = List(cards + edit) { CardId(it + day) }))
            now += 12 * 60 * 1000
        }
        now += 20 * HOUR
    }

    @Test
    fun sixtySittingsLeaveTwelveVersionsSpanningTheWholeTime() = runTest {
        val first = now
        repeat(60) { sitting(it, cards = 40) }

        val kept = decks.versions("d")

        assertEquals(DeckHistory.LIMIT, kept.size, "a history is bounded or it is a log")
        // Not `first` exactly: a copy is only ever of what was there *before* a
        // change, and the deck's opening state is written over inside its own
        // first sitting. The earliest version there can be is how that sitting
        // was left, which is what outlives everything after it.
        assertTrue(
            kept.last().keptAtEpochMs < first + HOUR,
            "the far end fell off: oldest is ${(kept.last().keptAtEpochMs - first) / HOUR}h in",
        )

        // Spread, not clustered: the oldest half of the deck's life keeps some
        // of the versions rather than all of them ending up in the last week.
        val middle = (first + now) / 2
        assertTrue(
            kept.any { it.keptAtEpochMs < middle },
            "every version ended up in the recent half",
        )
    }

    @Test
    fun anEveningsWorkIsOneVersionNoMatterHowManyEditsItTook() = runTest {
        repeat(5) { sitting(it, cards = 40) }

        // Five sittings, and the copy is of what was there *before* each one --
        // so the last sitting has not been closed off by another.
        assertEquals(4, decks.versions("d").size)
    }

    @Test
    fun theListYouRegisteredSurvivesTheRestOfTheSeason() = runTest {
        sitting(0, cards = 40)
        val registered = assertNotNull(decks.keepNow("d", "regionals"))

        repeat(60) { sitting(it + 1, cards = 40) }

        val named = decks.versions("d").filter { it.name != null }
        assertEquals(listOf("regionals"), named.map { it.name })
        assertEquals(registered.deck, named.single().deck, "card for card")
        assertEquals(registered.id, named.single().id)
    }

    @Test
    fun namingMoreThanTheLimitKeepsEveryOneOfThem() = runTest {
        repeat(20) {
            sitting(it, cards = 40)
            decks.keepNow("d", "week $it")
        }

        val named = decks.versions("d").filter { it.name != null }
        assertEquals(20, named.size, "somebody asking for these outranks the limit")
    }

    @Test
    fun goingBackAndForthNeverLosesADeck() = runTest {
        // The property the absence of a confirmation rests on: at every point,
        // whatever was on the table a moment ago is still reachable.
        repeat(8) { sitting(it, cards = 40) }

        repeat(6) { round ->
            val before = assertNotNull(decks.byId("d")).entry.deck
            val target = decks.versions("d").let { it[it.size / 2] }

            decks.restore("d", target.id)
            now += 30 * HOUR

            assertEquals(target.deck, assertNotNull(decks.byId("d")).entry.deck, "round $round")
            assertTrue(
                decks.versions("d").any { it.deck == before },
                "round $round: the deck being left was not kept on the way past",
            )
        }
    }

    @Test
    fun everyVersionStillBelongsToTheDeckItCameFrom() = runTest {
        repeat(30) { sitting(it, cards = 40) }
        decks.save("other", "Fiendsmith", Deck(main = List(40) { CardId(9000 + it) }))
        repeat(10) {
            now += 30 * HOUR
            decks.save("other", "Fiendsmith", Deck(main = List(40 + it) { CardId(9000 + it) }))
        }

        assertTrue(decks.versions("d").all { it.deckId == "d" })
        assertTrue(decks.versions("other").all { it.deckId == "other" })
        assertTrue(decks.versions("other").size <= DeckHistory.LIMIT)
    }

    @Test
    fun aDeletedDeckLeavesNothingBehindInTheTable() = runTest {
        repeat(30) { sitting(it, cards = 40) }
        decks.delete("d")

        assertEquals(emptyList(), decks.versions("d"))
        assertEquals(
            0L,
            database.deckVersionQueries.selectForDeck("d").executeAsList().size.toLong(),
            "and nothing orphaned in the table either",
        )
    }
}
