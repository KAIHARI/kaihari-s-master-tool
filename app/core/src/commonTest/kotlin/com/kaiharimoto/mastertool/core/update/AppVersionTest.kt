package com.kaiharimoto.mastertool.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun parsesPlainAndPrefixedVersions() {
        assertEquals(listOf(1, 2, 3), AppVersion.parse("1.2.3").numbers)
        assertEquals(listOf(1, 2, 3), AppVersion.parse("v1.2.3").numbers)
        assertEquals(listOf(1, 2, 3), AppVersion.parse("  V1.2.3  ").numbers)
    }

    @Test
    fun parsesPreReleaseSuffix() {
        val version = AppVersion.parse("1.2.3-beta.1")
        assertEquals(listOf(1, 2, 3), version.numbers)
        assertEquals("beta.1", version.preRelease)
        assertTrue(version.isPreRelease)
    }

    @Test
    fun missingComponentsCountAsZero() {
        assertEquals(0, AppVersion.parse("1.2").compareTo(AppVersion.parse("1.2.0")))
        assertEquals(0, AppVersion.parse("1").compareTo(AppVersion.parse("1.0.0")))
    }

    @Test
    fun comparesNumericallyNotAlphabetically() {
        // The classic bug: "1.10.0" sorts before "1.9.0" as text.
        assertTrue(AppVersion.isNewer("1.9.0", "1.10.0"))
        assertFalse(AppVersion.isNewer("1.10.0", "1.9.0"))
        assertTrue(AppVersion.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun detectsNewerVersions() {
        assertTrue(AppVersion.isNewer("1.0.0", "1.0.1"))
        assertTrue(AppVersion.isNewer("1.0.0", "1.1.0"))
        assertTrue(AppVersion.isNewer("1.0.0", "2.0.0"))
        assertTrue(AppVersion.isNewer("1.0.0", "v1.0.1"))
    }

    @Test
    fun identicalVersionsAreNotNewer() {
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "v1.0.0"))
        assertFalse(AppVersion.isNewer("1.2", "1.2.0"))
    }

    @Test
    fun olderVersionsAreNotNewer() {
        assertFalse(AppVersion.isNewer("2.0.0", "1.9.9"))
        assertFalse(AppVersion.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun preReleasePrecedesTheFinalRelease() {
        assertTrue(AppVersion.isNewer("1.0.0-beta.1", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0-beta.1"))
    }

    @Test
    fun unparseableVersionsNeverCountAsAnUpdate() {
        // Refusing to update beats offering a bogus one.
        assertFalse(AppVersion.isNewer("1.0.0", "not-a-version"))
        assertFalse(AppVersion.isNewer("not-a-version", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", ""))
        assertFalse(AppVersion.isNewer("", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "v"))
    }

    @Test
    fun toStringKeepsTheOriginalText() {
        assertEquals("v1.2.3", AppVersion.parse("v1.2.3").toString())
    }

    /**
     * A 3DS release tag must never look like an Android version.
     *
     * The two tracks publish into one repository — `v1.2.3` for the APK,
     * `3ds-v1.0.0` for the CIA — and GitHub's `/releases/latest` returns the
     * most recent non-prerelease release whatever its tag is shaped like. So
     * the Android updater *will* be handed 3DS tags, and the only place that
     * can be sorted out is here.
     *
     * It was not sorted out here. `takeWhile(Char::isDigit)` read "3ds" as 3,
     * making every 3DS release major version 3: newer than any APK this app has
     * shipped, carrying no APK to install, and shadowing the real Android
     * release that came before it. The user would have been told an update
     * existed and then told it had nothing to download, permanently.
     */
    @Test
    fun `a 3DS release tag is not an app version`() {
        assertEquals(AppVersion.UNKNOWN, AppVersion.parse("3ds-v1.0.0"))
        assertEquals(AppVersion.UNKNOWN, AppVersion.parse("3ds-v2.11.4"))
        assertFalse(AppVersion.isNewer("1.2.3", "3ds-v1.0.0"))
        assertFalse(AppVersion.isNewer("1.2.3", "3ds-v9.9.9"))
    }

    @Test
    fun `a segment must be entirely digits`() {
        assertEquals(AppVersion.UNKNOWN, AppVersion.parse("nightly-4"))

        // Still lenient where leniency was the point: a trailing oddity ends
        // the number list rather than poisoning it.
        assertEquals(listOf(1, 2), AppVersion.parse("1.2.3rc").numbers)

        // And leniency that survives, stated rather than assumed: a date-shaped
        // tag still reads as a very large version, because "2026" really is all
        // digits. Nothing in this repository tags that way, and tightening it
        // further would start rejecting the `1.2` and `1.2.3-beta.1` forms this
        // parser exists to accept. The case that mattered is the one above.
        assertEquals(listOf(2026), AppVersion.parse("2026-08-20").numbers)
    }
}
