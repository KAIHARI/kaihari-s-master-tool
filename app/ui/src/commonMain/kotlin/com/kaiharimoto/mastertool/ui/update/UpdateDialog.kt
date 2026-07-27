package com.kaiharimoto.mastertool.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.update.Release

/**
 * Offers a newer build.
 *
 * Shows the release notes, because on a tool you use at tournaments it matters
 * whether an update changes deck-building behaviour before an event.
 */
@Composable
fun UpdateDialog(
    release: Release,
    currentVersionName: String,
    manualOnly: Boolean,
    isDownloading: Boolean,
    progress: Float?,
    onInstall: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text("Version ${release.versionName} is available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You're on $currentVersionName.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                release.apkSizeBytes?.takeIf { !manualOnly }?.let { bytes ->
                    Text(
                        "Download size: ${bytes / 1_000_000} MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (manualOnly) {
                    Text(
                        "This build has to be installed from the GitHub release page.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isDownloading) {
                    // Indeterminate until the server reports a content length.
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        if (progress == null) "Downloading…" else "Downloading ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !isDownloading) {
                Text(if (manualOnly) "Open release" else "Update")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Column {
                    if (!manualOnly) {
                        TextButton(onClick = onOpenInBrowser) { Text("View on GitHub") }
                    }
                    TextButton(onClick = onDismiss) { Text("Later") }
                }
            }
        },
    )
}
