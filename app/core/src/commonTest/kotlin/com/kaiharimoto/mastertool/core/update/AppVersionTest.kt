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
}
