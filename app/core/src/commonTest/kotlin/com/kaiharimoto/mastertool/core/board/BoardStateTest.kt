package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardStateTest {

    private fun board() = BoardState.setUp(
        main = List(40) { CardId(100 + it) },
        extra = List(15) { CardId(900 + it) },
    )

    private val m0 = FieldZone.Monster(0)
    private val m1 = FieldZone.Monster(1)
    private val st0 = FieldZone.SpellTrap(0)

    @Test
    fun setupDealsNothingAndNumbersEveryInstance() {
        val b = board()
        assertEquals(40, b.deck.size)
        assertEquals(15, b.extraDeck.size)
        assertTrue(b.hand.isEmpty() && b.graveyard.isEmpty())
        assertEquals(8000, b.lifePoints)
        // Instance ids are unique across piles — duplicates of a passcode stay
        // individually addressable.
        val ids = (b.deck + b.extraDeck).map { it.instanceId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun drawComesOffTheTopAndEmptyDeckRefuses() {
        var b = board()
        val top = b.deck.first()
        b = b.draw()!!
        assertEquals(top, b.hand.last())
        assertEquals(39, b.deck.size)

        var empty = b
        repeat(39) { empty = empty.draw()!! }
        assertNull(empty.draw())
    }

    @Test
    fun aZoneHoldsOneCard() {
        var b = board().draw()!!.draw()!!
        b = b.playFromHand(0, m0, CardPosition.FACE_UP_ATK)!!
        // Occupied zone refuses; empty neighbour accepts.
        assertNull(b.playFromHand(0, m0, CardPosition.FACE_UP_ATK))
        assertNotNull(b.playFromHand(0, m1, CardPosition.FACE_DOWN_DEF))
        // Out-of-range zones are impossible, not crashes.
        assertNull(b.playFromHand(0, FieldZone.Monster(9), CardPosition.FACE_UP_ATK))
    }

    @Test
    fun positionsChangeAndFlipsAreDistinct() {
        var b = board().draw()!!.playFromHand(0, m0, CardPosition.FACE_DOWN_DEF)!!
        // A set card cannot "change position" — it flips.
        assertNull(b.changePosition(m0))
        b = b.flip(m0)!!
        assertEquals(CardPosition.FACE_UP_ATK, b.at(m0)!!.position)
        b = b.changePosition(m0)!!
        assertEquals(CardPosition.FACE_UP_DEF, b.at(m0)!!.position)
        assertNull(b.flip(m0)) // already face-up
    }

    @Test
    fun graveyardKeepsMostRecentOnTop() {
        var b = board().draw()!!.draw()!!
        val first = b.hand[0]
        val second = b.hand[1]
        b = b.playFromHand(0, m0, CardPosition.FACE_UP_ATK)!!
            .playFromHand(0, m1, CardPosition.FACE_UP_ATK)!!
        b = b.sendToGraveyard(m0)!!
        b = b.sendToGraveyard(m1)!!
        assertEquals(second.instanceId, b.graveyard[0].instanceId)
        assertEquals(first.instanceId, b.graveyard[1].instanceId)
        // And revival takes it back off by index.
        b = b.playFromGraveyard(0, m0, CardPosition.FACE_UP_DEF)!!
        assertEquals(second.instanceId, b.at(m0)!!.instanceId)
        assertEquals(1, b.graveyard.size)
    }

    @Test
    fun xyzMaterialsRideAndDetachToGraveyard() {
        var b = board().draw()!!.draw()!!
        b = b.playFromHand(0, m0, CardPosition.FACE_UP_ATK)!!
            .playFromHand(0, m1, CardPosition.FACE_UP_ATK)!!
        val host = FieldZone.ExtraMonster(0)
        b = b.playFromExtra(0, host, CardPosition.FACE_UP_ATK)!!
        b = b.attachAsMaterial(m0, host)!!.attachAsMaterial(m1, host)!!

        assertEquals(2, b.at(host)!!.materials.size)
        assertNull(b.at(m0))

        b = b.detachMaterial(host)!!
        assertEquals(1, b.at(host)!!.materials.size)
        assertEquals(1, b.graveyard.size)

        // Killing the host releases the remaining material with it.
        b = b.sendToGraveyard(host)!!
        assertEquals(3, b.graveyard.size)
        assertTrue(b.graveyard.all { it.materials.isEmpty() })
    }

    @Test
    fun banishAndBounceCleanUpState() {
        var b = board().draw()!!
        b = b.playFromHand(0, st0, CardPosition.FACE_DOWN_DEF)!!
        b = b.banishFrom(st0, faceDown = true)!!
        assertEquals(CardPosition.FACE_DOWN_ATK, b.banished[0].position)

        b = b.draw()!!.playFromHand(0, m0, CardPosition.FACE_UP_DEF)!!
        b = b.addCounter(m0, 2)!!
        b = b.returnToHand(m0)!!
        // Counters and position do not follow a card into the hand.
        assertEquals(0, b.hand.last().counters)
        assertEquals(CardPosition.FACE_UP_ATK, b.hand.last().position)
    }

    @Test
    fun phasesCycleAndTurnsAdvance() {
        var b = board()
        assertEquals(DuelPhase.DRAW, b.phase)
        repeat(5) { b = b.nextPhase() }
        assertEquals(DuelPhase.END, b.phase)
        b = b.nextPhase()
        assertEquals(DuelPhase.DRAW, b.phase)
        assertEquals(2, b.turn)
        assertEquals(3, b.endTurn().turn)
    }

    @Test
    fun lifePointsFloorAtZeroAndDeckRoundTrips() {
        val b = board()
        assertEquals(0, b.adjustLifePoints(-9000).lifePoints)

        var c = b.draw()!!
        c = c.handToDeckTop(0)!!
        assertEquals(40, c.deck.size)
        // A shuffle is a permutation, reproducible from its seed.
        assertEquals(
            c.shuffleDeck(3).deck.map { it.instanceId }.sorted(),
            c.deck.map { it.instanceId }.sorted(),
        )
        assertEquals(c.shuffleDeck(3).deck, c.shuffleDeck(3).deck)
    }
}
