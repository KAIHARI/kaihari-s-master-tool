package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.max

/**
 * Where a card's shadow lands, and how hard it is.
 *
 * [corners] are on the surface itself, so a renderer drawing on the mat can
 * walk them straight into a path. [spread] is how far to feather the edge and
 * [contact] is the separate, much tighter darkness right underneath a card that
 * is touching — the two together are what actually say "this is resting" versus
 * "this is held above".
 */
data class CardShadow(
    val corners: List<Vec3>,
    val alpha: Float,
    val spread: Float,
    /**
     * How far *inside* the cast edge the fully dark core stops.
     *
     * A real penumbra straddles the geometric edge: it begins a little inside it
     * and ends the same distance outside. Feathering only outward — which is
     * what this renderer has always done — makes the solid core exactly as big
     * as the shadow, so a soft shadow reads as a hard one wearing a halo.
     *
     * Zero when the light has no stated size, which is when [spread] is a
     * heuristic and there is no umbra to find. That is also what keeps the
     * shipped ring renderer producing the pixels it always did.
     */
    val umbra: Float,
    val contact: Float,
    val height: Float,
)

/**
 * Where a set of corners lands when the light pushes them onto a plane, and how
 * far each one had to travel to get there.
 *
 * Extracted from [Shadows.cast] rather than written beside it, because the
 * daylight patch needs the identical arithmetic: a window's aperture thrown onto
 * a desk is a card's outline thrown onto the felt with different numbers, and
 * two copies of that is two places the light can be.
 *
 * [rays] is what a penumbra is measured along — the further a shadow has been
 * thrown, the wider the source looks from where it lands.
 */
data class Landed(val corners: List<Vec3>, val rays: List<Float>)

/**
 * The one thing that makes a card look like it is off the table.
 *
 * Not the scale, and not the parallax: at the angle this table is seen from,
 * both are a few per cent and neither is legible. What reads is that the
 * shadow **separates from the card and softens**. Everything here is in service
 * of that single perception, which is why the shadow is cast properly — every
 * corner projected along the light onto the surface — rather than drawn as an
 * offset copy. An offset copy is right only for a card lying flat, and the
 * moment one tilts in the air, the difference between the two is the whole
 * effect.
 */
object Shadows {

    /** How dark a contact shadow gets, before any height fades it. */
    private const val DARKEST = 0.66f

    /** The height, in card heights, at which the cast shadow has half faded. */
    private const val FADE_OVER = 0.60f

    /** Feathering at rest, and how fast it grows with height, in card heights. */
    private const val SOFT_AT_REST = 0.018f
    private const val SOFT_PER_HEIGHT = 0.34f

    /** How quickly the tight ambient darkness under a card lets go of it. */
    private const val CONTACT_OVER = 0.10f

