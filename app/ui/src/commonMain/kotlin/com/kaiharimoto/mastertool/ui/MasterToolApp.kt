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
import com.kaiharimoto.mastertool.ui.library.DeckLibraryScreen
import com.kaiharimoto.mastertool.ui.theme.MasterToolTheme

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
    var screen by remember { mutableStateOf<Screen>(Screen.DeckBuilder) }

    DisposableEffect(Unit) {
        configureImageLoader()
        builderState.start()
        onDispose { }
    }

    MasterToolTheme {
        when (screen) {
            Screen.DeckBuilder -> DeckBuilderScreen(
                state = builderState,
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
