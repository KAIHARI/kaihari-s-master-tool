package com.kaiharimoto.mastertool.core.perf

import kotlin.math.roundToInt

/**
 * The frame clock, measured — so that claims about performance can be checked
 * on the device instead of argued about.
 *
 * A stage that costs nothing at rest is easy to believe in and impossible to
 * verify; the moment a movable camera and a solver are running on it, "it feels
 * fine" is the only evidence anyone has. This is the instrument that replaces
 * that. The mat's single `withFrameNanos` loop hands it one [sample] per frame
 * and a draw lambda reads the fields back, so the numbers on screen are the
 * timings of the very frames being drawn.
 *
 * Three things it must never do, because an instrument that changes what it
 * measures is worse than none at all:
 *
 * - **Allocate in [sample].** The ring and the histogram are allocated once,
 *   here, and every frame afterwards writes into them. No lists, no boxing, no
 *   lambdas on the hot path — a probe that produced garbage would show up as
 *   the jank it was invented to find.
 * - **Live in snapshot state.** These are plain fields. Writing a Compose
 *   `mutableStateOf` sixty or a hundred and twenty times a second invalidates
 *   whatever read it, which is a recomposition per frame bought with nothing.
 *   The overlay reads these inside its draw, the way `StageCard` reads pose
 *   inside `graphicsLayer`.
 * - **Believe every gap.** See [pauseMillis]. A backgrounded app comes back
 *   with a gap of seconds, and folding that in as one enormous frame makes
 *   every number useless for the whole window that follows.
 *
 * Reads are computed rather than cached, and cost one pass over the window
 * (256 floats) plus, for the percentile, a walk down a histogram. That is a
 * microsecond or two against a budget of eight thousand, and it keeps [sample]
 * — which runs every frame whether or not anyone is looking — down to a
 * handful of array writes.
 *
 * @param refreshHz the panel's refresh rate. The default is the tablet this app
 *   is built for; 16.67ms is a comfortable frame on a phone and a missed one
 *   here, so the budget is derived rather than assumed.
 * @param pauseMillis a gap longer than this is not a frame at all.
 */