    /**
     * The shadow a card at [pose] casts onto the plane at [surfaceZ].
     *
     * [bodyDepth] is how far the solid's body hangs behind its printed face, and
     * it is the difference between a deck and a card hovering above one. A pile's
     * pose is its *top card* — that is the arrangement the whole stage is built
     * on, because it is what puts the top of a deck on top of the deck — so a
     * shadow cast from the pose is cast from forty cards up in the air. It comes
     * out displaced by the pile's whole height, softened as if held, and with its
     * contact darkness faded to nothing, which reads exactly as what it is: a
     * card floating over the table with some white geometry standing where the
     * card is not.
     *
     * A solid resting on a table shadows from the part of it *touching the
     * table*. So the casting face is the base, and the height that decides how
     * soft and how faint the shadow is comes from the base too — for a deck that
     * is zero, which is the point: a deck presses onto the felt.
     *
     * Null when the card is at or below the surface, or when the light is
     * parallel to it — a light that never reaches the table cannot cast
     * anything, and the arithmetic that says so is a division by zero.
     */
    fun cast(
        pose: Pose3,
        width: Float,
        height: Float,
        light: Light,
        cardHeight: Float = height,
        surfaceZ: Float = 0f,
        bodyDepth: Float = 0f,
    ): CardShadow? {
        // The face that is actually against the table. At bodyDepth = 0 this is
        // the printed face and everything below behaves exactly as it always did.
        val base = CardSolid.face(pose, width, height, atDepth = bodyDepth)
        val landed = landOn(base, light, surfaceZ) ?: return null

        // Averaged over the four corners rather than taken from the pose, so a
        // card tilted in the air reports the height of its *body* and not of a
        // point that may be nowhere near the surface it is casting onto.
        val above = max(0f, base.map { it.z }.average().toFloat() - surfaceZ)
        val reference = max(cardHeight, 1f)
        val lift = above / reference

        // How wide the source looks from the card, which is the one number a
        // penumbra depends on. Multiplied by how far the shadow was thrown, it
        // *is* the width of the soft edge — where the two terms below were a
        // curve fitted by eye to stand in for exactly this.
        val angle = light.angularRadius(centreOf(base))
        val soft = if (angle <= 0f) {
            // The heuristic, character for character. A light with no stated
            // size has no angle to derive anything from, and the golden records
            // this number.
            reference * (SOFT_AT_REST + lift * SOFT_PER_HEIGHT)
        } else {
            // Floored at the same hairline, because even a point source's edge
            // needs a pixel to be antialiased across.
            max(reference * SOFT_AT_REST, angle * landed.rays.average().toFloat())
        }

        return CardShadow(
            corners = landed.corners,
            alpha = DARKEST / (1f + lift / FADE_OVER),
            spread = soft,
            // The *unfloored* angle, deliberately. `soft` carries a hairline
            // floor so that even a point source's edge has a pixel to be
            // antialiased across — and that argument is about the outer feather
            // only. Letting it set the inward half too would give a hard shadow
            // a two-and-a-half pixel umbra it has not earned, and eat that much
            // off the solid core of every card lying on the felt.
            umbra = if (angle <= 0f) 0f else angle * landed.rays.average().toFloat(),
            // Squared, so it is gone rather than merely faint by the time a
            // card is properly in the air. A linear tail leaves a smudge under
            // a held card, which is precisely the reading the separation
            // between card and cast shadow is trying to establish is wrong.
            contact = 1f / (1f + (lift / CONTACT_OVER) * (lift / CONTACT_OVER)),
            height = above,
        )
    }

    /**
     * Every corner slid along the light until it reaches the plane at [surfaceZ].
     *
     * The card can be tilted and the lamp can be close, so **every corner gets
     * its own ray** — its own direction as well as its own distance. That is the
     * half of this an offset copy cannot do at all, and with a placed lamp it is
     * also what makes a shadow *diverge*: the corners of a card near the bulb
     * spread further apart than the card itself, which is the single most
     * recognisable thing a lamp on a table does and which no directional light
     * can produce.
     *
     * Null when any ray runs parallel to the plane — a light that never reaches
     * the table cannot cast anything, and the arithmetic that says so is a
     * division by zero.
     *
     * **There is deliberately no guard against a negative distance.**
     * `CardSolid.face(…, atDepth)` drops a resting solid's base one thickness
     * *below* the felt, so a card lying on the table legitimately reports a
     * small negative travel today, and the golden records the result. Rejecting
     * it would return null for every card on the board at once.
     */
    fun landOn(corners: List<Vec3>, light: Light, surfaceZ: Float = 0f): Landed? {
        // Nothing to cast is not a shadow. Without this the loop below never
        // runs, the parallel-light bail inside it never fires, and an empty
        // `Landed` comes back for a caller to average into a NaN.
        if (corners.isEmpty()) return null

        val landedAt = ArrayList<Vec3>(corners.size)
        val rays = ArrayList<Float>(corners.size)

        corners.forEach { corner ->
            val travel = light.travelFrom(corner)
            if (abs(travel.z) < 1e-3f) return null
            val distance = (surfaceZ - corner.z) / travel.z
            landedAt += Vec3(
                x = corner.x + travel.x * distance,
                y = corner.y + travel.y * distance,
                z = surfaceZ,
            )
            rays += abs(distance)
        }
        return Landed(landedAt, rays)
    }

    private fun centreOf(corners: List<Vec3>): Vec3 {
        if (corners.isEmpty()) return Vec3.Zero
        var sum = Vec3.Zero
        corners.forEach { sum += it }
        return sum / corners.size.toFloat()
    }
}
