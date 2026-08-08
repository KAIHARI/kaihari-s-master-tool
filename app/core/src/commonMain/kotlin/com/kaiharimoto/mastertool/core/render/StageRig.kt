package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

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
        /**
         * Where on the stage this surface is, for a lamp that has a place.
         *
         * Last, and it has to be: every existing caller passes its arguments
         * positionally up to [rim], and a parameter inserted before them would
         * be a silent re-binding rather than a compile error.
         *
         * Null is not "the origin" — it is *no point offered*, which every lamp
         * without a position ignores anyway. All three lamps get the treatment
         * rather than only the key, because [Light.toLightFrom] and
         * [Light.attenuation] both short-circuit on a null position, so doing it
         * uniformly costs nothing and removes a special case. In practice only
         * the key is ever placed: a fill is the room and a rim is the player,
         * and neither is a fixture.
         */
        at: Vec3? = null,
    ): Lit {
        val unit = normal.normalised()
        val headroom = 1f - key.ambient

        val direct = max(0f, unit dot key.toLightFrom(at)) * key.intensity *
            key.attenuation(at) * headroom
        val fill = max(0f, unit dot bounce.toLightFrom(at)) * bounce.intensity *
            bounce.attenuation(at) * headroom

        // Zero when the camera is square on to the surface and largest along the
        // silhouette, which is the only place a rim light is supposed to exist.
        val graze = (1f - abs(unit dot eye.normalised())).coerceIn(0f, 1f)
        val kick = max(0f, unit dot rim.toLightFrom(at)) * rim.intensity *
            rim.attenuation(at) * graze.pow(GRAZE_FALLOFF) * headroom

        val amount = (key.ambient + direct + fill + kick).coerceIn(0f, 1f)
        if (amount <= 0f) return Lit.None

        val temperature = direct * key.warmth + fill * bounce.warmth + kick * rim.warmth
        return Lit(amount, temperature / amount)
    }

    /**
     * The same three lamps, handed over as one value instead of three.
     *
     * The only overload that exists, and it exists so that no call site has to
     * take a rig apart to pass it on. Every renderer in the app takes a
     * [StageLighting] and hands it straight here; none of them holds a [Light]
     * of its own, which is what keeps the unanimity [StageLighting]'s KDoc
     * inherits from this object's.
     */
    fun lit(normal: Vec3, eye: Vec3, lighting: StageLighting, at: Vec3? = null): Lit =
        lit(normal, eye, lighting.key, lighting.bounce, lighting.rim, at)

    /**
     * One face of a solid: how bright to paint it.
     *
     * It used to answer `Lit.None` for anything whose normal was not within
     * ninety degrees of the direction the viewer lies in — a back-face cull,
     * done a second time, in the wrong place, with the wrong test. Culling is
     * `CardSolid.visible`'s job and it asks a better question: whether the face
     * is turned toward *where the camera is*, rather than toward the direction
     * the stage as a whole is seen from. The two disagree exactly where it
     * matters, and while this function kept its own opinion the walls that
     * disagreement had been hiding came back **painted black** — which is a hole
     * in a deck of a different colour and no more convincing.
     *
     * So there is no opinion here any more. A face this is asked about is a face
     * the culler already passed, and the answer is how the three lamps land on
     * it.
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
    fun face(
        face: Face,
        eye: Vec3 = Vec3.Toward,
        lighting: StageLighting = StageLighting.Minimal,
    ): Lit = lit(face.normal, eye, lighting, at = face.centre)

    // ---- what a placed lamp does to a flat surface --------------------------------

    /**
     * Where the lamp's own reflection lands on the plane at [surfaceZ].
     *
     * The mirror image of the source, chased down to the plane — exact and one
     * line, because the surface is flat and its normal is +z.
     *
     * This exists because a *diffuse* pool on this mat cannot be seen. Derived
     * honestly from the rig, the night mat runs 11 levels of 255 under the lamp
     * down to 7 at the far corner: four levels, against the twenty-nine the
     * shipped additive pool gives. The physics is right and the picture is dead,
     * because a near-black matte surface has no albedo left to modulate. What
     * you actually see on a playmat under a lamp is a *sheen* — a reflection of
     * the source rather than of its light — which is exactly what [Shade.lamp]
     * already models for a card and exactly why `drawCardSurface` composites it
     * additively. So the felt gets both: the diffuse pool as a multiply, and
     * this as the additive highlight it always secretly was.
     *
     * Null for a lamp with no place. A directional source's reflection is a lobe
     * with no centre a plane this small can hold.
     */
    fun sheen(light: Light, eyeAt: Vec3, surfaceZ: Float = 0f): Vec2? {
        val lamp = light.position ?: return null
        val eyeUp = eyeAt.z - surfaceZ
        val lampUp = lamp.z - surfaceZ
        if (eyeUp <= 0f || eyeUp + lampUp <= 0f) return null
        val along = eyeUp / (eyeUp + lampUp)
        return Vec2(
            x = eyeAt.x + (lamp.x - eyeAt.x) * along,
            y = eyeAt.y + (lamp.y - eyeAt.y) * along,
        )
    }

    /**
     * How wide that reflection is: the source's mirror image, broadened by how
     * rough the surface reflecting it is.
     *
     * [roughness] is an RMS slope — a tangent, dimensionless — so it adds to the
     * source's own angular radius before both are carried down the same mirror
     * path [sheen] walks and scaled onto the plane by the same fraction.
     */
    fun sheenRadius(
        light: Light,
        eyeAt: Vec3,
        roughness: Float,
        surfaceZ: Float = 0f,
    ): Float {
        val lamp = light.position ?: return 0f
        val eyeUp = eyeAt.z - surfaceZ
        val lampUp = lamp.z - surfaceZ
        if (eyeUp <= 0f || eyeUp + lampUp <= 0f) return 0f
        val along = eyeUp / (eyeUp + lampUp)
        val mirror = Vec3(lamp.x, lamp.y, surfaceZ - lampUp)
        return (light.radius + roughness * (mirror - eyeAt).length) * along
    }

    /** Where the key's own share of the light has run out, as a fraction of it. */
    const val POOL_FLOOR = 0.10f

    /** How many colours the pool is drawn as. Nine is where a tenth stops showing. */
    const val POOL_STOPS = 9

    /**
     * The key's light on the table top: one shape, and both the wood and the
     * felt lying on it are consumers of it.
     *
     * **Nothing here is a second lighting model**, and that is the whole point of
     * it living in this object. [LightPool.stops] is [lit] itself, evaluated on a
     * ray of radii with the plane's own normal — the identical function that
     * shades every card and every wall — so the pool and the shadows cannot
     * disagree about where the lamp is, by construction rather than by care. The
     * felt's pool was a hand-placed gradient aimed by a *direction*, and it is
     * the one surface on the stage that has never been told a lamp exists.
     *
     * Sampling one ray is exact rather than an approximation: for a fixed normal
     * the bounce and the rim have no position, the graze is a constant, and the
     * only radially varying term is the key's — and a point source over a plane
     * is radially symmetric about its own foot.
     *
     * Null when the key has no place. A directional lamp has no foot to pool
     * around, which is why the shipped felt keeps the shipped drawing.
     */
    fun pool(
        lighting: StageLighting,
        eye: Vec3,
        steps: Int = POOL_STOPS,
        floor: Float = POOL_FLOOR,
    ): LightPool? {
        val key = lighting.key
        val lamp = key.position ?: return null
        val height = lamp.z
        if (height <= 0f) return null

        val reach = reachOf(key, height, floor)
        // A lamp too dim to reach [floor] anywhere, including directly beneath
        // itself. There is no pool to draw, and the honest answer is the one
        // that sends the caller back to the drawing it had before a lamp
        // existed — rather than a gradient of radius zero, which paints its
        // last stop over everything.
        if (reach <= 0f) return null

        val count = max(2, steps)
        return LightPool(
            foot = Vec2(lamp.x, lamp.y),
            radius = reach,
            stops = (0 until count).map { step ->
                val radius = reach * step / (count - 1f)
                lit(Rot3.FaceNormal, eye, lighting, at = Vec3(lamp.x + radius, lamp.y, 0f))
            },
        )
    }

    /**
     * How far out the key still carries [floor] of the light it throws straight
     * down, for a lamp [height] above the plane.
     *
     * Bisected rather than solved, because the closed form is a quartic and this
     * is called once a frame for a whole room. Thirty halvings of a bracket that
     * starts twenty times the lamp's height leaves the answer exact to a
     * millionth of a pixel, which is a great deal more than a gradient needs.
     */
    private fun reachOf(key: Light, height: Float, floor: Float): Float {
        // Everything below assumes the bracket straddles the answer. It cannot
        // when the lamp is already under the floor at its own foot, which is
        // where it is brightest.
        if (key.intensity <= floor) return 0f

        val size = key.radius * key.radius
        // The key's own share at radius r: lambert times attenuation. Both fall,
        // so the product is monotonic and a bracket cannot miss.
        fun share(radius: Float): Float {
            val span = radius * radius + height * height
            val lambert = height / sqrt(span)
            return key.intensity * lambert * ((size + height * height) / (size + span))
        }

        var near = 0f
        var far = height * 20f
        if (share(far) > floor) return far
        repeat(30) {
            val middle = (near + far) / 2f
            if (share(middle) > floor) near = middle else far = middle
        }
        return far
    }
}

/**
 * The light a placed lamp throws on a flat surface, as a gradient can draw it.
 *
 * [foot] is where the lamp stands, in the surface's own coordinates — which is
 * also the centre of the pool, because a point source over a plane is symmetric
 * about the point directly beneath it. [stops] runs from that foot out to
 * [radius] in equal steps, and each one is a real [Lit] rather than an opacity,
 * so a renderer multiplies its own surface colour by them and never composites
 * a wash over something it does not know the colour of.
 */
data class LightPool(val foot: Vec2, val radius: Float, val stops: List<Lit>)
