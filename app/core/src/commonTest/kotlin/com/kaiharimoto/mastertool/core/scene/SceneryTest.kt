package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Face
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the room is allowed to be.
 *
 * Almost none of this is about how it looks — a preset is numbers somebody
 * chose, and `GoldenStageTest` is the only place in this repository where
 * "today equals yesterday" is a thing worth asserting. What is here instead are
 * the four structural claims the room rests on, each of which is invisible
 * until it is broken and then is broken everywhere at once.
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

    // ---- the rule that lets a room exist at all ------------------------------------

    @Test
    fun nothingInTheRoomStandsOverTheMat() {
        // The load-bearing one. Cards are sorted in the composable tree and the
        // room is painted in one canvas underneath them, so a piece of room that
        // needed to be *in front of* a card could not be — and would silently be
        // painted behind it instead, on some frames and not others. Until there
        // is a retained scene and a real depth sort (docs/AAA.md #92, #93), the
        // felt is the boundary and this is what holds it.
        val mat = SceneBox.standing(
            left = Scenery.mat(layout).left,
            top = Scenery.mat(layout).top,
            right = Scenery.mat(layout).right,
            bottom = Scenery.mat(layout).bottom,
            floor = 0f,
            ceiling = 0f,
        )
        Scene.entries.forEach { scene ->
            Scenery.of(scene, layout, surfaceWidth, surfaceHeight).pieces.forEach { piece ->
                assertTrue(
                    piece.box.max.z <= 0f || !piece.box.overlapsOnFelt(mat),
                    "${scene.name}'s ${piece.name} stands over the mat",
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
        Scene.entries.forEach { scene ->
            Scenery.of(scene, layout, surfaceWidth, surfaceHeight).pieces.forEach { piece ->
                assertTrue(
                    piece.box.max.z <= ceiling,
                    "${scene.name}'s ${piece.name} stands ${piece.box.max.z} high, over $ceiling",
                )
            }
        }
    }

    @Test
    fun theRoomIsAHandfulOfObjectsRatherThanAScene() {
        // The play stage already holds about sixty cards against a ceiling
        // docs/AAA.md #92 puts somewhere north of eighty. Whatever the room
        // spends comes out of that, and it is the one budget nobody notices
        // going until the frame readout says so.
        Scene.entries.forEach { scene ->
            val pieces = Scenery.of(scene, layout, surfaceWidth, surfaceHeight).pieces.size
            assertTrue(pieces <= BUDGET, "${scene.name} is $pieces pieces, over $BUDGET")
        }
    }

    // ---- the new primitive is the old one, generalised ------------------------------

    @Test
    fun aFlatBoxIsExactlyTheSlabCardSolidWouldHaveBuilt() {
        // This is what makes replacing `drawTable`'s geometry safe rather than
        // merely tidy: the minimal stage's table is the same six faces it always
        // was, computed by a primitive that can also stand a wall up.
        val table = Scenery.table(layout)
        val thickness = layout.cardWidth * Scenery.TABLE_THICKNESS

        val slab = CardSolid.slab(
            pose = Pose3(position = Vec3(table.centerX, table.centerY, 0f)),
            width = table.width,
            height = table.height,
            depth = thickness,
        )
        val box = Scenery.minimal(layout).pieces.single().box.faces()

        assertEquals(slab.size, box.size)
        slab.forEach { expected ->
            val actual = box.firstOrNull { sameFace(it, expected) }
            assertTrue(
                actual != null,
                "no box face matches the slab's ${expected.normal} at ${expected.centre}",
            )
        }
    }

    @Test
    fun everyFaceOfEveryBoxPointsOutOfIt() {
        // A normal pointing inward is not a wrong colour, it is a face the
        // culler throws away — a hole where an object was, on some camera
        // angles only.
        Scene.entries.forEach { scene ->
            Scenery.of(scene, layout, surfaceWidth, surfaceHeight).pieces.forEach { piece ->
                piece.box.faces().forEach { face ->
                    val outward = face.centre - piece.box.centre
                    assertTrue(
                        face.normal dot outward > 0f,
                        "${piece.name}'s ${face.normal} face points into the box",
                    )
                }
            }
        }
    }

    // ---- the desk ------------------------------------------------------------------

    @Test
    fun theDeskRunsOffEverySideOfThePicture() {
        // A desk with both ends in frame is a table in a void with more steps.
        val desk = pieceNamed("desk")
        assertTrue(desk.box.min.x < 0f, "the desk's left end is on screen")
        assertTrue(desk.box.max.x > surfaceWidth, "the desk's right end is on screen")
        assertTrue(desk.box.max.y > surfaceHeight, "the desk stops before the glass does")
    }

    @Test
    fun theWallStandsBehindTheDeskAndNotOnIt() {
        val desk = pieceNamed("desk")
        val wall = pieceNamed("wall")
        assertTrue(wall.box.max.y <= desk.box.min.y, "the wall is standing on the desk")
        assertTrue(wall.box.max.z > 0f, "the wall does not stand up")
        assertTrue(wall.box.min.x < desk.box.min.x, "the wall stops before the desk does")
        assertTrue(wall.box.max.x > desk.box.max.x, "the wall stops before the desk does")
    }

    @Test
    fun theDeskAndTheWallMeetWithNoVoidBetweenThem() {
        val desk = pieceNamed("desk")
        val wall = pieceNamed("wall")
        assertEquals(desk.box.min.y, wall.box.max.y, 1e-3f, "a seam along the desk's far edge")
        assertEquals(desk.box.min.z, wall.box.min.z, 1e-3f, "a seam under the wall")
    }

    @Test
    fun theMinimalStageHasNoRoomInIt() {
        val minimal = Scenery.of(Scene.MINIMAL, layout, surfaceWidth, surfaceHeight)
        assertEquals(1, minimal.pieces.size)
        assertEquals(Surface.TABLE, minimal.pieces.single().surface)
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
            val expected =
                if (hour >= DeskClock.DAWN && hour < DeskClock.DUSK) TimeOfDay.DAY else TimeOfDay.NIGHT
            assertEquals(expected, DeskClock.resolve(DeskLight.AUTO, hour), "at $hour")
        }
    }

    @Test
    fun anHourThatIsNotAnHourStillLightsTheRoom() {
        // A platform that hands back nonsense should get a lit room rather than
        // an exception thrown out of a draw.
        assertEquals(DeskClock.resolve(DeskLight.AUTO, 9), DeskClock.resolve(DeskLight.AUTO, 33))
        assertEquals(DeskClock.resolve(DeskLight.AUTO, 21), DeskClock.resolve(DeskLight.AUTO, -3))
    }

    // ---- helpers -------------------------------------------------------------------

    private fun pieceNamed(name: String): ScenePiece =
        Scenery.of(Scene.DESK, layout, surfaceWidth, surfaceHeight)
            .pieces.single { it.name == name }

    private fun sameFace(a: Face, b: Face): Boolean =
        close(a.normal, b.normal) && close(a.centre, b.centre) &&
            a.corners.size == b.corners.size &&
            a.corners.all { corner -> b.corners.any { close(it, corner) } }

    private fun close(a: Vec3, b: Vec3): Boolean =
        abs(a.x - b.x) < 1e-2f && abs(a.y - b.y) < 1e-2f && abs(a.z - b.z) < 1e-2f

    private companion object {
        /** How many objects the room may add to a stage that already holds sixty. */
        const val BUDGET = 20
    }
}
