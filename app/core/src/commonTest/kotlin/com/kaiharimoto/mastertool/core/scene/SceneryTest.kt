package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.CameraEnvelope
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Face
import com.kaiharimoto.mastertool.core.render.StageLighting
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What the room is allowed to be.
 *
 * Almost none of this is about how it looks — a preset is numbers somebody
 * chose, and `GoldenStageTest` is the only place in this repository where
 * "today equals yesterday" is worth asserting. What is here instead are the
 * structural claims the room rests on, each of which is invisible until it is
 * broken and then is broken everywhere at once.
 */
class SceneryTest {

    /** A landscape tablet, which is the surface this app is built for. */
    private val surfaceWidth = 1600f
    private val surfaceHeight = 856f
    private val layout: BoardLayout = BoardLayouter.solve(
        width = surfaceWidth,
        height = surfaceHeight,
        aspectRatio = 59f / 86f,
        perspectiveGrowth = 1.2f,
    )

    private val mat get() = Scenery.mat(layout)

    private fun room(time: TimeOfDay = TimeOfDay.NIGHT) =
        Scenery.of(Scene.DESK, time, layout, surfaceWidth, surfaceHeight)

    private fun everyRoom() = Scene.entries.flatMap { scene ->
        TimeOfDay.entries.map { time ->
            "${scene.name}/${time.name}" to
                Scenery.of(scene, time, layout, surfaceWidth, surfaceHeight)
        }
    }

    // ---- the rules that let a room exist at all -------------------------------------

    @Test
    fun nothingInTheRoomStandsOverTheMat() {
        // The load-bearing one. Cards are sorted in the composable tree and the
        // room is painted in one canvas underneath them, so a piece of room that
        // needed to be *in front of* a card could not be — and would silently be
        // painted behind it instead, on some frames and not others. Until there
        // is a retained scene and a real depth sort (docs/AAA.md #92, #93), the
        // felt is the boundary and this is what holds it.
        val felt = SceneBox.standing(mat.left, mat.top, mat.right, mat.bottom, 0f, 0f)
        everyRoom().forEach { (name, model) ->
            model.pieces.forEach { piece ->
                assertTrue(
                    piece.box.max.z <= 0f || !piece.box.overlapsOnFelt(felt),
                    "$name's ${piece.name} stands over the mat",
                )
            }
        }
    }

    @Test
    fun nothingInTheRoomStandsHigherThanTheProjectionCanCarry() {
        // Height reaches the screen through a divide by `distance - depth`, so
        // it does not merely grow with z, it accelerates. A ceiling in card
        // heights is the cheap way to keep every piece far from the pole.
        val ceiling = layout.cardHeight * Scenery.WALL_CEILING
        everyRoom().forEach { (name, model) ->
            model.pieces.forEach { piece ->
                assertTrue(
                    piece.box.max.z <= ceiling,
                    "$name's ${piece.name} stands ${piece.box.max.z} high, over $ceiling",
                )
            }
        }
    }

    @Test
    fun noPieceOfAnyRoomEverCrossesTheLens() {
        // `StagePlane.project` clamps rather than dividing by zero, so a point
        // level with the camera does not crash — it flies a few hundred thousand
        // pixels away and takes its quad with it, which is worse, because it
        // draws. Swept over the whole envelope rather than the three seats,
        // because the hazard is a function of the pose and the seats are three
        // points in it.
        val envelope = CameraEnvelope()
        var pitch = envelope.minPitch
        while (pitch <= envelope.maxPitch) {
            val floor = envelope.minDistanceAt(pitch, surfaceWidth, surfaceHeight)
            repeat(13) { step ->
                val distance = floor + (envelope.maxDistance - floor) * step / 12f
                val plane = CameraPose(0f, pitch, distance).planeFor(surfaceWidth, surfaceHeight)
                everyRoom().forEach { (name, model) ->
                    model.pieces.forEach { piece ->
                        piece.box.corners().forEach { corner ->
                            // Short of the lens, or so far off the glass that
                            // the renderer's own guard is what keeps it there.
                            // The wall's top corner *does* pass the lens at the
                            // closest legal pose — four degrees of pitch at
                            // eight tenths of the distance — and lands entirely
                            // above the picture when it does. That is the
                            // measurement this test carries: the hazard is real
                            // and currently harmless, and `drawScene` drops the
                            // face rather than trusting that to stay true.
                            val at = plane.project(corner)
                            assertTrue(
                                plane.reaches(corner) || at.y < 0f || at.y > surfaceHeight ||
                                    at.x < 0f || at.x > surfaceWidth,
                                "$name's ${piece.name} crosses the lens *and* stays on screen " +
                                    "at pitch $pitch, distance $distance",
                            )
                        }
                    }
                }
            }
            pitch += 6f
        }
    }

