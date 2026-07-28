package com.kaiharimoto.mastertool.core.util

/**
 * How long ago something happened, in words.
 *
 * A timestamp is a fact and "3 days ago" is an answer, and the question anybody
 * asks of a saved deck is the second one. Deliberately coarse: nobody wants the
 * library to say "2 hours and 14 minutes ago", they want to know whether this is
 * the list they were working on this morning.
 *
 * No calendar, no time zone, no formatter. Elapsed milliseconds are all this
 * needs and all it takes, which is why it can be tested exhaustively without a
 * clock — and why "yesterday" here means *about a day ago* rather than
 * yesterday's date. That is a real simplification and it is the right one: a
 * deck saved at 00:30 was not "yesterday" at 01:00 in any sense a person means.
 */
object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY

    fun since(now: Long, then: Long): String {
        val elapsed = now - then

        // A clock that went backwards, or a row written by a machine whose clock
        // is ahead. "In 3 hours" would be true and useless; the honest thing is
        // to say the only part that is certainly right.
        if (elapsed < 0) return "just now"

        return when {
            elapsed < MINUTE -> "just now"
            elapsed < HOUR -> plural(elapsed / MINUTE, "minute")
            elapsed < DAY -> plural(elapsed / HOUR, "hour")
            elapsed < 2 * DAY -> "yesterday"
            elapsed < WEEK -> plural(elapsed / DAY, "day")
            elapsed < 4 * WEEK -> plural(elapsed / WEEK, "week")
            else -> "a while ago"
        }
    }

    private fun plural(count: Long, unit: String): String =
        if (count == 1L) "1 $unit ago" else "$count ${unit}s ago"
}