class FrameProbe(
    val refreshHz: Float = 120f,
    val pauseMillis: Float = PAUSE_MILLIS,
) {

    /** What one frame has to fit into: 8.33ms at 120Hz, 16.67 at 60. */
    val budgetMillis: Float = 1000f / refreshHz.coerceAtLeast(1f)

    /**
     * Over this, the frame missed.
     *
     * Not the budget exactly: frame timestamps wobble by tens of microseconds
     * on frames that comfortably made their deadline, and counting the wobble
     * would leave the drop count reading a few hundred on a window where
     * nothing whatsoever went wrong.
     */
    val missMillis: Float = budgetMillis * MISS_SLACK

    /** How many frames the window holds — about two seconds of them at 120Hz. */
    val capacity: Int get() = CAPACITY

    /** How many frames are actually in the window; below [capacity] until it fills. */
    var samples: Int = 0
        private set

    /** The interval that ended the most recent real frame. */
    var lastMillis: Float = 0f
        private set

    /** What the last frame said it was moving — the load the timings belong to. */
    var movingObjects: Int = 0
        private set

    /** Frames in the window that came in over [missMillis]. */
    var missedFrames: Int = 0
        private set

    /** Gaps thrown away as pauses. Lifetime, not windowed: it explains a hole. */
    var pauses: Int = 0
        private set

    private val ring = FloatArray(CAPACITY)

    /**
     * Where the window's frames fall, so a percentile costs a walk rather than
     * a sort. Buckets are added and removed as frames arrive and age out, which
     * is what makes it possible to answer at all without touching the samples.
     */
    private val histogram = IntArray(BUCKETS + 1)

    private var cursor = 0

    /**
     * The window's total, kept as it goes rather than re-added on every read.
     * Double, so that a day of adding and subtracting the same values does not
     * let the mean drift away from the frames it is made of.
     */
    private var sum = 0.0

    private var lastNanos = 0L

    /** Whether [lastNanos] means anything yet — the first frame has no interval. */
    private var primed = false

    /**
     * One frame. The caller passes the timestamp it was handed and how many
     * things are in motion; the interval is derived here, so no two call sites
     * can disagree about what a frame's duration is.
     */
    fun sample(frameNanos: Long, movingObjects: Int) {
        this.movingObjects = movingObjects

        if (!primed) {
            // The first timestamp is an origin, not a duration. Recording it as
            // one would put a frame of "however long the app has been running"
            // into the window.
            primed = true
            lastNanos = frameNanos
            return
        }

        val elapsed = frameNanos - lastNanos
        // Adopted whichever direction it came from: a clock that jumped
        // backwards is nonsense, but refusing to move the origin would leave the
        // probe measuring from a timestamp in the future forever after.
        lastNanos = frameNanos
        if (elapsed <= 0L) return

        val millis = elapsed.toFloat() / NANOS_PER_MILLI
        if (millis > pauseMillis) {
            // Backgrounded, stopped in a debugger, or blocked on a load. Nothing
            // rendered a 1400ms frame, and reporting one poisons the mean, the
            // worst and the percentile for the next two seconds — precisely the
            // two seconds someone is watching when they come back to the app.
            // A 200ms hitch is under this and is kept: that one is real jank,
            // and showing it is the point.
            pauses++
            return
        }

        record(millis)
    }

    /** Forget everything, e.g. on leaving the stage. Not on the hot path. */
    fun reset() {
        ring.fill(0f)
        histogram.fill(0)
        cursor = 0
        sum = 0.0
        samples = 0
        lastMillis = 0f
        movingObjects = 0
        missedFrames = 0
        pauses = 0
        lastNanos = 0L
        primed = false
    }

    /** The window's average frame. */
    val meanMillis: Float
        get() = if (samples == 0) 0f else (sum / samples).toFloat()

    /** The worst frame still in the window, exactly — this one is not quantised. */
    val worstMillis: Float
        get() {
            var worst = 0f
            for (i in 0 until samples) {
                val v = ring[i]
                if (v > worst) worst = v
            }
            return worst
        }

    /**
     * The 95th percentile frame, by nearest rank.
     *
     * P95 rather than P99 because the window holds 256 frames: a 99th
     * percentile is two and a half samples of it, which moves by whole
     * milliseconds when a single frame hiccups, and a number that jumps around
     * gets ignored. P95 rests on thirteen and still shows a hitch immediately —
     * one bad frame in twenty is exactly the rate at which stutter becomes
     * visible.
     *
     * Answered from the histogram, walking down from the top: the tail is only
     * ever a twentieth of the window, so the walk stops within a few buckets of
     * where the bad frames are. Reported at the bucket's midpoint, so it is
     * accurate to an eighth of a millisecond either way, and clamped to
     * [worstMillis] — a p95 printed above the worst frame on the same line
     * reads as a broken readout whatever the arithmetic says.
     */
    val p95Millis: Float
        get() {
            if (samples == 0) return 0f

            // Nearest rank: the ceil(0.95n)-th smallest frame, which is the
            // same as the (n - rank + 1)-th largest counting down.
            val rank = (samples * PERCENTILE + 99) / 100
            var remaining = samples - rank + 1

            var i = BUCKETS
            while (i > 0) {
                remaining -= histogram[i]
                if (remaining <= 0) break
                i--
            }

            val worst = worstMillis
            // The catch-all bucket has no upper edge to report, and a window
            // that spends its tail past the ceiling has nothing to say but the
            // truth: the frames themselves.
            if (i >= BUCKETS) return worst
            val midpoint = (i + 0.5f) * BUCKET_MILLIS
            return if (midpoint < worst) midpoint else worst
        }

    /**
     * The whole readout as one line: `8.1ms  p95 11.4  worst 24.9  miss 2  moving 7`.
     *
     * Here rather than in the overlay so there is no formatting for the UI layer
     * to get wrong, and so the wording of a number cannot drift from what the
     * number means. "miss" and not "drops" deliberately: these are frames that
     * came in over budget, which is not the same claim as a frame the
     * compositor never received.
     *
     * The one place in this file that allocates — a string and the builder
     * behind it. That is the tradeoff for the UI having no arithmetic: it is
     * paid once a frame and only while the overlay is on screen, which is
     * orders of magnitude below the cost of the recomposition that draws it,
     * and [sample] — which runs always — stays clean. If it ever does show up,
     * the fields are all public and can be drawn one at a time.
     */
    fun readout(): String =
        "${oneDecimal(lastMillis)}ms  p95 ${oneDecimal(p95Millis)}" +
            "  worst ${oneDecimal(worstMillis)}  miss $missedFrames  moving $movingObjects"

    private fun record(millis: Float) {
        if (samples == CAPACITY) {
            // The frame leaving the window has to be taken out of every number
            // it was counted in, or a hitch outlives the two seconds it
            // happened in and the readout never recovers.
            val leaving = ring[cursor]
            sum -= leaving
            histogram[bucketOf(leaving)]--
            if (leaving > missMillis) missedFrames--
        } else {
            samples++
        }

        ring[cursor] = millis
        sum += millis
        histogram[bucketOf(millis)]++
        if (millis > missMillis) missedFrames++

        cursor = (cursor + 1) and MASK
        lastMillis = millis
    }

    private fun bucketOf(millis: Float): Int {
        val i = (millis / BUCKET_MILLIS).toInt()
        return if (i >= BUCKETS) BUCKETS else i
    }

    /** One decimal place, without a formatter — common Kotlin has none. */
    private fun oneDecimal(value: Float): String {
        if (!value.isFinite() || value <= 0f) return "0.0"
        val tenths = (value * 10f).roundToInt()
        return "${tenths / 10}.${tenths % 10}"
    }

    companion object {
        /**
         * 256 frames: 2.1 seconds at 120Hz.
         *
         * Long enough that a hitch stays in the numbers while someone looks up
         * from the tablet to read them, short enough that the readout describes
         * what the stage is doing now rather than what it did a moment ago. A
         * power of two so the ring wraps with an `and` — no modulo, no branch,
         * on the one path that runs every single frame.
         */
        private const val CAPACITY = 256
        private const val MASK = CAPACITY - 1

        private const val NANOS_PER_MILLI = 1_000_000f

        /** See [FrameProbe.missMillis]. */
        private const val MISS_SLACK = 1.05f

        /** See [FrameProbe.pauseMillis]. Half a second is not a frame; it is a stop. */
        private const val PAUSE_MILLIS = 500f

        private const val PERCENTILE = 95

        /**
         * The histogram: an eighth of a millisecond per bucket, up to 64ms, plus
         * one catch-all above that. Fine enough that the quantisation disappears
         * at the one decimal place the readout prints, coarse enough that the
         * whole thing is two kilobytes and a walk down it is free.
         */
        private const val BUCKET_MILLIS = 0.125f
        private const val BUCKETS = 512
    }
}