    @Test
    fun theRoomIsAHandfulOfObjectsRatherThanAScene() {
        // The play stage already holds about sixty cards against a ceiling
        // docs/AAA.md #92 puts somewhere north of eighty. Whatever the room
        // spends comes out of that, and it is the one budget nobody notices
        // going until the frame readout says so.
        everyRoom().forEach { (name, model) ->
            assertTrue(model.pieces.size <= BUDGET, "$name is ${model.pieces.size}, over $BUDGET")
        }
    }

    @Test
    fun noPieceOfTheRoomStraddlesTheDeskTop() {
        // One line, and it is what makes `ground` and `standing` well defined —
        // which is what lets the felt be drawn *between* them, on the desk and
        // under the lamp.
        everyRoom().forEach { (name, model) ->
            model.pieces.forEach { piece ->
                assertTrue(
                    piece.box.min.z >= 0f || piece.box.max.z <= 0f,
                    "$name's ${piece.name} straddles the table top",
                )
            }
            assertEquals(
                model.pieces.size,
                model.ground.size + model.standing.size,
                "$name loses or duplicates a piece across the split",
            )
        }
    }

    @Test
    fun everyMeshInTheRoomFitsInsideItsOwnBox() {
        // The one test that keeps all the others meaning what they say. A piece
        // used to *be* its box, so "nothing stands over the mat", "nothing
        // crosses the lens", "no two pieces share volume" and the paint order
        // itself were all statements about the thing that gets drawn. Now that a
        // piece may carry a mesh, they are statements about the thing it was
        // sorted by — and they go on being true of the drawing only for as long
        // as the drawing stays inside it.
        var meshes = 0
        everyRoom().forEach { (name, model) ->
            model.pieces.forEach { piece ->
                val mesh = piece.mesh ?: return@forEach
                meshes++
                assertTrue(mesh.isNotEmpty(), "$name's ${piece.name} has an empty mesh")
                mesh.forEach { face ->
                    face.corners.forEach { corner ->
                        assertTrue(
                            corner.x >= piece.box.min.x - GAP && corner.x <= piece.box.max.x + GAP &&
                                corner.y >= piece.box.min.y - GAP && corner.y <= piece.box.max.y + GAP &&
                                corner.z >= piece.box.min.z - GAP && corner.z <= piece.box.max.z + GAP,
                            "$name's ${piece.name} is drawn at $corner, outside ${piece.box}",
                        )
                    }
                }
            }
        }
        assertTrue(meshes > 0, "nothing in the room is a mesh, so this test proves nothing")
    }

    @Test
    fun theLampIsTurnedOnItsOwnFootAndNotBesideIt() {
        // A body of revolution is centred on the axis it was turned about, and
        // the room only ever asks about its box — so if the two came apart, the
        // lamp would be lit from somewhere it is not standing and nothing else
        // would notice. `Turned.sides` returning an even number is what makes
        // this true; this is where it is claimed.
        val foot = Scenery.lampFoot(layout)
        listOf("lamp base", "lamp mast", "lamp shade", "lamp finial").forEach { name ->
            val box = piece(name).box
            assertEquals(foot.x, box.centre.x, 1e-2f, "$name has slid off the lamp's axis in x")
            assertEquals(foot.y, box.centre.y, 1e-2f, "$name has slid off the lamp's axis in y")
        }
    }

