package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DropIntentTest {

    private val layout = BoardLayouter.solve(1600f, 1000f, 59f / 86f)

    private val field = PlayField.setUp((1..40).map { CardId(it) }, (101..115).map { CardId(it) })

    /** A field with one card sitting at [at]. */
    private fun withCardAt(at: MatPoint): Pair<PlayField, Int> {
        val f = field.draw()!!.playFromHand(0, at, CardPosition.FACE_UP_ATK)!!
        return f to f.mat.single().id
    }

    private fun resolve(
        at: MatPoint,
        on: PlayField = field,
        dragged: Int? = null,
        previous: DropIntent? = null,
        attaching: Boolean = false,
    ) = DropTargets.resolve(at, dragged, on, layout, previous, attaching)

    /** The mat point that lands on a given slot's centre. */
    private fun atSlot(slot: BoardSlot): MatPoint {
        val rect = layout[slot]!!
        return layout.toMat(rect.centerX to rect.centerY)
    }

    // ---- the plain answer ----------------------------------------------------

    @Test
    fun theBareMatIsAlwaysAValidAnswer() {
        // Somewhere in the middle of the mat, clear of every zone: the card
        // simply stays where you put it, which is the point of a freeform table.
        val nowhere = MatPoint(0.5f, 0.02f)
        val intent = resolve(nowhere)

        assertIs<DropIntent.Free>(intent)
        assertTrue(intent.at.near(nowhere))
    }

    @Test
    fun aDegenerateLayoutStillAnswers() {
        // A surface with no room resolves to "where you put it" rather than
        // dividing by zero.
        val flat = BoardLayouter.solve(0f, 0f, 59f / 86f)

        assertIs<DropIntent.Free>(
            DropTargets.resolve(MatPoint.Centre, null, field, flat, null),
        )
    }

    // ---- zones pull ----------------------------------------------------------

    @Test
    fun releasingOverAZoneSnapsIntoIt() {
        val slot = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(2))
        val intent = resolve(atSlot(slot))

        assertIs<DropIntent.Zone>(intent)
        assertEquals(slot, intent.slot)
    }

    @Test
    fun aZoneSnapReportsWhereTheCardWillActuallyLand() {
        // The indicator and the outcome are the same answer, so the card lands
        // where the highlight said it would rather than near it.
        val slot = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(0))
        val rect = layout[slot]!!
        val intent = resolve(atSlot(slot)) as DropIntent.Zone

        assertTrue(intent.at.near(layout.toMat(rect.centerX to rect.centerY)))
    }

    @Test
    fun aCardWellClearOfEveryZoneIsNotPulledIntoOne() {
        // The top edge of the mat is above the extra monster row and clear of it.
        assertIs<DropIntent.Free>(resolve(MatPoint(0.5f, 0.0f)))
    }

    // ---- the piles and the hand win ------------------------------------------

    @Test
    fun thePilesBeatEverythingElse() {
        // You had to travel to a pile; nothing else at that spot is what you meant.
        assertEquals(DropIntent.Graveyard, resolve(atSlot(BoardSlot.Graveyard)))
        assertEquals(DropIntent.Banish, resolve(atSlot(BoardSlot.Banished)))
        assertEquals(DropIntent.Deck, resolve(atSlot(BoardSlot.Deck)))
        assertEquals(DropIntent.ExtraDeck, resolve(atSlot(BoardSlot.ExtraDeck)))
    }

    @Test
    fun aPileBeatsACardSittingOnIt() {
        // Even with a card parked on the graveyard, dragging there means the
        // graveyard — the pile is a destination, the card is scenery.
        val (on, _) = withCardAt(atSlot(BoardSlot.Graveyard))

        assertEquals(DropIntent.Graveyard, resolve(atSlot(BoardSlot.Graveyard), on = on))
    }

    @Test
    fun droppingOverTheHandPutsItBackInTheHand() {
        val inHand = layout.toMat(layout.hand.centerX to layout.hand.centerY)

        assertEquals(DropIntent.Hand, resolve(inHand))
    }

    // ---- stacking ------------------------------------------------------------

    @Test
    fun droppingOnACardStacksOnIt() {
        val where = MatPoint(0.5f, 0.5f)
        val (on, id) = withCardAt(where)

        val intent = resolve(where, on = on)
        assertIs<DropIntent.Stack>(intent)
        assertEquals(id, intent.onto)
    }

    @Test
    fun aCardCannotBeStackedOnItself() {
        // The card being dragged is not a target, or every drag would resolve
        // to "stack on the thing in your hand".
        val where = MatPoint(0.5f, 0.5f)
        val (on, id) = withCardAt(where)

        assertIs<DropIntent.Zone>(resolve(where, on = on, dragged = id))
    }

    @Test
    fun theNearestCardWins() {
        // A finger between two cards lands on the one it is closest to, not on
        // whichever happens to be earlier in the list.
        var on = field
        repeat(2) { on = on.draw()!! }
        on = on.playFromHand(0, MatPoint(0.40f, 0.5f), CardPosition.FACE_UP_ATK)!!
        on = on.playFromHand(0, MatPoint(0.60f, 0.5f), CardPosition.FACE_UP_ATK)!!
        val (left, right) = on.mat[0].id to on.mat[1].id

        assertEquals(left, (resolve(MatPoint(0.42f, 0.5f), on = on) as DropIntent.Stack).onto)
        assertEquals(right, (resolve(MatPoint(0.58f, 0.5f), on = on) as DropIntent.Stack).onto)
    }

    @Test
    fun stackingBeatsAZoneUnderIt() {
        // Aiming at a particular card is a more specific act than aiming at a
        // region, so a card parked in a zone is what you get.
        val slot = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(2))
        val (on, id) = withCardAt(atSlot(slot))

        val intent = resolve(atSlot(slot), on = on)
        assertIs<DropIntent.Stack>(intent)
        assertEquals(id, intent.onto)
    }

    @Test
    fun theSameSpotMeansAttachWhenTheGestureSaysSo() {
        // Geometry cannot tell "put on top" from "tuck underneath" — only the
        // gesture can, so the resolver is told rather than guessing.
        val where = MatPoint(0.5f, 0.5f)
        val (on, id) = withCardAt(where)

        val intent = resolve(where, on = on, attaching = true)
        assertIs<DropIntent.Attach>(intent)
        assertEquals(id, intent.onto)
    }

    // ---- hysteresis: an intent is slightly sticky -----------------------------

    @Test
    fun aZoneYouAreAlreadyInIsHarderToLeaveThanItWasToEnter() {
        // Without this a card lifted slightly off a zone flickers between
        // "zone" and "place" several times a second, and which one you get on
        // release is luck. Probed vertically, away from the row, so this tests
        // whether to snap at all rather than which zone.
        val slot = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(2))
        val rect = layout[slot]!!

        val above = layout.toMat(rect.centerX to (rect.centerY - rect.width * 0.75f))

        val cold = resolve(above)
        val sticky = resolve(above, previous = DropIntent.Zone(slot, atSlot(slot)))

        assertTrue(cold !is DropIntent.Zone || cold.slot != slot, "should not commit from cold")
        assertIs<DropIntent.Zone>(sticky)
        assertEquals(slot, sticky.slot, "and should hold on once it has")
    }

    @Test
    fun draggingAlongARowStillChangesZoneAtTheBoundary() {
        // The other half of the same rule: which zone you are over changes
        // honestly at the midline. Being sticky here would mean dragging a card
        // most of the way into its neighbour before the highlight admitted it.
        val from = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(2))
        val to = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(3))

        val overNext = atSlot(to)
        val intent = resolve(overNext, previous = DropIntent.Zone(from, atSlot(from)))

        assertIs<DropIntent.Zone>(intent)
        assertEquals(to, intent.slot, "sticky, not glued")
    }

    @Test
    fun aStackedIntentIsStickyToo() {
        val where = MatPoint(0.5f, 0.5f)
        val (on, id) = withCardAt(where)
        val cardWidth = layout.cardWidth

        // Between the enter and leave radii of the card.
        val edge = layout.toMat(
            layout.toPixels(where).let { (x, y) -> (x + cardWidth * 0.55f) to y },
        )

        val cold = resolve(edge, on = on)
        val sticky = resolve(edge, on = on, previous = DropIntent.Stack(id))

        assertTrue(cold !is DropIntent.Stack, "should not commit from cold")
        assertIs<DropIntent.Stack>(sticky)
        assertEquals(id, sticky.onto)
    }

    @Test
    fun stickinessRunsOutEventually() {
        // Sticky, not glued: drag far enough and it lets go.
        val slot = BoardSlot.Zone(com.kaiharimoto.mastertool.core.board.FieldZone.Monster(2))
        val far = MatPoint(0.5f, 0.0f)

        val intent = resolve(far, previous = DropIntent.Zone(slot, atSlot(slot)))
        assertTrue(intent !is DropIntent.Zone || intent.slot != slot)
    }

    @Test
    fun theHandIsStickyOnTheWayOut() {
        // A card half lifted out of the hand should not flicker between "hand"
        // and "field" while it is still over the band.
        val edge = layout.toMat(layout.hand.centerX to (layout.hand.top - layout.cardWidth * 0.3f))

        val cold = resolve(edge)
        val sticky = resolve(edge, previous = DropIntent.Hand)

        assertTrue(cold != DropIntent.Hand)
        assertEquals(DropIntent.Hand, sticky)
    }

    // ---- what the indicator says ---------------------------------------------

    @Test
    fun everyIntentCanSayWhatItIs() {
        // The indicator is the intent, so there is no outcome it cannot label.
        val all = listOf(
            DropIntent.Free(MatPoint.Centre),
            DropIntent.Zone(BoardSlot.Deck, MatPoint.Centre),
            DropIntent.Stack(1),
            DropIntent.Attach(1),
            DropIntent.Hand,
            DropIntent.Graveyard,
            DropIntent.Banish,
            DropIntent.Deck,
            DropIntent.ExtraDeck,
            DropIntent.Cancel,
        )

        all.forEach { assertTrue(it.label.isNotBlank(), "$it has no label") }
    }

    // ---- the mat's own coordinates --------------------------------------------

    @Test
    fun matCoordinatesRoundTripThroughPixels() {
        listOf(MatPoint(0f, 0f), MatPoint(0.5f, 0.5f), MatPoint(1f, 1f), MatPoint(0.2f, 0.8f))
            .forEach { point ->
                assertTrue(layout.toMat(layout.toPixels(point)).near(point), "$point")
            }
    }
}
