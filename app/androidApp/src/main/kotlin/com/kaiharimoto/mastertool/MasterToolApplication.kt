package com.kaiharimoto.mastertool

import android.app.Application
import com.kaiharimoto.mastertool.core.data.CardRepository
import com.kaiharimoto.mastertool.core.data.DatabaseFactory
import com.kaiharimoto.mastertool.core.data.DeckRepository
import com.kaiharimoto.mastertool.core.remote.HttpClientFactory
import com.kaiharimoto.mastertool.core.remote.YgoProDeckApi
import com.kaiharimoto.mastertool.core.update.GitHubReleaseApi
import com.kaiharimoto.mastertool.core.update.UpdateChecker
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers

/**
 * Builds the object graph once per process.
 *
 * The database and HTTP client are expensive to create and cheap to keep, so
 * they live for as long as the app does rather than per-Activity.
 */
class MasterToolApplication : Application() {

    lateinit var httpClient: HttpClient
        private set

    lateinit var cardRepository: CardRepository
        private set

    lateinit var deckRepository: DeckRepository
        private set

    lateinit var updateChecker: UpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()

        val database = DatabaseFactory.create(AndroidDatabaseDriverFactory(this))
        httpClient = HttpClientFactory.create()

        cardRepository = CardRepository(
            database = database,
            api = YgoProDeckApi(httpClient),
            clock = System::currentTimeMillis,
            ioDispatcher = Dispatchers.IO,
        )
        deckRepository = DeckRepository(
            database = database,
            clock = System::currentTimeMillis,
            ioDispatcher = Dispatchers.IO,
        )
        updateChecker = UpdateChecker(
            api = GitHubReleaseApi(httpClient),
            currentVersionName = appVersionName(),
        )
    }
}
