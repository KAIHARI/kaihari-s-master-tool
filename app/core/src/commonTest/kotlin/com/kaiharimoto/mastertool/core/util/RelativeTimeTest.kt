package com.kaiharimoto.mastertool.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelativeTimeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val week = 7 * day

    private fun ago(elapsed: Long) = RelativeTime.since(now = elapsed, then = 0L)

    @Test
    fun theLastMinuteIsJustNow() {
        assertEquals("just now", ago(0))
        assertEquals("just now", ago(minute - 1))
    }

    @Test
    fun minutesAndHoursCountUp() {
        assertEquals("1 minute ago", ago(minute))
        assertEquals("59 minutes ago", ago(hour - 1))
        assertEquals("1 hour ago", ago(hour))
        assertEquals("23 hours ago", ago(day - 1))
    }

    @Test
    fun oneOfAnythingIsSingular() {
        // The kind of thing nobody notices until it says "1 days ago".
        assertEquals("1 minute ago", ago(minute))
        assertEquals("1 hour ago", ago(hour))
        assertEquals("1 week ago", ago(week))
    }

    @Test
    fun aboutADayIsYesterday() {
        assertEquals("yesterday", ago(day))
        assertEquals("yesterday", ago(2 * day - 1))
    }

    @Test
    fun daysAndWeeksAfterThat() {
        assertEquals("2 days ago", ago(2 * day))
        assertEquals("6 days ago", ago(week - 1))
        assertEquals("1 week ago", ago(week))
        assertEquals("3 weeks ago", ago(4 * week - 1))
    }

    @Test
    fun pastAMonthTheNumberStopsHelping() {
        assertEquals("a while ago", ago(4 * week))
        assertEquals("a while ago", ago(400 * day))
    }

    @Test
    fun aClockThatWentBackwardsSaysTheSafeThing() {
        // Two machines, or a device whose clock was corrected. "In 3 hours"
        // would be true and useless.
        assertEquals("just now", RelativeTime.since(now = 0L, then = hour))
    }

    @Test
    fun everyElapsedTimeGetsAnAnswerAndItNeverGoesBackwards() {
        // Walking the whole range at a coarse step: each bucket must produce
        // something, and later must never read as more recent than earlier.
        val order = listOf("just now", "minute", "hour", "yesterday", "day", "week", "a while ago")
        fun rank(text: String) = order.indexOfFirst { text.contains(it) }

        var previous = -1
        generateSequence(0L) { it + hour / 4 }
            .takeWhile { it < 60 * day }
            .forEach { elapsed ->
                val answer = ago(elapsed)
                assertTrue(answer.isNotBlank(), "nothing for $elapsed")
                val position = rank(answer)
                assertTrue(position >= 0, "unexpected phrasing: $answer")
                assertTrue(position >= previous, "went backwards at $elapsed: $answer")
                previous = position
            }
    }
}