    @Test
    fun theLampIsAStackAndEveryJointIsOneNumber() {
        // Four convex pieces because `ScenePiece.mesh` may only hold one, and
        // stacked in z because that is what gives `ScenePainter` an axis that
        // separates every pair of them. A joint computed twice is a seam that
        // opens at some board size nobody tried, so each is read off the same
        // function both sides use.
        assertEquals(0f, piece("lamp base").box.min.z, 1e-3f, "the lamp is not on the desk")
        assertEquals(
            Scenery.baseTop(layout),
            piece("lamp base").box.max.z,
            1e-3f,
            "the base and the stem disagree about where they meet",
        )
        assertEquals(Scenery.baseTop(layout), piece("lamp mast").box.min.z, 1e-3f)
        assertEquals(Scenery.neckTop(layout), piece("lamp mast").box.max.z, 1e-3f)
        assertEquals(Scenery.neckTop(layout), piece("lamp shade").box.min.z, 1e-3f)
        assertEquals(Scenery.shadeTop(layout), piece("lamp shade").box.max.z, 1e-3f)
        assertEquals(Scenery.shadeTop(layout), piece("lamp finial").box.min.z, 1e-3f)

        // And the shade genuinely overhangs the stem it stands on, which is what
        // makes the lamp non-convex and is therefore the reason for all of this.
        assertTrue(
            piece("lamp shade").box.max.x - piece("lamp shade").box.min.x >
                (piece("lamp mast").box.max.x - piece("lamp mast").box.min.x) * 3f,
            "the shade does not overhang the stem, so it need not have been its own piece",
        )
    }

    @Test
    fun noTwoPiecesOfTheRoomShareAnyVolume() {
        // The precondition `ScenePainter` needs: two boxes that overlap on all
        // three axes have no separating axis, so no painter's order is correct
        // for them. Touching on a face is fine and is how the room is joined.
        everyRoom().forEach { (name, model) ->
            model.pieces.forEachIndexed { i, a ->
                model.pieces.drop(i + 1).forEach { b ->
                    assertTrue(
                        !overlaps(a.box, b.box),
                        "$name's ${a.name} and ${b.name} share volume",
                    )
                }
            }
        }
    }

    // ---- the new primitive is the old one, generalised ------------------------------

    @Test
    fun aFlatBoxIsExactlyTheSlabCardSolidWouldHaveBuilt() {
        // This is what made replacing `drawTable`'s geometry safe rather than
        // merely tidy: the minimal stage's table is the same six faces it always
        // was, computed by a primitive that can also stand a wall up.
        val table = Scenery.table(layout)
        val slab = CardSolid.slab(
            pose = Pose3(position = Vec3(table.centerX, table.centerY, 0f)),
            width = table.width,
            height = table.height,
            depth = layout.cardWidth * Scenery.TABLE_THICKNESS,
        )
        val box = Scenery.minimal(layout).pieces.single().box.faces()

        assertEquals(slab.size, box.size)
        slab.forEach { expected ->
            assertTrue(
                box.any { sameFace(it, expected) },
                "no box face matches the slab's ${expected.normal} at ${expected.centre}",
            )
        }
    }

    @Test
    fun everyFaceOfEveryBoxPointsOutOfIt() {
        // A normal pointing inward is not a wrong colour, it is a face the
        // culler throws away — a hole where an object was, on some camera
        // angles only.
        everyRoom().forEach { (name, model) ->
            model.pieces.forEach { piece ->
                piece.box.faces().forEach { face ->
                    assertTrue(
                        face.normal dot (face.centre - piece.box.centre) > 0f,
                        "$name's ${piece.name} has a ${face.normal} face pointing inward",
                    )
                }
            }
        }
    }

    // ---- the desk, the wall and the joint between them -------------------------------

    @Test
    fun theDeskRunsOffEverySideOfThePicture() {
        // A desk with both ends in frame is a table in a void with more steps.
        val desk = piece("desk").box
        assertTrue(desk.min.x < 0f, "the desk's left end is on screen")
        assertTrue(desk.max.x > surfaceWidth, "the desk's right end is on screen")
        assertTrue(desk.max.y > surfaceHeight, "the desk stops before the glass does")
    }

