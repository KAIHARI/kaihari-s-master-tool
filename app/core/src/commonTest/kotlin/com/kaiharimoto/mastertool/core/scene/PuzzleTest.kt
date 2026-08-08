package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.CameraEnvelope
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardSolid
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the one thing on this desk that moves is allowed to do.
 *
 * The room's rules are `SceneryTest`'s and they are all about furniture, which
 * is solved once and then holds still. This is the same rules asked of a shape
 * that turns, rises and is touched — so every one of them has to hold across
 * *every* pose it can reach, not at the one it happens to be resting in.
 */
class PuzzleTest {

    private val surfaceWidth = 1600f
    private val surfaceHeight = 856f
    private val layout: BoardLayout = BoardLayouter.solve(
        width = surfaceWidth,
        height = surfaceHeight,
        aspectRatio = 59f / 86f,
        perspectiveGrowth = 1.2f,
    )

    private val mat get() = Scenery.mat(layout)

    /** Every pose it can be in: a full revolution of nudges, at every lift. */
    private fun everyPose() = (0..11).flatMap { turns ->
        (0..8).map { step ->
            val lifted = step / 8f
            "turn $turns lift $lifted" to Puzzle.stirred(layout, turns, lifted)
        }
    }

    private fun room(time: TimeOfDay = TimeOfDay.NIGHT) =
        Scenery.of(Scene.DESK, time, layout, surfaceWidth, surfaceHeight)

    // ---- the room's rules, asked of something that moves ----------------------------

    @Test
    fun itIsNotOnTheHandbooksStage() {
        // The exception `docs/DESIGN.md` §11 grants is to the desk scenes and to
        // nothing else, and this is a whole ornament — the first object in this
        // app that is there because it is nice. If it ever appears on the minimal
        // stage, that stage has stopped being what the handbook says it is.
        assertTrue(Puzzle.standsIn(Scene.DESK), "the desk has no puzzle on it")
        assertTrue(!Puzzle.standsIn(Scene.MINIMAL), "the minimal stage has decoration on it")
    }

    @Test
    fun itNeverStandsOverTheMatAtAnyTurnOrAnyHeight() {
        // `SceneryTest.nothingInTheRoomStandsOverTheMat` is the load-bearing one:
        // cards are sorted in the composable tree and everything on the desk is
        // painted underneath all of them, so a prop that reached over the felt
        // would be painted *behind* a card it was in front of. Furniture proves
        // that once. This has to prove it for every pose it can be nudged into,
        // which is why the corners are checked and not just the resting box.
        everyPose().forEach { (name, pose) ->
            Puzzle.solid(layout, pose).flatMap { it.corners }.forEach { corner ->
                assertTrue(
                    corner.x < mat.left || corner.x > mat.right ||
                        corner.y < mat.top || corner.y > mat.bottom,
                    "at $name a corner at (${corner.x}, ${corner.y}) is over the felt",
                )
            }
        }
    }

    @Test
    fun itsReachContainsEveryPoseItCanBeIn() {
        // The box is what the rest of the room is compared against, and a box
        // that only holds the resting pose is a prop that walks through the wall
        // the first time somebody touches it.
        val reach = Puzzle.reach(layout)
        everyPose().forEach { (name, pose) ->
            Puzzle.solid(layout, pose).flatMap { it.corners }.forEach { corner ->
                assertTrue(
                    corner.x >= reach.min.x - SLACK && corner.x <= reach.max.x + SLACK &&
                        corner.y >= reach.min.y - SLACK && corner.y <= reach.max.y + SLACK &&
                        corner.z >= reach.min.z - SLACK && corner.z <= reach.max.z + SLACK,
                    "at $name a corner at $corner escapes its own reach",
                )
            }
        }
    }

