package com.kaiharimoto.mastertool.core.update

import com.kaiharimoto.mastertool.core.remote.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    private fun apiReturning(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        GitHubReleaseApi(
            HttpClientFactory.create(
                MockEngine {
                    respond(
                        content = ByteReadChannel(body),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            )
        )

    private fun releaseJson(
        tag: String = "v1.1.0",
        prerelease: Boolean = false,
        assets: String = """[{"name":"kai-master-tool-1.1.0.apk",
             "browser_download_url":"https://example.test/app.apk","size":2048}]""",
    ) = """
        {"tag_name":"$tag","name":"$tag","body":"Fixed things.","draft":false,
         "prerelease":$prerelease,"html_url":"https://example.test/release",
         "assets":$assets}
    """.trimIndent()

    @Test
    fun offersANewerRelease() = runTest {
        val status = UpdateChecker(apiReturning(releaseJson()), "1.0.0").check()

        val available = assertIs<UpdateStatus.Available>(status)
        assertEquals("1.1.0", available.release.versionName)
        assertEquals("https://example.test/app.apk", available.release.apkUrl)
        assertEquals(2048L, available.release.apkSizeBytes)
        assertEquals("Fixed things.", available.release.notes)
    }

    @Test
    fun sameVersionIsUpToDate() = runTest {
        val status = UpdateChecker(apiReturning(releaseJson(tag = "v1.0.0")), "1.0.0").check()
        assertIs<UpdateStatus.UpToDate>(status)
    }

    @Test
    fun olderPublishedReleaseIsUpToDate() = runTest {
        val status = UpdateChecker(apiReturning(releaseJson(tag = "v0.9.0")), "1.0.0").check()
        assertIs<UpdateStatus.UpToDate>(status)
    }

    @Test
    fun preReleasesAreIgnoredByStableBuilds() = runTest {
        val json = releaseJson(tag = "v2.0.0-beta.1", prerelease = true)
        assertIs<UpdateStatus.UpToDate>(UpdateChecker(apiReturning(json), "1.0.0").check())
    }

    @Test
    fun preReleasesAreOfferedToPreReleaseBuilds() = runTest {
        val json = releaseJson(tag = "v2.0.0-beta.2", prerelease = true)
        val status = UpdateChecker(apiReturning(json), "2.0.0-beta.1").check()
        assertIs<UpdateStatus.Available>(status)
    }

    @Test
    fun releaseWithoutAnApkIsReportedSeparately() = runTest {
        val json = releaseJson(assets = "[]")
        val status = UpdateChecker(apiReturning(json), "1.0.0").check()

        val noApk = assertIs<UpdateStatus.AvailableWithoutApk>(status)
        assertEquals("https://example.test/release", noApk.release.htmlUrl)
        assertNull(noApk.release.apkUrl)
    }

    @Test
    fun ignoresNonApkAssets() = runTest {
        val assets = """[{"name":"sources.zip","browser_download_url":"https://x/s.zip","size":1},
                         {"name":"app-release.APK","browser_download_url":"https://x/a.apk","size":9}]"""
        val status = UpdateChecker(apiReturning(releaseJson(assets = assets)), "1.0.0").check()

        val available = assertIs<UpdateStatus.Available>(status)
        assertEquals("https://x/a.apk", available.release.apkUrl)
    }

    @Test
    fun noReleasesYetIsUpToDateNotAnError() = runTest {
        val api = GitHubReleaseApi(
            HttpClientFactory.create(MockEngine { respondError(HttpStatusCode.NotFound) })
        )
        assertIs<UpdateStatus.UpToDate>(UpdateChecker(api, "1.0.0").check())
    }

    @Test
    fun networkFailureIsReportedNotSwallowed() = runTest {
        val api = GitHubReleaseApi(
            HttpClientFactory.create(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })
        )
        val status = UpdateChecker(api, "1.0.0").check()
        assertIs<UpdateStatus.Failed>(status)
        assertTrue(status.message.isNotBlank())
    }

    @Test
    fun malformedResponseIsReportedAsFailure() = runTest {
        val status = UpdateChecker(apiReturning("{not json"), "1.0.0").check()
        assertIs<UpdateStatus.Failed>(status)
    }

    @Test
    fun rateLimitIsReportedAsFailureNotAsUpToDate() = runTest {
        val api = GitHubReleaseApi(
            HttpClientFactory.create(MockEngine { respondError(HttpStatusCode.Forbidden) })
        )
        assertIs<UpdateStatus.Failed>(UpdateChecker(api, "1.0.0").check())
    }
}