    @Test
    fun thereIsBareDeskAroundTheMatOnEverySideTheCameraCanSee() {
        // This is what actually answers docs/AAA.md #61 — "where the felt stops
        // and the wood starts" — and it is worth a test because it is a
        // consequence of `BoardLayouter` centring the field rather than of
        // anything this file chose. A layout that grew to fill the surface would
        // take it away silently, and the desk would go back to being a border.
        //
        // The near edge is deliberately not asserted: it is off the bottom of
        // the glass at every seat in `CameraEnvelope` and cannot be brought back
        // by dollying. See `Scenery.DESK_NEAR`.
        val desk = piece("desk").box
        assertTrue(mat.left - desk.min.x > layout.cardWidth, "no desk to the left of the mat")
        assertTrue(desk.max.x - mat.right > layout.cardWidth, "no desk to the right of the mat")
        assertTrue(mat.top > 0f, "the mat starts above the glass, so no desk shows past it")
        assertTrue(mat.top - piece("wall left").box.max.y > 0f, "no desk between mat and wall")
    }

    @Test
    fun theWallStandsOnTheDeskRatherThanThroughIt() {
        // The wall used to carry a skirt down to the desk's underside. It is
        // hidden by the desk top from every angle *and painted over it*, because
        // the wall's tall front-top corner outruns the desk's near edge below
        // about twenty-seven degrees of pitch — which ate thirty of the forty
        // pixels of bare wood between the wall and the mat.
        val desk = piece("desk").box
        assertEquals(0f, walls().minOf { it.box.min.z }, 1e-3f, "the wall does not stand on the desk")
        walls().forEach { wall ->
            assertTrue(wall.box.min.z >= 0f, "${wall.name} hangs below the desk top")
            assertTrue(wall.box.min.y >= desk.min.y, "${wall.name} is behind the desk's far edge")
            assertTrue(wall.box.max.y <= desk.max.y, "${wall.name} is past the desk")
        }
        assertEquals(0f, desk.max.z, 1e-3f)
    }

    @Test
    fun theWindowIsAHoleAndTheJambsAreWhatIsLeft() {
        // The four wall pieces and the pane tile the old single wall exactly.
        // No gap and no overlap is what makes a window a hole rather than a
        // bright rectangle stuck on a wall.
        val parts = walls() + piece("pane")
        val union = parts.map { it.box }
        val left = union.minOf { it.min.x }
        val right = union.maxOf { it.max.x }
        val bottom = union.minOf { it.min.z }
        val top = union.maxOf { it.max.z }

        val area = union.sumOf { ((it.max.x - it.min.x) * (it.max.z - it.min.z)).toDouble() }
        assertEquals(
            ((right - left) * (top - bottom)).toDouble(),
            area,
            1.0,
            "the wall's pieces do not tile the wall",
        )
        // And every one of them is the same slab of wall, front to back.
        val face = parts.first().box
        parts.forEach {
            assertEquals(face.min.y, it.box.min.y, 1e-3f, "${it.name} is not in the wall")
            assertEquals(face.max.y, it.box.max.y, 1e-3f, "${it.name} is not in the wall")
        }
    }

    // ---- the lamp --------------------------------------------------------------------

    @Test
    fun theLampStandsWhereItsLightComesFrom() {
        // The compression, pinned. The shade is drawn at a fifth of the height
        // its light actually comes from, because an honest desk lamp on this
        // stage is off the top of the picture — but the foot and the light are
        // exact, and nobody can measure a mast.
        val shade = piece("lamp shade").box
        val lamp = Scenery.lamp(layout)
        assertEquals(shade.centre.x, lamp.x, 1e-3f, "the lamp does not stand under its light")
        assertEquals(shade.centre.y, lamp.y, 1e-3f, "the lamp does not stand under its light")
        assertEquals(
            Scenery.lampHeight(layout) / (layout.cardWidth * Scenery.LAMP_DRAWN),
            lamp.z / shade.max.z,
            1e-3f,
            "the compression has drifted",
        )
        assertTrue(lamp.z > shade.max.z, "the light is not above the shade")
    }

