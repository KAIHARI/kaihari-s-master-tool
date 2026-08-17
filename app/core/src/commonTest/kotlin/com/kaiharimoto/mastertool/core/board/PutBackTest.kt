package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.PileFan
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.scene.Scenery
import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Taking a card out of a spread and putting it straight back.
 *
 * The gesture is "changed my mind", and it is aimed at *the slot the card came
 * out of* rather than at the fan as a whole for a reason that is arithmetic
 * rather than taste: a spread is laid out over `layout.field`, which is the
 * seven-by-three grid itself, so its footprint covers every zone and every pile
 * on the table. A rule that let the fan outrank the board would mean that while
 * a pile is open you could not put a card down on the board at all.
 *
 * **And aiming at one slot was not enough, which is what this file exists to
 * measure now.** That slot is drawn lifted, so its footprint on the felt lands a
 * fifth of a card from a monster zone's centre — and a half-card catchment
 * around it swallowed a catchment of the same size belonging to the zone. kai
 * reported it as *"since the fan is above the field it tries to put the card
 * back into the pile"*. The claims below are therefore driven from real
 * `PileFan` output against real zone centres, through the real projection, over
 * every slot of three differently sized spreads. The version of this file that
 * shipped the bug asserted the same thing about **one hand-picked point**, and
 * that point was not even clear of the board: it sat an eighth of a card from a
 * monster zone's centre, so the file's own "the board still takes cards" claim
 * was a claim about a coincidence.
 */
class PutBackTest {

    private val layout = BoardLayouter.solve(1600f, 1000f, 59f / 86f)
    private val field = PlayField.setUp((1..40).map { CardId(it) }, (101..115).map { CardId(it) })

    /**
     * Somewhere clear of every zone, so nothing else can claim the answer.
     *
     * It used to be `MatPoint(0.34f, 0.5f)`, which is 0.12 card widths from a
     * monster zone's centre — the file was demonstrating "put back beats the
     * board" on a point that *is* the board, and every claim below it was a
     * claim about a coincidence. `theFixtureIsClearOfTheBoard` holds this one
     * honest.
     */
    private val came = MatPoint(0.04f, 0.08f)

    /** Armed: the card has been out of its slot, so putting it back means something. */
    private val left = FanHome(came, departed = true)

    private fun resolve(
        at: MatPoint,
        previous: DropIntent? = null,
        home: FanHome? = left,
    ) = DropTargets.resolve(at, null, field, layout, previous, false, home)

    /** [cards] card widths to the right of where the card came from. */
    private fun away(cards: Float): MatPoint {
        val px = layout.toPixels(came)
        return layout.toMat((px.first + layout.cardWidth * cards) to px.second)
    }

    private fun atSlot(slot: BoardSlot): MatPoint {
        val rect = layout[slot]!!
        return layout.toMat(rect.centerX to rect.centerY)
    }

