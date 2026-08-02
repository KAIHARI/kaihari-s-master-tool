package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.Slot
import kotlin.math.abs

/**
 * What letting go right now would do.
 *
 * The whole of the "smart movement" the table promises lives in this type and
 * the resolver below. Nothing about it is a gesture or a pixel — it is a pure
 * question about where a finger is and what is already on the mat, which is why
 * it can be tested exhaustively without a screen, and why the indicator the user
 * sees can never disagree with what actually happens: they are the same answer.
 */
sealed interface DropIntent {
    /** Put it down exactly here. */
    data class Free(val at: MatPoint) : DropIntent

    /** Pulled into one of the classic zones, which is where it will land. */
    data class Zone(val slot: BoardSlot, val at: MatPoint) : DropIntent

    /** Onto the card already there, making a pile. */
    data class Stack(val onto: Int) : DropIntent

    /** Tucked under the card already there, as Xyz material. */
    data class Attach(val onto: Int) : DropIntent

    data object Hand : DropIntent
    data object Graveyard : DropIntent
    data object Banish : DropIntent
    data object Deck : DropIntent
    data object ExtraDeck : DropIntent

    /** Nothing sensible; it goes back where it came from. */
    data object Cancel : DropIntent

    /** What the indicator should say, in the fewest words that are still true. */
    val label: String
        get() = when (this) {
            is Free -> "Place"
            is Zone -> "Zone"
            is Stack -> "Stack"
            is Attach -> "Attach"
            Hand -> "Hand"
            Graveyard -> "Graveyard"
            Banish -> "Banish"
            Deck -> "Deck"
            ExtraDeck -> "Extra deck"
            Cancel -> "Cancel"
        }
}

/**
 * Where a dragged card would land, decided continuously while it is in the air.
 *
 * Two things make this harder than "what is under the finger", and both are the
 * difference between a table that feels smart and one that feels twitchy.
 *
 * The first is **precedence**. A finger over a card that is itself sitting in a
 * zone could mean stack-on-that-card or snap-into-that-zone, and the answer has
 * to be the same every time. The order below is deliberate: the piles win over
 * everything because they are unambiguous destinations you had to travel to;
 * then a card under the finger, because aiming at a specific card is a more
 * particular act than aiming at a region; then a zone; then the bare mat.
 *
 * The second is **hysteresis**. Every threshold here is a pair, not a number: a
 * target is harder to enter than it is to leave. Without that, a finger resting
 * on a boundary flickers between two intents several times a second, the
 * indicator strobes, and which one you get on release is luck. With it, the
 * intent you have is slightly sticky, which is exactly how a physical thing
 * behaves and reads as confidence rather than as lag.
 */
object DropTargets {

    /**
     * How close the centre of a card must come to a zone's centre to be pulled
     * in, as a fraction of the zone's width — and how far it must go to escape.
     *
     * Enter at just over half a card, so a card roughly over a zone commits to
     * it; leave at nearly a full card, so nudging it around inside that zone
     * does not keep dropping it out again.
     */
    private const val ZONE_ENTER = 0.55f
    private const val ZONE_LEAVE = 0.95f

    /** The same idea for landing on top of another card, against card width. */
    private const val STACK_ENTER = 0.40f
    private const val STACK_LEAVE = 0.72f

    /**
     * How much closer the target you already have is allowed to seem.
     *
     * There are two hysteresis decisions here and they need different scales,
     * which is worth being explicit about because conflating them makes the
     * table feel wrong in one direction or the other.
     *
     * *Whether to snap at all* is the enter/leave pair above, and wants to be
     * generous: a card sitting in a zone should stay claimed by it while you
     * fidget. *Which* zone, when several are tiled a card apart, is a different
     * question — there the honest answer changes at the midline, and being
     * generous would mean dragging a card a zone and a half before the
     * highlight admits it moved. So the incumbent gets a small bias instead,
     * enough to kill jitter exactly on the boundary and not enough to lie.
     */
    private const val INCUMBENT_BIAS = 0.12f

    /** And for the piles and the hand, which are bands rather than points. */
    private const val PILE_ENTER = 0.60f
    private const val PILE_LEAVE = 1.00f