    @Test
    fun theLampIsAsHighAsTodaysShadowsSayItIs() {
        // The solved height, and the guarantee that goes with it — which is
        // about one point and not about every shadow on the board. The ray from
        // the lamp to the *middle of the table* has exactly the shipped preset's
        // horizontal-to-vertical ratio, so a card played in the centre throws
        // the shadow it throws today. Away from the centre both the length and
        // the direction change, which is the whole point of a lamp having a
        // place; see `Scenery.lampHeight`.
        val shipped = StageLighting.DeskNight.key.direction.normalised()
        val wanted = kotlin.math.sqrt(shipped.x * shipped.x + shipped.y * shipped.y) / abs(shipped.z)

        listOf(1600f to 856f, 1280f to 800f, 2000f to 1200f).forEach { (width, height) ->
            val board = BoardLayouter.solve(width, height, 59f / 86f, 1.2f)
            val rig = Scenery.lightingFor(Scene.DESK, TimeOfDay.NIGHT, board)
            val aimed = rig.key.direction.normalised()
            val ratio = kotlin.math.sqrt(aimed.x * aimed.x + aimed.y * aimed.y) / abs(aimed.z)
            assertEquals(wanted, ratio, 1e-4f, "at ${width}x$height the lamp is the wrong height")
        }
    }

    @Test
    fun theLampIsDarkInTheDaytimeAndTheGlassIsDarkAtNight() {
        // Both fixtures on at once is the failure this catches, and it is the
        // one a room gets when its geometry and its lighting are chosen apart.
        assertNull(piece("lamp shade", TimeOfDay.DAY).emission, "the lamp is on in daylight")
        assertNotNull(piece("lamp shade", TimeOfDay.NIGHT).emission, "the lamp is off at night")

        val dayPane = assertNotNull(piece("pane", TimeOfDay.DAY).emission)
        val nightPane = assertNotNull(piece("pane", TimeOfDay.NIGHT).emission)
        assertTrue(dayPane.amount > 0.9f, "daylight is not coming through the window")
        assertTrue(nightPane.amount < 0.05f, "the night sky is lit")
    }

    @Test
    fun theRoomIsTheSameRoomAtEveryHour() {
        // A room does not repaint itself at dusk. What the hour changes is which
        // fixture is emitting and which rig lights the rest.
        val day = room(TimeOfDay.DAY).pieces
        val night = room(TimeOfDay.NIGHT).pieces
        assertEquals(day.map { it.name }, night.map { it.name })
        assertEquals(day.map { it.box }, night.map { it.box })
    }

    // ---- the paint order --------------------------------------------------------------

    @Test
    fun everythingStandingOnTheDeskIsPaintedAfterIt() {
        StageSeat.entries.forEach { seat ->
            val order = painted(seat)
            assertTrue(
                order.indexOf("desk") < order.indexOf("lamp shade"),
                "at ${seat.label} the desk paints after the lamp: $order",
            )
        }
    }

