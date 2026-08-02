package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec3

/**
 * The lighting rig for the play stage: one key, one bounce, one eye.
 *
 * Two rigs would be one too many. Every surface on the table — a card's face,
 * the white edge of a pile, the felt, the shadow one card throws on another —
 * has to agree about where the light is, or the table stops reading as one
 * room, and the way to guarantee that is for there to be exactly one place the
 * answer comes from.
 *
 * **Everything here is in the mat's own frame**, not the screen's: +x across
 * the table, +y toward the player's edge, +z straight up off the felt. That is
 * the frame the whole play stage already computes in — card positions are mat
 * pixels and lift is a z — and it means the light does not have to be re-derived
 * when the table's tilt changes. The one place the tilt shows up is [eye],
 * because where the *camera* is, in a frame attached to a tilted table, is
 * genuinely a function of the tilt.
 */
object StageRig {

    /**
     * The key: high, in front, and off to the left.
     *
     * Off to one side because a light directly overhead casts a shadow directly
     * underneath, which is to say no visible shadow at all and no way to tell a
     * held card from a resting one. Toward the player rather than away because
     * this camera looks from the player's side, and a shadow thrown *away* from
     * the viewer spends most of its life hidden behind the card throwing it.
     */
    val Key = Light(
        direction = Vec3(0.30f, 0.45f, -0.84f).normalised(),
        intensity = 1f,
        ambient = 0.72f,
    )

    /**
     * The bounce: the light the room throws back up off the table.
     *
     * Weak, wide, and pointed the other way, so a card tilted hard away from
     * the key does not go flat. It has no shadow of its own — a fill that cast
     * one would be a second key.
     */
    val Bounce = Light(
        direction = Vec3(-0.22f, -0.30f, -0.93f).normalised(),
        intensity = 0.30f,
        ambient = 0f,
    )

    /**
     * Where the camera is, in the mat's frame, for a table tilted [tiltDegrees].
     *
     * The mat is turned to face the viewer by that much, so from the mat's own
     * point of view the viewer has moved up and toward its near edge by the
     * same amount. Getting this wrong is not subtle: with a flat `(0, 0, 1)`
     * the specular pool on every resting card sits a little too far up the
     * table, uniformly, and the whole board looks lit from a light that is not
     * the one lighting the piles.
     */
    fun eye(tiltDegrees: Float): Vec3 = Rot3.rotateX(Vec3.Toward, -tiltDegrees)

    /** Both lights on one surface, which is all any renderer needs to ask for. */
    fun lit(normal: Vec3, key: Light = Key, bounce: Light = Bounce): Float {
        val unit = normal.normalised()
        val fill = (unit dot bounce.toLight).coerceAtLeast(0f) * bounce.intensity
        return (Shading.lit(unit, key) + fill * (1f - key.ambient)).coerceIn(0f, 1f)
    }

    /**
     * One face of a solid: how bright to paint it, or zero if it is turned away.
     *
     * The bounce earns its place here. The key is toward the player, so the
     * *near* edge of every pile — the one edge of it anybody can see — faces
     * away from the key and would be lit by ambient alone. A deck's white edge
     * is not a dark band in any room, and the fill is why.
     */
    fun face(face: Face, eye: Vec3 = Vec3.Toward): Float =
        if (face.facing(eye) <= 0f) 0f else lit(face.normal)
}
