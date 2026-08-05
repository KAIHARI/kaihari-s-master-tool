package com.kaiharimoto.mastertool.core.perf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An instrument nobody has checked is worse than no instrument, because a wrong
 * number is still believed. Every degeneracy below is one that a frame loop on
 * a real tablet produces within a minute of use — the first frame, a frame the
 * choreographer timestamps twice, and the app being backgrounded — and each of
 * them, taken naively, makes the readout say something false at exactly the
 * moment someone is reading it.
 */
class FrameProbeTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    /** Half a histogram bucket: the most a reported percentile can be out by. */
    private val quantisation = 0.07f

    /** One frame of a 120Hz panel, in milliseconds. */
    private val perfect = 1000.0 / 120.0

    /**
     * A frame clock. The probe's first sample is only an origin, so the clock
     * spends one on construction and every `frame` after it records.
     */
    private class Clock(val probe: FrameProbe) {
        var nanos = 1_000_000_000L

        init {
            probe.sample(nanos, 0)
        }

        fun frame(millis: Double, moving: Int = 0) {
            nanos += (millis * 1_000_000.0).toLong()
            probe.sample(nanos, moving)
        }

        fun frames(count: Int, millis: Double, moving: Int = 0) {
            repeat(count) { frame(millis, moving) }
        }
    }

    // ---- the frame that has nothing to measure from ----------------------------

    @Test
    fun theFirstSampleIsAnOriginNotAFrame() {
        val probe = FrameProbe()
        probe.sample(5_000_000_000L, 3)

        assertEquals(0, probe.samples, "the first timestamp became a frame: ${probe.samples}")
        assertClose(0f, probe.lastMillis)
        assertEquals(3, probe.movingObjects)
        // And it still renders: the overlay is drawn from frame one.
        assertTrue(probe.readout().isNotEmpty(), "an empty probe said nothing at all")

        probe.sample(5_008_000_000L, 3)
        assertEquals(1, probe.samples, "the second sample did not land: ${probe.samples}")
        assertClose(8f, probe.lastMillis)
    }

    @Test
    fun anEmptyProbeAnswersZeroRatherThanNothing() {
        val probe = FrameProbe()

        assertClose(0f, probe.meanMillis)
        assertClose(0f, probe.worstMillis)
        assertClose(0f, probe.p95Millis)
        assertEquals(0, probe.missedFrames)
    }

    // ---- the budget is the tablet's, not sixty hertz ---------------------------

    @Test
    fun aFullWindowOfPerfectFramesMissesNothing() {
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(probe.capacity + 40, perfect)

        assertEquals(probe.capacity, probe.samples, "the window overfilled: ${probe.samples}")
        assertEquals(0, probe.missedFrames, "a perfect 120Hz window reported drops: ${probe.missedFrames}")
        assertClose(8.333f, probe.meanMillis)
        assertClose(8.333f, probe.worstMillis)
    }

    @Test
    fun theBudgetComesFromTheRefreshRateNotFromSixtyHertz() {
        // 16.7ms is a comfortable frame on a phone and a missed one on the tablet
        // this app is built for. Hard-coding 16.67 would report a stage running
        // at half its refresh rate as perfectly healthy.
        val tablet = FrameProbe(refreshHz = 120f)
        Clock(tablet).frames(60, 1000.0 / 60.0)
        assertEquals(60, tablet.missedFrames, "60Hz frames passed a 120Hz budget: ${tablet.missedFrames}")

        val phone = FrameProbe(refreshHz = 60f)
        Clock(phone).frames(60, 1000.0 / 60.0)
        assertEquals(0, phone.missedFrames, "60Hz frames missed a 60Hz budget: ${phone.missedFrames}")

        assertClose(8.333f, tablet.budgetMillis)
        assertClose(16.667f, phone.budgetMillis)
    }

    @Test
    fun jitterInsideTheBudgetIsNotAMiss() {
        // Frame timestamps wobble by tens of microseconds on frames that made
        // their deadline. Counting the wobble makes the drop count meaningless.
        val probe = FrameProbe()
        Clock(probe).frames(100, 8.6)

        assertEquals(0, probe.missedFrames, "jitter counted as drops: ${probe.missedFrames}")
    }

    @Test
    fun aWindowOfNothingButDropsCountsThemAll() {
        val probe = FrameProbe()
        Clock(probe).frames(50, 24.0)

        assertEquals(50, probe.missedFrames, "drops went uncounted: ${probe.missedFrames}")
    }

    // ---- a pause is not a frame -------------------------------------------------

    @Test
    fun aPauseIsNotAFourteenHundredMillisecondFrame() {
        // The app goes to the background and comes back. Folding the gap in
        // makes the mean, the worst and the percentile useless for the whole
        // window that follows — which is exactly the two seconds someone is
        // watching when they come back to it.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(200, perfect)

        clock.frame(1400.0)

        assertEquals(1, probe.pauses, "the pause went unnoticed: ${probe.pauses}")
        assertEquals(200, probe.samples, "the pause became a frame: ${probe.samples}")
        assertEquals(0, probe.missedFrames, "the pause was counted as a drop: ${probe.missedFrames}")
        assertClose(8.333f, probe.worstMillis, note = "worst after a pause:")
        assertClose(8.333f, probe.meanMillis, note = "mean after a pause:")
        assertClose(8.333f, probe.lastMillis, note = "last after a pause:")
    }

    @Test
    fun framesAfterAPauseAreMeasuredFromTheResume() {
        // The frame that ends the pause is discarded, not its timestamp — or the
        // frame after it would carry the whole gap instead.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(20, perfect)
        clock.frame(1400.0)
        clock.frames(20, perfect)

        assertEquals(40, probe.samples, "the resumed frames did not land: ${probe.samples}")
        assertClose(8.333f, probe.worstMillis, note = "worst across a resume:")
    }

    @Test
    fun realJankIsKeptEvenThoughItIsBig() {
        // A 200ms hitch is a garbage collection or a load, and it is precisely
        // what the readout exists to show. Only a gap no frame loop could have
        // produced is thrown away.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(20, perfect)
        clock.frame(200.0)

        assertEquals(21, probe.samples, "a real hitch was discarded: ${probe.samples}")
        assertEquals(0, probe.pauses, "a hitch was mistaken for a pause: ${probe.pauses}")
        assertClose(200f, probe.worstMillis, 0.05f, "the hitch:")
    }

    // ---- a clock that misbehaves --------------------------------------------------

    @Test
    fun aRepeatedOrBackwardTimestampIsIgnored() {
        val probe = FrameProbe()
        var t = 1_000_000_000L
        probe.sample(t, 0)
        repeat(10) {
            t += 8_000_000L
            probe.sample(t, 0)
        }
        assertEquals(10, probe.samples)

        probe.sample(t, 0)
        assertEquals(10, probe.samples, "the same frame twice became two: ${probe.samples}")

        probe.sample(t - 5_000_000L, 0)
        assertEquals(10, probe.samples, "a backward clock became a frame: ${probe.samples}")
        assertClose(8f, probe.lastMillis)

        // And it is not wedged afterwards: the last timestamp it was given is
        // the origin for the next frame, whichever direction it came from.
        probe.sample(t + 3_000_000L, 0)
        assertEquals(11, probe.samples, "the probe never recovered: ${probe.samples}")
        assertClose(8f, probe.lastMillis)
    }

    // ---- the window ----------------------------------------------------------------

    @Test
    fun theWindowIsAboutTwoSecondsOfATabletsFrames() {
        // Long enough that a hitch stays on screen to be read, short enough that
        // the readout answers for what is happening now.
        val seconds = FrameProbe().capacity / 120f

        assertTrue(seconds > 1.5f && seconds < 3f, "the window covers ${seconds}s")
    }

    @Test
    fun aWindowThatIsNotFullAnswersForWhatItHas() {
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frame(4.0)
        clock.frame(8.0)
        clock.frame(12.0)

        assertEquals(3, probe.samples)
        assertClose(8f, probe.meanMillis, note = "mean of three:")
        assertClose(12f, probe.worstMillis, note = "worst of three:")
    }

    @Test
    fun theRingForgetsWhatFellOutOfIt() {
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(probe.capacity, 20.0)
        assertEquals(probe.capacity, probe.missedFrames)

        // Half the window replaced: the arithmetic has to let go of what left.
        clock.frames(probe.capacity / 2, 8.0)
        assertClose(14f, probe.meanMillis, note = "mean of half and half:")
        assertEquals(probe.capacity / 2, probe.missedFrames, "stale drops: ${probe.missedFrames}")

        clock.frames(probe.capacity / 2, 8.0)
        assertClose(8f, probe.meanMillis, note = "mean once the slow frames are gone:")
        assertClose(8f, probe.worstMillis, note = "worst once the slow frames are gone:")
        assertEquals(0, probe.missedFrames, "drops outlived their frames: ${probe.missedFrames}")
        assertEquals(probe.capacity, probe.samples)
    }

    // ---- the percentile ----------------------------------------------------------

    @Test
    fun thePercentileMatchesAHandComputedAnswer() {
        // 100 frames: 95 at 8.2ms, 5 at 30ms. The 95th smallest is 8.2, so that
        // is P95 — and it is emphatically not the worst, which is the whole
        // reason a percentile is on the readout next to it.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(95, 8.2)
        clock.frames(5, 30.0)

        assertClose(8.2f, probe.p95Millis, quantisation, "p95 of a clean window with five spikes:")
        assertClose(30f, probe.worstMillis, note = "worst:")
        assertClose(9.29f, probe.meanMillis, 0.02f, "mean:")
    }

    @Test
    fun thePercentileFollowsTheTailWhenTheTailIsBigEnough() {
        // Same window, ten spikes instead of five: now the 95th smallest is one
        // of them, and a probe that reported the body of the distribution would
        // keep saying 8.2 while a tenth of the frames were catastrophic.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(90, 8.2)
        clock.frames(10, 30.0)

        assertClose(30f, probe.p95Millis, quantisation, "p95 with a tenth of the window spiking:")
    }

    @Test
    fun thePercentileNeverExceedsTheWorstFrameThereWas() {
        // Two numbers side by side on one line: p95 above worst reads as a bug
        // in the readout, whatever the histogram thinks.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(40, 8.0)

        assertTrue(
            probe.p95Millis <= probe.worstMillis,
            "p95 ${probe.p95Millis} above worst ${probe.worstMillis}",
        )
    }

    @Test
    fun aPercentileOffTheEndOfTheHistogramFallsBackOnTheWorst() {
        // Every frame past the histogram's ceiling: there is no bucket edge left
        // to report, so the honest answer is the frame itself.
        val probe = FrameProbe()
        Clock(probe).frames(30, 240.0)

        assertClose(240f, probe.p95Millis, 0.05f, "p95 of a window of disasters:")
    }

    // ---- the line the overlay draws -------------------------------------------------

    @Test
    fun theReadoutIsOneLineOfTheFieldsItReports() {
        // Formatting lives here so the UI layer has none to get wrong. The
        // percentile reads 8.2 from a bucket rather than from the sample, which
        // is the quarter-tenth the histogram costs.
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(95, 8.2, moving = 4)
        clock.frames(5, 30.0, moving = 4)

        assertEquals("30.0ms  p95 8.2  worst 30.0  miss 5  moving 4", probe.readout())
    }

    @Test
    fun movingObjectsIsWhateverTheLastFrameSaid() {
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(10, 8.0, moving = 3)
        clock.frame(8.0, moving = 11)

        assertEquals(11, probe.movingObjects, "the load count lagged: ${probe.movingObjects}")
    }

    // ---- starting over ----------------------------------------------------------------

    @Test
    fun resetLeavesAProbeThatHasNeverSeenAFrame() {
        val probe = FrameProbe()
        val clock = Clock(probe)
        clock.frames(300, 24.0)
        clock.frame(1400.0)
        probe.reset()

        assertEquals(0, probe.samples)
        assertEquals(0, probe.missedFrames)
        assertEquals(0, probe.pauses)
        assertClose(0f, probe.meanMillis)
        assertClose(0f, probe.worstMillis)
        assertClose(0f, probe.p95Millis)

        // Including the origin: the frame after a reset cannot be an interval
        // measured from before it.
        probe.sample(90_000_000_000L, 0)
        assertEquals(0, probe.samples, "the first frame after a reset was measured: ${probe.samples}")
        probe.sample(90_008_000_000L, 0)
        assertClose(8f, probe.lastMillis)
    }
}
