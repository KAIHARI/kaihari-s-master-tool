package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * The lighting rig for the play stage: a key, a bounce, a rim, one eye.
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
     *
     * Warm, and that is the half of it that is not geometry. Two pure-white
     * lamps are most of why the table used to read as lit by an equation rather
     * than by a room: nothing anybody has ever played on is lit by one colour
     * from both sides. Three quarters of [Lit.TEMPERATURE] — see there for the
     * magnitude and why it is nothing like as large as it sounds.
     */
    val Key = Light(
        direction = Vec3(0.30f, 0.45f, -0.84f).normalised(),
        intensity = 1f,
        warmth = 0.75f,
        ambient = 0.72f,
    )

    /**
     * The bounce: the light the room throws back up off the table.
     *
     * Weak, wide, and pointed the other way, so a card tilted hard away from
     * the key does not go flat. It has no shadow of its own — a fill that cast
     * one would be a second key.
     *
     * Cool, all the way, because the split is what does the work rather than
     * either end of it: a white edge that is a shade warm where the key lands
     * and a shade cool where only the fill reaches reads as an object in a room,
     * and a difference *across one object* is far more visible than the same
     * shift applied to the whole table. It can afford the full swing precisely
     * because it is the weakest lamp here — at its best it is two and a half per
     * cent of the light on a face.
     */
    val Bounce = Light(
        direction = Vec3(-0.22f, -0.30f, -0.93f).normalised(),
        intensity = 0.30f,
        warmth = -1f,
        ambient = 0f,
    )

    /**
     * The rim: low, behind the table, and on the opposite side from the key.
     *
     * The stage is true black, so a card's silhouette has nothing to be a
     * silhouette *against* — the only thing separating the edge of a card from
     * the felt is the card's own brightness, and when a card is dim so is the
     * boundary. This is the third point of an ordinary three-point rig and it
     * exists for exactly that line.
     *
     * Two things about it are load-bearing:
     *
     * - **It casts nothing.** `Shadows.cast` takes one light and goes on taking
     *   one light; a second shadow on this table would not read as a second lamp,
     *   it would read as a duplicated card.
     * - **It is gated on the graze, not on the lambert alone.** A backlight that
     *   simply added its dot product would brighten a card lying face-up toward
     *   the viewer, which is an ambient with extra steps and a more expensive
     *   one. Weighted by how edge-on the camera sees a surface it can only
     *   land where the silhouette is, and it is exactly zero square on.
     *
     * And one thing about it is not what a photographer would expect. A rim
     * light belongs *behind* the subject, and behind is the one place it would
     * do nothing here: this stage draws solids with back-face culling and looks
     * at them from above, so every surface a light behind the table could reach
     * has already been culled before it is shaded. The first version of this
     * lamp pointed that way and a test proved it changed exactly one face in a
     * whole board — the far edge of a card held in the air — while leaving every
     * pile it was written to outline byte-identical.
     *
     * So it sits on the *player's* side instead, low and cool: the light a room
     * throws back off whoever is sitting at the table. That is a real lamp in a
     * real room, it reaches the near edges — which are the only edges of a pile
     * anybody ever sees — and the graze gate still keeps it off the faces.
     */
    val Rim = Light(
        direction = Vec3(-0.62f, -0.66f, -0.42f).normalised(),
        intensity = 0.45f,
        warmth = -0.6f,
        ambient = 0f,
    )

    /**
     * How sharply the rim collapses as a surface turns to face the camera.
     *
     * Squared rather than linear. Linear leaves a rim of a third of its strength
     * on a surface forty degrees off the camera, which is most of a card that is
     * merely tilted in someone's hand — and a rim that is on everything is not a
     * rim, it is the ambient again.
     */
    private const val GRAZE_FALLOFF = 2f

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
    fun eye(tiltDegrees: Float, yawDegrees: Float = 0f): Vec3 {
        // Undo the mat's own layer, which is Rx(pitch)·Rz(-yaw): the viewer sits
        // wherever that transform came from. Written as the two steps rather
        // than as three constants so the sign of the yaw is decided in exactly
        // one place in this app — StagePlane's — and read everywhere else.
        val up = Rot3.rotateX(Vec3.Toward, -tiltDegrees)
        return Rot3.rotateZ(up, yawDegrees)
    }

    /**
     * All three lamps on one surface, which is all any renderer needs to ask for.
     *
     * Each of the three directional terms is scaled by the headroom above the
     * key's ambient, and they are then summed and clamped. A surface standing
     * square in all three at once would sum past white and be clipped — which
     * cannot happen, because the three lamps point in three different
     * directions and no normal faces all of them. The clamp is the backstop,
     * not the design.
     *
     * The temperature comes out as an energy-weighted average, and the ambient
     * is in the denominator as the white light it is. That one detail is what
     * keeps the rule the handbook actually cares about: the room is achromatic,
     * so a surface the key cannot reach comes back a neutral grey, and colour
     * appears in exact proportion to how much *directional* light landed on it.
     * Colour as light, arithmetically rather than by promise.
     */
    fun lit(
        normal: Vec3,
        eye: Vec3 = Vec3.Toward,
        key: Light = Key,
        bounce: Light = Bounce,
        rim: Light = Rim,
    ): Lit {
        val unit = normal.normalised()
        val headroom = 1f - key.ambient

        val direct = max(0f, unit dot key.toLight) * key.intensity * headroom
        val fill = max(0f, unit dot bounce.toLight) * bounce.intensity * headroom

        // Zero when the camera is square on to the surface and largest along the
        // silhouette, which is the only place a rim light is supposed to exist.
        val graze = (1f - abs(unit dot eye.normalised())).coerceIn(0f, 1f)
        val kick = max(0f, unit dot rim.toLight) * rim.intensity *
            graze.pow(GRAZE_FALLOFF) * headroom

        val amount = (key.ambient + direct + fill + kick).coerceIn(0f, 1f)
        if (amount <= 0f) return Lit.None

        val temperature = direct * key.warmth + fill * bounce.warmth + kick * rim.warmth
        return Lit(amount, temperature / amount)
    }

    /**
     * One face of a solid: how bright to paint it, or nothing if it is turned away.
     *
     * The bounce earns its place here. The key is toward the player, so the
     * *near* edge of every pile — the one edge of it anybody can see — faces
     * away from the key and would be lit by ambient alone. A deck's white edge
     * is not a dark band in any room, and the fill is why.
     *
     * The rim earns its place on the other axis. A face the camera sees nearly
     * edge-on is a face whose *drawn* width is a hairline, and a hairline of
     * ambient grey on a true-black stage is a hairline nobody can see — which is
     * the whole reason a card's outline used to dissolve into the felt whenever
     * the card itself was dim.
     */
    fun face(face: Face, eye: Vec3 = Vec3.Toward): Lit =
        if (face.facing(eye) <= 0f) Lit.None else lit(face.normal, eye)
}