    @Test
    fun theRoomIsPaintedOverItFromEverySideItShouldBe() {
        // The bug this replaced, and it is worth the length. The puzzle was
        // painted *after* everything standing on the desk, on the reasoning that
        // it stands alone on the bare left with nothing between it and the
        // camera. That reasoning assumed a camera in front of the table, and yaw
        // on this stage is free — walk round past about 145 degrees and you are
        // looking at the room from behind its own wall, where the header above
        // the window really is nearer to you than the desk is. Seated at 150 it
        // drew through the header over a 269 by 280 pixel patch.
        //
        // So it goes into the sort, as its reach box. What this asserts is the
        // consequence: for every piece the painter says is in front of it, the
        // painter also puts that piece after it — which is `order`'s whole
        // contract, and the only reason it holds for a shape with no axes is the
        // box, and the only reason the box works is the test below.
        val reach = Puzzle.reach(layout)
        var checked = 0
        listOf(TimeOfDay.DAY, TimeOfDay.NIGHT).forEach { time ->
            val room = room(time)
            val standIn = ScenePiece("prop", Surface.GOLD, reach)
            StageSeat.entries.forEach { seat ->
                var yaw = 0f
                while (yaw < 360f) {
                    val plane = CameraPose(yaw, seat.pose.pitchDegrees, seat.pose.distance)
                        .planeFor(surfaceWidth, surfaceHeight)
                    val eyeAt = plane.eyePoint(Vec3(0f, 0f, 1f))
                    val order = ScenePainter.order(room.standing + standIn, eyeAt) { box ->
                        box.corners().maxOf { plane.project(it).depth }
                    }
                    val mine = order.indexOfFirst { it === standIn }
                    order.forEachIndexed { index, piece ->
                        if (piece === standIn) return@forEachIndexed
                        if (ScenePainter.behind(reach, piece.box, eyeAt)) {
                            checked++
                            assertTrue(
                                index > mine,
                                "${piece.name} is in front of the puzzle at " +
                                    "${seat.label} yaw $yaw and is painted first",
                            )
                        }
                    }
                    yaw += 5f
                }
            }
        }
        // Against a test that passes because it compared nothing: at the yaws
        // this is really about, the wall is in front and there is something to
        // assert.
        assertTrue(checked > 0, "nothing in the room was ever in front of it")
    }

    @Test
    fun itStandsClearOfEverythingElseInTheRoom() {
        // Including the lamp, which is the other thing out on the bare desk, and
        // the wall, which is what it would back into if it were pushed any
        // further from the mat. Shares a volume with nothing, so `ScenePainter`
        // has a separating axis for it if it is ever asked for one.
        val reach = Puzzle.reach(layout)
        listOf(TimeOfDay.DAY, TimeOfDay.NIGHT).forEach { time ->
            room(time).pieces.forEach { piece ->
                assertTrue(
                    !overlaps(reach, piece.box),
                    "the puzzle shares a volume with the ${piece.name} at $time",
                )
            }
        }
    }

    @Test
    fun itRestsOnTheDeskRatherThanInIt() {
        // The pose is the *top* face and the body hangs down off it, so "resting"
        // is a claim about a number the caller never sees. One card width of
        // error here is a puzzle buried to its shoulders in the wood.
        val apex = Puzzle.solid(layout, Puzzle.rest(layout))
            .flatMap { it.corners }
            .minOf { it.z }
        assertTrue(apex > -SLACK && apex < SLACK, "it rests at z = $apex rather than on the desk")
    }

    @Test
    fun aNudgeMovesItAndAFullRevolutionPutsItBack() {
        // Four-fold symmetry is why [Puzzle.TURN] is not ninety degrees: a
        // quarter turn of a square pyramid is the identity, and a nudge that
        // does nothing anybody can see is a broken easter egg rather than a
        // subtle one.
        val rest = Puzzle.solid(layout, Puzzle.stirred(layout, 0, 0f))
        val nudged = Puzzle.solid(layout, Puzzle.stirred(layout, 1, 0f))
        val moved = rest.zip(nudged).maxOf { (a, b) ->
            a.corners.zip(b.corners).maxOf { (p, q) -> hypot(p.x - q.x, p.y - q.y) }
        }
        assertTrue(moved > layout.cardWidth * 0.3f, "a nudge only moves a corner $moved")

        val round = Puzzle.solid(layout, Puzzle.stirred(layout, 3, 0f))
        round.zip(rest).forEach { (a, b) ->
            a.corners.zip(b.corners).forEach { (p, q) ->
                assertTrue(
                    hypot(p.x - q.x, p.y - q.y) < 1e-2f,
                    "three nudges do not come back round: $p against $q",
                )
            }
        }
    }

