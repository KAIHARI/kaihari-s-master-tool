package com.kaiharimoto.mastertool

import android.app.Application
import com.kaiharimoto.mastertool.core.data.CardRepository
import com.kaiharimoto.mastertool.core.data.DatabaseFactory
import com.kaiharimoto.mastertool.core.data.DeckRepository
import com.kaiharimoto.mastertool.core.remote.HttpClientFactory
import com.kaiharimoto.mastertool.core.remote.YgoProDeckApi
import kotlinx.coroutines.Dispatchers

/**
 * Builds the object graph once per process.
 *
 * The database and HTTP client are expensive to create and cheap to keep, so
 * they live for as long as the app does rather than per-Activity.
 */
class MasterToolApplication : Application() {

    lateinit var cardRepository: CardRepository
        private set

    lateinit var deckRepository: DeckRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val database = DatabaseFactory.create(AndroidDatabaseDriverFactory(this))
        val api = YgoProDeckApi(HttpClientFactory.create())

        cardRepository = CardRepository(
            database = database,
            api = api,
            clock = System::currentTimeMillis,
            ioDispatcher = Dispatchers.IO,
        )
        deckRepository = DeckRepository(
            database = database,
            clock = System::currentTimeMillis,
            ioDispatcher = Dispatchers.IO,
        )
    }
}
