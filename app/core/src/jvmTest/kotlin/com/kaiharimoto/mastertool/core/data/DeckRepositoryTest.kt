package com.kaiharimoto.mastertool.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import com.kaiharimoto.mastertool.core.deck.DeckHistory
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.ydk.YdkCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HOUR = 60L * 60 * 1000

private fun deck(vararg main: Int) = Deck(main = main.map(::CardId))

/**
 * The repository against a real database, which nothing could do until the JDBC
 * driver arrived in this source set for [MigrationTest].
 *
 * Mostly about history: everything else here has been exercised by the app for a
 * while, but keeping a copy of a deck before writing over it is new and is the
 * one thing that cannot be checked by looking at the screen — you would have to
 * come back the next day to find out it had not happened.
 */
class DeckRepositoryTest {

    private var now = 1_000L * HOUR

    private val database = MasterToolDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { MasterToolDatabase.Schema.create(it) },
    )

    private val decks = DeckRepository(database, clock = { now })

    @Test
    fun aDeckSavedForTheFirstTimeHasNoHistory() = runTest {
        decks.save("d", "Snake-Eye", deck(1, 2, 3))

        assertEquals(emptyList(), decks.versions("d"))
    }

    @Test
    fun anEveningOfBuildingIsOneDeckAndNoVersions() = runTest {
        decks.save("d", "Snake-Eye", deck(1))
        now += 20 * 1000
        decks.save("d", "Snake-Eye", deck(1, 2))
        now += 3 * HOUR
        decks.save("d", "Snake-Eye", deck(1, 2, 3))

        assertEquals(emptyList(), decks.versions("d"), "autosave is not a timeline")
    }

    @Test
    fun comingBackTheNextDayKeepsWhatYouLeftIt() = runTest {
        decks.save("d", "Snake-Eye", deck(1, 2, 3), notes = "before the event")
        val lastNight = now

        now += 18 * HOUR
        decks.save("d", "Snake-Eye", deck(1, 2))

        val kept = decks.versions("d").single()
        assertEquals(deck(1, 2, 3), kept.deck, "the version holds what was there, not what replaced it")
        assertEquals("before the event", kept.notes)
        assertEquals(lastNight, kept.keptAtEpochMs, "stamped with when it stood that way")
        assertNull(kept.name, "kept by itself, not named")
    }

    @Test
    fun theCurrentDeckIsStillTheCurrentDeck() = runTest {
        decks.save("d", "Snake-Eye", deck(1, 2, 3))
        now += 18 * HOUR
        decks.save("d", "Snake-Eye", deck(9))

        assertEquals(deck(9), assertNotNull(decks.byId("d")).entry.deck)
    }

    @Test
    fun aSaveThatChangesNothingIsNotAnEdit() = runTest {
        decks.save("d", "Snake-Eye", deck(1, 2, 3))
        val saved = now

        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1, 2, 3))

        assertEquals(emptyList(), decks.versions("d"), "there was nothing to keep a copy of")
        assertEquals(
            saved,
            assertNotNull(decks.byId("d")).entry.updatedAtEpochMs,
            "and the deck was not touched, so it does not claim to have been",
        )
    }

    @Test
    fun aNoOpSaveDoesNotSwallowTheNextRealOne() = runTest {
        // The reason the stamp is left alone: moving it would restart the clock
        // that decides whether a copy is kept, so the edit right after a no-op
        // save would go unrecorded.
        decks.save("d", "Snake-Eye", deck(1, 2, 3))

        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1, 2, 3))
        now += 1000
        decks.save("d", "Snake-Eye", deck(1, 2))

        assertEquals(deck(1, 2, 3), decks.versions("d").single().deck)
    }

    @Test
    fun renamingTheDeckAloneIsNotWorthAVersion() = runTest {
        decks.save("d", "Snake-Eye", deck(1, 2, 3))
        now += 40 * HOUR
        decks.save("d", "Fiendsmith", deck(1, 2, 3))

        assertEquals(emptyList(), decks.versions("d"), "the cards are what a version is of")
    }

    @Test
    fun thePayloadRidesAlongInAVersion() = runTest {
        val extended = Json.parseToJsonElement("""{"look":{"mat":"wine"}}""") as JsonObject
        decks.save("d", "Snake-Eye", deck(1), extended = extended)

        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(2))

        // Gaps, notes and the mat all live in there. A version without them is a
        // version of the cards and not of the deck.
        assertEquals(extended, decks.versions("d").single().extended)
    }

    @Test
    fun theNewestVersionComesFirst() = runTest {
        repeat(3) {
            decks.save("d", "Snake-Eye", deck(it))
            now += 40 * HOUR
        }

        val kept = decks.versions("d")
        assertEquals(2, kept.size)
        assertTrue(kept[0].keptAtEpochMs > kept[1].keptAtEpochMs)
    }

    @Test
    fun aDeckStopsAtTheLimitAndKeepsBothEnds() = runTest {
        val first = now
        repeat(40) {
            decks.save("d", "Snake-Eye", deck(it))
            now += 40 * HOUR
        }

        val kept = decks.versions("d")
        assertEquals(DeckHistory.LIMIT, kept.size)
        assertEquals(first, kept.last().keptAtEpochMs, "the deck as it first stood is the one to keep")
    }

    @Test
    fun aNamedVersionSurvivesTheThinning() = runTest {
        decks.save("d", "Snake-Eye", deck(0))
        val registered = now
        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1))

        decks.nameVersion("d@$registered", "regionals")

        repeat(40) {
            now += 40 * HOUR
            decks.save("d", "Snake-Eye", deck(it + 2))
        }

        val named = decks.versions("d").filter { it.name != null }
        assertEquals(listOf("regionals"), named.map { it.name })
        assertEquals(deck(0), named.single().deck)
    }

    @Test
    fun aNameCanBeTakenBackOff() = runTest {
        decks.save("d", "Snake-Eye", deck(0))
        val stamp = now
        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1))

        decks.nameVersion("d@$stamp", "regionals")
        assertEquals("regionals", assertNotNull(decks.version("d@$stamp")).name)

        decks.nameVersion("d@$stamp", "  ")
        assertNull(assertNotNull(decks.version("d@$stamp")).name, "a blank name is no name")
    }

    @Test
    fun deletingADeckTakesItsHistoryWithIt() = runTest {
        decks.save("d", "Snake-Eye", deck(0))
        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1))
        decks.save("other", "Fiendsmith", deck(5))
        now += 40 * HOUR
        decks.save("other", "Fiendsmith", deck(6))

        decks.delete("d")

        assertEquals(emptyList(), decks.versions("d"))
        assertEquals(1, decks.versions("other").size, "and leaves everybody else's alone")
    }

    @Test
    fun aVersionCanBeThrownAwayOnItsOwn() = runTest {
        decks.save("d", "Snake-Eye", deck(0))
        val stamp = now
        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1))

        decks.deleteVersion("d@$stamp")

        assertEquals(emptyList(), decks.versions("d"))
        assertNotNull(decks.byId("d"), "and the deck is untouched")
    }

    @Test
    fun twoDecksKeepTheirOwnHistories() = runTest {
        decks.save("a", "Snake-Eye", deck(1))
        decks.save("b", "Fiendsmith", deck(2))
        now += 40 * HOUR
        decks.save("a", "Snake-Eye", deck(3))

        assertEquals(1, decks.versions("a").size)
        assertEquals(emptyList(), decks.versions("b"))
    }

    @Test
    fun aVersionGoesBackOutAsAFileTheWayADeckDoes() = runTest {
        decks.save("d", "Snake-Eye", deck(1, 2, 3))
        now += 40 * HOUR
        decks.save("d", "Snake-Eye", deck(1))

        val text = YdkCodec.write(decks.versions("d").single().toDocument())

        assertEquals(deck(1, 2, 3), YdkCodec.parse(text).document.deck)
    }
}