    @Test
    fun theFixtureIsClearOfTheBoard() {
        // Every unit claim in this file is "put back wins here", which says
        // nothing unless something else would otherwise have won here. With no
        // fan open, all three probe points must be bare mat.
        listOf(came, away(0.25f), away(0.7f)).forEach {
            assertIs<DropIntent.Free>(resolve(it, home = null), "the fixture sits on something")
        }
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

    // ---- but only once the card has actually been taken out ------------------

    @Test
    fun aCardThatHasNotLeftItsSlotYetIsNotBeingPutBack() {
        // The half that was missing, and the reason the gesture could steal a
        // drop it was never aimed at. A put-back is a change of mind; a card
        // that has not gone anywhere has not changed anybody's mind. Until it
        // has, the slot it is sitting over is just a place on the board, and the
        // board answers for it.
        assertEquals(DropIntent.Free(came), resolve(came, home = FanHome(came)))
    }

    @Test
    fun theLatchArmsWhenTheCardIsCarriedClearAndStaysArmed() {
        var home = FanHome(came)
        // A nudge is not a departure.
        home = home.seeing(away(0.5f), layout)
        assertTrue(!home.departed, "half a card away already counts as having left")

        home = home.seeing(away(1.5f), layout)
        assertTrue(home.departed, "a card and a half away is not a departure")

        // And bringing it back does not un-arm it: a target that could vanish
        // under your finger is worse than one that was never there.
        assertEquals(DropIntent.Cancel, resolve(came, home = home.seeing(came, layout)))
    }

    @Test
    fun aLiftAloneNeverArmsIt() {
        // A card taken out of a spread rises from the fan's own height to a
        // carried card's, so its landing point moves up-table with nobody's hand
        // moving at all. `DEPARTURE` has to clear that, or every drag out of a
        // spread would open already armed — which is the bug in its first form.
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(2960f, 1848f)
        val board = deskLayout(2960f, 1848f)
        val spread = PileFan.spread(40, board.field, board.cardWidth, board.cardHeight)
        val fanLift = CarryHeight.fan(board.cardHeight)
        val carryLift = CarryHeight.mat(board.cardHeight)

        spread.cards.forEach { card ->
            val slot = onFelt(board, plane, card.x, card.y, fanLift)
            val finger = drawnAt(board, plane, card.x, card.y, fanLift)
            val justLifted = onFelt(board, plane, finger.first, finger.second, carryLift)
            assertTrue(
                !FanHome(slot).seeing(justLifted, board).departed,
                "slot ${card.index} armed itself on the lift",
            )
        }
    }

    // ---- what it must not break, measured over a real spread -----------------

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
    fun everySlotOfEverySpreadCanReachEveryZoneAndEveryPile() {
        // The measurement, and the test the shipped one should have been. Three
        // spread sizes, every slot, every zone and every pile, dragged the way a
        // finger drags: the home is the slot's own drawn footprint, the landing
        // is the carried card's, the latch is fed each step and the previous
        // intent is fed back so the hysteresis is real.
        //
        // On the code this replaced, a six-card graveyard search lost the entire
        // monster row from every one of its six slots, and once "Put back" had
        // latched there was no point inside the zone far enough out of the
        // sticky disc to escape it.
        val board = deskLayout(2960f, 1848f)
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(2960f, 1848f)
        val targets = board.slots.keys.filter { it is BoardSlot.Zone } +
            listOf(BoardSlot.Graveyard, BoardSlot.Banished, BoardSlot.Deck, BoardSlot.ExtraDeck)

        listOf(40, 15, 6).forEach { count ->
            val spread = PileFan.spread(count, board.field, board.cardWidth, board.cardHeight)
            spread.cards.forEach { card ->
                targets.forEach { target ->
                    val rect = board[target] ?: return@forEach
                    val got = drag(board, plane, card.x, card.y, rect.centerX, rect.centerY)
                    assertNotEquals(
                        DropIntent.Cancel,
                        got,
                        "a $count-card spread: slot ${card.index} cannot reach $target",
                    )
                }
            }
        }
    }

    @Test
    fun andAfterWanderingAboutFirstItStillCan() {
        // The path the latch alone does not fix, and the reason rank 0 also asks
        // whether a zone is pulling harder. Take the card out, carry it two and
        // a half card widths clear while you decide — which arms the latch — and
        // only then aim at a monster zone.
        val board = deskLayout(2960f, 1848f)
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(2960f, 1848f)
        val zones = board.slots.entries.filter { it.key is BoardSlot.Zone }

        listOf(40, 15, 6).forEach { count ->
            val spread = PileFan.spread(count, board.field, board.cardWidth, board.cardHeight)
            spread.cards.forEach { card ->
                zones.forEach { (slot, rect) ->
                    val got = drag(
                        board, plane, card.x, card.y, rect.centerX, rect.centerY,
                        wanderTo = card.x + board.cardWidth * 2.5f to card.y,
                    )
                    assertNotEquals(
                        DropIntent.Cancel,
                        got,
                        "a $count-card spread: slot ${card.index} lost $slot after wandering",
                    )
                }
            }
        }
    }

    @Test
    fun andPuttingItBackStillWorksFromEveryDirection() {
        // The other half: the gesture the whole rank exists for has to survive
        // both new gates, from every slot of every spread, coming back from
        // either side. The card is aimed at the *hole*, which on the felt is the
        // slot's own drawn footprint — that is what `home.at` is, and it is
        // where the player can see the gap.
        val board = deskLayout(2960f, 1848f)
        val plane = StageTuning.DEFAULT.camera.pose().planeFor(2960f, 1848f)
        val fanLift = CarryHeight.fan(board.cardHeight)

        listOf(40, 15, 6).forEach { count ->
            val spread = PileFan.spread(count, board.field, board.cardWidth, board.cardHeight)
            spread.cards.forEach { card ->
                val hole = board.toPixels(onFelt(board, plane, card.x, card.y, fanLift))
                listOf(
                    card.x + board.cardWidth * 2.5f to card.y,
                    card.x - board.cardWidth * 2.5f to card.y,
                    card.x to card.y + board.cardHeight * 1.5f,
                ).forEach { wander ->
                    val got = drag(
                        board, plane, card.x, card.y, hole.first, hole.second,
                        wanderTo = wander,
                    )
                    assertEquals(
                        DropIntent.Cancel,
                        got,
                        "a $count-card spread: slot ${card.index} would not go back via $wander",
                    )
                }
            }
        }
    }

    @Test
    fun withNothingOpenNothingIsEverPutBack() {
        // The card is dropped exactly where a spread slot would have been, and
        // with no fan open that is simply a place on the mat.
        assertNotEquals(DropIntent.Cancel, resolve(came, home = null))
    }

    @Test
    fun aCardIsPutBackByBeingReleasedAndNothingElseHappens() {
        // `DropCommit` already answers Cancel with null, which is exactly the
        // move that is not made — so the field the table had is the field it
        // keeps, and the card returns to the spread it never really left.
        assertEquals(null, DropCommit.commit(field, DragOrigin.Pile(BoardSlot.Deck, 7), DropIntent.Cancel))
    }

    // ---- the simulation -------------------------------------------------------

    private fun deskLayout(width: Float, height: Float) = BoardLayouter.solve(
        width = width,
        height = height,
        aspectRatio = 59f / 86f,
        perspectiveGrowth = 1.2f,
        roomAbove = Scenery.ROOM_ABOVE,
    )

    /** The felt footprint of something drawn at [lift] over a mat point. */
    private fun onFelt(
        board: com.kaiharimoto.mastertool.core.layout.BoardLayout,
        plane: StagePlane,
        x: Float,
        y: Float,
        lift: Float,
    ): MatPoint = CarryHeight.landing(x, y, lift, plane).let { board.toMat(it.x to it.y) }

    /** And where the finger has to be for a thing at [lift] to be drawn there. */
    private fun drawnAt(
        board: com.kaiharimoto.mastertool.core.layout.BoardLayout,
        plane: StagePlane,
        x: Float,
        y: Float,
        lift: Float,
    ): Pair<Float, Float> = CarryHeight.drawnAt(x, y, lift, plane).let { it.x to it.y }

    /**
     * A whole drag, resolved frame by frame, aimed by where the card is *drawn*.
     *
     * Every leg is a place on the felt the player wants the card to be over, and
     * the finger is derived from it — because a drop lands where the card is
     * drawn and that is what anybody aiming does. Aiming the *finger* at a zone
     * centre is aiming the card a row up-table of it, which is a different
     * gesture and would have made this whole file measure the wrong thing.
     *
     * Each step the latch is fed the landing and the previous intent is fed
     * back, so the hysteresis is the hysteresis the table really has.
     */
    private fun drag(
        board: com.kaiharimoto.mastertool.core.layout.BoardLayout,
        plane: StagePlane,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        wanderTo: Pair<Float, Float>? = null,
        steps: Int = 24,
    ): DropIntent {
        val fanLift = CarryHeight.fan(board.cardHeight)
        val carryLift = CarryHeight.mat(board.cardHeight)
        val slot = onFelt(board, plane, fromX, fromY, fanLift)
        var home = FanHome(slot)
        var intent: DropIntent? = null

        // The finger at the lift is under the card as the *fan* draws it.
        var at = drawnAt(board, plane, fromX, fromY, fanLift)
        val legs = listOfNotNull(wanderTo, toX to toY)
            .map { drawnAt(board, plane, it.first, it.second, carryLift) }

        legs.forEach { leg ->
            val from = at
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val fx = from.first + (leg.first - from.first) * t
                val fy = from.second + (leg.second - from.second) * t
                val landing = onFelt(board, plane, fx, fy, carryLift)
                home = home.seeing(landing, board)
                intent = DropTargets.resolve(landing, null, field, board, intent, false, home)
            }
            at = leg
        }
        return intent ?: DropIntent.Free(MatPoint.Centre)
    }

