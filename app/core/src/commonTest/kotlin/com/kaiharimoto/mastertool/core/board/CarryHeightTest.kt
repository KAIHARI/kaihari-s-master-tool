package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.scene.Scenery
import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a card lands where you can see it.
 *
 * The stage hit-tests on the felt and draws in the air, and for as long as those
 * two were the same call the difference did not have a name. It does now: a
 * finger unprojects to z = 0 and the card it is holding is drawn at [CarryHeight],
 * so the two are different points on the table and the gap grows with the lift,
 * the pitch and the tuning. kai's hand tuning roughly doubled it, which is when
 * "a set monster lands in attack position" started being reported.
 */
class CarryHeightTest {

    private val surfaces = listOf(
        2960f to 1848f, // kai's tablet
        1600f to 856f,
        1280f to 800f,
    )

    private fun layoutFor(width: Float, height: Float) = BoardLayouter.solve(
        width = width,
        height = height,
        aspectRatio = 59f / 86f,
        perspectiveGrowth = 1.2f,
        roomAbove = Scenery.ROOM_ABOVE,
    )

    // ---- the two directions are one map ----------------------------------------

    @Test
    fun whereItIsDrawnAndWhereItLandsAreTheSameQuestionBothWaysRound() {
        // The property the whole thing rests on, and the reason `drawnAt` exists
        // even though the stage only calls `landing`: a one-way conversion is a
        // conversion nobody can check.
        surfaces.forEach { (width, height) ->
            val layout = layoutFor(width, height)
            val lift = CarryHeight.hand(layout.cardHeight)
            StageSeat.entries.forEach { seat ->
                val plane = seat.pose.planeFor(width, height)
                listOf(
                    layout.field.centerX to layout.field.centerY,
                    layout.field.left + 20f to layout.field.top + 20f,
                    layout.field.right - 20f to layout.hand.centerY,
                ).forEach { (x, y) ->
                    val down = CarryHeight.landing(x, y, lift, plane)
                    val back = CarryHeight.drawnAt(down.x, down.y, lift, plane)
                    assertTrue(
                        abs(back.x - x) < 0.5f && abs(back.y - y) < 0.5f,
                        "at ${width}x$height ${seat.label} ($x, $y) came back as $back",
                    )
                }
            }
        }
    }

    @Test
    fun aCardInTheAirLandsExactlyWhereItIsDrawn() {
        // Said in screen pixels, which is the only place the claim means
        // anything: the card is drawn by projecting its own mat point at the
        // lift, and it lands by projecting the landing point flat on the felt.
        // Those two projections have to be the same pixel, or the table is
        // telling you one thing and doing another.
        surfaces.forEach { (width, height) ->
            val layout = layoutFor(width, height)
            listOf(
                CarryHeight.mat(layout.cardHeight),
                CarryHeight.hand(layout.cardHeight),
            ).forEach { lift ->
                val plane = StageTuning.DEFAULT.camera.pose().planeFor(width, height)
                val drawnFrom = layout.field.centerX to layout.field.centerY
                val landsAt = CarryHeight.landing(drawnFrom.first, drawnFrom.second, lift, plane)

                val onGlass = plane.project(drawnFrom.first, drawnFrom.second, lift)
                val landedOnGlass = plane.project(landsAt.x, landsAt.y, 0f)

                assertTrue(
                    abs(onGlass.x - landedOnGlass.x) < 0.5f &&
                        abs(onGlass.y - landedOnGlass.y) < 0.5f,
                    "at ${width}x$height a card drawn at $onGlass lands at $landedOnGlass",
                )
            }
        }
    }

    @Test
    fun theOffsetIsBigEnoughToMissAZoneWith() {
        // The measurement, so that "it was a rounding error really" is not
        // available to anybody reading this later. At kai's camera on his own
        // tablet the card out of the hand is drawn most of a card up-table of
        // the finger holding it — which is a whole row of the board.
        val (width, height) = surfaces.first()
        val layout = layoutFor(width, height)
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(width, height)
        val at = layout.field.centerX to layout.field.centerY

        val slid = CarryHeight.landing(at.first, at.second, CarryHeight.mat(layout.cardHeight), plane)
        val played = CarryHeight.landing(at.first, at.second, CarryHeight.hand(layout.cardHeight), plane)

        assertTrue(at.second - slid.y > layout.cardHeight * 0.2f, "slid: ${at.second - slid.y}")
        assertTrue(at.second - played.y > layout.cardHeight * 0.5f, "played: ${at.second - played.y}")
    }

