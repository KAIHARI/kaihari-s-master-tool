package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.motion.CardDynamics
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.PoseMotion
import com.kaiharimoto.mastertool.core.motion.PosePhysics
import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec3

/**
 * Where one card is, and where it is heading.
 *
 * Held by the screen's state object rather than by a composable, and that is
 * load-bearing rather than tidiness. A card that leaves the mat for the air
 * layer changes *parent*, which destroys and recreates its composable — so a
 * pose remembered inside the composable would reset to nothing at the exact
 * moment a card is picked up, which is the one frame it must not.
 *
 * Only [pose] is snapshot state. The seven velocities change every frame and
 * nothing ever draws them; keeping those in the snapshot would double the state
 * churn for no readers. And [parked] means a settled card is skipped entirely,
 * so a full board of sixty cards steps the handful that are actually moving.
 */
class StageCard(val id: Int) {

    /** Read inside `graphicsLayer`, never in a composable body. */
    var pose by mutableStateOf(Pose3())
        private set

    var target: Pose3 = Pose3()
        private set

    /**
     * True while a finger is carrying it.
     *
     * A pinned card's position is *assigned* from the finger rather than sprung
     * toward it. Any spring between a finger and the thing it is holding is
     * lag, and lag on a touch drag is the single thing that makes a simulator
     * feel fake. Its rotations still spring, which is where the weight comes
     * from instead.
     */
    var pinned: Boolean = false

    /**
     * How wide the card is being drawn, so the bank scales with the table.
     *
     * The screen already knows; the card would otherwise have to be told the
     * same number by every caller of [step].
     */
    var cardWidth: Float = 0f

    private var motion = PoseMotion.at(Pose3())
    private var parked = true

    /**
     * How fast the finger is dragging it, smoothed.
     *
     * Measured here rather than passed in, because the only honest measurement
     * is the distance between two assigned positions and this is the object
     * that holds both of them.
     */
    private var sweep = Vec3.Zero
    private var carrying = false

    /** Puts the card somewhere with no travel — for a card appearing. */
    fun placeAt(pose: Pose3) {
        motion = PoseMotion.at(pose)
        this.pose = pose
        target = pose
        parked = true
        sweep = Vec3.Zero
    }

    fun aimAt(pose: Pose3) {
        if (pose == target) return
        target = pose
        parked = false
    }

    /**
     * Springs one frame. Returns whether anything moved, so the loop can
     * write [pose] only when there is something new to draw.
     */
    fun step(spec: SpringSpec, dt: Float): Boolean {
        if (pinned) {
            // The finger owns the position outright; only the rotations and the
            // scale are allowed to have opinions about where they are going.
            val carried = motion.copy(
                pose = motion.pose.copy(position = target.position),
                vPosition = Vec3.Zero,
            )
            motion = PosePhysics.step(carried, aimWithBank(dt), spec, dt)
            pose = motion.pose
            return true
        }

        carrying = false
        sweep = Vec3.Zero
        if (parked) return false

        motion = PosePhysics.step(motion, target, spec, dt)
        pose = motion.pose

        if (PosePhysics.settled(motion, target)) {
            motion = PoseMotion.at(target)
            pose = target
            parked = true
        }
        return true
    }

    /**
     * The target, plus however far the sweep leans the card.
     *
     * The bank is added to the *target* rather than to the pose, so it is the
     * springs that wind it on and let it go — the card leans into a drag over
     * two or three frames and levels out over two or three more, instead of
     * snapping to an angle computed from this frame's finger jitter.
     *
     * The first frame of a carry is skipped. A card being picked up is
     * assigned the finger's position in one step from wherever it was lying,
     * which is a legitimate reading of several thousand pixels per second and
     * would flick every card hard sideways at the moment of pickup.
     */
    private fun aimWithBank(dt: Float): Pose3 {
        val step = dt.coerceAtLeast(MIN_FRAME)
        val travelled = target.position - motion.pose.position

        if (!carrying) {
            carrying = true
            sweep = Vec3.Zero
        } else {
            val instant = travelled / step
            sweep = sweep * (1f - SMOOTHING) + instant * SMOOTHING
        }

        val bank = CardDynamics.bank(sweep, cardWidth)
        return target.copy(
            rotX = target.rotX + bank.rotX,
            rotY = target.rotY + bank.rotY,
            rotZ = target.rotZ + bank.rotZ,
        )
    }

    private companion object {
        /**
         * How much of this frame's speed to believe.
         *
         * A finger's reported position is noisy at the pixel level and a
         * per-frame difference amplifies that noise by sixty; without a filter
         * the bank chatters. Low enough to be smooth, high enough that a flick
         * has banked the card before it has crossed a zone.
         */
        const val SMOOTHING = 0.32f

        /** So a very short frame cannot report an infinite speed. */
        const val MIN_FRAME = 1f / 240f
    }
}
