package com.kaiharimoto.mastertool.ui.deckbuilder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kaiharimoto.mastertool.core.data.CardRepository
import com.kaiharimoto.mastertool.core.data.DeckRepository
import com.kaiharimoto.mastertool.core.data.PreferencesRepository
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.remote.YgoProDeckApi
import com.kaiharimoto.mastertool.core.update.GitHubReleaseApi
import com.kaiharimoto.mastertool.core.update.Release
import com.kaiharimoto.mastertool.core.update.UpdateChecker
import com.kaiharimoto.mastertool.ui.AppDependencies
import com.kaiharimoto.mastertool.ui.DeckFileAccess
import com.kaiharimoto.mastertool.ui.ImportedFile
import com.kaiharimoto.mastertool.ui.update.AppUpdater
import com.kaiharimoto.mastertool.ui.update.InstallOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A deck builder driven directly, with a real database behind it.
 *
 * The state holders are plain objects over Compose's snapshot system, so they
 * run with no window and no composition — which is the only reason this module
 * can be tested at all in an environment where it cannot even be compiled.
 *
 * Cards are constructed and handed straight to `addCard`, rather than seeded
 * through the repository: the pool only exists to *find* cards, and none of the
 * behaviour worth testing here is about finding them.
 */
internal fun testDependencies(
    fileAccess: DeckFileAccess = NoFileAccess,
): AppDependencies {
    val database = MasterToolDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), MasterToolDatabase.Schema),
    )

    // Never reached: nothing here syncs. Present because the graph asks for it,
    // and refusing every request is the honest offline behaviour anyway.
    val offline = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })

    return AppDependencies(
        cardRepository = CardRepository(
            database,
            YgoProDeckApi(offline),
            { 0L },
            Dispatchers.Unconfined,
        ),
        deckRepository = DeckRepository(database, { 0L }, Dispatchers.Unconfined),
        preferencesRepository = PreferencesRepository(database, Dispatchers.Unconfined),
        fileAccess = fileAccess,
        updateChecker = UpdateChecker(GitHubReleaseApi(offline), currentVersionName = "1.0.0"),
        updater = NoUpdater,
        newDeckId = { "test-deck" },
        now = { 0L },
        computeDispatcher = Dispatchers.Unconfined,
    )
}

/**
 * A builder whose coroutines have already run by the time the call returns.
 *
 * `runTest`'s own scope queues work on a scheduler that only advances when the
 * test says so, which would mean every assertion here came before the thing it
 * is asserting about. Unconfined runs each launch eagerly, so importing a file
 * and then reading what it contained does what it looks like it does.
 */
internal fun TestScope.builderState(
    deps: AppDependencies = testDependencies(),
): DeckBuilderState =
    DeckBuilderState(deps, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

internal object NoFileAccess : DeckFileAccess {
    override suspend fun importDeck(): ImportedFile? = null
    override suspend fun exportDeck(suggestedName: String, content: String) = false
    override suspend fun shareDeck(suggestedName: String, content: String) = Unit
}

/** Hands back a file instead of opening a picker, and keeps whatever was written. */
internal class StubFileAccess(private val file: ImportedFile) : DeckFileAccess {
    var exported: String? = null
        private set

    override suspend fun importDeck(): ImportedFile = file

    override suspend fun exportDeck(suggestedName: String, content: String): Boolean {
        exported = content
        return true
    }

    override suspend fun shareDeck(suggestedName: String, content: String) = Unit
}

internal object NoUpdater : AppUpdater {
    override val currentVersionName = "1.0.0"
    override val canInstallInPlace = false
    override suspend fun downloadAndInstall(
        release: Release,
        onProgress: (Float?) -> Unit,
    ): InstallOutcome = error("no test installs anything")

    override fun openReleasePage(url: String) = Unit
}

/** Real passcodes, so a failure reads like the app rather than like a number. */
internal object TestPool {
    val ash = monster(14558127, "Ash Blossom & Joyous Spring", Attribute.FIRE)
    val maxx = monster(23434538, "Maxx \"C\"", Attribute.EARTH)
    val nibiru = monster(27204311, "Nibiru, the Primal Being", Attribute.LIGHT)
    val droll = monster(94145021, "Droll & Lock Bird", Attribute.WIND)

    val imperm = Card(
        id = CardId(10045474),
        name = "Infinite Impermanence",
        type = "Trap Card",
        frameType = "trap",
    )

    private fun monster(id: Int, name: String, attribute: Attribute) = Card(
        id = CardId(id),
        name = name,
        type = "Effect Monster",
        frameType = "effect",
        race = "Zombie",
        attribute = attribute,
        atk = 0,
        def = 1800,
        level = 3,
    )
}
