package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.toPixels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatControlsTest {

    private val layout = BoardLayouter.solve(
        width = 1600f,
        height = 1000f,
        aspectRatio = 59f / 86f,
    )

    private fun slot(control: MatControl) = assertNotNull(MatControls.slotFor(control, layout))

    @Test
    fun aShuffleMarkSitsUnderThePileItIsAbout() {
        // The whole of what makes an unlabelled mark legible. A mark under the
        // deck is about the deck; the same mark anywhere else is a mystery.
        MatControl.entries.forEach { control ->
            val pile = assertNotNull(layout[control.pile])
            val mark = slot(control)

            assertEquals(pile.centerX, mark.centerX, "$control is centred under its pile")
            assertTrue(mark.top > pile.bottom, "$control is below its pile")
        }
    }

    @Test
    fun theMarksAreClearOfThePilesAndOfEachOther() {
        val deck = slot(MatControl.SHUFFLE_DECK)
        val extra = slot(MatControl.SHUFFLE_EXTRA)

        assertTrue(
            deck.left > extra.right || extra.left > deck.right,
            "the two marks do not overlap: $deck against $extra",
        )
        MatControl.entries.forEach { control ->
            val pile = assertNotNull(layout[control.pile])
            assertTrue(slot(control).top > pile.bottom, "$control does not touch its pile")
        }
    }

    @Test
    fun theMarksLieInTheBandBetweenTheBoardAndTheHand() {
        // Which is empty on this screen, and is the only place on the felt where
        // something that is not a card can sit without being in the way of one.
        MatControl.entries.forEach { control ->
            val mark = slot(control)
            assertTrue(
                mark.top >= layout.field.bottom,
                "$control starts below the board: ${mark.top} against ${layout.field.bottom}",
            )
            assertTrue(
                mark.bottom <= layout.hand.top,
                "$control ends above the hand: ${mark.bottom} against ${layout.hand.top}",
            )
        }
    }

    @Test
    fun aFingerOnAMarkFindsIt() {
        MatControl.entries.forEach { control ->
            val mark = slot(control)
            assertEquals(control, MatControls.at(layout, mark.centerX, mark.centerY))
        }
    }

    @Test
    fun aFingerAnywhereElseFindsNothing() {
        // Including on the piles themselves, which have their own meaning: a
        // tap on the deck searches it, and a mark that stole that tap would have
        // taken the more important of the two gestures.
        listOf(
            layout[BoardSlot.Deck]!!.centerX to layout[BoardSlot.Deck]!!.centerY,
            layout[BoardSlot.ExtraDeck]!!.centerX to layout[BoardSlot.ExtraDeck]!!.centerY,
            layout.field.centerX to layout.field.centerY,
            layout.hand.centerX to layout.hand.centerY,
        ).forEach { (x, y) ->
            assertNull(MatControls.at(layout, x, y), "nothing is under $x,$y")
        }
    }

    @Test
    fun aMarkIsBigEnoughToHitWithAThumb() {
        // Smaller than a card and not much smaller. This is a tablet, and the
        // handbook's 26dp icon buttons are the floor for something you press.
        MatControl.entries.forEach { control ->
            val mark = slot(control)
            assertTrue(mark.width >= layout.cardWidth * 0.5f, "$control is wide enough: $mark")
            assertTrue(mark.height >= layout.cardWidth * 0.18f, "$control is tall enough: $mark")
            assertTrue(mark.width < layout.cardWidth, "$control is not a card: $mark")
        }
    }

    @Test
    fun aCardPutDownOnAMarkIsStillTheThingYouAreTouching() {
        // The one ordering that could go wrong. The band below the board is
        // somewhere a card can perfectly well be left, and a mark that answered
        // first would make that card unreachable. Stated here as the fact the
        // caller depends on: a mark's footprint is a place on the mat like any
        // other, and the hit test asks the cards before it asks this.
        val mark = slot(MatControl.SHUFFLE_DECK)
        val onMark = layout.toPixels(MatPoint(0.5f, 0.5f))

        assertNotNull(MatControls.at(layout, mark.centerX, mark.centerY))
        assertNull(MatControls.at(layout, onMark.first, onMark.second))
    }
}
