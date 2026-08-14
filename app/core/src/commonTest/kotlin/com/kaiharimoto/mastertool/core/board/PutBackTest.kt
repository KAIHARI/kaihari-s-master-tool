package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Taking a card out of a spread and putting it straight back.
 *
 * The gesture is "changed my mind", and the reason it is aimed at *the slot the
 * card came out of* rather than at the fan as a whole is arithmetic rather than
 * taste: a spread is laid out over `layout.field`, which is the seven-by-three
 * grid itself, so its footprint covers every zone and every pile on the table.
 * A rule that let the fan outrank the board would mean that while a pile is open
 * you could not put a card down on the board at all — which is the commonest
 * thing anybody does after finding a card.
 */
class PutBackTest {

    private val layout = BoardLayouter.solve(1600f, 1000f, 59f / 86f)
    private val field = PlayField.setUp((1..40).map { CardId(it) }, (101..115).map { CardId(it) })

    /** Somewhere clear of every zone, so nothing else can claim the answer. */
    private val came = MatPoint(0.34f, 0.5f)

    private fun resolve(
        at: MatPoint,
        previous: DropIntent? = null,
        cameOutOf: MatPoint? = came,
    ) = DropTargets.resolve(at, null, field, layout, previous, false, cameOutOf)

    /** [cards] card widths to the right of where the card came from. */
    private fun away(cards: Float): MatPoint {
        val px = layout.toPixels(came)
        return layout.toMat((px.first + layout.cardWidth * cards) to px.second)
    }

    private fun atSlot(slot: BoardSlot): MatPoint {
        val rect = layout[slot]!!
        return layout.toMat(rect.centerX to rect.centerY)
    }

    // ---- the gesture ----------------------------------------------------------

    @Test
    fun droppedBackOnItsOwnSlotItGoesBack() {
        assertEquals(DropIntent.Cancel, resolve(came))
    }

    @Test
    fun theLabelSaysWhatItDoesRatherThanThatItFailed() {
        assertEquals("Put back", DropIntent.Cancel.label)
    }

    @Test
    fun aQuarterOfACardAwayIsStillTheSameGap() {
        assertEquals(DropIntent.Cancel, resolve(away(0.25f)))
    }

    @Test
    fun aWholeCardAwayIsNotTheGapAnyMore() {
        assertNotEquals(DropIntent.Cancel, resolve(away(1f)))
    }

    // ---- and it is sticky, like every other threshold here --------------------

    @Test
    fun harderToEnterThanToLeave() {
        // Two thirds of a card is outside the enter radius and inside the leave
        // one, so which answer you get depends on which one you already had.
        // Without that pair, a finger resting on the boundary strobes between
        // "put back" and "place" several times a second and which one you get on
        // release is luck.
        val edge = away(0.7f)

        assertNotEquals(DropIntent.Cancel, resolve(edge))
        assertEquals(DropIntent.Cancel, resolve(edge, previous = DropIntent.Cancel))
    }

    // ---- what it must not break -----------------------------------------------

    @Test
    fun aFanIsOpenAndTheBoardStillTakesCards() {
        // The whole reason this is aimed at one slot. A monster zone sits well
        // inside any spread's footprint, and dropping a searched card into one
        // has to go on meaning what it means.
        val intent = resolve(atSlot(BoardSlot.Zone(FieldZone.Monster(2))))

        assertIs<DropIntent.Zone>(intent)
    }

    @Test
    fun aFanIsOpenAndTheGraveyardStillTakesCards() {
        // The piles are drawn in the same grid the spread covers, so this is the
        // same hazard as the zones and would have been the more annoying one:
        // "search the deck, send it to the graveyard" is a real line of play.
        assertEquals(DropIntent.Graveyard, resolve(atSlot(BoardSlot.Graveyard)))
    }

    @Test
    fun withNothingOpenNothingIsEverPutBack() {
        // The card is dropped exactly where a spread slot would have been, and
        // with no fan open that is simply a place on the mat.
        assertNotEquals(DropIntent.Cancel, resolve(came, cameOutOf = null))
    }

    @Test
    fun aCardIsPutBackByBeingReleasedAndNothingElseHappens() {
        // `DropCommit` already answers Cancel with null, which is exactly the
        // move that is not made — so the field the table had is the field it
        // keeps, and the card returns to the spread it never really left.
        assertEquals(null, DropCommit.commit(field, DragOrigin.Pile(BoardSlot.Deck, 7), DropIntent.Cancel))
    }
}
