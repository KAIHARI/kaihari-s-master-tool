package com.kaiharimoto.mastertool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import com.kaiharimoto.mastertool.core.remote.HttpClientFactory
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderScreen
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckLayoutState
import com.kaiharimoto.mastertool.ui.fx.LocalFeedback
import com.kaiharimoto.mastertool.ui.fx.defaultFeedbackEnabled
import com.kaiharimoto.mastertool.ui.fx.rememberFeedback
import com.kaiharimoto.mastertool.ui.library.DeckLibraryScreen
import com.kaiharimoto.mastertool.ui.table.GoldfishScreen
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
    data object Goldfish : Screen
}

@Composable
fun MasterToolApp(deps: AppDependencies) {
    val scope = rememberCoroutineScope()
    val builderState = remember { DeckBuilderState(deps, scope) }
    val layoutState = remember { DeckLayoutState(deps.preferencesRepository, scope) }
    val updateState = remember { UpdateState(deps.updateChecker, deps.updater, scope) }
    var screen by remember { mutableStateOf<Screen>(Screen.DeckBuilder) }

    DisposableEffect(Unit) {
        configureImageLoader(deps.imageCacheDir)
        // The format is a layout preference on disk but belongs to the builder at
        // runtime, so it is handed over once the stored settings arrive.
        layoutState.start { preferences -> builderState.onFormatChange(preferences.format) }
        builderState.start()
        // Silent on launch: it only interrupts if there is something to install.
        updateState.check(userInitiated = false)
        // The last layout change before the window closes is exactly the one
        // the user quit to keep; without this it was still waiting out its
        // save debounce when the scope died.
        onDispose { layoutState.flush() }
    }

    val feedback = rememberFeedback(
        enabled = {
            layoutState.preferences.feedbackEnabled ?: defaultFeedbackEnabled()
        },
    )

    MasterToolTheme(mode = layoutState.preferences.themeMode) {
        CompositionLocalProvider(LocalFeedback provides feedback) {
        when (screen) {
            Screen.DeckBuilder -> DeckBuilderScreen(
                state = builderState,
                layout = layoutState,
                updateState = updateState,
                onOpenLibrary = { screen = Screen.Library },
                onOpenGoldfish = { screen = Screen.Goldfish },
            )

            Screen.Goldfish -> GoldfishScreen(
                state = builderState,
                onBack = { screen = Screen.DeckBuilder },
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
}

/**
 * Card art is fetched over the same Ktor stack as the card data.
 *
 * A generous memory cache matters here: a deck pane can show 90 thumbnails at
 * once and scrolling back and forth should never re-decode them. The disk
 * cache matters more: art must survive a cold start with no network, because
 * the venue with no signal is where this app earns its keep.
 */
private fun configureImageLoader(cacheDir: String?) {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(HttpClientFactory.create())) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .apply {
                if (cacheDir != null) {
                    diskCache {
                        DiskCache.Builder()
                            .directory(cacheDir.toPath())
                            .maxSizeBytes(512L * 1024 * 1024)
                            .build()
                    }
                }
            }
            .crossfade(true)
            .build()
    }
}
