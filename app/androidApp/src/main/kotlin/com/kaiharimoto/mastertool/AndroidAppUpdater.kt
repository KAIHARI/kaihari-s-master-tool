package com.kaiharimoto.mastertool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kaiharimoto.mastertool.core.update.Release
import com.kaiharimoto.mastertool.ui.update.AppUpdater
import com.kaiharimoto.mastertool.ui.update.InstallOutcome
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads a release APK and hands it to Android's package installer.
 *
 * The app can only replace itself because every build is signed with the same
 * committed release key — Android refuses to update an app whose signing
 * certificate changed, so a per-machine debug key would make this impossible.
 */
class AndroidAppUpdater(
    private val activity: Activity,
    private val client: HttpClient,
) : AppUpdater {

    override val currentVersionName: String
        get() = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

    override val canInstallInPlace: Boolean = true

    override suspend fun downloadAndInstall(
        release: Release,
        onProgress: (Float?) -> Unit,
    ): InstallOutcome {
        val url = release.apkUrl ?: return InstallOutcome.Failed("That release has no APK.")

        // Checked before downloading 20 MB the user would not be able to install.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            requestInstallPermission()
            return InstallOutcome.NeedsInstallPermission
        }

        val file = runCatching { download(url, onProgress) }
            .getOrElse { error ->
                return InstallOutcome.Failed(error.message ?: "Download failed.")
            }

        return runCatching {
            launchInstaller(file)
            InstallOutcome.HandedToInstaller
        }.getOrElse { error ->
            InstallOutcome.Failed(error.message ?: "Could not start the installer.")
        }
    }

    override fun openReleasePage(url: String) {
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private suspend fun download(url: String, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val response: HttpResponse = client.get(url) {
                onDownload { received, total ->
                    // The server does not always send a length; report
                    // indeterminate rather than inventing a percentage.
                    onProgress(total?.takeIf { it > 0 }?.let { received.toFloat() / it })
                }
            }

            if (!response.status.isSuccess()) {
                error("Download failed with ${response.status}")
            }

            val bytes = response.readRawBytes()

            // Cleared each time so a failed download cannot be installed later.
            val directory = File(activity.cacheDir, UPDATE_DIRECTORY).apply {
                deleteRecursively()
                mkdirs()
            }
            File(directory, "kai-master-tool-update.apk").apply { writeBytes(bytes) }
        }

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    /** Sends the user to the per-app "install unknown apps" toggle. */
    private fun requestInstallPermission() {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            // Some OEM builds lack the per-app screen; fall back to the list.
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UPDATE_DIRECTORY = "updates"
    }
}

/** Convenience for reading the running build's version outside an Activity. */
fun Context.appVersionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
        .getOrNull() ?: "unknown"
