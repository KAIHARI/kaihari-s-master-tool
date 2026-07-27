package com.kaiharimoto.mastertool

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.kaiharimoto.mastertool.core.data.DatabaseDriverFactory
import com.kaiharimoto.mastertool.core.data.DatabaseFactory
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {

    override fun create(): SqlDriver = AndroidSqliteDriver(
        schema = MasterToolDatabase.Schema,
        context = context,
        name = DatabaseFactory.DATABASE_NAME,
    )
}
