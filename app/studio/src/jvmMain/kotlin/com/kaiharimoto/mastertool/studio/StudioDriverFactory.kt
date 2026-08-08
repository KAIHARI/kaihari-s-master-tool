package com.kaiharimoto.mastertool.studio

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.data.DatabaseDriverFactory
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import java.util.Properties

/**
 * The studio's database, on disk beside its card-art cache.
 *
 * A copy of the desktop app's factory rather than a dependency on it: the
 * desktop module is an *application*, and a tool reaching into one to borrow a
 * class is how a tool ends up deciding what the application may refactor.
 */
class StudioDriverFactory(private val databasePath: String) : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        JdbcSqliteDriver("jdbc:sqlite:$databasePath", Properties(), MasterToolDatabase.Schema)
}
