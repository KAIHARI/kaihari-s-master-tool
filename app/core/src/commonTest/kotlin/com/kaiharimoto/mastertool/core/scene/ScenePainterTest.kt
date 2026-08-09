package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.StageRig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That the room can be painted at all.
 *
 * Two claims, and everything the room looks like rests on both. The relation
 * has to be an order — no cycles — or there is no correct sequence to find; and
 * the sort has to actually find it. Each was false in a shipped build, neither
 * was visible until the room grew, and neither would have been caught by
 * looking at three seats.
 */
class ScenePainterTest {

    private val surfaceWidth = 1600f
    private val surfaceHeight = 856f
    private val layout = BoardLayouter.solve(
        width = surfaceWidth,
        height = surfaceHeight,
        aspectRatio = 59f / 86f,
        perspectiveGrowth = 1.2f,
        roomAbove = Scenery.ROOM_ABOVE,
    )

    /** Every camera the stage can reach, near enough: three seats, all the way round. */
    private fun everyView(): List<View> = buildList {
        StageSeat.entries.forEach { seat ->
            var yaw = 0f
            while (yaw < 360f) {
                val plane = CameraPose(yaw, seat.pose.pitchDegrees, seat.pose.distance)
                    .planeFor(surfaceWidth, surfaceHeight)
                // The rig's heading, so that turning the yaw actually moves the
                // camera. Handed a fixed vector it moves the projection and
                // leaves the eye where it was, and every pose asks the same
                // question of `behind`.
                val eyeAt = plane.eyePoint(StageRig.eye(plane.tiltDegrees, plane.yawDegrees))
                add(View("${seat.label} yaw $yaw", eyeAt) { box -> plane.reachOf(box) })
                yaw += 5f
            }
        }
    }

    private fun everyPiece(time: TimeOfDay) =
        Scenery.of(Scene.DESK, time, layout, surfaceWidth, surfaceHeight).pieces

    // ---- it is an order ---------------------------------------------------------------

    @Test
    fun theRoomNeverFormsACycleFromAnySeatOrAnyYaw() {
        // The one that was false. Choosing between disagreeing separating axes
        // put fourteen of sixteen pieces into a single cycle at the overhead
        // seat, and a cycle means *no* sequence is correct — so the sort below
        // could not have been fixed without fixing this first.
        TimeOfDay.entries.forEach { time ->
            val pieces = everyPiece(time)
            everyView().forEach { (where, eyeAt, reachOf) ->
                assertTrue(
                    !hasCycle(pieces, eyeAt, reachOf),
                    "the room at $time is cyclic from $where",
                )
            }
        }
    }

    @Test
    fun twoAxesThatDisagreeAreTakenAsNoAnswerRatherThanAsAVote() {
        // Diagonally apart: a is to the left of b and above it. From an eye at
        // the origin, x says a is in front and z says b is. No ray leaving the
        // eye can hit both, so the honest answer is neither.
        val a = SceneBox(Vec3(10f, 0f, 100f), Vec3(20f, 10f, 110f))
        val b = SceneBox(Vec3(100f, 0f, 10f), Vec3(110f, 10f, 20f))
        val eyeAt = Vec3(0f, 0f, 0f)

        assertFalse(ScenePainter.behind(a, b, eyeAt))
        assertFalse(ScenePainter.behind(b, a, eyeAt))
    }

    @Test
    fun oneSeparatingAxisIsStillAnswered() {
        // The case the whole thing exists for: the lamp stands in front of the
        // wall, they overlap in x and in z, and y answers on its own.
        val wall = SceneBox(Vec3(0f, 0f, 0f), Vec3(200f, 10f, 300f))
        val lamp = SceneBox(Vec3(80f, 60f, 0f), Vec3(120f, 100f, 240f))
        val inTheRoom = Vec3(100f, 900f, 400f)

        assertTrue(ScenePainter.behind(wall, lamp, inTheRoom), "the wall is not behind the lamp")
        assertFalse(ScenePainter.behind(lamp, wall, inTheRoom))
        // And walking round the back swaps it, which is the definition working
        // rather than a case to handle.
        val behindTheWall = Vec3(100f, -900f, 400f)
        assertTrue(ScenePainter.behind(lamp, wall, behindTheWall))
    }

