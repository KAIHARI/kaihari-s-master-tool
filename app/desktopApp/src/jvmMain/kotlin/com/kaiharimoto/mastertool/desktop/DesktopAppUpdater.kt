package com.kaiharimoto.mastertool.desktop

import com.kaiharimoto.mastertool.core.update.Release
import com.kaiharimoto.mastertool.ui.update.AppUpdater
import com.kaiharimoto.mastertool.ui.update.InstallOutcome
import java.awt.Desktop
import java.net.URI

/**
 * Desktop update handling.
 *
 * The desktop build ships as a `.dmg` / `.msi` / `.deb`, and replacing a running
 * installed application from inside itself is a platform-specific mess with
 * elevation prompts. So this reports [canInstallInPlace] false and sends the
 * user to the release page — the APK self-update path is Android-only.
 */
class DesktopAppUpdater : AppUpdater {

    override val currentVersionName: String
        get() = System.getProperty(VERSION_PROPERTY)?.takeIf { it.isNotBlank() } ?: FALLBACK_VERSION

    override val canInstallInPlace: Boolean = false

    override suspend fun downloadAndInstall(
        release: Release,
        onProgress: (Float?) -> Unit,
    ): InstallOutcome {
        openReleasePage(release.htmlUrl)
        return InstallOutcome.HandedToInstaller
    }

    override fun openReleasePage(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI(url))
                }
            }
        }
    }

    private companion object {
        /** Injected by the Gradle build so the version lives in one place. */
        const val VERSION_PROPERTY = "mastertool.version"

        /** Used when running from an IDE without the build's jvmArgs. */
        const val FALLBACK_VERSION = "0.0.0-dev"
    }
}
