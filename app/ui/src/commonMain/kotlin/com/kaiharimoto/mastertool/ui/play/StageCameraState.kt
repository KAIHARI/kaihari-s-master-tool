package com.kaiharimoto.mastertool.ui.play

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.CameraRig
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.StageRig

/**
 * The one piece of the camera that Compose is allowed to see.
 *
 * `CameraRig` lives in core and holds plain fields, because core has no Compose
 * and because a camera that is snapshot state all the way down is a camera that
 * recomposes sixty cards sixty times a second. But *something* has to be
 * observable or the mat's layer would never redraw, so exactly one value is:
 * the [plane] the camera currently describes, written by the frame loop only on
 * the frames the camera actually moved.
 *
 * The rule for reading it is the rule [StageCard] already obeys, and it is the
 * whole reason this class is one field: **read it inside a `graphicsLayer` or a
 * draw lambda, never in a composable body and never as a parameter.** A card
 * that takes the plane as an argument recomposes whenever the camera twitches,
 * and sixty of those is the frame budget. Two other things must not read it
 * either — the layout, which is solved once for the seat the stage opens at,
 * and any `pointerInput` key, which would tear down the single gesture arbiter
 * mid-drag and cancel whatever the user was doing.
 */
@Stable
internal class StageCameraState(val rig: CameraRig) {

    /** Read inside `graphicsLayer` and draw lambdas. Nowhere else. */
    var plane: StagePlane by mutableStateOf(rig.pose.planeFor(0f, 0f))
        private set

    /**
     * Where the viewer is, in the mat's own frame.
     *
     * Derived here rather than at each call site so the two layers of the stage
     * cannot end up shaded from two different eyes — which is exactly what they
     * used to be, one hard-coded straight-on and one tilted, back when a card in
     * the air lived in a frame of its own.
     */
    var eye: Vec3 = Vec3.Toward
        private set

    /** Told the surface, and asked for the plane that goes with it. */
    fun sync(width: Float, height: Float) {
        rig.width = width
        rig.height = height
        val next = rig.pose.planeFor(width, height)
        if (next != plane) {
            plane = next
            eye = StageRig.eye(next.tiltDegrees, next.yawDegrees)
        }
    }

    /**
     * Re-read the rig after something moved it outside the frame loop.
     *
     * A dragged camera is *assigned* rather than sprung — the same rule a
     * carried card obeys — so it parks immediately and the loop's `step` has
     * nothing to report. Without this the plane would not catch up until the
     * next frame the camera happened to be springing, which is never.
     */
    fun refresh() = sync(rig.width, rig.height)

    val pose: CameraPose get() = rig.pose
}
