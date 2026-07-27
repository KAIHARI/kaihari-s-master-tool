package com.kaiharimoto.mastertool.desktop

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.data.DatabaseDriverFactory
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import java.util.Properties

/**
 * Desktop driver.
 *
 * Lives in the desktop application rather than in `:core` so the JDBC SQLite
 * driver — which carries its own native library — never reaches the APK.
 *
 * Passing the schema to the driver lets SQLDelight create it on first run and
 * migrate it afterwards, so the same call works for a fresh install and an
 * existing database.
 */
class JvmDatabaseDriverFactory(
    private val databasePath: String? = null,
) : DatabaseDriverFactory {

    override fun create(): SqlDriver {
        val url = databasePath?.let { "jdbc:sqlite:$it" } ?: JdbcSqliteDriver.IN_MEMORY
        return JdbcSqliteDriver(url, Properties(), MasterToolDatabase.Schema)
    }
}
