package com.kaiharimoto.mastertool.ui

import com.kaiharimoto.mastertool.core.data.CardRepository
import com.kaiharimoto.mastertool.core.data.DeckRepository
import com.kaiharimoto.mastertool.core.data.PreferencesRepository
import com.kaiharimoto.mastertool.core.update.UpdateChecker
import com.kaiharimoto.mastertool.ui.update.AppUpdater
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** A deck file chosen by the user, as returned by the platform picker. */
data class ImportedFile(val name: String, val content: String)

/**
 * Platform file access.
 *
 * An interface rather than expect/actual so `:ui` stays free of platform source
 * sets: Android supplies an implementation backed by the Storage Access
 * Framework, desktop one backed by a file dialog.
 *
 * Bytes rather than text, because a picture of a deck is not text. The siding
 * sheet gets away with riding a string export only because the PDF writer keeps
 * the whole file inside ASCII on purpose; a PNG cannot, and would not survive
 * being decoded and re-encoded on the way out. The two string helpers below keep
 * every existing caller reading the way it did.
 */
interface DeckFileAccess {
    /** Opens a picker. Returns null when the user cancels. */
    suspend fun importDeck(): ImportedFile?

    /** Writes [bytes] out, letting the user choose where. */
    suspend fun export(suggestedName: String, bytes: ByteArray, mimeType: String): Boolean

    /**
     * Hands the file to the platform share sheet, where one exists.
     *
     * Returns whether anything happened, like [export] does. Not every platform
     * has a share sheet — the desktop writes the file and shows it instead — and
     * one that quietly does neither is a menu item that looks broken rather than
     * unavailable.
     */
    suspend fun share(suggestedName: String, bytes: ByteArray, mimeType: String): Boolean
}

suspend fun DeckFileAccess.exportDeck(suggestedName: String, content: String): Boolean =
    export(suggestedName, content.encodeToByteArray(), mimeTypeFor(suggestedName))

suspend fun DeckFileAccess.shareDeck(suggestedName: String, content: String): Boolean =
    share(suggestedName, content.encodeToByteArray(), mimeTypeFor(suggestedName))

/**
 * What to tell the platform a file is, from what it is called.
 *
 * `.ydk` has no registered type and never will, so it goes out as plain text —
 * which is what it is. The two that do have types matter: a share sheet offers a
 * different set of applications for an image than for a document, and getting it
 * wrong is the difference between "post this" and "attach this to an email".
 */
fun mimeTypeFor(name: String): String = when {
    name.endsWith(".png", ignoreCase = true) -> "image/png"
    name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    else -> "text/plain"
}

/**
 * Everything the UI needs, assembled by each platform's entry point.
 *
 * Hand-rolled rather than reached for via a DI framework: the graph is four
 * objects deep and an explicit constructor is easier to follow than annotations.
 */
class AppDependencies(
    val cardRepository: CardRepository,
    val deckRepository: DeckRepository,
    val preferencesRepository: PreferencesRepository,
    val fileAccess: DeckFileAccess,
    val updateChecker: UpdateChecker,
    val updater: AppUpdater,
    val newDeckId: () -> String,
    val now: () -> Long,
    /**
     * The one HTTP client in the process.
     *
     * There used to be three — card data, release checks and card art each built
     * their own — which is three connection pools and three thread pools for a
     * program that talks to two hosts, and none of them was ever closed. Ktor is
     * built to be shared, so the graph carries one and everything reaching the
     * network is handed it.
     */
    val httpClient: HttpClient,
    /**
     * Where CPU-bound work runs.
     *
     * Searching scores every card in the pool, which is far too much to do on
     * the frame thread — and the scope the UI hands to its state holders is the
     * composition's, so anything launched there lands on the main dispatcher
     * unless it says otherwise.
     */
    val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
)
