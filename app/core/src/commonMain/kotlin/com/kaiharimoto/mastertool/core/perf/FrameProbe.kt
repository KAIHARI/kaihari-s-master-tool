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
 * - **Report a hole as health.** See [pauseMillis] and [staleMillis]. An
 *   interval of seconds is not a frame and folding it in as one would poison
 *   every number for the window that follows — but silently dropping it and
 *   carrying on printing the timings from before it is worse, because that is
 *   the reading a hang produces and it looks perfect. The gap is kept out of
 *   the statistics and put on the line instead.
 *
 * Reads are computed rather than cached. [worstMillis] is a pass over the
 * window (256 floats), [p95Millis] is a walk up the histogram plus a call to
 * [worstMillis] to clamp itself, and [readout] calls [worstMillis] again — so
 * one line costs two passes over the ring and a few hundred int reads. That is
 * still a microsecond or two against a budget of eight thousand, and it keeps
 * [sample] — which runs every frame whether or not anyone is looking — down to
 * a handful of array writes.
 *
 * @param refreshHz the panel's refresh rate. The default is the tablet this app
 *   is built for; 16.67ms is a comfortable frame on a phone and a missed one
 *   here, so the budget is derived rather than assumed. A rate that is not a
 *   rate — zero, negative, or the NaN that a platform API returns when it does
 *   not know — falls back on the default rather than propagating into the
 *   arithmetic, where it would silently switch the miss count off.
 * @param pauseMillis a gap longer than this is not a frame at all; it is a
 *   hole, and [staleMillis] measures it.
 */
