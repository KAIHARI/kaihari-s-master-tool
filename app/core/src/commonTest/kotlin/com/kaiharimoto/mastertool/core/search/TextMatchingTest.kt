package com.kaiharimoto.mastertool.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextMatchingTest {

    @Test
    fun normalizeStripsPunctuationAndCase() {
        assertEquals("ash blossom joyous spring", TextMatching.normalize("Ash Blossom & Joyous Spring"))
        assertEquals("nibiru the primal being", TextMatching.normalize("Nibiru, the Primal Being"))
        assertEquals("maxx c", TextMatching.normalize("Maxx \"C\""))
        assertEquals("number 39 utopia", TextMatching.normalize("Number 39: Utopia"))
    }

    @Test
    fun normalizeCollapsesRunsAndTrims() {
        assertEquals("a b", TextMatching.normalize("  a --- b  "))
        assertEquals("", TextMatching.normalize("!!!"))
    }

    @Test
    fun editDistanceIdentical() {
        assertEquals(0, TextMatching.editDistanceAtMost("abc", "abc", 2))
    }

    @Test
    fun editDistanceSingleSubstitution() {
        assertEquals(1, TextMatching.editDistanceAtMost("nibiru", "nibiro", 2))
    }

    @Test
    fun editDistanceSingleDeletion() {
        assertEquals(1, TextMatching.editDistanceAtMost("talker", "taker", 2))
    }

    @Test
    fun editDistanceSingleInsertion() {
        assertEquals(1, TextMatching.editDistanceAtMost("taker", "talker", 2))
    }

    @Test
    fun editDistanceDeletionInLongerStrings() {
        assertEquals(
            1,
            TextMatching.editDistanceAtMost("accesscode talker", "accesscode taker", 2),
        )
    }

    @Test
    fun editDistanceTwoEdits() {
        assertEquals(1, TextMatching.editDistanceAtMost("kitten", "kitte", 2))
        // A transposition costs two substitutions under plain Levenshtein.
        assertEquals(2, TextMatching.editDistanceAtMost("abcdef", "abdcef", 2))
    }

    /** Full-matrix Levenshtein, used only to check the banded implementation. */
    private fun referenceDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            }
        }
        return d[a.length][b.length]
    }

    @Test
    fun bandedDistanceAgreesWithFullMatrix() {
        val words = listOf(
            "", "a", "ash", "nibiru", "nibiro", "talker", "taker", "accesscode",
            "accesscode talker", "accesscode taker", "kitten", "sitting", "maxx c",
            "dark magician", "dark magician girl", "abcdef", "abdcef", "zzzzzz",
            "pot of prosperity", "pot of desires",
        )
        for (a in words) {
            for (b in words) {
                for (max in 0..3) {
                    val expected = referenceDistance(a, b)
                    val actual = TextMatching.editDistanceAtMost(a, b, max)
                    if (expected <= max) {
                        assertEquals(expected, actual, "d('$a','$b') bounded by $max")
                    } else {
                        assertTrue(actual > max, "d('$a','$b') should exceed $max, got $actual")
                    }
                }
            }
        }
    }

    @Test
    fun editDistanceExceedingBoundIsReportedAsOverBound() {
        assertTrue(TextMatching.editDistanceAtMost("abcdef", "zzzzzz", 2) > 2)
        assertTrue(TextMatching.editDistanceAtMost("short", "muchlongerstring", 2) > 2)
    }

    @Test
    fun editDistanceHandlesEmptyInputs() {
        assertEquals(3, TextMatching.editDistanceAtMost("", "abc", 5))
        assertEquals(3, TextMatching.editDistanceAtMost("abc", "", 5))
        assertEquals(0, TextMatching.editDistanceAtMost("", "", 2))
    }

    @Test
    fun editDistanceIsSymmetric() {
        val pairs = listOf(
            "accesscode talker" to "accesscode taker",
            "nibiru" to "nibiro",
            "ash blossom" to "ash blosom",
            "talker" to "taker",
        )
        pairs.forEach { (a, b) ->
            assertEquals(
                TextMatching.editDistanceAtMost(a, b, 2),
                TextMatching.editDistanceAtMost(b, a, 2),
                "distance should be symmetric for '$a' / '$b'",
            )
        }
    }

    @Test
    fun scoreRanksExactAboveEverythingElse() {
        val exact = TextMatching.score("dark magician", "dark magician", listOf("dark", "magician"))
        val prefix = TextMatching.score("dark magician girl", "dark magician", listOf("dark", "magician"))
        assertNotNull(exact)
        assertNotNull(prefix)
        assertTrue(exact > prefix)
    }

    @Test
    fun scoreMatchesInteriorWordPrefix() {
        assertNotNull(TextMatching.score("nibiru the primal being", "primal", listOf("primal")))
    }

    @Test
    fun scoreMatchesTokensOutOfOrder() {
        assertNotNull(
            TextMatching.score("nibiru the primal being", "primal nibiru", listOf("primal", "nibiru")),
        )
    }

    @Test
    fun scoreToleratesTypo() {
        assertNotNull(TextMatching.score("nibiru the primal being", "nibiro", listOf("nibiro")))
        assertNotNull(
            TextMatching.score("accesscode talker", "accesscode taker", listOf("accesscode", "taker")),
        )
    }

    @Test
    fun scoreRejectsUnrelatedText() {
        assertNull(TextMatching.score("dark magician", "zzzzqqq", listOf("zzzzqqq")))
    }

    @Test
    fun scoreDoesNotFuzzyMatchVeryShortQueries() {
        // "ash" must not fuzzily match "ace"; short queries are too ambiguous.
        assertNull(TextMatching.score("ace of wands", "ash", listOf("ash")))
    }

    @Test
    fun emptyQueryScoresNeutral() {
        assertEquals(0, TextMatching.score("anything", "", emptyList()))
    }
}
