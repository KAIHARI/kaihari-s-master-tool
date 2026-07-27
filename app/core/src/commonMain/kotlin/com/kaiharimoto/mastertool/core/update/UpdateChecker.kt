package com.kaiharimoto.mastertool.core.update

/** Result of asking GitHub whether a newer build exists. */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus

    data class Available(val release: Release) : UpdateStatus

    /**
     * A newer release exists but carries no APK — a source-only or
     * still-uploading release. Worth telling the user rather than claiming they
     * are up to date.
     */
    data class AvailableWithoutApk(val release: Release) : UpdateStatus

    data class Failed(val message: String) : UpdateStatus
}

/**
 * Decides whether the running build should be replaced.
 *
 * Pre-releases are ignored unless the running build is itself a pre-release, so
 * a test build published to the repository never pushes itself onto a stable
 * install.
 */
class UpdateChecker(
    private val api: GitHubReleaseApi,
    private val currentVersionName: String,
) {

    suspend fun check(): UpdateStatus {
        val result = api.latestRelease()

        val release = result.getOrElse { error ->
            return UpdateStatus.Failed(
                error.message ?: "Could not reach GitHub to check for updates."
            )
        } ?: return UpdateStatus.UpToDate

        if (release.isPreRelease && !AppVersion.parse(currentVersionName).isPreRelease) {
            return UpdateStatus.UpToDate
        }

        if (!AppVersion.isNewer(currentVersionName, release.versionName)) {
            return UpdateStatus.UpToDate
        }

        return if (release.hasApk) {
            UpdateStatus.Available(release)
        } else {
            UpdateStatus.AvailableWithoutApk(release)
        }
    }
}
