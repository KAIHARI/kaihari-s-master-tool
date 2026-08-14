package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reaching a card that is not on top of anything.
 *
 * Until the fan, the only card in a stack this app could name was the one you
 * could see: `unstack` took the card directly beneath one, a card at a time, and
 * `detachMaterial` sent the bottom-most material to the graveyard. The third
 * card down of a pile, and any material that was not the last, were cards on the
 * table that no operation could reach — which for an Xyz monster means its own
 * materials.
 */
class BuriedCardTest {

    private val here = MatPoint(0.4f, 0.5f)
    private val there = MatPoint(0.7f, 0.3f)

    /** A monster with two cards resting on it and two attached as material. */
    private fun table(): PlayField {
        val monster = BoardCard(0, CardId(1))
        val resting = listOf(BoardCard(1, CardId(2)), BoardCard(2, CardId(3)))
        val materials = listOf(BoardCard(3, CardId(4)), BoardCard(4, CardId(5)))
        return PlayField(
            mat = listOf(
                PlacedCard(monster.copy(materials = materials), at = here, beneath = resting),
            ),
        )
    }

    // ---- what is under a card -------------------------------------------------------

    @Test
    fun aCardsOwnListStartsWithItself() {
        // So an index into it is an index into the whole object, and the fan's
        // first card is the one you were already looking at rather than a
        // special case at every call site.
        val under = table().under(0)

        assertEquals(5, under.size)
        assertEquals(0, under.first().instanceId)
    }

    @Test
    fun whatIsRestingOnItComesBeforeWhatIsPartOfIt() {
        // Two different relationships and one list, ordered so that reading it
        // top to bottom is reading the physical object top to bottom: the
        // monster, the cards lying on it, then the materials tucked underneath.
        val under = table().under(0)

        assertEquals(listOf(0, 1, 2, 3, 4), under.map { it.instanceId })
    }

    @Test
    fun aCardWithNothingUnderItIsJustItself() {
        val alone = PlayField(mat = listOf(PlacedCard(BoardCard(9, CardId(1)), at = here)))

        assertEquals(listOf(9), alone.under(9).map { it.instanceId })
    }

    @Test
    fun acardThatIsNotOnTheMatHasNothingUnderIt() {
        assertTrue(table().under(404).isEmpty())
    }

    // ---- taking one out -------------------------------------------------------------

    @Test
    fun theCardOnTopIsSimplyMoved() {
        // Index zero is already a card on the mat, with a name and a place, so
        // taking it "out of the stack" is the move it always was — and it takes
        // its stack with it, which is what moving the top of a pile does.
        val moved = assertNotNull(table().takeFromUnder(0, 0, there, CardPosition.FACE_UP_ATK))

        assertEquals(1, moved.mat.size)
        assertEquals(there, moved.placed(0)?.at)
        assertEquals(2, moved.placed(0)?.beneath?.size)
    }

    @Test
    fun aCardRestingUnderAnotherComesOutOntoTheMat() {
        val taken = assertNotNull(table().takeFromUnder(0, 2, there, CardPosition.FACE_UP_ATK))

        assertEquals(2, taken.mat.size, "it is its own card on the table now")
        assertEquals(there, taken.placed(2)?.at)
        assertEquals(listOf(1), taken.placed(0)?.beneath?.map { it.instanceId })
    }

    @Test
    fun aMaterialCanBeTakenOutOfTheMiddleOfTheOnesAttached() {
        // The case nothing could do. `detachMaterial` only ever reached the
        // bottom-most one, so the first material of a two-material Xyz monster
        // was unreachable without detaching the other first.
        val taken = assertNotNull(table().takeFromUnder(0, 3, there, CardPosition.FACE_UP_ATK))

        assertEquals(listOf(4), taken.placed(0)?.card?.materials?.map { it.instanceId })
        assertEquals(there, taken.placed(3)?.at)
        assertEquals(2, taken.placed(0)?.beneath?.size, "the cards resting on it did not move")
    }

    @Test
    fun aMaterialComesOutCarryingNothing() {
        // A material is a plain card that happens to be under a monster, so it
        // arrives on the table as one.
        val taken = assertNotNull(table().takeFromUnder(0, 4, there, CardPosition.FACE_UP_ATK))

        assertTrue(taken.placed(4)?.beneath.isNullOrEmpty())
        assertTrue(taken.placed(4)?.card?.materials.isNullOrEmpty())
    }

    @Test
    fun everyCardOfAStackCanBeReachedAndNoneOfThemTwice() {
        // The property the fan depends on: every index the spread offers names a
        // different card, and all of them work.
        val field = table()
        val reached = field.under(0).indices.map { index ->
            val taken = assertNotNull(
                field.takeFromUnder(0, index, there, CardPosition.FACE_UP_ATK),
                "index $index cannot be taken",
            )
            taken.onMat.size
        }

        assertEquals(5, field.onMat.size)
        // Nothing is created or destroyed by taking a card out of a stack.
        reached.forEach { assertEquals(5, it) }
    }

