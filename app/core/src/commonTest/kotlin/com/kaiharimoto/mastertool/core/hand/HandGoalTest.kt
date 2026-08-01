package com.kaiharimoto.mastertool.core.hand

import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandGoalTest {

    private val deck = (1..40).map { CardId(it) }

    /** Twelve starters, six handtraps, four bricks, eighteen unlabelled. */
    private val groups = run {
        var g = DeckGroups.EMPTY
            .upsert(DeckGroup("starter", "Starter", color = 2, order = 0))
            .upsert(DeckGroup("trap", "Handtrap", color = 5, order = 1))
            .upsert(DeckGroup("brick", "Brick", color = 0, order = 2))
        (0..11).forEach { g = g.assign(deck[it], "starter") }
        (12..17).forEach { g = g.assign(deck[it], "trap") }
        (18..21).forEach { g = g.assign(deck[it], "brick") }
        g
    }

    private fun goal(vararg asks: Pair<String, Ask>) =
        HandGoal("q1", "Opens", asks = asks.toMap())

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected, was $actual")
    }

    // ---- the asks join with AND ---------------------------------------------

    @Test
    fun aGoalIsTheJointQuestionNotTheListOfFacts() {
        // Opening a starter AND a handtrap is strictly rarer than either alone.
        // Conflating the two would quietly answer something nobody asked.
        val both = GoalOdds.probability(
            goal("starter" to Ask.AT_LEAST_1, "trap" to Ask.AT_LEAST_1),
            deck,
            groups,
        )
        val starterOnly = GoalOdds.probability(goal("starter" to Ask.AT_LEAST_1), deck, groups)
        val trapOnly = GoalOdds.probability(goal("trap" to Ask.AT_LEAST_1), deck, groups)

        assertTrue(both < starterOnly, "$both should be rarer than $starterOnly")
        assertTrue(both < trapOnly, "$both should be rarer than $trapOnly")
    }

    @Test
    fun oneAskMatchesTheSingleKeyOdds() {
        // The goal editor and the lens bar must never print two numbers for
        // one question.
        val viaGoal = GoalOdds.probability(goal("starter" to Ask.AT_LEAST_1), deck, groups)
        val complement = (0 until 5).fold(1.0) { acc, i -> acc * (28 - i) / (40 - i) }

        assertClose(1.0 - complement, viaGoal)
    }

    @Test
    fun askingNothingIsCertain() {
        // And is why an empty goal shows no number rather than "100%".
        assertClose(1.0, GoalOdds.probability(goal(), deck, groups))
        assertClose(1.0, GoalOdds.probability(goal("starter" to Ask.ANY), deck, groups))
        assertTrue(goal("starter" to Ask.ANY).isEmpty)
    }

    // ---- the half nobody could say before ------------------------------------

    @Test
    fun theUnlabelledPartOfTheDeckCanBeAskedAbout() {
        // "And no more than one card that does nothing" is half of what anyone
        // wants to know, and it cannot be said in terms of roles you drew —
        // the point is the cards you drew none for.
        val loose = GoalOdds.probability(
            goal("starter" to Ask.AT_LEAST_1),
            deck,
            groups,
        )
        val strict = GoalOdds.probability(
            goal("starter" to Ask.AT_LEAST_1, HandGoal.UNGROUPED to Ask.AT_MOST_1),
            deck,
            groups,
        )

        assertTrue(strict < loose, "adding a ceiling should only ever narrow it")
        assertTrue(strict > 0.0)
    }

    @Test
    fun aBrickFreeOpenIsRarerThanAnyOpen() {
        val any = GoalOdds.probability(goal("starter" to Ask.AT_LEAST_1), deck, groups)
        val clean = GoalOdds.probability(
            goal("starter" to Ask.AT_LEAST_1, "brick" to Ask.NONE),
            deck,
            groups,
        )

        assertTrue(clean < any)
    }

    @Test
    fun twoOfSomethingIsRarerThanOne() {
        val one = GoalOdds.probability(goal("starter" to Ask.AT_LEAST_1), deck, groups)
        val two = GoalOdds.probability(goal("starter" to Ask.AT_LEAST_2), deck, groups)

        assertTrue(two < one)
    }

    @Test
    fun theSixthCardHelps() {
        val five = GoalOdds.probability(goal("starter" to Ask.AT_LEAST_1), deck, groups)
        val six = GoalOdds.probability(
            goal("starter" to Ask.AT_LEAST_1).copy(handSize = 6),
            deck,
            groups,
        )

        assertTrue(six > five)
    }

    // ---- goals survive the deck changing under them --------------------------

    @Test
    fun deletingARoleDoesNotLeaveTheQuestionHaunted() {
        // An ask against a group that no longer exists would silently read as
        // a constraint on an empty group — "at least one of nothing" — and
        // report zero forever with nothing on screen explaining why.
        val asked = goal("starter" to Ask.AT_LEAST_1, "brick" to Ask.NONE)
        val without = groups.remove("brick")

        val pruned = asked.pruned(without)

        assertEquals(Ask.ANY, pruned.ask("brick"))
        assertEquals(Ask.AT_LEAST_1, pruned.ask("starter"))
    }

    @Test
    fun theUngroupedAskSurvivesPruning() {
        // It names no group, so a naive prune would throw it away.
        val asked = goal(HandGoal.UNGROUPED to Ask.AT_MOST_1)

        assertEquals(Ask.AT_MOST_1, asked.pruned(DeckGroups.EMPTY).ask(HandGoal.UNGROUPED))
    }

    @Test
    fun renamingARoleKeepsTheQuestionAttached() {
        // Keyed by id, not by name, exactly so this holds.
        val asked = goal("starter" to Ask.AT_LEAST_1)
        val renamed = groups.upsert(
            groups.byId("starter")!!.copy(name = "Level 1 openers")
        )

        assertClose(
            GoalOdds.probability(asked, deck, groups),
            GoalOdds.probability(asked.pruned(renamed), deck, renamed),
        )
    }

    // ---- the collection ------------------------------------------------------

    @Test
    fun twoQuestionsAreHeldAtOnce() {
        // "Do I open my combo" and "do I brick" pull against each other, and a
        // deck tuned to only the first loses to its own opening hand.
        val goals = HandGoals.EMPTY
            .upsert(HandGoal.blank("q1", "Opens"))
            .upsert(HandGoal.blank("q2", "Bricks"))

        assertEquals(listOf("q1", "q2"), goals.goals.map { it.id })
        assertEquals("Bricks", goals.byId("q2")?.name)
    }

    @Test
    fun editingAGoalKeepsItsPlaceInTheList() {
        // Or the chips reorder under your finger as you answer them.
        val goals = HandGoals.EMPTY
            .upsert(HandGoal.blank("q1", "Opens"))
            .upsert(HandGoal.blank("q2", "Bricks"))
            .upsert(HandGoal.blank("q1", "Opens well"))

        assertEquals(listOf("q1", "q2"), goals.goals.map { it.id })
        assertEquals("Opens well", goals.byId("q1")?.name)
    }

    @Test
    fun removingAGoalLeavesTheRest() {
        val goals = HandGoals.EMPTY
            .upsert(HandGoal.blank("q1"))
            .upsert(HandGoal.blank("q2"))
            .remove("q1")

        assertNull(goals.byId("q1"))
        assertEquals(1, goals.goals.size)
    }

    @Test
    fun settingAnAskToAnyClearsIt() {
        // So a goal stated and then relaxed does not carry a dead constraint.
        val asked = goal("starter" to Ask.AT_LEAST_1).with("starter", Ask.ANY)

        assertTrue(asked.asks.isEmpty())
        assertTrue(asked.isEmpty)
        assertFalse(Ask.ANY.constrains)
    }
}
