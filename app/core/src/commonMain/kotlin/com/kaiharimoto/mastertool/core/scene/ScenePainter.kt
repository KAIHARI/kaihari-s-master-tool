package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs

/**
 * The order the room has to be painted in, for a camera that can move.
 *
 * ## Why depth alone is not enough
 *
 * Sorting solids by the depth of their nearest corner is the painter's rule, and
 * it is right for solids that do not interpenetrate — which every piece of this
 * room satisfies. It is right about *which of two overlapping boxes is nearer*
 * only when neither is much taller than the other, and the room now contains a
 * counter-example: the wall is five hundred pixels tall and stands behind
 * everything, the lamp is two hundred and forty and stands in front of it, and
 * the wall's top-front corner reaches further toward the camera than any part of
 * the lamp does. By nearest-corner depth the wall sorts last, and at the seated
 * seat it paints straight over the top of the lamp for fifty pixels.
 *
 * Grouping by height does not fix it — both stand on the desk. Grouping by
 * "ground and standing" does not fix it either, for the same reason. The
 * information that resolves it is not in either box alone; it is in the pair,
 * and in where the camera is.
 *
 * ## The separating axis
 *
 * Two axis-aligned boxes that are disjoint along some axis have an unambiguous
 * answer: the one on the far side of that axis *from the eye* is behind. Every
 * pair in this room is disjoint along at least one axis, because nothing
 * interpenetrates. So depth gives the starting order and this gives the
 * correction.
 *
 * It reads the camera because "behind" does. Turn the table halfway round and
 * the wall is in front of the lamp, which is not a special case to handle but
 * the definition working.
 *
 * ## Why it is bubbled rather than sorted
 *
 * [behind] is a *partial* order — two boxes with no separating axis have no
 * opinion about each other — and `sortedWith` requires a total one. Fed a
 * partial comparator it produces an arbitrary order, quietly, on some inputs.
 * A bubble pass is exact for any set whose pieces form a directed acyclic graph
 * under [behind], which the no-interpenetration invariant guarantees, and ten
 * pieces is a hundred comparisons a frame — beneath discussion against a budget
 * of eight thousand microseconds.
 *
 * This is `docs/AAA.md` #93's cheap ancestor and it is honest about being one.
 * A real depth sort across the composable tree is still the work that would let
 * a mug stand on the felt.
 */
object ScenePainter {

    /**
     * The room, furthest first, for a camera at [eyeAt].
     *
     * @param depthOf how near a box comes to the camera — the caller supplies it
     *   because it needs the projection, and this object deliberately knows
     *   nothing about one.
     */
    fun order(
        pieces: List<ScenePiece>,
        eyeAt: Vec3,
        depthOf: (SceneBox) -> Float,
    ): List<ScenePiece> {
        if (pieces.size < 2) return pieces

        val ordered = pieces.sortedBy { depthOf(it.box) }.toMutableList()
        repeat(ordered.size) {
            var settled = true
            for (i in 0 until ordered.size - 1) {
                if (behind(ordered[i + 1].box, ordered[i].box, eyeAt)) {
                    val held = ordered[i]
                    ordered[i] = ordered[i + 1]
                    ordered[i + 1] = held
                    settled = false
                }
            }
            if (settled) return ordered
        }
        return ordered
    }

    /**
     * Whether [a] must be painted before [b]: they are disjoint along some axis,
     * and [a] is on the far side of it from the eye.
     *
     * False when no axis separates them, which is the honest answer rather than
     * a guess — two boxes that overlap on all three axes are interpenetrating,
     * and no painter's order is correct for those.
     */
    fun behind(a: SceneBox, b: SceneBox, eyeAt: Vec3): Boolean {
        // Which way the camera lies from the pair, which is what decides *which*
        // separating axis to believe. Two boxes sitting diagonally apart are
        // separated on more than one axis and those axes disagree — the window's
        // sill is to the left of the lamp *and* behind it *and* below it, and
        // only one of those three is the reason one hides the other. The axis to
        // trust is the one the camera is most nearly looking along, because that
        // is the direction "in front" is measured in.
        //
        // Taking the first axis that happened to separate is what the first
        // version did, and it put the wall's right jamb after the lamp at every
        // seat: they overlap in x, so x abstains and y answers correctly — but
        // the sill separates in x first and answers about a pair that does not
        // overlap on screen at all.
        val toEye = eyeAt - (a.centre + b.centre) * 0.5f

        var answer: Boolean? = null
        var best = -1f
        farther(a.min.x, a.max.x, b.min.x, b.max.x, eyeAt.x)?.let {
            if (abs(toEye.x) > best) { best = abs(toEye.x); answer = it }
        }
        farther(a.min.y, a.max.y, b.min.y, b.max.y, eyeAt.y)?.let {
            if (abs(toEye.y) > best) { best = abs(toEye.y); answer = it }
        }
        farther(a.min.z, a.max.z, b.min.z, b.max.z, eyeAt.z)?.let {
            if (abs(toEye.z) > best) { best = abs(toEye.z); answer = it }
        }
        return answer ?: false
    }

    /**
     * One axis: null when the two spans overlap on it and there is nothing to
     * say, otherwise whether the first is the further away along it.
     *
     * The eye is compared against the *midpoint of the gap* rather than against
     * either box, so a camera sitting exactly between them gets a consistent
     * answer instead of one that depends on which box was asked first.
     */
    private fun farther(
        aMin: Float,
        aMax: Float,
        bMin: Float,
        bMax: Float,
        eye: Float,
    ): Boolean? = when {
        aMax <= bMin -> eye > (aMax + bMin) / 2f
        bMax <= aMin -> eye < (bMax + aMin) / 2f
        else -> null
    }
}
