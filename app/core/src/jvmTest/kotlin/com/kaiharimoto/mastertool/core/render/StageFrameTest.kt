package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.scene.Scene
import com.kaiharimoto.mastertool.core.scene.TimeOfDay
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Writes a contact sheet of the stage to `core/build/stage-frames`.
 *
 * Not an assertion about how the stage should look — `GoldenStageTest` is the
 * only place in this repository where "today equals yesterday" is worth
 * asserting, and pinning a picture would mean re-recording it on every
 * deliberate change. This exists so that whoever is working on the room can
 * *look* at it, which until now required an APK and a tablet.
 *
 * It asserts only that every frame was produced and is not a blank rectangle,
 * because a harness that silently writes black squares is worse than none.
 */
class StageFrameTest {

    private val width = 1600
    private val height = 856
    private val layout = BoardLayouter.solve(
        width = width.toFloat(),
        height = height.toFloat(),
        aspectRatio = 59f / 86f,
        perspectiveGrowth = 1.2f,
    )

    private val out = File("build/stage-frames")

    @Test
    fun everySceneAtEverySeat() {
        val frames = mutableListOf<File>()
        listOf(
            Triple(Scene.MINIMAL, TimeOfDay.NIGHT, "minimal"),
            Triple(Scene.DESK, TimeOfDay.DAY, "desk-day"),
            Triple(Scene.DESK, TimeOfDay.NIGHT, "desk-night"),
        ).forEach { (scene, time, name) ->
            StageSeat.entries.forEach { seat ->
                val file = File(out, "$name-${seat.label.lowercase()}.png")
                StageFrame.render(
                    scene = scene,
                    time = time,
                    layout = layout,
                    width = width,
                    height = height,
                    pose = seat.pose,
                    into = file,
                )
                frames += file
            }
        }
        assertEveryFrameHasSomethingInIt(frames)
    }

    @Test
    fun theRoomFromAllTheWayRound() {
        // The angles the paint order was wrong at, and the ones nothing has ever
        // been looked at from.
        val frames = listOf(0f, 45f, 90f, 145f, 180f, 270f).map { yaw ->
            val file = File(out, "yaw-${yaw.toInt()}.png")
            StageFrame.render(
                scene = Scene.DESK,
                time = TimeOfDay.NIGHT,
                layout = layout,
                width = width,
                height = height,
                pose = CameraPose(yaw, StageSeat.SEATED.pose.pitchDegrees, StageSeat.SEATED.pose.distance),
                into = file,
            )
            file
        }
        assertEveryFrameHasSomethingInIt(frames)
    }

    @Test
    fun theProdAtRestAndHalfwayUp() {
        val frames = listOf(0 to 0f, 1 to 0.5f, 1 to 1f).map { (turns, lifted) ->
            val file = File(out, "puzzle-$turns-${(lifted * 10).toInt()}.png")
            StageFrame.render(
                scene = Scene.DESK,
                time = TimeOfDay.NIGHT,
                layout = layout,
                width = width,
                height = height,
                pose = StageSeat.SEATED.pose,
                turns = turns,
                lifted = lifted,
                into = file,
            )
            file
        }
        assertEveryFrameHasSomethingInIt(frames)
    }

    private fun assertEveryFrameHasSomethingInIt(frames: List<File>) {
        frames.forEach { file ->
            assertTrue(file.isFile && file.length() > 0, "no frame written to $file")
            val image = javax.imageio.ImageIO.read(file)
            val distinct = buildSet {
                var y = 0
                while (y < image.height) {
                    var x = 0
                    while (x < image.width) {
                        add(image.getRGB(x, y))
                        if (size > 8) return@buildSet
                        x += 7
                    }
                    y += 7
                }
            }
            assertTrue(distinct.size > 4, "${file.name} is nearly a flat fill")
        }
    }
}