    @Test
    fun itIsAClosedSolidWhoseNormalsAllPointOutward() {
        // The taper is what makes it a pyramid rather than a box, and a tapered
        // side quad is the one case where a face normal could plausibly have
        // come out inverted — the two back corners have moved *inward* relative
        // to the front ones. `CardSolid.slab` takes the cross product of the
        // diagonals for exactly this reason, and this is the check that says so.
        everyPose().forEach { (name, pose) ->
            val faces = Puzzle.solid(layout, pose)
            val centre = faces.flatMap { it.corners }
                .fold(Vec3.Zero) { sum, corner -> sum + corner } / (faces.size * 4f)
            assertTrue(faces.size == 6, "at $name it has ${faces.size} faces")
            faces.forEach { face ->
                val outward = face.centre - centre
                assertTrue(
                    (face.normal dot outward) > 0f,
                    "at $name a face normal ${face.normal} points inward",
                )
            }
        }
    }

    // ---- the hit test ---------------------------------------------------------------

    @Test
    fun aFingerWhereItLooksLikeItIsHitsIt() {
        // The whole reason [Puzzle.holds] exists. A solid a hand tall does not
        // appear on screen where its base is, so the pixels the user is aiming
        // at are nowhere near the footprint. Measured on this board, whose cards
        // are 104px wide, the middle of the top face lands 56px from the foot
        // overhead, 102px at the table and 159px seated — so a hit test against
        // the base would miss it outright from the seat the room was built for,
        // and would be a coin toss from the one directly above it.
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            val eye = Vec3(0f, 0f, 1f)
            val pose = Puzzle.rest(layout)
            val top = Puzzle.solid(layout, pose).last().corners
                .map { plane.flatten(it) }
            val at = Vec2(top.sumOf { it.x.toDouble() }.toFloat() / 4f, top.sumOf { it.y.toDouble() }.toFloat() / 4f)

            assertTrue(
                Puzzle.holds(layout, pose, plane, eye, at),
                "${seat.label} cannot touch the middle of its own top face",
            )
            val foot = Puzzle.foot(layout)
            assertTrue(
                hypot(at.x - foot.x, at.y - foot.y) > Puzzle.width(layout) / 2f,
                "${seat.label} draws it close enough to its base that this proves nothing",
            )
        }
    }

    @Test
    fun aFingerOnTheFeltIsNeverOnIt() {
        // The other half, and the one that decides whether the table still
        // orbits: this hit test runs before `claimForCamera`, so a false
        // positive anywhere on the mat is a stage that cannot be turned.
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            val eye = Vec3(0f, 0f, 1f)
            everyPose().forEach { (name, pose) ->
                onTheFelt().forEach { at ->
                    assertTrue(
                        !Puzzle.holds(layout, pose, plane, eye, at),
                        "${seat.label} finds the puzzle on the felt at $at, $name",
                    )
                }
            }
        }
    }

    @Test
    fun theHitTestTurnsWithIt() {
        // The pose goes into both the drawing and the test, so this cannot come
        // apart — which is worth one assertion, because the failure mode if it
        // ever did is the worst kind: a prop you can see and cannot touch.
        val plane = StageSeat.SEATED.pose.planeFor(surfaceWidth, surfaceHeight)
        val eye = Vec3(0f, 0f, 1f)
        val lifted = Puzzle.stirred(layout, 0, 1f)
        val top = Puzzle.solid(layout, lifted).last().corners.map { plane.flatten(it) }
        val at = Vec2(
            top.sumOf { it.x.toDouble() }.toFloat() / 4f,
            top.sumOf { it.y.toDouble() }.toFloat() / 4f,
        )
        assertTrue(Puzzle.holds(layout, lifted, plane, eye, at), "lifted, it cannot be touched")
        assertTrue(
            !Puzzle.holds(layout, Puzzle.rest(layout), plane, eye, at),
            "resting, it is still found where the lifted one was",
        )
    }

    // ---- the seats ------------------------------------------------------------------

    @Test
    fun everySeatCanSeeTheWholeOfIt() {
        // It is out on the left of the desk, which is the part of the room the
        // camera is most likely to swing off the edge of the glass. A prop half
        // off the screen is not an easter egg, it is a rendering artefact.
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            everyPose().forEach { (name, pose) ->
                Puzzle.solid(layout, pose).flatMap { it.corners }.forEach { corner ->
                    val at = plane.project(corner)
                    assertTrue(
                        at.x > 0f && at.x < surfaceWidth && at.y > 0f && at.y < surfaceHeight,
                        "${seat.label} pushes it off the glass to (${at.x}, ${at.y}) at $name",
                    )
                }
            }
        }
    }

    @Test
    fun itNeverCrossesTheLensAnywhereInTheEnvelope() {
        // The room's own version of this is `SceneryTest`'s, and it exists
        // because `StagePlane.project` clamps rather than dividing by zero: a
        // point level with the camera does not crash, it flies a few hundred
        // thousand pixels away and takes its quad with it, which is worse
        // because it draws. Swept over the whole envelope rather than the three
        // seats, because the hazard is a function of the pose and the seats are
        // three points in it — and swept at full lift, which is the only pose
        // that could reach it.
        val envelope = CameraEnvelope()
        val pose = Puzzle.stirred(layout, 0, 1f)
        val corners = Puzzle.solid(layout, pose).flatMap { it.corners }
        var pitch = envelope.minPitch
        while (pitch <= envelope.maxPitch) {
            val floor = envelope.minDistanceAt(pitch, surfaceWidth, surfaceHeight)
            repeat(13) { step ->
                val distance = floor + (envelope.maxDistance - floor) * step / 12f
                val plane = CameraPose(0f, pitch, distance).planeFor(surfaceWidth, surfaceHeight)
                corners.forEach { corner ->
                    assertTrue(
                        plane.reaches(corner),
                        "it crosses the lens at pitch $pitch, distance $distance",
                    )
                }
            }
            pitch += 6f
        }
    }

    @Test
    fun theCameraCanAlwaysSeeSomeOfIt() {
        // Back-face culling is what [Puzzle.holds] tests against, so a pose with
        // no visible face is a pose that cannot be touched at all.
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            val eyeAt = plane.eyePoint(Vec3(0f, 0f, 1f))
            everyPose().forEach { (name, pose) ->
                assertTrue(
                    CardSolid.visible(Puzzle.solid(layout, pose), eyeAt).isNotEmpty(),
                    "${seat.label} can see no face of it at $name",
                )
            }
        }
    }

    private fun onTheFelt(): List<Vec2> = listOf(
        Vec2(mat.centerX, mat.centerY),
        Vec2(mat.left + 1f, mat.top + 1f),
        Vec2(mat.left + 1f, mat.centerY),
        Vec2(mat.left + 1f, mat.bottom - 1f),
        Vec2(mat.right - 1f, mat.centerY),
        Vec2(mat.centerX, mat.top + 1f),
        Vec2(mat.centerX, mat.bottom - 1f),
    )

    private fun overlaps(a: SceneBox, b: SceneBox): Boolean =
        a.min.x < b.max.x - GAP && a.max.x > b.min.x + GAP &&
            a.min.y < b.max.y - GAP && a.max.y > b.min.y + GAP &&
            a.min.z < b.max.z - GAP && a.max.z > b.min.z + GAP

    private companion object {
        /** Touching on a face is a joint; a hair past it is an overlap. */
        const val GAP = 1e-3f

        /** A pixel, against dimensions in the hundreds. */
        const val SLACK = 1e-2f
    }
}