    @Test
    fun nothingBehindTheLampIsEverPaintedOverIt() {
        // The bug this whole object exists for. By nearest-corner depth alone
        // the wall sorts last — it is five hundred pixels tall and its top-front
        // corner reaches further toward the camera than any part of a lamp two
        // hundred and forty tall — and at the seated seat it paints straight
        // over it.
        //
        // Only pairs that actually overlap **on the glass** are asserted about,
        // and that is the claim rather than a weakening of it. Two boxes sitting
        // diagonally apart are separated on more than one axis and those axes
        // give opposite answers; the reason that is not a contradiction is that
        // no ray from the camera hits both, so neither answer is ever seen. The
        // wall's right jamb is the pair that *does* overlap, and it is the pair
        // that was wrong.
        var checked = 0
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            val order = painted(seat)
            val lamp = onGlass(piece("lamp shade"), plane)
            walls().forEach { wall ->
                if (!overlapOnGlass(onGlass(wall, plane), lamp)) return@forEach
                checked++
                assertTrue(
                    order.indexOf(wall.name) < order.indexOf("lamp shade"),
                    "at ${seat.label} ${wall.name} paints over the lamp: $order",
                )
            }
        }
        assertTrue(checked > 0, "no wall overlaps the lamp, so this test proves nothing")
    }

    @Test
    fun theOrderTurnsRoundWhenTheTableDoes() {
        // "Behind" is a fact about where the camera is, not a group a piece
        // belongs to. Asserted on the relation itself rather than on the room,
        // because it is the relation that has to hold for every room there will
        // ever be.
        val far = SceneBox.standing(0f, 0f, 100f, 50f, 0f, 400f)
        val near = SceneBox.standing(0f, 300f, 100f, 350f, 0f, 100f)

        assertTrue(
            ScenePainter.behind(far, near, Vec3(50f, 1200f, 900f)),
            "seen from the player's side, the far box is not behind",
        )
        assertTrue(
            ScenePainter.behind(near, far, Vec3(50f, -900f, 900f)),
            "seen from the other side, the order did not turn round",
        )
    }

    @Test
    fun twoBoxesThatShareAVolumeHaveNoOpinionAboutEachOther() {
        // The honest answer, and the reason `noTwoPiecesOfTheRoomShareAnyVolume`
        // exists: no painter's order is correct for solids that interpenetrate,
        // so this refuses to invent one rather than returning something
        // arbitrary that would look right until it did not.
        val a = SceneBox.standing(0f, 0f, 100f, 100f, 0f, 100f)
        val b = SceneBox.standing(50f, 50f, 150f, 150f, 50f, 150f)
        val eye = Vec3(50f, 900f, 900f)
        assertTrue(!ScenePainter.behind(a, b, eye) && !ScenePainter.behind(b, a, eye))
    }

    // ---- what is actually on the glass ------------------------------------------------

    @Test
    fun theLampAndTheWindowAreOnTheGlassAtEverySeat() {
        // A fixture nobody can see is an assertion rather than an object.
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            listOf("lamp shade", "pane").forEach { name ->
                val points = piece(name).box.corners().map { plane.project(it) }
                assertTrue(
                    points.any { it.x in 0f..surfaceWidth && it.y in 0f..surfaceHeight },
                    "$name is off the glass at ${seat.label}",
                )
            }
        }
    }

    @Test
    fun theFloorClosesHolesTheDeskLeavesWhenTheTableTurns() {
        // Why the floor earns its place, as a measurement rather than an opinion
        // about floors. Turned side-on, the desk's own footprint stops covering
        // the picture and the void shows past its far corner; the floor takes
        // most of that back. It does not take all of it — a second wall or a
        // much larger floor would — and the residual is named here rather than
        // hidden.
        listOf(45f, 90f).forEach { yaw ->
            val plane = CameraPose(yawDegrees = yaw, pitchDegrees = 34f)
                .planeFor(surfaceWidth, surfaceHeight)
            val model = room()
            val withFloor = misses(model.pieces, plane)
            val without = misses(model.pieces.filter { it.name != "floor" }, plane)
            assertTrue(
                withFloor < without,
                "at yaw $yaw the floor covers nothing: $withFloor vs $without",
            )
        }
    }

    @Test
    fun theRoomFillsThePictureFromEverySeat() {
        StageSeat.entries.forEach { seat ->
            val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
            assertEquals(0, misses(room().pieces, plane), "a hole in the picture at ${seat.label}")
        }
    }

    // ---- the minimal stage is untouched -------------------------------------------------

    @Test
    fun theMinimalRoomIsStillOnePieceAndStillLitByNoFixture() {
        TimeOfDay.entries.forEach { time ->
            val model = Scenery.of(Scene.MINIMAL, time, layout, surfaceWidth, surfaceHeight)
            assertEquals(1, model.pieces.size)
            assertEquals(Surface.TABLE, model.pieces.single().surface)
            assertNull(model.pieces.single().emission)
            assertSame(StageLighting.Minimal, model.lighting)
            assertNull(model.lighting.key.position, "the minimal stage has a fixture in it")
            assertEquals(0f, model.lighting.key.radius)
            assertEquals(0f, model.lighting.key.distance)
        }
    }

    // ---- the clock -----------------------------------------------------------------

    @Test
    fun theManualSettingsIgnoreTheHourEntirely() {
        (0..23).forEach { hour ->
            assertEquals(TimeOfDay.DAY, DeskClock.resolve(DeskLight.DAY, hour))
            assertEquals(TimeOfDay.NIGHT, DeskClock.resolve(DeskLight.NIGHT, hour))
        }
    }

    @Test
    fun autoIsDaylightBetweenDawnAndDusk() {
        (0..23).forEach { hour ->
            val expected = if (hour >= DeskClock.DAWN && hour < DeskClock.DUSK) {
                TimeOfDay.DAY
            } else {
                TimeOfDay.NIGHT
            }
            assertEquals(expected, DeskClock.resolve(DeskLight.AUTO, hour), "at $hour")
        }
    }

    @Test
    fun anHourThatIsNotAnHourStillLightsTheRoom() {
        assertEquals(DeskClock.resolve(DeskLight.AUTO, 9), DeskClock.resolve(DeskLight.AUTO, 33))
        assertEquals(DeskClock.resolve(DeskLight.AUTO, 21), DeskClock.resolve(DeskLight.AUTO, -3))
    }

    // ---- helpers -------------------------------------------------------------------

    private fun piece(name: String, time: TimeOfDay = TimeOfDay.NIGHT): ScenePiece =
        room(time).pieces.single { it.name == name }

    private fun walls(): List<ScenePiece> =
        room().pieces.filter { it.surface == Surface.WALL }

    private fun rigEye(plane: StagePlane): Vec3 =
        com.kaiharimoto.mastertool.core.render.StageRig.eye(plane.tiltDegrees, plane.yawDegrees)

    /** The room in the order `drawScene` paints it, by name. */
    private fun painted(seat: StageSeat): List<String> {
        val plane = seat.pose.planeFor(surfaceWidth, surfaceHeight)
        val model = room()
        return ScenePainter
            .order(model.pieces, plane.eyePoint(rigEye(plane))) { box -> plane.reachOf(box) }
            .map { it.name }
    }

    /** A piece's projected bounding box on the glass: left, top, right, bottom. */
    private fun onGlass(piece: ScenePiece, plane: StagePlane): List<Float> {
        val points = piece.box.corners().map { plane.project(it) }
        return listOf(
            points.minOf { it.x },
            points.minOf { it.y },
            points.maxOf { it.x },
            points.maxOf { it.y },
        )
    }

    private fun overlapOnGlass(a: List<Float>, b: List<Float>): Boolean =
        a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]

    /** How many points of a screen grid no piece of the room covers. */
    private fun misses(pieces: List<ScenePiece>, plane: StagePlane): Int {
        val eyeAt = plane.eyePoint(rigEye(plane))
        val quads = pieces.flatMap { piece ->
            CardSolid.visible(piece.box.faces(), eyeAt).map { face ->
                face.corners.map { plane.project(it) }.map { it.x to it.y }
            }
        }
        var missed = 0
        for (column in 0..16) {
            for (row in 0..10) {
                val x = surfaceWidth * column / 16f
                val y = surfaceHeight * row / 10f
                if (quads.none { inside(it, x, y) }) missed++
            }
        }
        return missed
    }

    private fun inside(quad: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var hit = false
        var j = quad.size - 1
        for (i in quad.indices) {
            val (xi, yi) = quad[i]
            val (xj, yj) = quad[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) hit = !hit
            j = i
        }
        return hit
    }

    private fun overlaps(a: SceneBox, b: SceneBox): Boolean =
        a.min.x < b.max.x - GAP && a.max.x > b.min.x + GAP &&
            a.min.y < b.max.y - GAP && a.max.y > b.min.y + GAP &&
            a.min.z < b.max.z - GAP && a.max.z > b.min.z + GAP

    private fun sameFace(a: Face, b: Face): Boolean =
        close(a.normal, b.normal) && close(a.centre, b.centre) &&
            a.corners.size == b.corners.size &&
            a.corners.all { corner -> b.corners.any { close(it, corner) } }

    private fun close(a: Vec3, b: Vec3): Boolean =
        abs(a.x - b.x) < 1e-2f && abs(a.y - b.y) < 1e-2f && abs(a.z - b.z) < 1e-2f

    private companion object {
        /** How many objects the room may add to a stage that already holds sixty. */
        const val BUDGET = 20

        /** Touching on a face is a joint; a hair past it is an overlap. */
        const val GAP = 1e-3f
    }
}
