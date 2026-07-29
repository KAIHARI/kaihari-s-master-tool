package com.kaiharimoto.mastertool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderScreen
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckLayoutState
import com.kaiharimoto.mastertool.ui.library.DeckLibraryScreen
import com.kaiharimoto.mastertool.ui.sandbox.SandboxScreen
import com.kaiharimoto.mastertool.ui.sandbox.SandboxState
import com.kaiharimoto.mastertool.ui.theme.MasterToolTheme
import com.kaiharimoto.mastertool.ui.update.UpdateDialog
import com.kaiharimoto.mastertool.ui.update.UpdateState
import io.ktor.client.HttpClient

/**
 * Which screen is showing.
 *
 * Two destinations do not justify a navigation library; a sealed type keeps the
 * whole routing story visible in one place.
 */
private sealed interface Screen {
    data object DeckBuilder : Screen
    data object Library : Screen
    data object Sandbox : Screen
}

@Composable
fun MasterToolApp(deps: AppDependencies) {
    val scope = rememberCoroutineScope()
    val builderState = remember { DeckBuilderState(deps, scope) }
    val layoutState = remember { DeckLayoutState(deps.preferencesRepository, scope) }
    val updateState = remember { UpdateState(deps.updateChecker, deps.updater, scope) }
    val sandboxState = remember { SandboxState() }
    var screen by remember { mutableStateOf<Screen>(Screen.DeckBuilder) }

    DisposableEffect(Unit) {
        configureImageLoader(deps.httpClient)
        // The format is a layout preference on disk but belongs to the builder at
        // runtime, so it is handed over once the stored settings arrive.
        layoutState.start { preferences ->
            builderState.onFormatChange(preferences.format)
            // Pick up where the last run left off. A deck that has since been
            // deleted simply does not load, which is the right amount of fuss.
            preferences.lastDeckId?.let(builderState::load)
        }
        builderState.start()
        // Silent on launch: it only interrupts if there is something to install.
        updateState.check(userInitiated = false)
        onDispose { }
    }

    // Written whenever the open deck changes rather than on the way out: there
    // is no reliable moment of closing on a tablet, whose process is reclaimed
    // without ceremony, and that is the case this exists for.
    LaunchedEffect(builderState.deckId) {
        layoutState.rememberOpenDeck(builderState.deckId)
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
                onOpenSandbox = { hand ->
                    // Dealt fresh on the way in. A board left over from twenty
                    // minutes ago is not the question anybody is asking, and the
                    // deck may not even be the same deck. Unless a hand came
                    // with the request, in which case that hand is the question.
                    sandboxState.open(builderState.deck, hand.ifEmpty { null })
                    screen = Screen.Sandbox
                },
            )

            Screen.Sandbox -> SandboxScreen(
                state = sandboxState,
                index = builderState.index,
                matChoice = builderState.mat,
                onBack = { screen = Screen.DeckBuilder },
            )

            Screen.Library -> DeckLibraryScreen(
                deps = deps,
                // The pool the builder already has. The library needs it only to
                // turn three passcodes into three pictures.
                index = builderState.index,
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
 * Card art is fetched over the very same client as the card data, not merely
 * the same stack: one pool of connections to one host, shared.
 *
 * A generous memory cache matters here: a deck pane can show 90 thumbnails at
 * once and scrolling back and forth should never re-decode them.
 */
private fun configureImageLoader(httpClient: HttpClient) {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient)) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
