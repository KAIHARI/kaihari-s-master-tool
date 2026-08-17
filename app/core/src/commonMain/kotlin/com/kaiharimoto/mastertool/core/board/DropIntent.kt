package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.HandFan
import com.kaiharimoto.mastertool.core.layout.HandRow
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

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

    /**
     * Into your hand, at a particular place in it.
     *
     * [at] is a *gap*, not a card: zero is before everything, `hand.size` is
     * after everything. It used to be a `data object`, which said "the hand" and
     * could only ever append — so the order of your hand was whatever order the
     * cards happened to arrive in, and the one thing every player does with a
     * hand, which is arrange it, was the one thing this table could not do.
     */
    data class Hand(val at: Int) : DropIntent
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
            is Hand -> "Hand"
            Graveyard -> "Graveyard"
            Banish -> "Banish"
            Deck -> "Deck"
            ExtraDeck -> "Extra deck"
            // Said as the thing it does rather than as the thing it declines to
            // do. It is now reachable on purpose — put a card back in the spread
            // you took it out of — and "Cancel" describes a gesture failing.
            Cancel -> "Put back"
        }
}

/**
 * The gap in an open spread a card came out of, and whether it has ever left it.
 *
 * [at] alone is what the table used to carry, and [at] alone is not enough to
 * mean "put it back". A spread is laid out over `layout.field` — the seven-by-
 * three grid itself — and its cards are drawn lifted, so the felt footprint of
 * the slot a card came out of sits a fifth of a card from a monster zone's
 * centre. A rule that says "near where it started means put it back" therefore
 * also says "near that monster zone means put it back", and the search that was
 * supposed to end with the card on the board ends with it back in the deck.
 *
 * [departed] is the missing half. A put-back is a *change of mind*: the card has
 * to have been taken somewhere before bringing it back can mean anything. Once
 * the card's landing point has been [DEPARTURE] card widths clear of its own
 * slot, the latch is set and stays set — a card brought back and taken out again
 * is still a card that has left, and a latch that could un-arm would flicker
 * exactly where the two catchments overlap, which is where all of this happens.
 *
 * It is a value rather than a flag on the carry so that the arithmetic lives
 * beside the thresholds it has to clear, and so `DropTargets` never has to be
 * told twice about the same slot.
 */
