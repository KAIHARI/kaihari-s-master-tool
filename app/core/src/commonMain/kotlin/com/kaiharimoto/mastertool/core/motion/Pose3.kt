package com.kaiharimoto.mastertool.core.motion

/**
 * A position in the fake-3D space the UI draws: screen x/y plus a z that the
 * renderer turns into scale, shadow and paint order. Plain data, unit-agnostic.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(factor: Float) = Vec3(x * factor, y * factor, z * factor)

    companion object {
        val Zero = Vec3(0f, 0f, 0f)
    }
}

/**
 * Where a card is and how it is held: position, Euler tilt, scale.
 *
 * Eulers rather than quaternions on purpose — `graphicsLayer` takes exactly
 * these three angles, and a card's repertoire (tilt in hand, flip over,
 * defence-position turn) never composes rotations far enough to gimbal-lock
 * in a way anyone could see.
 */
data class Pose3(
    val position: Vec3 = Vec3.Zero,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val scale: Float = 1f,
)

/** A pose in motion: current values plus per-component velocity. */
data class PoseMotion(
    val pose: Pose3 = Pose3(),
    val vPosition: Vec3 = Vec3.Zero,
    val vRotX: Float = 0f,
    val vRotY: Float = 0f,
    val vRotZ: Float = 0f,
    val vScale: Float = 0f,
) {
    companion object {
        fun at(pose: Pose3) = PoseMotion(pose)
    }
}

object PosePhysics {

    /** Springs every component of [motion] toward [target] by [dt] seconds. */
    fun step(
        motion: PoseMotion,
        target: Pose3,
        spec: SpringSpec,
        dt: Float,
    ): PoseMotion {
        fun axis(value: Float, velocity: Float, goal: Float): SpringValue =
            Springs.step(SpringValue(value, velocity), goal, spec, dt)

        val x = axis(motion.pose.position.x, motion.vPosition.x, target.position.x)
        val y = axis(motion.pose.position.y, motion.vPosition.y, target.position.y)
        val z = axis(motion.pose.position.z, motion.vPosition.z, target.position.z)
        val rx = axis(motion.pose.rotX, motion.vRotX, target.rotX)
        val ry = axis(motion.pose.rotY, motion.vRotY, target.rotY)
        val rz = axis(motion.pose.rotZ, motion.vRotZ, target.rotZ)
        val s = axis(motion.pose.scale, motion.vScale, target.scale)

        return PoseMotion(
            pose = Pose3(
                position = Vec3(x.value, y.value, z.value),
                rotX = rx.value,
                rotY = ry.value,
                rotZ = rz.value,
                scale = s.value,
            ),
            vPosition = Vec3(x.velocity, y.velocity, z.velocity),
            vRotX = rx.velocity,
            vRotY = ry.velocity,
            vRotZ = rz.velocity,
            vScale = s.velocity,
        )
    }

    fun settled(
        motion: PoseMotion,
        target: Pose3,
        positionTolerance: Float = 0.5f,
        angleTolerance: Float = 0.25f,
    ): Boolean {
        fun done(value: Float, velocity: Float, goal: Float, tolerance: Float) =
            Springs.settled(SpringValue(value, velocity), goal, tolerance, tolerance)

        return done(motion.pose.position.x, motion.vPosition.x, target.position.x, positionTolerance) &&
            done(motion.pose.position.y, motion.vPosition.y, target.position.y, positionTolerance) &&
            done(motion.pose.position.z, motion.vPosition.z, target.position.z, positionTolerance) &&
            done(motion.pose.rotX, motion.vRotX, target.rotX, angleTolerance) &&
            done(motion.pose.rotY, motion.vRotY, target.rotY, angleTolerance) &&
            done(motion.pose.rotZ, motion.vRotZ, target.rotZ, angleTolerance) &&
            done(motion.pose.scale, motion.vScale, target.scale, 0.01f)
    }
}
