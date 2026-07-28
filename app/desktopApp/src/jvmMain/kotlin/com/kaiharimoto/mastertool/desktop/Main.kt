package com.kaiharimoto.mastertool.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kaiharimoto.mastertool.core.data.CardRepository
import com.kaiharimoto.mastertool.core.data.DatabaseFactory
import com.kaiharimoto.mastertool.core.data.DeckRepository
import com.kaiharimoto.mastertool.core.data.PreferencesRepository
import com.kaiharimoto.mastertool.core.remote.HttpClientFactory
import com.kaiharimoto.mastertool.core.remote.YgoProDeckApi
import com.kaiharimoto.mastertool.core.update.GitHubReleaseApi
import com.kaiharimoto.mastertool.core.update.UpdateChecker
import com.kaiharimoto.mastertool.ui.AppDependencies
import com.kaiharimoto.mastertool.ui.MasterToolApp
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.UUID

/**
 * Desktop entry point.
 *
 * The same Compose tree that runs on the tablet, in a window. Data lives beside
 * the OS's usual per-user application directory rather than next to the binary,
 * so an update never takes your decks with it.
 */
fun main() = application {
    val deps = remember { buildDependencies() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "kai's master tool",
        state = rememberWindowState(size = DpSize(1600.dp, 1000.dp)),
    ) {
        MasterToolApp(deps)
    }
}

private fun buildDependencies(): AppDependencies {
    val updater = DesktopAppUpdater()
    val database = DatabaseFactory.create(
        JvmDatabaseDriverFactory(File(dataDirectory(), DatabaseFactory.DATABASE_NAME).absolutePath)
    )
    val api = YgoProDeckApi(HttpClientFactory.create())

    return AppDependencies(
        cardRepository = CardRepository(
            database = database,
            api = api,
            clock = System::currentTimeMillis,
            ioDispatcher = Dispatchers.IO,
        ),
        deckRepository = DeckRepository(
            database = database,
            clock = System::currentTimeMillis,
            ioDispatcher = Dispatchers.IO,
        ),
        preferencesRepository = PreferencesRepository(
            database = database,
            ioDispatcher = Dispatchers.IO,
        ),
        fileAccess = DesktopDeckFileAccess(),
        updateChecker = UpdateChecker(
            api = GitHubReleaseApi(HttpClientFactory.create()),
            currentVersionName = updater.currentVersionName,
        ),
        updater = updater,
        newDeckId = { UUID.randomUUID().toString() },
        now = System::currentTimeMillis,
    )
}

/** Per-user data location, following each platform's convention. */
private fun dataDirectory(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")

    val base = when {
        os.contains("mac") -> File(home, "Library/Application Support/KaiMasterTool")
        os.contains("win") -> File(
            System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path,
            "KaiMasterTool",
        )
        else -> File(
            System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path,
            "kai-master-tool",
        )
    }

    base.mkdirs()
    return base
}