data class FanHome(
    /** Where the slot is, on the felt, in the mat's own fractions. */
    val at: MatPoint,
    /** True once the card has really been taken out of it. */
    val departed: Boolean = false,
) {
    /**
     * This home, having seen the card at [landing]. Idempotent once armed.
     *
     * Measured against the *landing* rather than the finger, because the landing
     * is what the resolver compares — and the two are most of a card apart for a
     * card out of the hand. It is also why [DEPARTURE] has headroom: merely
     * lifting a card out of a spread moves its landing point about a fifth of a
     * card up-table with nobody's hand moving, because a spread floats lower
     * than a carried card does.
     */
    fun seeing(landing: MatPoint, layout: BoardLayout): FanHome {
        if (departed || layout.cardWidth <= 0f) return this
        val (hx, hy) = layout.toPixels(at)
        val (lx, ly) = layout.toPixels(landing)
        val gone = hypot(lx - hx, ly - hy) > layout.cardWidth * DEPARTURE
        return if (gone) copy(departed = true) else this
    }

    companion object {
        /**
         * How far clear of its own slot a card must get before dropping it back
         * there means "put it back" rather than "I have not moved it yet", in
         * card widths.
         *
         * Above `PUT_BACK_LEAVE` (0.88), so the latch cannot arm while the slot
         * still claims the card — otherwise the two would fight on the boundary.
         * Well above the fifth of a card the landing jumps on a bare lift.
         */
        const val DEPARTURE = 1.10f
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
     * How near the gap it came out of a card must be dropped to go back into it.
     *
     * Half a card to claim it, seven eighths to lose it.
     *
     * The obvious rule was "anywhere inside the open fan", and it cannot be used:
     * a spread is laid out over `layout.field`, which *is* the seven-by-three
     * grid, so its footprint covers every zone and every pile on the table.
     * "Inside the fan outranks the board" would mean that while a pile is open
     * you cannot put a card down on the board at all — and putting a searched
     * card straight onto the field is the commonest thing anybody does after
     * finding it.
     *
     * So the question is asked about the one place in the spread that means
     * something: the slot the card was taken *out* of.
     *
     * **And that used to be the whole argument, which said this was "the
     * tightest catchment on the board, and deliberately so". It is not, and the
     * numbers are the report.** A slot of a spread is laid out over the same
     * grid the zones are in, and every fan card is *drawn* lifted, so the slot's
     * footprint on the felt lands a fifth of a card from a monster zone's
     * centre — 34 mat pixels on the shipped stage, against a 94-pixel enter disc
     * that outranks a 104-pixel zone disc. Half a card is 91% of the catchment
     * it was outranking, which is not a tighter target, it is the same target
     * standing in front of another one. Every card of a graveyard search lost
     * the entire monster row, and the hysteresis made it terminal: once "Put
     * back" latched, no point inside the zone was far enough out of the sticky
     * disc to escape.
     *
     * Size cannot separate these two. [FanHome] separates them by **history** —
     * a put-back is a change of mind, and a change of mind needs a mind that
     * changed — and rank 0 below separates them a second way, by asking whether
     * a zone is pulling harder. Either alone leaves aims lost; both together
     * leave none.
     */
    private const val PUT_BACK_ENTER = 0.50f
    private const val PUT_BACK_LEAVE = 0.88f

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
     * @param home where in an open spread this card was sitting before it was
     *   picked up, and whether it has left — null unless the drag started in the
     *   fan that is still open. Dropping it back there puts it back, once it has
     *   been somewhere else first. See [FanHome].
     * @param hand the hand **as it is drawn** — which cards are in the air and
     *   which place is being held open — so the gap the finger is over is
     *   measured against the row the user can actually see. It used to be one
     *   index and a `count - 1` correction, which describes a row of evenly
     *   spaced cards and is not the row the screen draws.
     * @param handStep how far apart the hand's cards sit, in card widths — the
     *   same tuning the pose and the hit box read, because a third reading that
     *   disagreed would be a caret drawn in a gap the card does not go into.
     */
    fun resolve(
        point: MatPoint,
        dragged: Int?,
        field: PlayField,
        layout: BoardLayout,
        previous: DropIntent? = null,
        attaching: Boolean = false,
        home: FanHome? = null,
        hand: HandRow = HandRow.of(field.hand.size),
        handStep: Float = StageTuning.DEFAULT.hand.stepFraction,
    ): DropIntent {
        if (layout.cardWidth <= 0f) return DropIntent.Free(point)

        val px = layout.toPixels(point)
        val cardWidth = layout.cardWidth

        // 0. Back into the gap it came out of — once the card has really been
        //    taken out of it, and only while no zone is pulling harder.
        //
        //    Both halves are load-bearing and neither is enough on its own. The
        //    latch alone fixes an aim made straight from the slot and leaves a
        //    drag that wanders before it aims failing at exactly today's rate,
        //    because by then it has armed and the rule is the old rule. The
        //    comparison alone fixes the zones and leaves the piles, because a
        //    slot at the end of a row is nearer the graveyard than any zone.
        //    Together they leave nothing, which `PutBackTest` measures over
        //    every slot of a real spread rather than over a hand-picked point.
        //
        //    **And the hysteresis on that comparison is capped at half the gap,
        //    which is what makes it a fact about geometry rather than about one
        //    seat.** A bias exists so a decision does not flicker while a finger
        //    trembles on a boundary; it must never be worth so much that it
        //    carries one target past the *centre* of the other, because then the
        //    losing gesture is unreachable no matter how exactly it is aimed.
        //    Both of these were reachable at the seat this was tuned at and
        //    neither is a card width away from breaking: the nearest a spread's
        //    hole gets to a zone centre is 0.178 card widths at the old opening
        //    pitch of 41.5 degrees and **0.072** at the head-height 58, against
        //    an `INCUMBENT_BIAS` of 0.12 — so a latched put-back beat a zone the
        //    finger was pointing exactly at, and a latched zone beat a hole the
        //    finger was pointing exactly at. Two failures in opposite directions
        //    from one number being larger than the distance it was arbitrating.
        //    Half the gap can do neither by construction, and where the two are
        //    comfortably apart it is the whole bias, so nothing about the seat
        //    this was tuned at changes.
        if (home != null && home.departed) {
            val sticky = previous == DropIntent.Cancel
            val homePx = layout.toPixels(home.at)
            // Unbiased, only to name which zone this contest is against: letting
            // hysteresis pick the candidate *and* then bounding itself by the
            // candidate it picked is a loop with no fixed point.
            val gap = nearestZone(px, layout, previous, cap = 0f)
                ?.let { distance(homePx, centre(it.rect)) }
                ?: Float.MAX_VALUE
            val margin = min(cardWidth * INCUMBENT_BIAS, gap * 0.5f)

            val toHome = distance(px, homePx) - if (sticky) margin else 0f
            val reach = cardWidth * threshold(sticky, PUT_BACK_ENTER, PUT_BACK_LEAVE)
            val pull = nearestZone(px, layout, previous, cap = margin)
            if (toHome <= reach && (pull == null || toHome <= pull.biased)) return DropIntent.Cancel
        }

        // 1. The piles and the hand: unambiguous places you had to travel to.
        pileAt(px, layout, previous)?.let { return it }
        if (inHand(px, layout, previous)) {
            return DropIntent.Hand(
                HandFan.insertAt(
                    band = layout.hand,
                    cardWidth = cardWidth,
                    row = hand,
                    count = field.hand.size,
                    x = px.first,
                    stepFraction = handStep,
                ),
            )
        }

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
        val zone = nearestZone(px, layout, previous)

        if (zone != null) {
            val reach = zone.rect.width * threshold(
                sticky = previous is DropIntent.Zone && previous.slot == zone.slot,
                enter = ZONE_ENTER,
                leave = ZONE_LEAVE,
            )
            if (distance(px, centre(zone.rect)) <= reach) {
                return DropIntent.Zone(zone.slot, layout.toMat(centre(zone.rect)))
            }
        }

        // 4. The mat itself, which is always a valid answer.
        return DropIntent.Free(point)
    }

    /** Which zone is pulling, and how hard once its incumbency is paid for. */
    private data class Pull(val slot: BoardSlot, val rect: Slot, val biased: Float)

    /**
     * The zone nearest [px], with the incumbent's bias already subtracted.
     *
     * Extracted because rank 0 now asks it too, and two independent searches
     * over the same map with the same bias are two answers waiting to disagree
     * — which on this table would read as the put-back winning against a zone
     * the highlight says is not the one being aimed at.
     */
    /**
     * @param cap the most the incumbent's bias may be worth here, in pixels.
     *   Unbounded for every caller but the put-back contest, which has to hold
     *   its hysteresis below the distance between the two things it is choosing
     *   between — see rank 0.
     */
    private fun nearestZone(
        px: Pair<Float, Float>,
        layout: BoardLayout,
        previous: DropIntent?,
        cap: Float = Float.MAX_VALUE,
    ): Pull? {
        val incumbent = (previous as? DropIntent.Zone)?.slot
        fun biased(slot: BoardSlot, rect: Slot) = distance(px, centre(rect)) -
            if (slot == incumbent) min(rect.width * INCUMBENT_BIAS, cap) else 0f

        val nearest = layout.slots.entries
            .filter { it.key is BoardSlot.Zone }
            .minByOrNull { biased(it.key, it.value) }
            ?: return null

        return Pull(nearest.key, nearest.value, biased(nearest.key, nearest.value))
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
            sticky = previous is DropIntent.Hand,
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
