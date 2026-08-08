package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.PoseMotion
import com.kaiharimoto.mastertool.core.motion.PosePhysics
import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.scene.Puzzle
import com.kaiharimoto.mastertool.core.scene.Surface

/**
 * The puzzle, and the only motion in this room.
 *
 * ## Why it is here rather than in the scene
 *
 * A [com.kaiharimoto.mastertool.core.scene.SceneModel] is furniture: solved once
 * for a board size and an hour, remembered, and identical on every frame until
 * one of those changes. This is the opposite — it has a pose that springs — so
 * it is built the way a card is instead, and this class is deliberately
 * [StageCard] with the parts a card needs and a prop does not taken out.
 *
 * Only [pose] is snapshot state, and it is read **inside the canvas's draw
 * lambda** and nowhere else. That is the same rule the whole stage obeys and the
 * same reason: read in a composable body it would recompose the play screen
 * sixty times a second while the thing is moving, and there are sixty cards in
 * that composition.
 *
 * ## Why it does not idle
 *
 * `docs/DESIGN.md` §11 grants the desk scenes decoration and refuses them
 * ambience, and this class is where that promise is actually kept: [step]
 * returns false and writes nothing at all unless somebody has touched it. A
 * prop that animated on its own would show up here as a loop that never parks,
 * and there is no such loop to write.
 *
 * ## The two phases, and why there is no timer
 *
 * A nudge aims it at [Puzzle.LIFT] with a spring that overshoots, and the frame
 * it arrives, aims it back down with one that does not. So it turns as it rises,
 * hangs for exactly as long as a spring takes to stop, and sinks. Nothing here
 * counts milliseconds — a duration would be a second opinion about how heavy the
 * thing is, and the spring already has one.
 */
internal class StageProp {

    /** Read inside the canvas's draw lambda, never in a composable body. */
    var pose by mutableStateOf(Pose3())
        private set

    /**
     * The board it is standing on.
     *
     * Held rather than passed, because the two callers that need it are the
     * frame loop and the hit test, and neither is in a position to be told: one
     * runs inside `withFrameNanos` and the other inside a pointer event, and
     * threading a layout through both is how the drawn puzzle and the touchable
     * puzzle end up on different boards after a resize.
     */
    private var board: BoardLayout? = null

    private var motion = PoseMotion.at(Pose3())
    private var target = Pose3()

    /** How many times it has been nudged. Its whole memory. */
    private var turns = 0

    private var rising = false
    private var parked = true

    /**
     * Stands it on a board, with no travel.
     *
     * A resize is not a nudge. The mat's coordinates are pixels and every one of
     * them moves when the window changes shape, so a prop that *sprang* to its
     * new place would slide across the desk while the user was dragging a window
     * edge — and would do it from wherever the old board happened to have put
     * it, which is not a place that means anything on the new one.
     */
    fun standOn(layout: BoardLayout) {
        if (board == layout) return
        board = layout
        target = Puzzle.stirred(layout, turns, 0f)
        motion = PoseMotion.at(target)
        pose = target
        rising = false
        parked = true
    }

    /** A finger landed on it: turn, and rise. */
    fun nudge() {
        val layout = board ?: return
        turns++
        rising = true
        parked = false
        target = Puzzle.stirred(layout, turns, 1f)
    }

    /**
     * Springs one frame. Returns whether anything moved, so the loop can count
     * it and so a puzzle nobody has touched costs one boolean.
     */
    fun step(dt: Float): Boolean {
        if (parked) return false
        val layout = board ?: return false

        // Bouncy on the way up because a thing that is answering you should
        // overshoot; Calm on the way down because it is settling under its own
        // weight and nothing lands twice.
        motion = PosePhysics.step(motion, target, if (rising) SpringSpec.Bouncy else SpringSpec.Calm, dt)
        pose = motion.pose

        if (PosePhysics.settled(motion, target)) {
            if (rising) {
                rising = false
                target = Puzzle.stirred(layout, turns, 0f)
            } else {
                motion = PoseMotion.at(target)
                pose = target
                parked = true
            }
        }
        return true
    }

    /**
     * Whether a finger is on it, at the pose it is in **this frame**.
     *
     * Mid-flight rather than at rest, and that is the point: it is at its most
     * inviting halfway up, and a hit test against where it will eventually come
     * back down to would be a target that moves out from under the finger.
     */
    fun holds(plane: StagePlane, eye: Vec3, at: Vec2): Boolean {
        val layout = board ?: return false
        return Puzzle.holds(layout, pose, plane, eye, at)
    }

    /**
     * What to draw and how to order it, for the frame being drawn.
     *
     * Null when it is not standing on a board yet, which is the one frame
     * between the first composition and the `SideEffect` that places it.
     */
    fun drawn(): Prop? {
        val layout = board ?: return null
        return Prop(
            surface = Surface.GOLD,
            box = Puzzle.reach(layout),
            faces = Puzzle.solid(layout, pose),
        )
    }
}
