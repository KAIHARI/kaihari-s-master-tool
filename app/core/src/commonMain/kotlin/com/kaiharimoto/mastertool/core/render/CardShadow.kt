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
    val contact: Float,
    val height: Float,
)

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
        val travel = light.direction.normalised()
        if (abs(travel.z) < 1e-3f) return null

        // The face that is actually against the table. At bodyDepth = 0 this is
        // the printed face and everything below behaves exactly as it always did.
        val base = CardSolid.face(pose, width, height, atDepth = bodyDepth)

        val corners = base.map { corner ->
            // Slide along the light until it reaches the surface. The card can
            // be tilted, so every corner gets its own distance to travel, which
            // is exactly the part an offset copy cannot do.
            val distance = (surfaceZ - corner.z) / travel.z
            Vec3(
                x = corner.x + travel.x * distance,
                y = corner.y + travel.y * distance,
                z = surfaceZ,
            )
        }

        // Averaged over the four corners rather than taken from the pose, so a
        // card tilted in the air reports the height of its *body* and not of a
        // point that may be nowhere near the surface it is casting onto.
        val above = max(0f, base.map { it.z }.average().toFloat() - surfaceZ)
        val reference = max(cardHeight, 1f)
        val lift = above / reference

        return CardShadow(
            corners = corners,
            alpha = DARKEST / (1f + lift / FADE_OVER),
            spread = reference * (SOFT_AT_REST + lift * SOFT_PER_HEIGHT),
            // Squared, so it is gone rather than merely faint by the time a
            // card is properly in the air. A linear tail leaves a smudge under
            // a held card, which is precisely the reading the separation
            // between card and cast shadow is trying to establish is wrong.
            contact = 1f / (1f + (lift / CONTACT_OVER) * (lift / CONTACT_OVER)),
            height = above,
        )
    }
}