    // ---- and what that was costing ---------------------------------------------

    @Test
    fun aMonsterSetOverAMonsterZoneLandsFaceDownInDefence() {
        // kai's fourth report, end to end and through the real resolver rather
        // than through `SetPosition.of` alone — which `SetPositionTest` already
        // covers and which was never wrong. What was wrong is the point the
        // resolver was asked about.
        //
        // Worth knowing while here, and deliberately not changed: `resolve` asks
        // "a specific card" *before* "a zone's pull", so setting onto a monster
        // zone that already holds a card answers `Stack`, and `SetPosition` then
        // falls back to the card's own kind. That ordering is argued for where it
        // is written; the zone below is empty, which is the case the report was
        // about.
        val (width, height) = surfaces.first()
        val layout = layoutFor(width, height)
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(width, height)
        val field = PlayField()

        val zone = layout[BoardSlot.Zone(FieldZone.Monster(2))]!!
        val lift = CarryHeight.hand(layout.cardHeight)

        // The finger sits below the zone, because the card it is carrying is
        // drawn above it. That offset is the bug and this is what it looks like
        // from the input side.
        val finger = CarryHeight.drawnAt(zone.centerX, zone.centerY, lift, plane)
        val landing = layout.toMat(
            CarryHeight.landing(finger.x, finger.y, lift, plane).let { it.x to it.y },
        )

        val intent = DropTargets.resolve(landing, null, field, layout)
        assertEquals(BoardSlot.Zone(FieldZone.Monster(2)), (intent as? DropIntent.Zone)?.slot)
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(faceDown = true, turned = false, intent = intent, monster = true),
        )

        // And the thing that used to happen: resolved under the finger, the same
        // gesture lands a row nearer the player.
        val underTheFinger = layout.toMat(finger.x to finger.y)
        val wrong = DropTargets.resolve(underTheFinger, null, field, layout)
        assertTrue(
            (wrong as? DropIntent.Zone)?.slot != BoardSlot.Zone(FieldZone.Monster(2)),
            "the test proves nothing: the finger's own point already found the zone",
        )
    }

    // ---- the three lifts are one place -----------------------------------------

    @Test
    fun aCardOutOfTheHandComesUpFurtherThanOneOffTheTableAndASpreadSitsBetween() {
        val cardHeight = 200f
        val mat = CarryHeight.mat(cardHeight)
        val hand = CarryHeight.hand(cardHeight)
        val fan = CarryHeight.fan(cardHeight)

        assertTrue(fan < mat, "a spread is over the board: $fan against $mat")
        assertTrue(mat < hand, "the hand does not come up further: $mat against $hand")
        assertEquals(mat, CarryHeight.of(DragOrigin.Mat(1), cardHeight))
        assertEquals(mat, CarryHeight.of(DragOrigin.Pile(BoardSlot.Deck, 0), cardHeight))
        assertEquals(hand, CarryHeight.of(DragOrigin.Hand(0), cardHeight))
    }

    @Test
    fun withNoCameraAndNothingOffTheFeltItIsTheIdentity() {
        // The frame between the first composition and the `SideEffect` that
        // hands a plane over, and every keyboard shortcut, which has no finger
        // to be offset from.
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(1600f, 856f)
        assertEquals(60f to 70f, CarryHeight.landing(60f, 70f, 100f, null).let { it.x to it.y })
        assertEquals(60f to 70f, CarryHeight.landing(60f, 70f, 0f, plane).let { it.x to it.y })
        assertEquals(60f to 70f, CarryHeight.drawnAt(60f, 70f, 100f, null).let { it.x to it.y })
        assertEquals(60f to 70f, CarryHeight.drawnAt(60f, 70f, 0f, plane).let { it.x to it.y })
    }
}
