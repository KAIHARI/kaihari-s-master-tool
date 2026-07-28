package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShootoutRunTest {

    @Test
    fun aFreshRunIsAtTheStartOfTheFirstTrial() {
        val run = ShootoutRun()

        assertEquals(1, run.nextTrial)
        assertEquals(1, run.nextGame)
        assertFalse(run.nextIsSided, "game one is pre-side by definition")
        assertFalse(run.finished)
    }

    @Test
    fun theSecondGameOfATrialAsksForTheSidedList() {
        val run = ShootoutRun().record(youWentFirst = true, sided = false, playable = true)

        assertEquals(1, run.nextTrial)
        assertEquals(2, run.nextGame)
        assertTrue(run.nextIsSided)
    }

    @Test
    fun finishingATrialStartsTheNextOnePreSide() {
        val run = ShootoutRun()
            .record(youWentFirst = true, sided = false, playable = true)
            .record(youWentFirst = true, sided = true, playable = true)

        assertEquals(2, run.nextTrial)
        assertEquals(1, run.nextGame)
        assertFalse(run.nextIsSided)
    }

    @Test
    fun aRunEndsAfterItsTrials() {
        var run = ShootoutRun(trials = 2, gamesPerTrial = 2)
        repeat(4) { run = run.record(youWentFirst = true, sided = false, playable = true) }

        assertTrue(run.finished)
        assertEquals(4, run.played)
    }

    @Test
    fun aFinishedRunRefusesMoreVerdicts() {
        var run = ShootoutRun(trials = 1, gamesPerTrial = 1)
        run = run.record(youWentFirst = true, sided = false, playable = true)

        assertEquals(run, run.record(youWentFirst = false, sided = true, playable = false))
    }

    @Test
    fun theSuggestedSideAlternatesSoBothHalvesFill() {
        var run = ShootoutRun()
        val sides = mutableListOf<Boolean>()

        repeat(6) {
            val side = run.suggestedFirst
            sides.add(side)
            run = run.record(youWentFirst = side, sided = false, playable = true)
        }

        assertEquals(listOf(true, false, true, false, true, false), sides)
    }

    @Test
    fun dealingAgainstTheSuggestionIsCorrectedByTheNextSuggestion() {
        // Two on the same side and the run asks for the other one twice, rather
        // than politely alternating from wherever it was left.
        var run = ShootoutRun()
        repeat(2) { run = run.record(youWentFirst = true, sided = false, playable = true) }

        assertFalse(run.suggestedFirst)
        run = run.record(youWentFirst = false, sided = false, playable = true)
        assertFalse(run.suggestedFirst)
    }

    @Test
    fun undoingAGameTakesBackOneVerdict() {
        val run = ShootoutRun()
            .record(youWentFirst = true, sided = false, playable = true)
            .record(youWentFirst = true, sided = true, playable = false)
            .undoGame()

        assertEquals(1, run.played)
        assertEquals(2, run.nextGame)
    }

    @Test
    fun undoingNothingIsHarmless() {
        val run = ShootoutRun()

        assertEquals(run, run.undoGame())
        assertEquals(run, run.undoTrial())
    }

    @Test
    fun undoingATrialInProgressThrowsAwayItsGameOneToo() {
        val run = ShootoutRun()
            .record(youWentFirst = true, sided = false, playable = true)
            .record(youWentFirst = true, sided = true, playable = true)
            .record(youWentFirst = false, sided = false, playable = false)
            .undoTrial()

        assertEquals(2, run.played, "only the third game belonged to the trial in progress")
        assertEquals(2, run.nextTrial)
    }

    @Test
    fun undoingWithNoTrialInProgressThrowsAwayTheLastFinishedOne() {
        val run = ShootoutRun()
            .record(youWentFirst = true, sided = false, playable = true)
            .record(youWentFirst = true, sided = true, playable = true)
            .undoTrial()

        assertEquals(0, run.played)
        assertEquals(1, run.nextTrial)
    }
}

class ShootoutReportTest {

    private fun run(vararg games: Triple<Boolean, Boolean, Boolean>): ShootoutRun =
        games.fold(ShootoutRun(trials = 50)) { run, (first, sided, playable) ->
            run.record(youWentFirst = first, sided = sided, playable = playable)
        }

    @Test
    fun theTwoVersionsOfTheListAreCountedApart() {
        val report = run(
            Triple(true, false, false),
            Triple(true, true, true),
            Triple(false, false, false),
            Triple(false, true, true),
        ).report()

        assertEquals(2, report.preSide.total)
        assertEquals(1.0, report.preSide.brickRate, "both pre-side openings were bricks")
        assertEquals(2, report.postSide.total)
        assertEquals(0.0, report.postSide.brickRate, "both post-side openings were playable")
    }

    @Test
    fun theOverallRecordStillKnowsWhichSideOfTheDieRoll() {
        // Bricks going first, functions going second, either version of the list.
        val games = (1..6).flatMap {
            listOf(
                Triple(true, false, false),
                Triple(false, true, true),
            )
        }
        val report = run(*games.toTypedArray()).report()

        assertEquals(Preference.SECOND, report.overall.preference())
    }

    @Test
    fun aSideDeckThatFixesTheMatchupSaysSo() {
        val games = (1..10).flatMap {
            listOf(
                Triple(true, false, false),
                Triple(true, true, true),
            )
        }

        assertEquals(SideVerdict.HELPS, run(*games.toTypedArray()).report().sideVerdict())
    }

    @Test
    fun aSideDeckThatMakesThingsWorseAlsoSaysSo() {
        val games = (1..10).flatMap {
            listOf(
                Triple(true, false, true),
                Triple(true, true, false),
            )
        }

        assertEquals(SideVerdict.HURTS, run(*games.toTypedArray()).report().sideVerdict())
    }

    @Test
    fun aSideDeckDoingNothingIsAFindingRatherThanSilence() {
        val games = (1..10).flatMap {
            listOf(
                Triple(true, false, true),
                Triple(true, true, true),
            )
        }

        assertEquals(
            SideVerdict.NO_DIFFERENCE,
            run(*games.toTypedArray()).report().sideVerdict(),
        )
    }

    @Test
    fun oneHandOfSwingIsNotAnImprovement() {
        // Ten each, one fewer brick after siding: inside the margin, so it is
        // reported as no difference rather than as the side deck working.
        val games = buildList {
            repeat(10) { add(Triple(true, false, it == 0)) }
            repeat(10) { add(Triple(true, true, it <= 1)) }
        }

        assertEquals(
            SideVerdict.NO_DIFFERENCE,
            run(*games.toTypedArray()).report().sideVerdict(),
        )
    }

    @Test
    fun tooFewHandsIsNotAVerdict() {
        val games = (1..8).flatMap {
            listOf(
                Triple(true, false, false),
                Triple(true, true, true),
            )
        }

        assertNull(run(*games.toTypedArray()).report().sideVerdict())
    }

    @Test
    fun aRunWithNoSidedGamesNeverClaimsSidingDidAnything() {
        val games = (1..12).map { Triple(true, false, false) }

        assertNull(run(*games.toTypedArray()).report().sideVerdict())
    }
}
