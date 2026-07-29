package com.kaiharimoto.mastertool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.kaiharimoto.mastertool.ui.AppDependencies
import com.kaiharimoto.mastertool.ui.DeckFileAccess
import com.kaiharimoto.mastertool.ui.ImportedFile
import com.kaiharimoto.mastertool.ui.MasterToolApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity(), DeckFileAccess {

    private var pendingImport: CompletableDeferred<ImportedFile?>? = null
    private var pendingExport: CompletableDeferred<Boolean>? = null
    private var pendingExportContent: ByteArray? = null

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val deferred = pendingImport
            pendingImport = null
            deferred?.complete(uri?.let(::readDeckFile))
        }

    // Registered with a wildcard rather than a type: three different kinds of
    // file leave by this door now -- a decklist, a siding sheet and a picture of
    // the deck -- and the contract fixes its type at registration. The picker
    // takes the type from the suggested name's extension instead.
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ANY_TYPE)) { uri ->
            val deferred = pendingExport
            val content = pendingExportContent
            pendingExport = null
            pendingExportContent = null

            val written = if (uri != null && content != null) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(content) }
                }.isSuccess
            } else {
                false
            }
            deferred?.complete(written)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MasterToolApplication
        val deps = AppDependencies(
            cardRepository = app.cardRepository,
            deckRepository = app.deckRepository,
            preferencesRepository = app.preferencesRepository,
            fileAccess = this,
            updateChecker = app.updateChecker,
            updater = AndroidAppUpdater(this, app.httpClient),
            newDeckId = { UUID.randomUUID().toString() },
            now = System::currentTimeMillis,
            // Owned by the Application, not this Activity: it outlives every
            // recreation and there is only ever one of it.
            httpClient = app.httpClient,
        )

        setContent { MasterToolApp(deps) }
    }

    // ---- DeckFileAccess ----------------------------------------------------

    override suspend fun importDeck(): ImportedFile? {
        // Only one picker may be open at a time; abandon any previous request.
        pendingImport?.complete(null)

        val deferred = CompletableDeferred<ImportedFile?>()
        pendingImport = deferred
        // .ydk has no registered MIME type, so the picker has to allow anything.
        openDocument.launch(arrayOf("*/*"))
        return deferred.await()
    }

    override suspend fun export(
        suggestedName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Boolean {
        pendingExport?.complete(false)

        val deferred = CompletableDeferred<Boolean>()
        pendingExport = deferred
        pendingExportContent = bytes
        createDocument.launch(suggestedName)
        return deferred.await()
    }

    override suspend fun share(suggestedName: String, bytes: ByteArray, mimeType: String): Boolean {
        val uri = withContext(Dispatchers.IO) {
            val shareDir = File(cacheDir, "shared").apply { mkdirs() }
            val file = File(shareDir, suggestedName).apply { writeBytes(bytes) }
            FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            // The real type, not a placeholder: a share sheet offers a different
            // set of applications for an image than for a document, which is the
            // difference between "post this" and "attach this to an email".
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, suggestedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // A device with nothing that can receive the file throws rather than
        // showing an empty sheet, and a crash is not the answer to "share this".
        return runCatching { startActivity(Intent.createChooser(intent, "Share")) }.isSuccess
    }

    // ---- helpers -----------------------------------------------------------

    private fun readDeckFile(uri: Uri): ImportedFile? = runCatching {
        val name = displayName(uri) ?: "deck.ydk"
        val content = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return null
        ImportedFile(name, content)
    }.getOrNull()

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    private companion object {
        const val ANY_TYPE = "*/*"
    }
}
