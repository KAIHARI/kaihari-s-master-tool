package com.kaiharimoto.mastertool.core.motion

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A damped spring, the unit of card motion.
 *
 * Everything a card does in this app — lift, settle, snap, fly to the hand —
 * is a spring approaching a target, because springs are what make digital
 * objects read as having mass: they arrive with the deceleration of a real
 * thing, and interrupting them mid-flight composes naturally (the new target
 * simply takes over, velocity intact).
 *
 * Same integration style as `SpritePhysics`, deliberately: semi-implicit
 * Euler, dt clamped so a paused frame cannot teleport anything. Mass is 1;
 * critical damping is `2·√stiffness`.
 */
data class SpringSpec(
    val stiffness: Float,
    val damping: Float,
) {
    companion object {
        fun critical(stiffness: Float) = SpringSpec(stiffness, 2f * sqrt(stiffness))

        /** Settles fast with no overshoot — snaps, docks, returns. */
        val Snappy = critical(stiffness = 520f)

        /** A touch of overshoot — lifts and lands that feel like mass. */
        val Bouncy = SpringSpec(stiffness = 380f, damping = 0.62f * 2f * sqrt(380f))

        /** Slow and weighty — big scene moves, camera-like drifts. */
        val Calm = critical(stiffness = 120f)
    }
}

/** One animated scalar: where it is and how fast it is going. */
data class SpringValue(
    val value: Float,
    val velocity: Float = 0f,
)

object Springs {

    /** Clamped like `SpritePhysics.MAX_STEP`, and for the same reason. */
    const val MAX_STEP = 1f / 30f

    fun step(
        state: SpringValue,
        target: Float,
        spec: SpringSpec,
        dt: Float,
    ): SpringValue {
        if (dt <= 0f) return state
        val step = if (dt > MAX_STEP) MAX_STEP else dt

        val force = -spec.stiffness * (state.value - target) - spec.damping * state.velocity
        val velocity = state.velocity + force * step
        return SpringValue(
            value = state.value + velocity * step,
            velocity = velocity,
        )
    }

    /** Close enough to stop integrating and snap to the target. */
    fun settled(
        state: SpringValue,
        target: Float,
        positionTolerance: Float = 0.5f,
        velocityTolerance: Float = 0.5f,
    ): Boolean =
        abs(state.value - target) <= positionTolerance &&
            abs(state.velocity) <= velocityTolerance
}
