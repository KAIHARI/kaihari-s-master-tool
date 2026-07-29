package com.kaiharimoto.mastertool.core.visual

/**
 * Which frame a card is printed in.
 *
 * The one thing about a card you can read across a table without reading
 * anything: orange is an effect monster, green is a spell, magenta is a trap,
 * black is an Xyz. Deriving it here rather than in the drawing code means the
 * mapping can be checked against every string the card database actually emits.
 */
enum class FrameKind { NORMAL, EFFECT, RITUAL, FUSION, SYNCHRO, XYZ, LINK, SPELL, TRAP, TOKEN, OTHER }

/** One translucent shape in a card's art window. All values are fractions of it. */
data class FaceShape(
    val x: Float,
    val y: Float,
    val size: Float,
    /** Rotation, in turns: 0f..1f is a full circle, which is what `graphicsLayer` wants times 360. */
    val turns: Float,
    val alpha: Float,
)

/**
 * A card drawn from what is known about it, for when there is no picture.
 *
 * This application is built for a venue with no signal — it says so, it keeps an
 * outdated card pool rather than none, and it will not clear the cache on a
 * failed refresh. And then it drew every card as a grey rectangle with the name
 * written across it, so the offline case it was designed for was also the one
 * where forty cards touching read as a wall of labels rather than as a deck.
 *
 * So the card is drawn: its frame, its name, its level, its attribute, its ATK
 * and DEF, and an abstract field in the art window seeded by the passcode — so
 * three copies of the same card look like three copies of the same card, and the
 * one beside them does not. It is not a forgery of the artwork and is not trying
 * to be; it is a card-shaped thing that is different for every card, which is all
 * that an arrangement needs from it.
 *
 * Every number here is pure so it can be tested, because the module that draws it
 * cannot be compiled in the environment it was written in.
 */
object CardFace {

    /** How many shapes go in an art window. Four reads as texture; eight as noise. */
    const val SHAPES = 4

    /** Stars beyond this stop being countable at the size a card is drawn. */
    const val MAX_PIPS = 12

    /**
     * The frame from the database's machine-readable `frameType`.
     *
     * Order matters. `normal_pendulum` contains "normal" and `fusion_pendulum`
     * contains "fusion", and the second half is the one that says where the card
     * is summoned from — so the Extra Deck frames are matched first, exactly as
     * `Card.isExtraDeck` does it. A Pendulum's split frame is not drawn: at the
     * size cards are drawn here, half a frame is a smudge.
     */
    fun kindOf(frameType: String): FrameKind {
        val frame = frameType.trim().lowercase()
        return when {
            frame.isEmpty() -> FrameKind.OTHER
            "fusion" in frame -> FrameKind.FUSION
            "synchro" in frame -> FrameKind.SYNCHRO
            "xyz" in frame -> FrameKind.XYZ
            "link" in frame -> FrameKind.LINK
            "ritual" in frame -> FrameKind.RITUAL
            "spell" in frame -> FrameKind.SPELL
            "trap" in frame -> FrameKind.TRAP
            "token" in frame -> FrameKind.TOKEN
            "effect" in frame -> FrameKind.EFFECT
            "normal" in frame -> FrameKind.NORMAL
            else -> FrameKind.OTHER
        }
    }

    /** Whether this frame carries ATK, DEF and a level rather than a type word. */
    fun isMonster(kind: FrameKind): Boolean = when (kind) {
        FrameKind.SPELL, FrameKind.TRAP, FrameKind.OTHER -> false
        else -> true
    }

    /** Stars under the name, clamped so a Level 12 and a Rank 13 both fit. */
    fun pips(level: Int?): Int = level?.coerceIn(0, MAX_PIPS) ?: 0

    /**
     * The angle of the wash behind a card's art, in turns.
     *
     * Taken from the passcode so it is the card's own and never moves: an art
     * window that looked different between two draws would make a deck flicker
     * while being scrolled.
     */
    fun angle(seed: Int): Float = Rng(seed).next()

    /**
     * The shapes in a card's art window.
     *
     * Deterministic in the passcode, so the same card is the same picture on
     * every screen it is drawn on and in the PNG exported of it.
     */
    fun shapes(seed: Int, count: Int = SHAPES): List<FaceShape> {
        val rng = Rng(seed)
        rng.next() // burn the angle, so the two agree about which draw is which

        return List(count.coerceAtLeast(0)) {
            FaceShape(
                x = rng.next(),
                y = rng.next(),
                size = 0.20f + rng.next() * 0.40f,
                turns = rng.next() * 0.5f,
                alpha = 0.05f + rng.next() * 0.09f,
            )
        }
    }
}

/**
 * A small deterministic generator.
 *
 * The textbook linear congruential one, spelled out rather than taken from
 * `kotlin.random.Random`: this has to give the same picture on every platform
 * and in every version, and `Random(seed)`'s sequence is not promised to.
 */
private class Rng(seed: Int) {

    // Zero is a fixed point of a multiplicative step, so a passcode of 0 would
    // produce a card with every shape stacked in one corner.
    private var state: Int = if (seed == 0) 1 else seed and 0x7fffffff

    fun next(): Float {
        state = (state * 1103515245 + 12345) and 0x7fffffff
        return state.toFloat() / 0x7fffffff
    }
}
