package com.kaiharimoto.mastertool.desktop

import com.kaiharimoto.mastertool.ui.DeckFileAccess
import com.kaiharimoto.mastertool.ui.ImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * File dialogs for the desktop build.
 *
 * Swing dialogs are modal and block the calling thread, so every call hops to a
 * background dispatcher — running them on Compose's UI thread would deadlock the
 * window they are parented to.
 */
class DesktopDeckFileAccess : DeckFileAccess {

    override suspend fun importDeck(): ImportedFile? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import deck"
            fileFilter = FileNameExtensionFilter("Deck files (*.ydk, *.ydkx)", "ydk", "ydkx")
            isAcceptAllFileFilterUsed = true
        }

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return@withContext null
        }

        val file = chooser.selectedFile ?: return@withContext null
        runCatching { ImportedFile(file.name, file.readText()) }.getOrNull()
    }

    override suspend fun export(
        suggestedName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export"
            selectedFile = File(suggestedName)
        }

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return@withContext false
        }

        val target = chooser.selectedFile ?: return@withContext false
        // Bytes, not text: a deck file and a picture of one leave by the same
        // door, and writing a PNG through a character encoder destroys it.
        runCatching { target.writeBytes(bytes) }.isSuccess
    }

    /**
     * Desktop has no share sheet; the closest equivalent is writing the file out
     * and revealing it in the file manager.
     */
    override suspend fun share(suggestedName: String, bytes: ByteArray, mimeType: String) {
        withContext(Dispatchers.IO) {
            val file = File(System.getProperty("java.io.tmpdir"), suggestedName)
            runCatching {
                file.writeBytes(bytes)
                if (Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(file.parentFile)
                    }
                }
            }
        }
    }
}
