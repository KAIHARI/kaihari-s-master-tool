package com.kaiharimoto.mastertool.core.data

import app.cash.sqldelight.db.SqlDriver
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase

/**
 * Supplies the platform's SQLite driver.
 *
 * Declared as an interface rather than an expect/actual class so `:core` carries
 * no platform code at all: each app module builds the driver it needs (Android
 * needs a Context, desktop needs a file path) and hands it in.
 */
fun interface DatabaseDriverFactory {
    fun create(): SqlDriver
}

object DatabaseFactory {
    const val DATABASE_NAME = "kai_master_tool.db"

    fun create(driverFactory: DatabaseDriverFactory): MasterToolDatabase =
        MasterToolDatabase(driverFactory.create())
}
