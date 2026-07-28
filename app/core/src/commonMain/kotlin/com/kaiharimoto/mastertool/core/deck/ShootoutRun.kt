package com.kaiharimoto.mastertool.core.deck

/**
 * One opening, judged, and where in the run it happened.
 *
 * [sided] is recorded rather than derived from [game] on purpose. The run *asks*
 * for a sided deck after game one; whether one was actually applied is something
 * only the caller knows, and a report that assumes compliance is a report that
 * can be wrong about the one thing it exists to measure.
 */
data class ShootoutGame(
    val trial: Int,
    val game: Int,
    val youWentFirst: Boolean,
    val sided: Boolean,
    val playable: Boolean,
)

/**
 * A structured playtest against one deck, in trials rather than in hands.
 *
 * The difference from the test-hand panel is the whole point. Judging loose
 * openings tells you how often a list bricks. A *run* asks the question a
 * tournament actually asks: game one is pre-side, everything after it is not,
 * and the reason anybody owns a side deck is the gap between those two numbers.
 * Nothing else in the program can measure that, because nothing else knows which
 * hands came from which version of the list.
 *
 * The run is a plain value. Every step returns a new one, which is what makes
 * undoing a whole trial a `dropLast` instead of a stack of inverse operations.
 */
data class ShootoutRun(
    val trials: Int = DEFAULT_TRIALS,
    val gamesPerTrial: Int = DEFAULT_GAMES_PER_TRIAL,
    val games: List<ShootoutGame> = emptyList(),
) {
    val played: Int get() = games.size

    /** Openings in a full run, which is what the progress line counts towards. */
    val length: Int get() = trials * gamesPerTrial

    val finished: Boolean get() = played >= length

    /** 1-based position of the opening about to be dealt. */
    val nextTrial: Int get() = played / gamesPerTrial + 1
    val nextGame: Int get() = played % gamesPerTrial + 1

    /**
     * Whether the next opening is meant to come from a sided deck.
     *
     * Game one of a trial is pre-side by definition; everything after it is the
     * post-side list. This is the prompt, not the record — see [ShootoutGame].
     */
    val nextIsSided: Boolean get() = nextGame > 1

    /**
     * Which side of the die roll to deal next, so the sample stays balanced.
     *
     * Not a simulation of who won the previous game — nothing here knows that.
     * It is the more useful thing: an even split means both halves of the record
     * reach the point of being worth reading at the same time, instead of one
     * side sitting at two hands all run because the coin kept landing the same
     * way. Alternating falls out of it, and a player who disagrees can just deal
     * the other side.
     */
    val suggestedFirst: Boolean
        get() {
            val first = games.count { it.youWentFirst }
            return first <= played - first
        }

    /** Records a verdict. A finished run ignores it rather than growing past its length. */
    fun record(youWentFirst: Boolean, sided: Boolean, playable: Boolean): ShootoutRun {
        if (finished) return this
        return copy(
            games = games + ShootoutGame(
                trial = nextTrial,
                game = nextGame,
                youWentFirst = youWentFirst,
                sided = sided,
                playable = playable,
            ),
        )
    }

    /** Takes back the last verdict, for a misclick. */
    fun undoGame(): ShootoutRun =
        if (games.isEmpty()) this else copy(games = games.dropLast(1))

    /**
     * Throws away the trial in progress, or the last finished one if none is.
     *
     * The case this exists for is realising halfway through a match that you
     * sided wrong, or against the wrong list — at which point game one of that
     * trial is no longer evidence about anything either, so taking back one
     * verdict is not enough.
     */
    fun undoTrial(): ShootoutRun {
        if (games.isEmpty()) return this
        val partial = played % gamesPerTrial
        return copy(games = games.dropLast(if (partial == 0) gamesPerTrial else partial))
    }

    fun report(): ShootoutReport {
        var pre = MatchupRecord()
        var post = MatchupRecord()
        games.forEach { game ->
            if (game.sided) {
                post = post.judged(game.youWentFirst, game.playable)
            } else {
                pre = pre.judged(game.youWentFirst, game.playable)
            }
        }
        return ShootoutReport(pre, post)
    }

    companion object {
        const val DEFAULT_TRIALS = 10

        /**
         * Game one and game two.
         *
         * A match is best of three, but the third game only happens when the
         * first two split, and counting a game that often does not get played
         * would weight the post-side half of every report. Two is the part of a
         * match that always happens.
         */
        const val DEFAULT_GAMES_PER_TRIAL = 2
    }
}

/**
 * What a run came to, split by which version of the deck dealt the hand.
 *
 * Kept as two records rather than one number because the two questions are
 * different and both are worth an answer: *which side of the die roll do I want
 * against this deck* comes off the whole run, and *is my side deck doing
 * anything* is the gap between the halves.
 */
data class ShootoutReport(
    val preSide: MatchupRecord,
    val postSide: MatchupRecord,
) {
    val overall: MatchupRecord get() = preSide + postSide

    val total: Int get() = preSide.total + postSide.total

    /**
     * Whether siding helped, hurt, did nothing, or has not been asked enough.
     *
     * Null and [SideVerdict.NO_DIFFERENCE] are deliberately not the same answer.
     * Null is "not enough hands to say"; NO_DIFFERENCE is a finding, and often
     * the useful one — a side deck that changes nothing measurable against this
     * matchup is fifteen cards doing no work, which is exactly what somebody
     * running a shootout wants to be told.
     *
     * The margin is set deliberately above what one hand is worth at the
     * minimum sample. Ten hands means a single verdict moves the rate ten
     * points, so a margin of ten would report one lucky opening as proof the
     * side deck works, which is reading noise aloud in a confident voice.
     */
    fun sideVerdict(
        minimumEach: Int = MINIMUM_EACH,
        margin: Double = MARGIN,
    ): SideVerdict? {
        if (preSide.total < minimumEach || postSide.total < minimumEach) return null

        val before = preSide.brickRate ?: return null
        val after = postSide.brickRate ?: return null

        return when {
            before - after >= margin -> SideVerdict.HELPS
            after - before >= margin -> SideVerdict.HURTS
            else -> SideVerdict.NO_DIFFERENCE
        }
    }

    companion object {
        /**
         * Hands from each version of the list before the comparison means
         * anything — which a default run reaches exactly at its last trial.
         *
         * That is the intended shape rather than an accident. Deciding whether
         * fifteen cards are earning their place off six openings is the kind of
         * confident wrong answer this program keeps declining to give.
         */
        const val MINIMUM_EACH = 10

        /** Above one hand's worth at [MINIMUM_EACH], so noise is never a finding. */
        const val MARGIN = 0.15
    }
}

enum class SideVerdict { HELPS, NO_DIFFERENCE, HURTS }
