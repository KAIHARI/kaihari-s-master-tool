package com.kaiharimoto.mastertool.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import java.util.Properties

/**
 * A throwaway in-memory database.
 *
 * `:core` ships no SQL driver of its own — each application supplies one — so
 * the tests bring their own JDBC driver rather than reaching for production code.
 */
internal fun testDatabase(): MasterToolDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), MasterToolDatabase.Schema)
    return MasterToolDatabase(driver)
}