class FrameProbe(
    refreshHz: Float = DEFAULT_HZ,
    pauseMillis: Float = PAUSE_MILLIS,
) {

    /** The rate the budget is derived from — the one given, if it was usable. */
    val refreshHz: Float = usable(refreshHz, DEFAULT_HZ)

    /** See the constructor parameter; nonsense here would disable the pause rule. */
    val pauseMillis: Float = usable(pauseMillis, PAUSE_MILLIS)

    /** What one frame has to fit into: 8.33ms at 120Hz, 16.67 at 60. */
    val budgetMillis: Float = 1000f / this.refreshHz

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

    /**
     * Consecutive gaps thrown away since the last frame that landed — this
     * stall's worth, where [pauses] is the session's.
     *
     * With [staleMillis] this is what tells a backgrounded app from a hung one,
     * which duration alone cannot: twenty-four seconds across one dropped gap
     * is an app that was not running, and twenty-four seconds across forty of
     * them is a frame loop that *is* running, at 1.7fps, which is the failure
     * this probe exists to catch.
     */
    var stalePauses: Int = 0
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

    /** When the newest frame *in the window* arrived. See [staleMillis]. */
    private var recordedNanos = 0L

    /** Whether [lastNanos] means anything yet — the first frame has no interval. */
    private var primed = false

    /**
     * How old the window is: the time between the last frame that landed in it
     * and the last timestamp the probe was handed at all.
     *
     * Zero on a stage that is drawing, and the whole point of the field when it
     * is not. Everything else here answers for the frames in the window, and
     * during a stall those frames are from before the stall — a reading of a
     * moment that has passed, which without this cannot be told from a reading
     * of now.
     *
     * It can only ever count time the probe was told about. A loop that stops
     * calling [sample] entirely stops moving this too, but that is a loop that
     * has also stopped drawing the overlay, so there is no reading to mislead.
     */
    val staleMillis: Float
        get() = (lastNanos - recordedNanos).toFloat() / NANOS_PER_MILLI

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
            recordedNanos = frameNanos
            return
        }

        val elapsed = frameNanos - lastNanos
        if (elapsed <= 0L) {
            // A timestamp that repeats or steps backwards is not a frame, and
            // it is not an origin either: the last one that *was* a frame stays,
            // so the next real timestamp yields the interval it actually took.
            // Adopting the bad one instead would add the size of the backward
            // step to that frame and count it as a miss. A clock that jumps back
            // and stays back would leave the probe ignoring frames until real
            // time catches up, but `withFrameNanos` is monotonic — a step back
            // is a repeat or a test, and the repeat is what happens hourly.
            return
        }
        lastNanos = frameNanos

        val millis = elapsed.toFloat() / NANOS_PER_MILLI
        if (millis > pauseMillis) {
            // Backgrounded, stopped in a debugger, blocked on a load — or hung.
            // Nothing rendered a 1400ms frame, and reporting one poisons the
            // mean, the worst and the percentile for the next two seconds. So it
            // stays out of the statistics; but it does not vanish, because a
            // discarded gap and a healthy window are indistinguishable and the
            // second reading is a lie. [staleMillis] and [stalePauses] carry it,
            // and [readout] leads with them. A 200ms hitch is under this and is
            // kept: that one is real jank, and showing it is the point.
            pauses++
            stalePauses++
            return
        }

        record(millis)
        recordedNanos = frameNanos
        stalePauses = 0
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
        stalePauses = 0
        lastNanos = 0L
        recordedNanos = 0L
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
     * Answered from the histogram, counting up from the bottom until the rank
     * is reached. Up rather than down because the answer sits near the bottom
     * on every window anyone spends time looking at — bucket 66 of 624 on a
     * healthy stage — so the walk is shortest exactly when the overlay is being
     * read most. Reported at the bucket's midpoint, and clamped to
     * [worstMillis]: a p95 printed above the worst frame on the same line reads
     * as a broken readout whatever the arithmetic says.
     */
    val p95Millis: Float
        get() {
            if (samples == 0) return 0f

            // Nearest rank: the ceil(0.95n)-th smallest frame in the window.
            val rank = (samples * PERCENTILE + 99) / 100

            var counted = 0
            var i = 0
            while (i < BUCKETS) {
                counted += histogram[i]
                if (counted >= rank) break
                i++
            }

            val worst = worstMillis
            // Only reachable when the tail is past the histogram's ceiling,
            // which takes a [pauseMillis] set above it — the buckets otherwise
            // cover every interval that can be recorded at all. There is no
            // bucket edge left to report, so the frames themselves are the
            // honest answer.
            if (i >= BUCKETS) return worst
            val midpoint = midpointOf(i)
            return if (midpoint < worst) midpoint else worst
        }

    /**
     * The whole readout as one line: `8.1ms  p95 11.4  worst 24.9  miss 2  moving 7`,
     * and during a stall `stalled 24.0s  gaps 40  8.3ms  p95 8.3  ...`.
     *
     * "gaps" and not "pauses", even though [stalePauses] is what it prints,
     * because there is a public [pauses] on this class holding a *different*
     * number — the session's rather than this stall's. A line and a field that
     * share a word and disagree about the value is the kind of thing somebody
     * reads once, believes, and debugs for an afternoon.
     *
     * Here rather than in the overlay so there is no formatting for the UI layer
     * to get wrong, and so the wording of a number cannot drift from what the
     * number means. "miss" and not "drops" deliberately: these are frames that
     * came in over budget, which is not the same claim as a frame the
     * compositor never received.
     *
     * The stall leads rather than trails because it is a qualifier on
     * everything after it — those timings are [staleMillis] old — and a reader
     * who has already reached "8.3ms" has stopped reading. Its count is this
     * stall's ([stalePauses]), not the session's, so the line only ever
     * describes the hole it is inside.
     *
     * The one place in this file that allocates — a string and the builder
     * behind it. That is the tradeoff for the UI having no arithmetic: it is
     * paid once a frame and only while the overlay is on screen, which is
     * orders of magnitude below the cost of the recomposition that draws it,
     * and [sample] — which runs always — stays clean. If it ever does show up,
     * the fields are all public and can be drawn one at a time.
     */
    fun readout(): String {
        val line = "${oneDecimal(lastMillis)}ms  p95 ${oneDecimal(p95Millis)}" +
            "  worst ${oneDecimal(worstMillis)}  miss $missedFrames  moving $movingObjects"
        val stale = staleMillis
        if (stale <= 0f) return line
        return "stalled ${oneDecimal(stale / 1000f)}s  gaps $stalePauses  $line"
    }

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
        if (millis < FINE_CEILING) return (millis / FINE_MILLIS).toInt()
        val i = FINE_BUCKETS + ((millis - FINE_CEILING) / COARSE_MILLIS).toInt()
        return if (i >= BUCKETS) BUCKETS else i
    }

    private fun midpointOf(bucket: Int): Float =
        if (bucket < FINE_BUCKETS) {
            (bucket + 0.5f) * FINE_MILLIS
        } else {
            FINE_CEILING + (bucket - FINE_BUCKETS + 0.5f) * COARSE_MILLIS
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

        /** The tablet this app is built for, and the fallback for a bad rate. */
        private const val DEFAULT_HZ = 120f

        /** See [FrameProbe.pauseMillis]. Half a second is not a frame; it is a stop. */
        private const val PAUSE_MILLIS = 500f

        private const val PERCENTILE = 95

        /**
         * The histogram, in two resolutions.
         *
         * An eighth of a millisecond up to 64ms, where the quantisation
         * disappears at the one decimal place the readout prints; then four
         * milliseconds up to 512, which is past any interval a pause rule set
         * anywhere sane can let through. Coarse up there because a window whose
         * tail is at 70ms has already answered the question, and whether it was
         * 70 or 72 changes nothing anyone would do about it.
         *
         * The second tier is what stops the percentile collapsing onto the
         * worst frame. A single catch-all swallowed the whole tail, so five per
         * cent of a window past the ceiling made p95 print the one worst frame
         * in the window — an eightfold overstatement of a stage that was ninety
         * per cent healthy, on the reading someone would act on.
         */
        private const val FINE_MILLIS = 0.125f
        private const val FINE_BUCKETS = 512
        private const val FINE_CEILING = FINE_BUCKETS * FINE_MILLIS
        private const val COARSE_MILLIS = 4f
        private const val COARSE_BUCKETS = 112
        private const val BUCKETS = FINE_BUCKETS + COARSE_BUCKETS

        /** A rate or a threshold that is not a number is not a setting. */
        private fun usable(value: Float, fallback: Float): Float =
            if (value.isFinite() && value > 0f) value else fallback
    }
}