    // ---- and the sort finds it --------------------------------------------------------

    @Test
    fun everyPieceIsPaintedAfterEverythingItStandsInFrontOf() {
        // `order`'s whole contract, asked of the real room at 216 cameras. The
        // adjacent-swap version this replaced could not satisfy it: a piece can
        // only bubble past its neighbour, so anything with no opinion sitting
        // between two pieces that *do* have one blocks the swap forever.
        var edges = 0
        TimeOfDay.entries.forEach { time ->
            val pieces = everyPiece(time)
            everyView().forEach { (where, eyeAt, reachOf) ->
                val order = ScenePainter.order(pieces, eyeAt, reachOf)
                val place = order.indices.associateBy { order[it].name }
                pieces.forEach { a ->
                    pieces.forEach { b ->
                        if (a === b) return@forEach
                        if (!reachOf(a.box).overlaps(reachOf(b.box))) return@forEach
                        if (!ScenePainter.behind(a.box, b.box, eyeAt)) return@forEach
                        edges++
                        assertTrue(
                            place.getValue(a.name) < place.getValue(b.name),
                            "${a.name} stands behind ${b.name} at $where and is painted after it",
                        )
                    }
                }
            }
        }
        // Against a test that passes because it compared nothing.
        assertTrue(edges > 1000, "only $edges pairs had an opinion")
    }

    @Test
    fun everyPieceIsPaintedExactlyOnce() {
        // A topological sort that drops or repeats a node is a room with a hole
        // in it, and the cycle-breaking branch is the place that could do it.
        val pieces = everyPiece(TimeOfDay.NIGHT)
        everyView().forEach { (where, eyeAt, reachOf) ->
            val order = ScenePainter.order(pieces, eyeAt, reachOf)
            assertEquals(pieces.size, order.size, "the room lost a piece at $where")
            assertEquals(pieces.map { it.name }.toSet(), order.map { it.name }.toSet())
        }
    }

    @Test
    fun aRoomOfOneOrNoneIsHandedStraightBack() {
        val eyeAt = Vec3(0f, 900f, 400f)
        val only = listOf(everyPiece(TimeOfDay.DAY).first())

        val nowhere = ScenePainter.Reach(0f, 0f, 0f, 0f, 0f)
        assertEquals(emptyList(), ScenePainter.order(emptyList(), eyeAt) { nowhere })
        assertEquals(only, ScenePainter.order(only, eyeAt) { nowhere })
    }

    private data class View(
        val where: String,
        val eyeAt: Vec3,
        val reachOf: (SceneBox) -> ScenePainter.Reach,
    )

    /** The same relation `order` builds, walked for a cycle rather than for an order. */
    private fun hasCycle(
        pieces: List<ScenePiece>,
        eyeAt: Vec3,
        reachOf: (SceneBox) -> ScenePainter.Reach,
    ): Boolean {
        val count = pieces.size
        val reaches = pieces.map { reachOf(it.box) }
        val after = Array(count) { mutableListOf<Int>() }
        val waiting = IntArray(count)
        for (i in 0 until count) {
            for (j in 0 until count) {
                if (i == j || !reaches[i].overlaps(reaches[j])) continue
                if (ScenePainter.behind(pieces[i].box, pieces[j].box, eyeAt)) {
                    after[i] += j
                    waiting[j]++
                }
            }
        }
        val queue = ArrayDeque((0 until count).filter { waiting[it] == 0 })
        var reached = 0
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            reached++
            after[next].forEach { if (--waiting[it] == 0) queue += it }
        }
        return reached < count
    }
}
