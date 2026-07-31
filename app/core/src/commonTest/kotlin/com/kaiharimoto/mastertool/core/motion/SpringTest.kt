package com.kaiharimoto.mastertool.core.motion

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SpringTest {

    private fun settleTime(
        spec: SpringSpec,
        from: Float = 0f,
        target: Float = 100f,
        dt: Float = 1f / 60f,
    ): Pair<Float, Float> {
        var state = SpringValue(from)
        var maxValue = from
        var t = 0f
        repeat(600) {
            state = Springs.step(state, target, spec, dt)
            if (state.value > maxValue) maxValue = state.value
            t += dt
            if (Springs.settled(state, target)) return t to maxValue
        }
        return t to maxValue
    }

    @Test
    fun snappyConvergesQuicklyWithoutOvershoot() {
        val (time, peak) = settleTime(SpringSpec.Snappy)
        assertTrue(time < 1.0f, "critically damped spring took ${time}s")
        // Critical damping never crosses the target (tiny numeric slack).
        assertTrue(peak <= 100.5f, "overshot to $peak")
    }

    @Test
    fun bouncyOvershootsButStaysBounded() {
        val (time, peak) = settleTime(SpringSpec.Bouncy)
        assertTrue(time < 1.5f, "bouncy spring took ${time}s")
        assertTrue(peak > 100.5f, "a bouncy spring should overshoot, peaked at $peak")
        assertTrue(peak < 125f, "overshoot out of hand: $peak")
    }

    @Test
    fun aHugeFrameGapCannotTeleportOrExplode() {
        // A GC pause hands the integrator two whole seconds; the clamp must
        // keep the step stable rather than launching the value to infinity.
        var state = SpringValue(0f)
        state = Springs.step(state, 100f, SpringSpec.Snappy, dt = 2f)
        assertTrue(state.value.isFinite() && abs(state.value) < 200f)

        // And repeated huge steps still converge.
        repeat(200) { state = Springs.step(state, 100f, SpringSpec.Snappy, dt = 2f) }
        assertTrue(Springs.settled(state, 100f), "ended at ${state.value}")
    }

    @Test
    fun retargetingMidFlightKeepsVelocity() {
        var state = SpringValue(0f)
        repeat(10) { state = Springs.step(state, 100f, SpringSpec.Bouncy, 1f / 60f) }
        val movingVelocity = state.velocity
        assertTrue(movingVelocity > 0f)

        // New target simply takes over — no reset, no jump: semi-implicit
        // Euler moves exactly one new-velocity step, never a teleport.
        val next = Springs.step(state, -50f, SpringSpec.Bouncy, 1f / 60f)
        assertTrue(abs((next.value - state.value) - next.velocity * (1f / 60f)) < 1e-3f)
    }

    @Test
    fun poseSpringsEveryComponent() {
        var motion = PoseMotion.at(Pose3())
        val target = Pose3(Vec3(50f, -20f, 30f), rotX = 15f, rotY = -10f, rotZ = 5f, scale = 1.1f)

        repeat(300) { motion = PosePhysics.step(motion, target, SpringSpec.Snappy, 1f / 60f) }

        assertTrue(PosePhysics.settled(motion, target), "ended at ${motion.pose}")
        assertTrue(abs(motion.pose.scale - 1.1f) < 0.02f)
    }

    @Test
    fun zeroOrNegativeDtIsANoOp() {
        val state = SpringValue(10f, 5f)
        assertTrue(Springs.step(state, 100f, SpringSpec.Snappy, 0f) == state)
        assertTrue(Springs.step(state, 100f, SpringSpec.Snappy, -1f) == state)
    }
}
