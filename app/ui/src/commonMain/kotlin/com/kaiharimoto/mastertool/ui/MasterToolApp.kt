package com.kaiharimoto.mastertool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.kaiharimoto.mastertool.core.remote.HttpClientFactory
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderScreen
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckLayoutState
import com.kaiharimoto.mastertool.ui.library.DeckLibraryScreen
import com.kaiharimoto.mastertool.ui.theme.MasterToolTheme
import com.kaiharimoto.mastertool.ui.update.UpdateDialog
import com.kaiharimoto.mastertool.ui.update.UpdateState

/**
 * Which screen is showing.
 *
 * Two destinations do not justify a navigation library; a sealed type keeps the
 * whole routing story visible in one place.
 */
private sealed interface Screen {
    data object DeckBuilder : Screen
    data object Library : Screen
}

@Composable
fun MasterToolApp(deps: AppDependencies) {
    val scope = rememberCoroutineScope()
    val builderState = remember { DeckBuilderState(deps, scope) }
    val layoutState = remember { DeckLayoutState(deps.preferencesRepository, scope) }
    val updateState = remember { UpdateState(deps.updateChecker, deps.updater, scope) }
    var screen by remember { mutableStateOf<Screen>(Screen.DeckBuilder) }

    DisposableEffect(Unit) {
        configureImageLoader()
        // The format is a layout preference on disk but belongs to the builder at
        // runtime, so it is handed over once the stored settings arrive.
        layoutState.start { preferences -> builderState.onFormatChange(preferences.format) }
        builderState.start()
        // Silent on launch: it only interrupts if there is something to install.
        updateState.check(userInitiated = false)
        onDispose { }
    }

    // Read from stored settings, so the surface is whatever it was left as
    // rather than whatever the tablet thinks the time of day is.
    MasterToolTheme(layoutState.preferences.theme) {
        when (screen) {
            Screen.DeckBuilder -> DeckBuilderScreen(
                state = builderState,
                layout = layoutState,
                updateState = updateState,
                onOpenLibrary = { screen = Screen.Library },
            )

            Screen.Library -> DeckLibraryScreen(
                deps = deps,
                onOpenDeck = { id ->
                    builderState.load(id)
                    screen = Screen.DeckBuilder
                },
                onBack = { screen = Screen.DeckBuilder },
            )
        }

        updateState.pendingUpdate?.let { release ->
            UpdateDialog(
                release = release,
                currentVersionName = updateState.currentVersionName,
                manualOnly = updateState.pendingUpdateIsManual,
                isDownloading = updateState.isDownloading,
                progress = updateState.downloadProgress,
                onInstall = updateState::install,
                onOpenInBrowser = updateState::openInBrowser,
                onDismiss = updateState::dismiss,
            )
        }
    }
}

/**
 * Card art is fetched over the same Ktor stack as the card data.
 *
 * A generous memory cache matters here: a deck pane can show 90 thumbnails at
 * once and scrolling back and forth should never re-decode them.
 */
private fun configureImageLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(HttpClientFactory.create())) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
