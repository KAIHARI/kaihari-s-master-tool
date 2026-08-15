package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2

/** One thing the mat was told, and which hand told it. */
data class LaneEvent<T>(
    /** Stable for the life of one gesture; reused once that gesture is over. */
    val lane: Int,
    /** What the press that started this gesture landed on, as the host named it. */
    val token: T?,
    val event: MatEvent,
)

/**
 * Ten hands on one table.
 *
 * ## What this changes, and what it deliberately does not
 *
 * `MatGestureMachine`'s own KDoc states the rule this file argues with, and the
 * rule's *reason* is worth quoting exactly because it survives intact: Compose
 * hit-tests each pointer independently, so **per-card detectors** let one finger
 * start a drag on one card while a second starts a separate drag on another, and
 * no amount of event consumption fixes it because by then there are two gesture
 * loops that each believe they are in charge.
 *
 * There is still one `pointerInput` and still one place that arbitrates. What
 * changes is only the conclusion that one arbiter may hand out one gesture. Two
 * competing drags were a bug when nothing decided between them; two *deliberate*
 * drags are what kai asked for — "one finger on two cards … it supports two
 * handed play for faster playing and actions" — and the difference between the
 * bug and the feature is that this decides, once, at the moment a finger lands.
 *
 * ## The rule, which is one sentence
 *
 * **A finger that lands on something nothing else is holding starts its own
 * gesture; every other finger joins the gesture nearest it.**
 *
 * That single line settles all four cases the table cares about, and settles
 * them the way the existing gestures already work:
 *
 *  - a finger on a *different* card is a second drag — two-handed play;
 *  - a finger on the *same* card is a second finger — the twist, the menu, the
 *    set, all of which have always been two fingers on one card;
 *  - a finger on bare felt beside a held card is the steadying hand, exactly as
 *    it is today, because bare felt is nothing and the nearest gesture takes it;
 *  - a finger on bare felt with nothing else happening is the camera, which the
 *    host claims as it always did.
 *
 * ## What is not two-handed
 *
 * The camera. A pinch is two fingers doing one thing, so a felt press joins a
 * live camera lane rather than opening a second — and there is no arrangement
 * where two independent cameras would mean anything.
 *
 * And **bare felt**, at any number of hands. A contact that lands on nothing
 * joins the nearest live gesture and can never open its own, which is the whole
 * of the palm rejection here and the reason the cap could be raised at all: a
 * hand put down flat on the mat is mostly felt, and every one of those contacts
 * is furniture to whichever gesture it lands nearest.
 *
 * ## What raising the cap cost, said out loud
 *
 * The cap was two, and its argument was that *"three fingers is a hand being put
 * down, which every rule in `MatGestureMachine` is built to ignore"* — true, and
 * those rules are reached only because `nearest` delivers a third contact into a
 * lane that already holds two. At ten, a flat hand landing across several
 * *cards* opens several one-finger gestures instead, and every
 * `landedFingers < 3` guard in the machine sees one.
 *
 * **`MatThreeFingerTest`'s sixteen claims all stay green and none of them covers
 * this**, because they drive `MatGestureMachine` directly and never see a lane.
 * The suite will not tell you. If it bites, the repair already has a name in
 * this repo: the *arrival window* that
 * `MatThreeFingerTest.aThreeFingerSweepStillTakesTheStack` names as the missing
 * piece — how soon after the second finger the third one landed.
 *
 * The second cost is quieter. `frameOut`'s `extras` is credited only when there
 * is exactly one lane, because an unattributable contact cannot be told from the
 * other hand's; at ten lanes that gate is false more often, so the fully-batched
 * two-finger tap resolves to a flip less often than it did. Two tests cover that
 * behaviour and both stay green while the table gets worse, which is exactly why
 * it is written here.
 */
