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
    private var pendingExportContent: String? = null

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val deferred = pendingImport
            pendingImport = null
            deferred?.complete(uri?.let(::readDeckFile))
        }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { uri ->
            val deferred = pendingExport
            val content = pendingExportContent
            pendingExport = null
            pendingExportContent = null

            val written = if (uri != null && content != null) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
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
            fileAccess = this,
            newDeckId = { UUID.randomUUID().toString() },
            now = System::currentTimeMillis,
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

    override suspend fun exportDeck(suggestedName: String, content: String): Boolean {
        pendingExport?.complete(false)

        val deferred = CompletableDeferred<Boolean>()
        pendingExport = deferred
        pendingExportContent = content
        createDocument.launch(suggestedName)
        return deferred.await()
    }

    override suspend fun shareDeck(suggestedName: String, content: String) {
        val uri = withContext(Dispatchers.IO) {
            val shareDir = File(cacheDir, "shared").apply { mkdirs() }
            val file = File(shareDir, suggestedName).apply { writeText(content) }
            FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, suggestedName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share deck"))
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
        const val MIME_TYPE = "text/plain"
    }
}
