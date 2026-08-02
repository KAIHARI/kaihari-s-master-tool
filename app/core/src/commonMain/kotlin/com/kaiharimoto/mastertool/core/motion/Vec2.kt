package com.kaiharimoto.mastertool.core.motion

import kotlin.math.sqrt

/**
 * A point or a movement on a flat surface.
 *
 * [Vec3]'s sibling, and deliberately not it. A gesture happens on the plane of
 * the mat and has no depth at all; carrying a z through the arithmetic would
 * mean every gesture calculation ends in `.z` being ignored, which is the kind
 * of thing that is fine until the day it is not.
 */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Float) = Vec2(x * factor, y * factor)
    operator fun div(factor: Float) = if (factor == 0f) Zero else Vec2(x / factor, y / factor)
    operator fun unaryMinus() = Vec2(-x, -y)

    val length: Float get() = sqrt(x * x + y * y)

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}
