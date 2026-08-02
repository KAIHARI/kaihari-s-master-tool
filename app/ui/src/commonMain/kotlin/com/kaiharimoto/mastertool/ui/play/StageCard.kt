package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    private var motion = PoseMotion.at(Pose3())
    private var parked = true

    /** Puts the card somewhere with no travel — for a card appearing. */
    fun placeAt(pose: Pose3) {
        motion = PoseMotion.at(pose)
        this.pose = pose
        target = pose
        parked = true
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
            motion = PosePhysics.step(carried, target, spec, dt)
            pose = motion.pose
            return true
        }

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
}
