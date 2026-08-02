package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayFieldTest {

    private val main = (1..40).map { CardId(it) }
    private val extra = (101..115).map { CardId(it) }

    private fun field() = PlayField.setUp(main, extra)

    /** A field with [count] cards from hand out on the mat, left to right. */
    private fun withCardsOut(count: Int): PlayField {
        var f = field()
        repeat(count) { f = f.draw()!! }
        repeat(count) {
            f = f.playFromHand(0, MatPoint(0.1f + it * 0.15f, 0.5f), CardPosition.FACE_UP_ATK)!!
        }
        return f
    }

    private val PlayField.ids: List<Int> get() = mat.map { it.id }

    // ---- the mat holds cards anywhere ---------------------------------------

    @Test
    fun aCardCanBePutDownAnywhereOnTheMat() {
        val f = field().draw()!!.playFromHand(0, MatPoint(0.13f, 0.77f), CardPosition.FACE_UP_ATK)!!

        assertEquals(1, f.mat.size)
        assertTrue(f.mat.single().at.near(MatPoint(0.13f, 0.77f)))
        assertTrue(f.hand.isEmpty())
    }

    @Test
    fun aCardCannotBeDroppedOffTheEdgeOfTheWorld() {
        val f = field().draw()!!.playFromHand(0, MatPoint(-3f, 9f), CardPosition.FACE_UP_ATK)!!

        val at = f.mat.single().at
        assertTrue(at.x in 0f..1f && at.y in 0f..1f, "landed at $at")
    }

    @Test
    fun positionsAreFractionsSoTheBoardSurvivesAResize() {
        // The whole reason the model is not in pixels: a board built on a tablet
        // and then rotated is the same board.
        val f = withCardsOut(3)

        assertTrue(f.mat.all { it.at.x in 0f..1f && it.at.y in 0f..1f })
    }

    @Test
    fun playingFromAnywhereWorksAndLeavesTheSourceShorter() {
        val f = field()
        assertEquals(15, f.extraDeck.size)

        val out = f.playFromExtra(0, MatPoint.Centre, CardPosition.FACE_UP_ATK)!!
        assertEquals(14, out.extraDeck.size)
        assertEquals(1, out.mat.size)
    }

    @Test
    fun playingFromAnEmptyPlaceIsNotAMove() {
        assertNull(field().playFromHand(0, MatPoint.Centre, CardPosition.FACE_UP_ATK))
        assertNull(PlayField().draw())
    }

    // ---- draw order ----------------------------------------------------------

    @Test
    fun theLastCardPutDownIsOnTop() {
        val f = withCardsOut(3)

        // Drawn back to front, so the list's tail is what a finger reaches first.
        assertEquals(3, f.mat.size)
        assertEquals(f.mat.last().id, f.ids.last())
    }

    @Test
    fun movingACardBringsItToTheFront() {
        // Otherwise a card you just touched can end up behind one you did not.
        val f = withCardsOut(3)
        val bottom = f.ids.first()

        val moved = f.moveOnMat(bottom, MatPoint(0.8f, 0.2f))!!
        assertEquals(bottom, moved.ids.last())
    }

    @Test
    fun bringingToFrontIsNotAMoveWhenItIsAlreadyThere() {
        val f = withCardsOut(2)

        assertNull(f.bringToFront(f.ids.last()))
        assertNotNull(f.bringToFront(f.ids.first()))
    }

    // ---- stacking ------------------------------------------------------------

    @Test
    fun oneCardOnAnotherMakesAPile() {
        val f = withCardsOut(2)
        val (under, over) = f.ids[0] to f.ids[1]

        val stacked = f.stackOnto(over, under)!!

        assertEquals(1, stacked.mat.size)
        val pile = stacked.mat.single()
        assertEquals(over, pile.id, "the card you moved ends up on top")
        assertEquals(2, pile.depth)
        assertTrue(pile.at.near(f.placed(under)!!.at), "the pile stays where the bottom card was")
    }

    @Test
    fun aPileDroppedOnAPileKeepsBothOrders() {
        var f = withCardsOut(4)
        val (a, b, c, d) = listOf(f.ids[0], f.ids[1], f.ids[2], f.ids[3])

        f = f.stackOnto(b, a)!!   // b over a
        f = f.stackOnto(d, c)!!   // d over c
        f = f.stackOnto(d, b)!!   // the d/c pile onto the b/a pile

        val pile = f.mat.single()
        assertEquals(4, pile.depth)
        assertEquals(listOf(d, c, b, a), pile.pile.map { it.instanceId })
    }

    @Test
    fun aCardCannotBeStackedOnItself() {
        val f = withCardsOut(1)

        assertNull(f.stackOnto(f.ids.first(), f.ids.first()))
    }

    @Test
    fun takingTheTopCardOffLeavesTheRestWhereItWas() {
        val f = withCardsOut(2)
        val (under, over) = f.ids[0] to f.ids[1]
        val where = f.placed(under)!!.at

        val stacked = f.stackOnto(over, under)!!
        val split = stacked.unstack(over, MatPoint(0.9f, 0.9f))!!

        assertEquals(2, split.mat.size)
        assertTrue(split.placed(under)!!.at.near(where))
        assertTrue(split.placed(over)!!.at.near(MatPoint(0.9f, 0.9f)))
    }

    @Test
    fun thereIsNothingToUnstackFromALoneCard() {
        val f = withCardsOut(1)

        assertNull(f.unstack(f.ids.first(), MatPoint.Centre))
    }

    @Test
    fun aPileLeavesTheMatTogether() {
        // Sweeping a stack into the graveyard takes all of it, the way a hand does.
        var f = withCardsOut(3)
        val (a, b, c) = Triple(f.ids[0], f.ids[1], f.ids[2])
        f = f.stackOnto(b, a)!!
        f = f.stackOnto(c, b)!!

        assertEquals(1, f.mat.size)
        assertEquals(3, f.mat.single().depth)

        val gone = f.toGraveyard(c)!!
        assertTrue(gone.mat.isEmpty())
        assertEquals(listOf(c, b, a), gone.graveyard.map { it.instanceId })
    }

    // ---- which way a card faces ----------------------------------------------

    @Test
    fun flippingKeepsWhetherItIsSideways() {
        val f = field().draw()!!
            .playFromHand(0, MatPoint.Centre, CardPosition.FACE_UP_DEF)!!
        val id = f.ids.single()

        val down = f.flip(id)!!
        assertEquals(CardPosition.FACE_DOWN_DEF, down.placed(id)!!.card.position)
        assertTrue(down.placed(id)!!.turned)
        assertTrue(!down.placed(id)!!.faceUp)

        assertEquals(CardPosition.FACE_UP_DEF, down.flip(id)!!.placed(id)!!.card.position)
    }

    @Test
    fun turningKeepsWhichWayUpItIsFacing() {
        val f = field().draw()!!
            .playFromHand(0, MatPoint.Centre, CardPosition.FACE_DOWN_ATK)!!
        val id = f.ids.single()

        val turned = f.rotate(id)!!
        assertEquals(CardPosition.FACE_DOWN_DEF, turned.placed(id)!!.card.position)
        assertTrue(!turned.placed(id)!!.faceUp, "turning a set card must not reveal it")
    }

    @Test
    fun aCardCanBePlayedFaceDown() {
        val f = field().draw()!!
            .playFromHand(0, MatPoint.Centre, CardPosition.FACE_DOWN_DEF)!!

        assertTrue(!f.mat.single().faceUp)
        assertTrue(f.mat.single().turned)
    }

    @Test
    fun settingThePositionItAlreadyHasIsNotAMove() {
        val f = withCardsOut(1)

        assertNull(f.setPosition(f.ids.single(), CardPosition.FACE_UP_ATK))
    }

    // ---- leaving the mat ------------------------------------------------------

    @Test
    fun everythingThatLeavesArrivesClean() {
        // A card in the graveyard has no counters and no materials; carrying
        // them along would quietly resurrect them if it ever came back.
        var f = withCardsOut(2)
        val (a, b) = f.ids[0] to f.ids[1]
        f = f.attachAsMaterial(b, a)!!
        f = f.addCounter(a, 3)!!

        val gone = f.toGraveyard(a)!!
        assertTrue(gone.graveyard.all { it.counters == 0 && it.materials.isEmpty() })
        assertEquals(2, gone.graveyard.size, "the material goes with it")
    }

    @Test
    fun aCardComesBackFaceUpWhereverItGoes() {
        val f = field().draw()!!
            .playFromHand(0, MatPoint.Centre, CardPosition.FACE_DOWN_DEF)!!
        val id = f.ids.single()

        assertTrue(f.toHand(id)!!.hand.all { it.position.faceUp })
        assertTrue(f.toDeckTop(id)!!.deck.first().position.faceUp)
        assertTrue(f.toGraveyard(id)!!.graveyard.first().position.faceUp)
    }

    @Test
    fun banishingFaceDownStaysFaceDown() {
        val f = withCardsOut(1)

        assertTrue(!f.toBanish(f.ids.single(), faceDown = true)!!.banished.first().position.faceUp)
        assertTrue(f.toBanish(f.ids.single())!!.banished.first().position.faceUp)
    }

    @Test
    fun theGraveyardReadsMostRecentFirst() {
        var f = withCardsOut(2)
        val (first, second) = f.ids[0] to f.ids[1]

        f = f.toGraveyard(first)!!.toGraveyard(second)!!

        assertEquals(second, f.graveyard.first().instanceId)
    }

    @Test
    fun aCardThatIsNotThereCannotBeMoved() {
        val f = withCardsOut(1)

        assertNull(f.toGraveyard(9999))
        assertNull(f.moveOnMat(9999, MatPoint.Centre))
        assertNull(f.flip(9999))
        assertNull(f.addCounter(9999, 1))
    }

    // ---- materials ------------------------------------------------------------

    @Test
    fun materialsRideWithTheirMonster() {
        var f = withCardsOut(2)
        val (host, material) = f.ids[0] to f.ids[1]

        f = f.attachAsMaterial(material, host)!!

        assertEquals(1, f.mat.size)
        assertEquals(1, f.placed(host)!!.card.materials.size)
    }

    @Test
    fun detachingSendsTheMaterialToTheGraveyard() {
        var f = withCardsOut(2)
        f = f.attachAsMaterial(f.ids[1], f.ids[0])!!
        val host = f.mat.single().id

        val detached = f.detachMaterial(host)!!

        assertEquals(1, detached.graveyard.size)
        assertTrue(detached.placed(host)!!.card.materials.isEmpty())
        assertNull(detached.detachMaterial(host), "and there is nothing left to detach")
    }

    // ---- the piles and the hand ------------------------------------------------

    @Test
    fun drawingTakesFromTheTop() {
        val f = field().shuffleDeck(7)
        val top = f.deck.first()

        assertEquals(top.cardId, f.draw()!!.hand.single().cardId)
        assertEquals(39, f.draw()!!.deck.size)
    }

    @Test
    fun shufflingKeepsEveryCard() {
        val f = field().shuffleDeck(99)

        assertEquals(main.size, f.deck.size)
        assertEquals(main.toSet(), f.deck.map { it.cardId }.toSet())
    }

    @Test
    fun shufflingIsReproducibleFromItsSeed() {
        // Same reason the goldfish deal is seeded: an open worth talking about
        // has to be one you can get back.
        assertEquals(
            field().shuffleDeck(4).deck.map { it.cardId },
            field().shuffleDeck(4).deck.map { it.cardId },
        )
    }

    @Test
    fun aCardGoesFromTheHandStraightToAPile() {
        val f = field().draw()!!

        assertEquals(1, f.handToGraveyard(0)!!.graveyard.size)
        assertTrue(f.handToGraveyard(0)!!.hand.isEmpty())
        // Drawing took one off a forty-card deck; putting it back makes forty.
        assertEquals(40, f.handToDeckBottom(0)!!.deck.size)
        assertEquals(f.hand.single().cardId, f.handToDeckTop(0)!!.deck.first().cardId)
    }

    // ---- life, phases, turns ---------------------------------------------------

    @Test
    fun lifePointsNeverGoBelowZero() {
        assertEquals(0, field().adjustLifePoints(-99_000).lifePoints)
        assertEquals(7600, field().adjustLifePoints(-400).lifePoints)
    }

    @Test
    fun endingTheTurnStartsTheNextOneAtItsDraw() {
        val f = field().endTurn()

        assertEquals(2, f.turn)
        assertEquals(DuelPhase.DRAW, f.phase)
    }

    // ---- undo is free -----------------------------------------------------------

    @Test
    fun everyMoveLeavesTheOneBeforeItUntouched() {
        // Which is the whole reason undo costs nothing: a move is a new field,
        // never an edit to the old one.
        val before = withCardsOut(2)
        val snapshot = before.copy()

        before.stackOnto(before.ids[1], before.ids[0])
        before.toGraveyard(before.ids[0])
        before.flip(before.ids[0])

        assertEquals(snapshot, before)
    }
}