class MatDesk<T>(
    /**
     * What is under a finger, as the host names it.
     *
     * Generic, and compared only with `==`. Core has no idea what a card is and
     * must not learn: the host hands back whatever it uses to name one, this
     * asks whether two fingers landed on the same thing, and a test can drive
     * the whole router with strings.
     */
    private val hitTest: (Vec2) -> T?,
    private val limits: GestureThresholds = GestureThresholds(),
) {

    private inner class Lane(val index: Int) {
        val machine = MatGestureMachine(limits)
        var token: T? = null
        var pressedAt: Vec2 = Vec2.Zero
        val pointers = mutableSetOf<Long>()
    }

    private val lanes = mutableListOf<Lane>()

    /**
     * Every pointer that was down last frame, across the whole desk.
     *
     * The furniture rule, one level up from the machine's own: a gesture only
     * ever starts on a *fresh* landing, so anything left resting when its lane
     * ended is unroutable until it lifts and lands again. Which is why a lane
     * can be dropped the moment it goes idle even with fingers still on it —
     * this is what keeps them inert, and it is one set rather than two.
     */
    private var lastSeen: Set<Long> = emptySet()

    /** True when any lane owns its pointers, which is what the host consumes on. */
    val locked: Boolean get() = lanes.any { it.machine.phase.locked }

    /** The phase of a lane, for a host that needs to know what one is doing. */
    fun phaseOf(lane: Int): MatPhase =
        lanes.firstOrNull { it.index == lane }?.machine.let { it?.phase ?: MatPhase.IDLE }

    /** Where a lane's gesture is pointing, in the mat's own coordinates. */
    fun focusOf(lane: Int): Vec2 = lanes.firstOrNull { it.index == lane }?.machine?.focus ?: Vec2.Zero

    /** The felt claimed a lane's gesture — see [MatGestureMachine.claimForCamera]. */
    fun claimForCamera(lane: Int) {
        lanes.firstOrNull { it.index == lane }?.machine?.claimForCamera()
    }

    fun onFrame(frame: TouchFrame): List<LaneEvent<T>> {
        val here = frame.touches.associate { it.id to it.at }

        // Sorted, because pointer order changes hand between platforms and a
        // router that assigned lanes in event order would give two fingers two
        // different answers on two different devices.
        val landed = frame.touches.filter { it.id !in lastSeen }.sortedBy { it.id }
        landed.forEach { touch -> adopt(touch) }

        // A gesture delivered whole, with nothing ever pressed: Android batches
        // hard enough that a brisk two-finger tap can arrive with both fingers
        // already gone. There is no lane to route it to and no landing to make
        // one from, so the release itself opens one — its machine settles out of
        // IDLE, which is exactly the case `MatGestureMachine.settle` handles.
        val orphanReleases = frame.released.filter { touch -> lanes.none { touch.id in it.pointers } }
        if (orphanReleases.isNotEmpty() && lanes.isEmpty()) {
            val first = orphanReleases.first()
            open(first, hitTest(first.at))?.let { lane ->
                orphanReleases.forEach { lane.pointers += it.id }
            }
        }

        val events = mutableListOf<LaneEvent<T>>()
        // A copy, because a lane may be dropped inside the loop below.
        lanes.toList().forEach { lane ->
            events += lane.wrap(lane.machine.onFrame(lane.frameOut(frame, here)))
        }

        lastSeen = here.keys
        lanes.forEach { lane -> lane.pointers.removeAll { it !in here } }
        sweep()
        return events
    }

    /**
     * The clock, turned by the mat's frame loop.
     *
     * Every lane, because a finger held perfectly still generates no pointer
     * events at all and each hand has its own long press to find.
     */
    fun onTick(timeMillis: Long): List<LaneEvent<T>> {
        val events = mutableListOf<LaneEvent<T>>()
        lanes.toList().forEach { lane ->
            events += lane.wrap(lane.machine.onTick(timeMillis))
        }
        sweep()
        return events
    }

    /** The composable left, or an ancestor took the gesture away. */
    fun cancel(): List<LaneEvent<T>> {
        val events = lanes.toList().flatMap { lane -> lane.wrap(lane.machine.cancel()) }
        lanes.clear()
        lastSeen = emptySet()
        return events
    }

    // ---- routing ---------------------------------------------------------------

    /**
     * Which gesture a finger that has just landed belongs to.
     *
     * The whole of the rule, in the order the cases have to be asked. Every
     * branch that does *not* open a lane hands the pointer to an existing one,
     * so a contact is never dropped on the floor — a finger the router forgot
     * would be a finger no machine ever sees lift, and a gesture that cannot end.
     */
    private fun adopt(touch: Touch) {
        val token = hitTest(touch.at)
        val live = lanes

        val lane = when {
            // The same card, so this is a second finger on one gesture: a twist,
            // a menu, a set. Asked first because it is the only branch that can
            // be decided by identity rather than by geometry, and identity beats
            // proximity — two cards can overlap on a busy board.
            token != null -> live.firstOrNull { it.token == token }
                ?: if (live.size < MAX_LANES) open(touch, token) else nearest(touch)

            // Bare felt. A live camera joins rather than doubling, because a
            // pinch is two fingers doing the one thing the gesture already is.
            live.any { it.machine.phase == MatPhase.CAMERA } ->
                live.first { it.machine.phase == MatPhase.CAMERA }

            // Bare felt beside something already being held: the steadying hand,
            // and the second finger of every two-finger gesture there is, since
            // in practice it lands next to the card rather than on it.
            live.isNotEmpty() -> nearest(touch)

            // Bare felt, nothing else happening. The host claims it for the
            // camera — or, with the camera switched off, nothing at all.
            else -> open(touch, token)
        }

        lane?.pointers?.add(touch.id)
    }

    private fun open(touch: Touch, token: T?): Lane? {
        if (lanes.size >= MAX_LANES) return null
        // The lowest index not in use, so a lane number means something for as
        // long as its gesture lasts and the host's per-lane state can be an
        // array rather than a growing map.
        val index = (0 until MAX_LANES).firstOrNull { free -> lanes.none { it.index == free } }
            ?: return null
        val lane = Lane(index)
        lane.token = token
        lane.pressedAt = touch.at
        lanes += lane
        return lane
    }

    /**
     * The gesture a stray finger belongs to.
     *
     * Measured from where the gesture *is* rather than where it began, because
     * a drag that has crossed the board has taken its meaning with it — and from
     * the press point for a lane opened in this same frame, whose machine has
     * not run yet and whose focus is therefore still the origin.
     */
    private fun nearest(touch: Touch): Lane? = lanes.minByOrNull { lane ->
        val anchor = if (lane.machine.phase == MatPhase.IDLE) lane.pressedAt else lane.machine.focus
        (anchor - touch.at).length
    }

    /** The frame as one lane sees it: its own pointers and nobody else's. */
    private fun Lane.frameOut(frame: TouchFrame, here: Map<Long, Vec2>): TouchFrame {
        val mine = frame.touches.filter { it.id in pointers }
        val released = frame.released.filter { it.id in pointers }

        // Contacts the event mentioned that neither pressed nor lifted — the
        // evidence a fully batched second finger leaves behind. Handed over only
        // when this is the *only* gesture on the mat: with two hands working, an
        // unattributable contact is far more likely to be the other hand than a
        // phantom of this one, and crediting it here is how a resting palm turns
        // an ordinary tap into a flipped card.
        val lifted = pointers.count { it !in here }
        val extras = if (lanes.size == 1) {
            (frame.seen - here.size - lifted).coerceAtLeast(0)
        } else {
            0
        }

        return TouchFrame(
            timeMillis = frame.timeMillis,
            touches = mine,
            seen = mine.size + released.size + extras,
            released = released,
        )
    }

    private fun Lane.wrap(events: List<MatEvent>): List<LaneEvent<T>> =
        events.map { LaneEvent(index, token, it) }

    /**
     * Forgets lanes that have finished.
     *
     * A lane may go idle with fingers still on the glass — a carried card lands
     * the moment the finger that lifted it lets go, whatever else is resting —
     * and those fingers must not start anything. [lastSeen] is what makes them
     * inert, so dropping the lane costs nothing and keeps lane numbers, and the
     * host's per-lane state, from accumulating.
     *
     * Pointers are pruned by the caller and not here, because the clock has no
     * frame to prune against: sweeping against an empty hand on a tick would
     * take every finger off every live lane, and the next frame would settle a
     * gesture nobody had let go of.
     */
    private fun sweep() {
        lanes.removeAll { it.machine.phase == MatPhase.IDLE }
    }

    private companion object {
        /**
         * Ten fingers, which is all of them.
         *
         * kai asked for it in those words — *"expand it to 10 fingers, meaning
         * you can make actions 10 times simultaneously"* — and the number is
         * the hands rather than a limit anybody will reach: a lane costs a
         * `MatGestureMachine` and about seven short-lived objects a frame, so
         * ten idle slots cost nothing and ten live ones cost less than one card
         * being drawn.
         *
         * **It is the only number that moved.** The routing did not, and that is
         * deliberate: only a finger that lands on a *token* can open a lane, so
         * bare felt still joins the nearest gesture however many are live. That
         * is the palm rejection, it is structural rather than heuristic, and it
         * is why ten fingers on ten overlapping hand cards each get their own
         * gesture while a hand laid on the felt still gets none.
         *
         * What it did cost is in the class KDoc above, under *what raising the
         * cap cost* — the three-finger guards stop being *reached* on a board
         * full of cards, and no test in this repo can see that happen.
         */
        const val MAX_LANES = 10
    }
}