    /**
     * Where a card would land, given where the finger is.
     *
     * @param point the dragged card's centre, in mat fractions
     * @param dragged the instance being dragged, which cannot land on itself
     * @param attaching true when the gesture means "tuck under" rather than
     *   "put on top" — the same position, a different intention, and the only
     *   one the geometry cannot tell you
     * @param previous what was decided last frame, which is what makes the
     *   thresholds sticky
     */
    fun resolve(
        point: MatPoint,
        dragged: Int?,
        field: PlayField,
        layout: BoardLayout,
        previous: DropIntent? = null,
        attaching: Boolean = false,
    ): DropIntent {
        if (layout.cardWidth <= 0f) return DropIntent.Free(point)

        val px = layout.toPixels(point)
        val cardWidth = layout.cardWidth

        // 1. The piles and the hand: unambiguous places you had to travel to.
        pileAt(px, layout, previous)?.let { return it }
        if (inHand(px, layout, previous)) return DropIntent.Hand

        // 2. A specific card. Nearest first, so a finger between two piles
        //    lands on the one it is actually closest to rather than whichever
        //    happens to be earlier in the list.
        val heldOnto = (previous as? DropIntent.Stack)?.onto
            ?: (previous as? DropIntent.Attach)?.onto

        val nearest = field.mat
            .filter { it.id != dragged }
            .minByOrNull {
                distance(px, layout.toPixels(it.at)) -
                    if (it.id == heldOnto) cardWidth * INCUMBENT_BIAS else 0f
            }

        if (nearest != null) {
            val reach = cardWidth * threshold(
                sticky = previous is DropIntent.Stack && previous.onto == nearest.id ||
                    previous is DropIntent.Attach && previous.onto == nearest.id,
                enter = STACK_ENTER,
                leave = STACK_LEAVE,
            )
            if (distance(px, layout.toPixels(nearest.at)) <= reach) {
                return if (attaching) DropIntent.Attach(nearest.id) else DropIntent.Stack(nearest.id)
            }
        }

        // 3. A zone's pull. Only the field zones — the piles were handled above
        //    and have their own, larger, catchment.
        val incumbent = (previous as? DropIntent.Zone)?.slot

        val zone = layout.slots.entries
            .filter { it.key is BoardSlot.Zone }
            .minByOrNull {
                distance(px, centre(it.value)) -
                    if (it.key == incumbent) it.value.width * INCUMBENT_BIAS else 0f
            }

        if (zone != null) {
            val reach = zone.value.width * threshold(
                sticky = previous is DropIntent.Zone && previous.slot == zone.key,
                enter = ZONE_ENTER,
                leave = ZONE_LEAVE,
            )
            if (distance(px, centre(zone.value)) <= reach) {
                return DropIntent.Zone(zone.key, layout.toMat(centre(zone.value)))
            }
        }

        // 4. The mat itself, which is always a valid answer.
        return DropIntent.Free(point)
    }

    /**
     * Whether the pointer is over one of the piles.
     *
     * Their catchment is generous and rectangular rather than radial, because a
     * pile sits at the edge of the mat and half its catchment would otherwise
     * be off the table where no finger can reach.
     */
    private fun pileAt(
        px: Pair<Float, Float>,
        layout: BoardLayout,
        previous: DropIntent?,
    ): DropIntent? {
        val piles = listOf(
            BoardSlot.Graveyard to DropIntent.Graveyard,
            BoardSlot.Banished to DropIntent.Banish,
            BoardSlot.Deck to DropIntent.Deck,
            BoardSlot.ExtraDeck to DropIntent.ExtraDeck,
        )

        return piles.firstNotNullOfOrNull { (slot, intent) ->
            val rect = layout[slot] ?: return@firstNotNullOfOrNull null
            val grow = rect.width * threshold(
                sticky = previous == intent,
                enter = PILE_ENTER - 0.5f,
                leave = PILE_LEAVE - 0.5f,
            )
            if (rect.inflated(grow).contains(px.first, px.second)) intent else null
        }
    }

    private fun inHand(px: Pair<Float, Float>, layout: BoardLayout, previous: DropIntent?): Boolean {
        val grow = layout.cardWidth * threshold(
            sticky = previous == DropIntent.Hand,
            enter = 0.10f,
            leave = 0.45f,
        )
        return layout.hand.inflated(grow).contains(px.first, px.second)
    }

    private fun threshold(sticky: Boolean, enter: Float, leave: Float) = if (sticky) leave else enter

    private fun centre(slot: Slot) = slot.centerX to slot.centerY

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

/**
 * The mat's own coordinates, against the pixels the layout is drawn in.
 *
 * The mat is the field's rectangle: everything the classic zones occupy. Points
 * outside it are perfectly legal — the hand is below it, and a card being
 * dragged over the hand has a y greater than one — so nothing here clamps.
 */
fun BoardLayout.toPixels(point: MatPoint): Pair<Float, Float> =
    (field.left + point.x * field.width) to (field.top + point.y * field.height)

fun BoardLayout.toMat(px: Pair<Float, Float>): MatPoint = MatPoint(
    x = if (field.width > 0f) (px.first - field.left) / field.width else 0.5f,
    y = if (field.height > 0f) (px.second - field.top) / field.height else 0.5f,
)

/** How far apart two mat points are, for tests that want to talk in fractions. */
internal fun MatPoint.near(other: MatPoint, tolerance: Float = 0.001f): Boolean =
    abs(x - other.x) < tolerance && abs(y - other.y) < tolerance
