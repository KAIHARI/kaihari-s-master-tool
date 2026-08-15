package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DropCommitTest {

    private val field = PlayField.setUp((1..40).map { CardId(it) }, (101..115).map { CardId(it) })

    private fun withHand(count: Int): PlayField {
        var f = field
        repeat(count) { f = f.draw()!! }
        return f
    }

    private fun withCardOut(at: MatPoint = MatPoint.Centre): Pair<PlayField, Int> {
        val f = withHand(1).playFromHand(0, at, CardPosition.FACE_UP_ATK)!!
        return f to f.mat.single().id
    }

    private fun commit(
        on: PlayField,
        from: DragOrigin,
        intent: DropIntent,
        position: CardPosition = CardPosition.FACE_UP_ATK,
    ) = DropCommit.commit(on, from, intent, position)

    // ---- onto the mat --------------------------------------------------------

    @Test
    fun aHandCardLandsWhereTheIntentSaid() {
        val where = MatPoint(0.3f, 0.7f)
        val done = commit(withHand(1), DragOrigin.Hand(0), DropIntent.Free(where))!!

        assertTrue(done.mat.single().at.near(where))
        assertTrue(done.hand.isEmpty())
    }

    @Test
    fun aZoneDropLandsAtTheZoneNotWhereTheFingerWas() {
        // The indicator promised the zone, so the card goes to the zone.
        val zoneCentre = MatPoint(0.42f, 0.5f)
        val done = commit(
            withHand(1),
            DragOrigin.Hand(0),
            DropIntent.Zone(BoardSlot.Zone(FieldZone.Monster(2)), zoneCentre),
        )!!

        assertTrue(done.mat.single().at.near(zoneCentre))
    }

    @Test
    fun aCardCanBeSetFaceDownAsItLands() {
        val done = commit(
            withHand(1),
            DragOrigin.Hand(0),
            DropIntent.Free(MatPoint.Centre),
            CardPosition.FACE_DOWN_DEF,
        )!!

        assertTrue(!done.mat.single().faceUp)
        assertTrue(done.mat.single().turned)
    }

    @Test
    fun aCardAlreadyOnTheMatJustMoves() {
        val (on, id) = withCardOut()
        val done = commit(on, DragOrigin.Mat(id), DropIntent.Free(MatPoint(0.8f, 0.2f)))!!

        assertEquals(1, done.mat.size)
        assertTrue(done.placed(id)!!.at.near(MatPoint(0.8f, 0.2f)))
        // And it is still lying the way it was, because nothing said otherwise.
        assertEquals(CardPosition.FACE_UP_ATK, done.placed(id)!!.card.position)
    }

    /**
     * A card already on the field can be set, and this is the whole of kai's
     * *"it sets it in vertical attack position instead of face down defence"*.
     *
     * `SetPosition` was right, the carry was right, and the card genuinely
     * turned over and lay down **in the air** where you could watch it. Then
     * `DropCommit.land` reached `moveOnMat`, which took an id and a point and
     * had nowhere to put the answer — so the card landed exactly as it had been
     * picked up. Every other origin took the position, which is why setting
     * worked from the hand and from a pile and looked intermittent rather than
     * broken.
     */
    @Test
    fun aCardAlreadyOnTheFieldCanStillBeSet() {
        val (on, id) = withCardOut()
        val done = commit(
            on,
            DragOrigin.Mat(id),
            DropIntent.Free(MatPoint(0.5f, 0.4f)),
            CardPosition.FACE_DOWN_DEF,
        )!!

        val landed = done.placed(id)!!
        assertTrue(!landed.faceUp, "it landed face up")
        assertTrue(landed.turned, "it landed upright")
    }

    /** And the same when it is set onto another card rather than onto felt. */
    @Test
    fun aCardSetOntoAnotherCardLandsTheWayItWasSet() {
        val (on, first) = withCardOut()
        val second = on.draw()!!.playFromHand(0, MatPoint(0.2f, 0.2f), CardPosition.FACE_UP_ATK)!!
        val moving = second.mat.first { it.id != first }.id

        val done = commit(
            second,
            DragOrigin.Mat(moving),
            DropIntent.Stack(first),
            CardPosition.FACE_DOWN_DEF,
        )!!

        val landed = done.placed(moving)!!
        assertEquals(2, landed.depth, "it did not stack")
        assertTrue(!landed.faceUp && landed.turned, "it landed as ${landed.card.position}")
    }

    // ---- stacking ------------------------------------------------------------

    @Test
    fun aHandCardStacksOntoWhatIsThere() {
        // It has to arrive on the mat before it can be stacked, and the caller
        // should not have to know that.
        val (on, id) = withCardOut()
        val withHand = on.draw()!!

        val done = commit(withHand, DragOrigin.Hand(0), DropIntent.Stack(id))!!

        assertEquals(1, done.mat.size)
        assertEquals(2, done.mat.single().depth)
        assertTrue(done.hand.isEmpty())
    }

    @Test
    fun aMatCardStacksOntoAnother() {
        var on = withHand(2)
        on = on.playFromHand(0, MatPoint(0.3f, 0.5f), CardPosition.FACE_UP_ATK)!!
        on = on.playFromHand(0, MatPoint(0.6f, 0.5f), CardPosition.FACE_UP_ATK)!!
        val (under, over) = on.mat[0].id to on.mat[1].id

        val done = commit(on, DragOrigin.Mat(over), DropIntent.Stack(under))!!

        assertEquals(1, done.mat.size)
        assertEquals(over, done.mat.single().id)
    }

    @Test
    fun aCardFromTheGraveyardCanBeStackedBackOnto() {
        val (on, id) = withCardOut()
        val buried = on.draw()!!.handToGraveyard(0)!!

        val done = commit(
            buried,
            DragOrigin.Pile(BoardSlot.Graveyard, 0),
            DropIntent.Stack(id),
        )!!

        assertEquals(2, done.mat.single().depth)
        assertTrue(done.graveyard.isEmpty())
    }

    @Test
    fun attachingTucksItUnderInsteadOfOnTop() {
        val (on, id) = withCardOut()
        val withHand = on.draw()!!

        val done = commit(withHand, DragOrigin.Hand(0), DropIntent.Attach(id))!!

        assertEquals(1, done.mat.size)
        assertEquals(1, done.placed(id)!!.card.materials.size)
        assertEquals(1, done.mat.single().depth, "a material is not a stack")
    }

    @Test
    fun stackingOntoNothingIsNotAMove() {
        assertNull(commit(withHand(1), DragOrigin.Hand(0), DropIntent.Stack(9999)))
        assertNull(commit(withHand(1), DragOrigin.Hand(0), DropIntent.Attach(9999)))
    }

    // ---- off the mat ----------------------------------------------------------

    @Test
    fun aMatCardGoesWhereverItWasDropped() {
        val (on, id) = withCardOut()

        assertEquals(1, commit(on, DragOrigin.Mat(id), DropIntent.Graveyard)!!.graveyard.size)
        assertEquals(1, commit(on, DragOrigin.Mat(id), DropIntent.Banish)!!.banished.size)
        assertEquals(1, commit(on, DragOrigin.Mat(id), DropIntent.Hand(0))!!.hand.size)
        assertEquals(40, commit(on, DragOrigin.Mat(id), DropIntent.Deck)!!.deck.size)
    }

    @Test
    fun aHandCardGoesStraightToAPile() {
        val on = withHand(2)

        assertEquals(1, commit(on, DragOrigin.Hand(0), DropIntent.Graveyard)!!.graveyard.size)
        assertEquals(1, commit(on, DragOrigin.Hand(0), DropIntent.Banish)!!.banished.size)
        assertEquals(39, commit(on, DragOrigin.Hand(0), DropIntent.Deck)!!.deck.size)
    }

    @Test
    fun aHandCardDroppedBackInItsOwnPlaceChangesNothing() {
        // Both the gaps either side of a card are the card's own place: the one
        // before it and the one after it describe the same hand. This used to be
        // `null` for *every* hand-to-hand release, which said "a card in your
        // hand cannot go to your hand" — true of the destination and false of
        // the gesture.
        assertNull(commit(withHand(2), DragOrigin.Hand(0), DropIntent.Hand(0)))
        assertNull(commit(withHand(2), DragOrigin.Hand(0), DropIntent.Hand(1)))
    }

    @Test
    fun aHandCardDroppedSomewhereElseInTheHandMoves() {
        val on = withHand(3)
        val order = on.hand.map { it.instanceId }

        val done = assertNotNull(commit(on, DragOrigin.Hand(0), DropIntent.Hand(3)))

        assertEquals(listOf(order[1], order[2], order[0]), done.hand.map { it.instanceId })
        assertEquals(on.hand.size, done.hand.size)
    }

    // ---- pile to pile ---------------------------------------------------------

    @Test
    fun aCardMovesBetweenPiles() {
        // A banished card back to the graveyard is a real thing you do, and it
        // is the same two steps as every other pile move.
        val buried = withHand(1).handToBanish(0)!!

        val done = commit(buried, DragOrigin.Pile(BoardSlot.Banished, 0), DropIntent.Graveyard)!!

        assertTrue(done.banished.isEmpty())
        assertEquals(1, done.graveyard.size)
    }

    @Test
    fun aGraveyardCardCanGoBackOnTheDeck() {
        val buried = withHand(1).handToGraveyard(0)!!
        val card = buried.graveyard.single().cardId

        val done = commit(buried, DragOrigin.Pile(BoardSlot.Graveyard, 0), DropIntent.Deck)!!

        assertEquals(card, done.deck.first().cardId)
        assertEquals(40, done.deck.size)
    }

    @Test
    fun aPileDroppedOnItselfIsNotAMove() {
        val buried = withHand(1).handToGraveyard(0)!!

        assertNull(commit(buried, DragOrigin.Pile(BoardSlot.Graveyard, 0), DropIntent.Graveyard))
    }

    @Test
    fun liftingFromAPileTakesTheRightCardAndLeavesTheRest() {
        // The bug this guards is a local `drop` shadowing the standard one and
        // recursing until the stack runs out — which compiled perfectly well.
        var on = withHand(3)
        repeat(3) { on = on.handToGraveyard(0)!! }
        val middle = on.graveyard[1]

        val done = commit(on, DragOrigin.Pile(BoardSlot.Graveyard, 1), DropIntent.Banish)!!

        assertEquals(2, done.graveyard.size)
        assertEquals(middle.instanceId, done.banished.single().instanceId)
        assertTrue(done.graveyard.none { it.instanceId == middle.instanceId })
    }

    @Test
    fun anIndexPastTheEndOfAPileIsNotAMove() {
        assertNull(commit(field, DragOrigin.Pile(BoardSlot.Graveyard, 4), DropIntent.Hand(0)))
        assertNull(commit(field, DragOrigin.Pile(BoardSlot.Deck, 999), DropIntent.Hand(0)))
    }

    @Test
    fun aZoneIsNotAPileToDragOutOf() {
        assertNull(
            commit(
                field,
                DragOrigin.Pile(BoardSlot.Zone(FieldZone.Monster(0)), 0),
                DropIntent.Hand(0),
            ),
        )
    }

    // ---- the extra deck --------------------------------------------------------

    @Test
    fun theExtraDeckPlaysOutAndTakesCardsBack() {
        val out = commit(
            field,
            DragOrigin.Pile(BoardSlot.ExtraDeck, 0),
            DropIntent.Free(MatPoint.Centre),
        )!!
        assertEquals(14, out.extraDeck.size)
        assertEquals(1, out.mat.size)

        val back = commit(out, DragOrigin.Mat(out.mat.single().id), DropIntent.ExtraDeck)!!
        assertEquals(15, back.extraDeck.size)
        assertTrue(back.mat.isEmpty())
    }

    @Test
    fun aHandCardCannotBePutInTheExtraDeck() {
        assertNull(commit(withHand(1), DragOrigin.Hand(0), DropIntent.ExtraDeck))
    }

    // ---- cancelling -------------------------------------------------------------

    @Test
    fun cancellingDoesNothingAtAll() {
        val (on, id) = withCardOut()

        assertNull(commit(on, DragOrigin.Mat(id), DropIntent.Cancel))
        assertNull(commit(on, DragOrigin.Hand(0), DropIntent.Cancel))
    }

    @Test
    fun everyIntentFromEveryOriginEitherWorksOrSaysNo() {
        // The exhaustive sweep: nothing may throw, and nothing may quietly
        // corrupt the field. Either a new field comes back or null does.
        val (on, id) = withCardOut()
        val ready = on.draw()!!.draw()!!

        val origins = listOf(
            DragOrigin.Hand(0),
            DragOrigin.Mat(id),
            DragOrigin.Pile(BoardSlot.Deck, 0),
            DragOrigin.Pile(BoardSlot.ExtraDeck, 0),
            DragOrigin.Pile(BoardSlot.Graveyard, 0),
            DragOrigin.Pile(BoardSlot.Banished, 0),
        )
        val intents = listOf(
            DropIntent.Free(MatPoint.Centre),
            DropIntent.Zone(BoardSlot.Zone(FieldZone.Monster(0)), MatPoint.Centre),
            DropIntent.Stack(id),
            DropIntent.Attach(id),
            DropIntent.Hand(0),
            DropIntent.Graveyard,
            DropIntent.Banish,
            DropIntent.Deck,
            DropIntent.ExtraDeck,
            DropIntent.Cancel,
        )

        origins.forEach { from ->
            intents.forEach { intent ->
                val result = commit(ready, from, intent)
                if (result != null) {
                    val before = ready.hand.size + ready.deck.size + ready.extraDeck.size +
                        ready.graveyard.size + ready.banished.size + ready.onMat.size
                    val after = result.hand.size + result.deck.size + result.extraDeck.size +
                        result.graveyard.size + result.banished.size + result.onMat.size
                    assertEquals(before, after, "$from -> $intent lost or invented a card")
                }
            }
        }
    }

    @Test
    fun theFieldItStartedFromIsNeverTouched() {
        val (on, id) = withCardOut()
        val snapshot = on.copy()

        commit(on, DragOrigin.Mat(id), DropIntent.Graveyard)
        commit(on, DragOrigin.Mat(id), DropIntent.Stack(id))
        commit(on, DragOrigin.Hand(0), DropIntent.Free(MatPoint.Centre))

        assertEquals(snapshot, on)
        assertNotNull(on.placed(id))
    }
}
