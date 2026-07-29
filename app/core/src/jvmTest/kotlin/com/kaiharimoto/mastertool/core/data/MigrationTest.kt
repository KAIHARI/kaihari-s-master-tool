package com.kaiharimoto.mastertool.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Upgrades a real old database and checks it against a fresh one.
 *
 * SQLDelight derives the schema version from the number of migration files, and
 * both driver factories hand `MasterToolDatabase.Schema` to the driver. So adding
 * a table to a `.sq` file *alone* leaves the version unchanged, no migration
 * runs, and the first query against the new table throws — on every device that
 * already has the app, and on none of the ones it was tested on. Signed builds
 * carry an in-app updater, so those devices exist.
 *
 * That is a failure mode nothing else here can see: a fresh install is correct
 * either way, which is exactly why it has to be tested rather than noticed.
 *
 * The version 1 schema below is transcribed from the commit that introduced it
 * and must never be edited to match a later change — it is a record of what is on
 * somebody's tablet, not a copy of the current files. When adding a table, add
 * the migration; do not touch this.
 *
 * Lives in `jvmTest` because the JDBC driver does: an app module picks its own
 * driver, and one bundled here would end up inside the APK.
 */
class MigrationTest {

    @Test
    fun aVersionOneDatabaseUpgradesIntoWhatAFreshInstallGets() {
        val upgraded = versionOne()
        MasterToolDatabase.Schema.migrate(upgraded, 1L, MasterToolDatabase.Schema.version)

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MasterToolDatabase.Schema.create(fresh)

        assertEquals(
            fresh.schema(),
            upgraded.schema(),
            "an upgraded database and a new one have to be the same database",
        )

        upgraded.close()
        fresh.close()
    }

    @Test
    fun theDecksThatWereAlreadyThereAreStillThereAfterwards() {
        val driver = versionOne()
        driver.execute(
            null,
            """
            INSERT INTO deckEntity(id, name, main, extra, side, notes, extendedJson,
                createdAtEpochMs, updatedAtEpochMs)
            VALUES ('d1', 'Snake-Eye', '14558127,14558127', '', '23434538', 'built at locals',
                NULL, 1000, 2000)
            """.trimIndent(),
            0,
        )

        MasterToolDatabase.Schema.migrate(driver, 1L, MasterToolDatabase.Schema.version)

        val decks = MasterToolDatabase(driver).deckQueries.selectAll().executeAsList()

        assertEquals(1, decks.size)
        assertEquals("Snake-Eye", decks.single().name)
        assertEquals("14558127,14558127", decks.single().main)
        assertEquals("built at locals", decks.single().notes)

        driver.close()
    }

    @Test
    fun aFreshDatabaseIsUsableWithoutAnyMigrationRunning() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MasterToolDatabase.Schema.create(driver)

        val database = MasterToolDatabase(driver)
        assertEquals(emptyList(), database.deckQueries.selectAll().executeAsList())
        assertEquals(0L, database.cardQueries.countAll().executeAsOne())

        driver.close()
    }

    @Test
    fun everyTableAFreshInstallHasIsThereAfterAnUpgradeToo() {
        // Names rather than definitions. This is the same assertion as the one
        // above, but when a table is added to a `.sq` file and its migration is
        // forgotten, this is the failure that says which table -- and that is
        // the whole mistake being guarded against, so it is worth naming.
        val upgraded = versionOne()
        MasterToolDatabase.Schema.migrate(upgraded, 1L, MasterToolDatabase.Schema.version)

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MasterToolDatabase.Schema.create(fresh)

        assertEquals(
            fresh.tables(),
            upgraded.tables(),
            "a table added to a .sq file needs a migration file too, or it only " +
                "ever exists on installs made after the change",
        )

        upgraded.close()
        fresh.close()
    }

    /**
     * A database exactly as version 1 left it, transcribed from `3fec0b9`.
     *
     * Do not regenerate this from the current `.sq` files. Doing that would make
     * the test compare the schema against itself, which passes no matter what.
     */
    private fun versionOne(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        VERSION_ONE.forEach { driver.execute(null, it, 0) }
        driver.execute(null, "PRAGMA user_version = 1", 0)

        return driver
    }
}

private val VERSION_ONE = listOf(
    """
    CREATE TABLE cardEntity (
        id              INTEGER NOT NULL PRIMARY KEY,
        name            TEXT    NOT NULL,
        type            TEXT    NOT NULL,
        frameType       TEXT    NOT NULL,
        description     TEXT    NOT NULL DEFAULT '',
        race            TEXT,
        attribute       TEXT    NOT NULL DEFAULT 'UNKNOWN',
        atk             INTEGER,
        def             INTEGER,
        level           INTEGER,
        linkValue       INTEGER,
        linkMarkers     TEXT    NOT NULL DEFAULT '',
        pendulumScale   INTEGER,
        archetype       TEXT,
        imageUrl        TEXT,
        imageUrlSmall   TEXT,
        tcgBanStatus    TEXT    NOT NULL DEFAULT 'UNLIMITED',
        ocgBanStatus    TEXT    NOT NULL DEFAULT 'UNLIMITED',
        alternateIds    TEXT    NOT NULL DEFAULT ''
    )
    """.trimIndent(),
    """
    CREATE TABLE cardSyncState (
        id              INTEGER NOT NULL PRIMARY KEY,
        lastSyncEpochMs INTEGER NOT NULL,
        cardCount       INTEGER NOT NULL
    )
    """.trimIndent(),
    """
    CREATE TABLE deckEntity (
        id                TEXT    NOT NULL PRIMARY KEY,
        name              TEXT    NOT NULL,
        main              TEXT    NOT NULL DEFAULT '',
        extra             TEXT    NOT NULL DEFAULT '',
        side              TEXT    NOT NULL DEFAULT '',
        notes             TEXT    NOT NULL DEFAULT '',
        extendedJson      TEXT,
        createdAtEpochMs  INTEGER NOT NULL,
        updatedAtEpochMs  INTEGER NOT NULL
    )
    """.trimIndent(),
    "CREATE INDEX cardEntity_name ON cardEntity(name)",
    "CREATE INDEX cardEntity_archetype ON cardEntity(archetype)",
    "CREATE INDEX deckEntity_updatedAt ON deckEntity(updatedAtEpochMs)",
)

/** Every table and index the database holds, as SQLite itself describes them. */
private fun SqlDriver.schema(): List<String> =
    rows(
        "SELECT type || ' ' || name || ' :: ' || coalesce(sql, '') FROM sqlite_master " +
            "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name",
    ).map(::tidy)

private fun SqlDriver.tables(): List<String> =
    rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")

private fun SqlDriver.rows(sql: String): List<String> =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val out = ArrayList<String>()
            while (cursor.next().value) out += cursor.getString(0).orEmpty()
            QueryResult.Value(out.toList())
        },
        parameters = 0,
    ).value

/**
 * The same schema written two ways is the same schema.
 *
 * SQLite stores a table's definition as the text that created it, so reindenting
 * a `.sq` file would otherwise fail this test — which would teach people to stop
 * trusting it. Comments go for the same reason. There are no `--` sequences
 * inside string literals in this schema, so cutting at the first one is safe.
 */
private fun tidy(sql: String): String = sql
    .lineSequence()
    .joinToString(" ") { it.substringBefore("--") }
    .replace(Regex("\\s+"), " ")
    .trim()