    // ---- and it holds at whatever angle the stage is looked at from ------------

    @Test
    fun bothGesturesSurviveEveryPitchTheStageCanOpenAt() {
        // The claim the two above make at *one* seat, made at every seat — and
        // the reason it is here is that the one-seat version passed for two
        // releases while being one number away from false.
        //
        // A spread's holes and the zones are laid out over the same grid, and
        // what separates them is that a fanned card is drawn lifted, so its
        // footprint sits up-table by an offset that grows with `sin(tilt)`.
        // Measured against real `PileFan` output, the nearest a hole gets to a
        // zone centre is 0.178 card widths at 41.5 degrees and **0.072** at 58 —
        // while `INCUMBENT_BIAS` is 0.12. So at the head-height seat the
        // hysteresis was worth more than the distance it was arbitrating, and
        // both gestures broke at once in opposite directions: a latched put-back
        // won against a zone the finger pointed exactly at, and a latched zone
        // won against a hole the finger pointed exactly at.
        //
        // Capping that bias at half the gap makes it arithmetically impossible
        // for either to carry past the other's centre, at any angle. This sweeps
        // the envelope rather than the four seats, because the camera is free and
        // a player who tilts to sixty-five degrees is still searching a pile.
        val board = deskLayout(2960f, 1848f)
        val fanLift = CarryHeight.fan(board.cardHeight)
        val zones = board.slots.entries.filter { it.key is BoardSlot.Zone }

        listOf(21f, 34f, 41.5f, 50f, 58f, 65f, 72f).forEach { pitch ->
            val plane = StageTuning.DEFAULT.camera
                .copy(pitchDegrees = pitch)
                .let { it.envelope().clamp(it.pose(), 2960f, 1848f) }
                .planeFor(2960f, 1848f)

            listOf(40, 15).forEach { count ->
                val spread = PileFan.spread(count, board.field, board.cardWidth, board.cardHeight)
                spread.cards.forEach { card ->
                    // Aiming at a zone must reach the zone.
                    zones.forEach { (slot, rect) ->
                        assertNotEquals(
                            DropIntent.Cancel,
                            drag(
                                board, plane, card.x, card.y, rect.centerX, rect.centerY,
                                wanderTo = card.x + board.cardWidth * 2.5f to card.y,
                            ),
                            "at $pitch degrees, a $count-card spread: slot ${card.index} lost $slot",
                        )
                    }
                    // And aiming at the hole must still put it back.
                    val hole = board.toPixels(onFelt(board, plane, card.x, card.y, fanLift))
                    assertEquals(
                        DropIntent.Cancel,
                        drag(
                            board, plane, card.x, card.y, hole.first, hole.second,
                            wanderTo = card.x + board.cardWidth * 2.5f to card.y,
                        ),
                        "at $pitch degrees, a $count-card spread: slot ${card.index} would not go back",
                    )
                }
            }
        }
    }
}