    @Test
    fun reachingPastTheEndOfAStackIsNotAMove() {
        assertNull(table().takeFromUnder(0, 9, there, CardPosition.FACE_UP_ATK))
        assertNull(table().takeFromUnder(404, 1, there, CardPosition.FACE_UP_ATK))
    }

    // ---- and everywhere else it can go ----------------------------------------------

    @Test
    fun aBuriedCardCanBeSentAnywhereACarriedOneCan() {
        // `DropCommit` is the whole vocabulary of what a release means, and a
        // card dug out of a stack has to speak all of it or half the reason for
        // digging it out is missing — milling a material, banishing one, putting
        // one back on top of the deck.
        val from = DragOrigin.Buried(0, 3)

        listOf(
            DropIntent.Hand(0) to { f: PlayField -> f.hand.size },
            DropIntent.Graveyard to { f: PlayField -> f.graveyard.size },
            DropIntent.Banish to { f: PlayField -> f.banished.size },
            DropIntent.Deck to { f: PlayField -> f.deck.size },
            DropIntent.ExtraDeck to { f: PlayField -> f.extraDeck.size },
        ).forEach { (intent, count) ->
            val done = assertNotNull(
                DropCommit.commit(table(), from, intent),
                "$intent should be a move",
            )
            assertEquals(1, count(done), "$intent received the card")
            assertEquals(
                listOf(4),
                done.placed(0)?.card?.materials?.map { it.instanceId },
                "$intent took it off the monster",
            )
        }
    }

    @Test
    fun aBuriedCardDroppedOnTheFeltLandsThere() {
        val done = assertNotNull(
            DropCommit.commit(table(), DragOrigin.Buried(0, 1), DropIntent.Free(there)),
        )

        assertEquals(there, done.placed(1)?.at)
    }

    // ---- naming the card a finger is over -------------------------------------------

    @Test
    fun aFanIndexNamesTheCardTheSpreadShows() {
        // The join between "third from the left" and "this card", and the only
        // place it exists. Getting it wrong is picking up the wrong card, which
        // is the one failure a search cannot survive.
        val field = table()
        val source = DragOrigin.Mat(0)

        field.fanOf(source).forEachIndexed { index, card ->
            val named = assertNotNull(source.fanCardAt(index))
            val taken = assertNotNull(
                DropCommit.commit(field, named, DropIntent.Hand(0)),
                "index $index should be reachable",
            )
            assertEquals(
                card.instanceId,
                taken.hand.single().instanceId,
                "index $index named the wrong card",
            )
        }
    }

    @Test
    fun aStackSpreadsOutWhatIsUnderItAndLeavesTheCardItself() {
        // The monster stays on the table while you look through its materials,
        // which is both the honest picture and the thing that keeps every card
        // in the spread behaving alike: the top of a stack takes the stack with
        // it when it leaves, and nothing else in the spread does.
        val spread = table().fanOf(DragOrigin.Mat(0))

        assertEquals(listOf(1, 2, 3, 4), spread.map { it.instanceId })
        assertEquals(DragOrigin.Buried(0, 1), DragOrigin.Mat(0).fanCardAt(0))
        assertEquals(DragOrigin.Buried(0, 3), DragOrigin.Mat(0).fanCardAt(2))
    }

    @Test
    fun everyCardOfAFanAgreesAboutWhatTheFanIsOpenOn() {
        // Whatever the finger last touched, the fan is about one thing — so
        // "is this fan already open" is a comparison rather than a case analysis.
        val stack = DragOrigin.Mat(0)
        assertEquals(stack, DragOrigin.Buried(0, 3).fanSource)
        assertEquals(stack, stack.fanSource)

        val deck = DragOrigin.Pile(BoardSlot.Deck, 0)
        assertEquals(deck, DragOrigin.Pile(BoardSlot.Deck, 17).fanSource)
    }

    @Test
    fun aPileFansThroughItsOwnIndices() {
        val field = PlayField(deck = List(5) { BoardCard(it, CardId(it)) })
        val deck = DragOrigin.Pile(BoardSlot.Deck, 0)

        assertEquals(5, field.fanOf(deck).size)
        assertEquals(DragOrigin.Pile(BoardSlot.Deck, 3), deck.fanCardAt(3))
    }

    @Test
    fun aHandIsNotSomethingYouSearch() {
        // You can already see all of it.
        assertTrue(table().fanOf(DragOrigin.Hand(0)).isEmpty())
        assertNull(DragOrigin.Hand(0).fanCardAt(0))
    }
}
