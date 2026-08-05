package com.kaiharimoto.mastertool.core.render

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneTest {

    // A channel is 0..1 and one step of an eight-bit ramp is about 0.004, so a
    // tolerance of 1e-4 is a good deal finer than anything that can be shown.
    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-4f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    // ---- the transfer curve ----------------------------------------------------------

    @Test
    fun theCurveComesBackToWhereItStarted() {
        (0..100).forEach { step ->
            val channel = step / 100f
            assertClose(channel, Tone.toSrgb(Tone.toLinear(channel)), note = "at $channel:")
        }
    }

    @Test
    fun theDarkEndOfTheRampRoundTripsAsWellAsTheRest() {
        // Sampled where the app lives. A curve that only round-trips at the
        // top would pass the test above and still lose every near-black edge
        // on the stage.
        listOf(0.0f, 0.0004f, 0.002f, 0.004f, 0.012f, 0.04f, 0.08f).forEach { channel ->
            assertClose(channel, Tone.toSrgb(Tone.toLinear(channel)), note = "at $channel:")
        }
    }

    @Test
    fun blackIsExactlyBlackAndWhiteIsWhite() {
        // Everything else here is approximate; black is not allowed to be. A
        // true-black stage whose black comes back as 0.0001 is a grey stage.
        assertEquals(0f, Tone.toLinear(0f), "linear black")
        assertEquals(0f, Tone.toSrgb(0f), "encoded black")
        assertClose(1f, Tone.toLinear(1f), note = "linear white:")
        assertClose(1f, Tone.toSrgb(1f), note = "encoded white:")
    }

    @Test
    fun theTwoHalvesOfTheCurveMeetAtTheKnee() {
        assertClose(
            Tone.KNEE_ENCODED,
            Tone.toSrgb(Tone.KNEE_LINEAR),
            tolerance = 1e-5f,
            note = "the knee, encoded:",
        )
        assertClose(
            Tone.KNEE_LINEAR,
            Tone.toLinear(Tone.KNEE_ENCODED),
            tolerance = 1e-6f,
            note = "and back:",
        )

        // Continuity across the join, which is the whole reason those two
        // constants are the numbers they are: a step here would be a visible
        // band right where the stage's darkest surfaces sit.
        val below = Tone.toSrgb(Tone.KNEE_LINEAR - 1e-5f)
        val above = Tone.toSrgb(Tone.KNEE_LINEAR + 1e-5f)

        assertClose(below, above, tolerance = 1e-3f, note = "either side of the join:")
    }

    @Test
    fun theStraightSegmentIsWhyTheCheapApproximationIsRefused() {
        // Near black is where this app lives — the stage is true black, so
        // every edge, veil and pile band on it is decided down here — and it is
        // exactly where a plain gamma-2.2 curve is worst: it leaves the origin
        // with an infinite slope and the real function leaves it with a slope
        // of 12.92, so the two disagree by more than a factor of two.
        val dark = 0.002f
        val real = Tone.toSrgb(dark)
        val cheap = dark.pow(1f / 2.2f)

        assertTrue(real < cheap * 0.6f, "gamma 2.2 says $cheap where the real curve says $real")

        // And it is a curve at all, which the bound above does not say on its
        // own: a function that just handed the channel back would satisfy it
        // and encode nothing. Down here the real one is steep, so a linear
        // 0.002 has to arrive an order of magnitude higher than it left.
        assertTrue(real > dark * 12f, "an encoding this flat is no encoding: $real from $dark")
    }

    @Test
    fun neitherDirectionEverGoesBackwards() {
        val encoded = (0..200).map { Tone.toSrgb(it / 200f) }
        val linear = (0..200).map { Tone.toLinear(it / 200f) }

        assertEquals(encoded.sorted(), encoded, "encoding went backwards: $encoded")
        assertEquals(linear.sorted(), linear, "decoding went backwards: $linear")
    }

    // ---- one channel under one light -------------------------------------------------

    @Test
    fun fullLightLeavesTheColourAloneExactly() {
        // Not nearly. A card at full brightness that comes back a hair off its
        // own colour is a card that never looks like the art it was given.
        listOf(0f, 0.02f, 0.29f, 0.5f, 0.91f, 1f).forEach { channel ->
            assertEquals(channel, Tone.shade(channel, 1f), "at $channel")
        }
    }

    @Test
    fun noLightIsBlack() {
        listOf(0f, 0.29f, 0.5f, 1f).forEach { channel ->
            assertEquals(0f, Tone.shade(channel, 0f), "at $channel")
        }
    }

    @Test
    fun moreLightIsAlwaysMoreColour() {
        listOf(0.05f, 0.2f, 0.5f, 0.8f, 1f).forEach { channel ->
            val ramp = (0..20).map { Tone.shade(channel, it / 20f) }

            assertEquals(ramp.sorted(), ramp, "the ramp on $channel went backwards: $ramp")
            assertEquals(ramp.distinct().size, ramp.size, "every step should move: $ramp")
        }
    }

    @Test
    fun aBrighterSurfaceStaysTheBrighterOneUnderTheSameLight() {
        val light = 0.4f
        val ramp = (0..20).map { Tone.shade(it / 20f, light) }

        assertEquals(ramp.sorted(), ramp, "shading reordered the ramp: $ramp")
    }

    @Test
    fun midGreyUnderHalfTheLightIsNotHalfOfMidGrey() {
        // The bug the whole file exists for. Multiplying an sRGB channel by the
        // light term multiplies the *encoding*, not the light, and the result
        // is a long way past where half the light actually lands.
        val naive = 0.5f * 0.5f
        val correct = Tone.shade(0.5f, 0.5f)

        assertClose(0.3608f, correct, tolerance = 1e-3f, note = "half-lit mid grey:")
        assertTrue(correct > naive + 0.1f, "the raw multiply gives $naive against $correct")
    }

    @Test
    fun theRawMultiplyIsAlwaysTheDarkerAnswer() {
        // Which is why the stage went muddy rather than merely wrong: the
        // encoding curve is convex, so multiplying in it can only ever take
        // light away, and it takes the most out of the midtones.
        var worst = 0f
        (1..19).forEach { c ->
            (1..19).forEach { l ->
                val channel = c / 20f
                val light = l / 20f
                val correct = Tone.shade(channel, light)

                assertTrue(correct >= channel * light, "at $channel under $light: $correct")
                worst = maxOf(worst, correct - channel * light)
            }
        }
        assertTrue(worst > 0.1f, "and the gap should be a real one, was $worst")
    }

    // ---- darkening art nobody can read -----------------------------------------------

    @Test
    fun theVeilIsThinnerThanTheAlphaItReplaces() {
        // A card's face is art the renderer cannot inspect, so the same
        // statement about light has to arrive as the opacity of a black veil
        // instead of as a channel.
        assertClose(0.2784f, Tone.veil(0.5f), tolerance = 1e-3f, note = "half light:")
        assertEquals(0f, Tone.veil(1f), "full light should need no veil")
        assertEquals(1f, Tone.veil(0f), "and no light should be opaque")
    }

    @Test
    fun theVeilIsExactAtItsOwnChannelAndWithinTwoLevelsEverywhereElse() {
        // The one that matters for the stage: an edge corrected and a face not
        // corrected is worse than neither, because then the white band on a
        // pile and the card sitting on it disagree about where the lamp is. One
        // alpha over a picture cannot say the same thing to every channel
        // underneath it, so what has to be held is how far off it is — and at
        // the light the stage can actually produce, which is floored at the
        // rig's own ambient because every drawn face goes through StageRig.lit.
        val diffuse = StageRig.Key.ambient
        val veil = Tone.veil(diffuse)

        assertClose(
            Tone.shade(Tone.MID_TONE, diffuse),
            Tone.MID_TONE * (1f - veil),
            note = "the reference channel should be exact:",
        )

        // One step of an eight-bit ramp is 1/255 and the whole ramp stays
        // inside two of them. This is the bound that fails the day the veil is
        // solved at a different reference channel — and the day the ambient
        // comes down, which is what it is really here to argue with.
        (0..100).forEach { step ->
            val channel = step / 100f

            assertClose(
                Tone.shade(channel, diffuse),
                channel * (1f - veil),
                tolerance = 2f / 255f,
                note = "at $channel:",
            )
        }
    }

    @Test
    fun theVeilLeavesDarkChannelsTooBrightAndTheGapGrowsAsTheLightFalls() {
        // The sign, because it is what someone picks the tablet up looking for
        // and having it backwards sends them at the wrong constant: below the
        // reference channel the veil is too thin, so dark art comes back
        // lifted, not crushed. Above it, slightly the other way.
        val diffuse = 0.72f
        val veil = Tone.veil(diffuse)

        listOf(0.05f, 0.10f, 0.20f).forEach { channel ->
            val veiled = channel * (1f - veil)

            assertTrue(veiled > Tone.shade(channel, diffuse), "dark channel $channel came back at $veiled")
        }
        listOf(0.75f, 0.90f).forEach { channel ->
            val veiled = channel * (1f - veil)

            assertTrue(veiled < Tone.shade(channel, diffuse), "light channel $channel came back at $veiled")
        }

        // And what a dimmer key would cost, which is the change this rig
        // invites next. The error is not a constant: the veil thickens as the
        // light falls and the fixed offset in the curve becomes a larger share
        // of a smaller number, so the worst dark channel goes from a fifth too
        // bright to nearly twice too bright.
        val worst = listOf(0.72f, 0.5f, 0.3f).map { light ->
            val thin = 1f - Tone.veil(light)
            (1..100).maxOf { step ->
                val channel = step / 100f
                channel * thin / Tone.shade(channel, light)
            }
        }

        assertEquals(worst.sorted(), worst, "the error should grow as the light falls: $worst")
        assertClose(1.19f, worst[0], tolerance = 0.02f, note = "worst overshoot at the rig's ambient:")
        assertClose(1.87f, worst[2], tolerance = 0.02f, note = "and at a diffuse of 0.3:")
    }

    @Test
    fun theVeilNeverThickensAsTheLightComesUp() {
        val ramp = (0..20).map { Tone.veil(it / 20f) }

        assertEquals(ramp.sortedDescending(), ramp, "the veil went the wrong way: $ramp")
    }

    // ---- nonsense --------------------------------------------------------------------

    @Test
    fun aNumberThatIsNotOneNeverReachesTheScreen() {
        // pow of a negative base is NaN and a NaN alpha paints nothing at all,
        // so every way in has to close the range itself rather than trust its
        // caller — the light terms upstream are dot products and divisions.
        val nonsense = listOf(
            -1f,
            -0.0001f,
            1.0001f,
            4f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
        )

        nonsense.forEach { odd ->
            val answers = listOf(
                "toLinear" to Tone.toLinear(odd),
                "toSrgb" to Tone.toSrgb(odd),
                "shade channel" to Tone.shade(odd, 0.5f),
                "shade light" to Tone.shade(0.5f, odd),
                "veil" to Tone.veil(odd),
                "veil over" to Tone.veil(0.5f, over = odd),
            )

            answers.forEach { (name, value) ->
                assertTrue(!value.isNaN(), "$name on $odd was $value")
                assertTrue(value >= 0f && value <= 1f, "$name on $odd was $value")
            }
        }
    }
}
