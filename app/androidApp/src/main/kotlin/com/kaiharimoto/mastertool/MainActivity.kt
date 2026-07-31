package com.kaiharimoto.mastertool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Deliberately theme-free: it must render even when the theme cannot. */
@Composable
private fun CrashReportScreen(trace: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF060608))
            .padding(24.dp),
    ) {
        BasicText(
            "The app crashed last time it ran",
            style = TextStyle(color = Color.White, fontSize = 22.sp),
        )
        BasicText(
            "Share this report so it can be fixed, then continue.",
            style = TextStyle(color = Color(0xFF9C9CAB), fontSize = 14.sp),
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        Row {
            BasicText(
                "SHARE REPORT",
                style = TextStyle(color = Color(0xFF35E0FF), fontSize = 16.sp),
                modifier = Modifier.clickable(onClick = onShare).padding(10.dp),
            )
            Spacer(Modifier.width(20.dp))
            BasicText(
                "CONTINUE TO APP",
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp),
            )
        }

        SelectionContainer(
            Modifier
                .padding(top = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                trace,
                style = TextStyle(color = Color(0xFFFF4D4D), fontSize = 11.sp),
            )
        }
    }
}

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

        // A crash on a tablet with no adb is a crash nobody can read. Persist
        // any uncaught exception; the next launch shows it with a share button
        // instead of dying silently again.
        val crashFile = File(filesDir, "last-crash.txt")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashFile.writeText(error.stackTraceToString()) }
            previousHandler?.uncaughtException(thread, error)
        }

        val pendingCrash = crashFile.takeIf { it.exists() }?.readText()

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
            imageCacheDir = cacheDir.resolve("card_art").absolutePath,
        )

        setContent {
            var showCrash by remember { mutableStateOf(pendingCrash != null) }
            if (showCrash && pendingCrash != null) {
                // Raw primitives on purpose: if the crash lives in the theme or
                // font pipeline, this screen must not share its fate.
                CrashReportScreen(
                    trace = pendingCrash,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, pendingCrash)
                        }
                        startActivity(Intent.createChooser(send, "Share crash report"))
                    },
                    onDismiss = {
                        crashFile.delete()
                        showCrash = false
                    },
                )
            } else {
                MasterToolApp(deps)
            }
        }
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
