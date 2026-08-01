package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId

/**
 * A way of cutting the deck up.
 *
 * The breakdown draws whatever partition it is handed — it cracks the mosaic
 * where two neighbouring cards belong to different keys and fills each block
 * solid. Which partition that is was hard-wired to the user's own groups, which
 * meant the mode could only ever show back the bookkeeping you had already
 * done: thirty taps in, it tells you what you decided thirty taps ago.
 *
 * Making the partition a parameter is the whole idea here. The same machinery,
 * pointed at facts the card database already knows, puts a reading on a deck
 * you imported ten seconds ago and never labelled.
 */
enum class Lens {
    /** No partition: the plain mosaic, nothing coloured. The quiet position. */
    DECK,

    /** The groups the user drew. */
    ROLES,

    /** The archetypes the cards belong to, largest first. */
    ARCHETYPE,

    /** How many copies of each card the section holds: singletons, pairs, threes. */
    COPIES,
    ;

    val displayName: String
        get() = when (this) {
            DECK -> "Deck"
            ROLES -> "Roles"
            ARCHETYPE -> "Archetype"
            COPIES -> "Copies"
        }

    /** Whether the user can edit this partition, or only read it. */
    val isEditable: Boolean get() = this == ROLES

    fun next(): Lens = entries[(ordinal + 1) % entries.size]

    fun previous(): Lens = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * How a key is coloured.
 *
 * Two kinds, because the two say different things. A [Hue] is nominal — these
 * are different, not more or less — and comes from the prismatic ramp. A [Grey]
 * is ordinal, and reads as a scale: brightest is rarest, because the singleton
 * is the thing you are looking for.
 */
sealed interface KeyPaint {
    data class Hue(val prismIndex: Int) : KeyPaint
    data class Grey(val luminance: Float) : KeyPaint
}

/** One part of a partition: what it is called, how it is marked, how it is lit. */
data class LensKey(
    val id: String,
    val label: String,
    val mark: String,
    val paint: KeyPaint,
)

/**
 * A partition of one section: its keys, and which key each card falls in.
 *
 * [keyOfCell] is index-aligned with the section, so the layout can ask "what is
 * at this position" without a search, and a null means the card is unclaimed —
 * bare, uncoloured, counted.
 */
data class LensKeying(
    val lens: Lens,
    val keys: List<LensKey>,
    val keyOfCell: List<String?>,
) {
    val keyOrder: List<String> get() = keys.map { it.id }

    fun countOf(keyId: String): Int = keyOfCell.count { it == keyId }

    fun keyById(id: String?): LensKey? = id?.let { wanted -> keys.firstOrNull { it.id == wanted } }

    fun keyAt(cell: Int): String? = keyOfCell.getOrNull(cell)

    val unclaimed: Int get() = keyOfCell.count { it == null }

    /** Whether anything is coloured at all — a lens with nothing in it is the plain deck. */
    val isEmpty: Boolean get() = keyOfCell.none { it != null }

    companion object {
        fun none(size: Int, lens: Lens = Lens.DECK) =
            LensKeying(lens, emptyList(), List(size) { null })
    }
}

object DeckLenses {

    /**
     * How many archetypes get a colour.
     *
     * Four, because the ramp has four hues left once red and amber are reserved
     * for illegal and warning — and because the fifth archetype in a deck is
     * usually a one-card splash, which belongs in the remainder where the
     * non-engine is.
     */
    const val MAX_ARCHETYPES = 4

    private val ARCHETYPE_HUES = listOf(2, 3, 4, 5)

    /** Brightest is rarest: the singleton is the card you are looking for. */
    private val COPY_KEYS = listOf(
        Triple("c1", "Singletons", 0.90f),
        Triple("c2", "Pairs", 0.44f),
        Triple("c3", "Threes", 0.22f),
    )

    fun key(
        lens: Lens,
        section: List<CardId>,
        cards: (CardId) -> Card?,
        groups: DeckGroups,
    ): LensKeying = when (lens) {
        Lens.DECK -> LensKeying.none(section.size)
        Lens.ROLES -> roles(section, groups)
        Lens.ARCHETYPE -> archetype(section, cards)
        Lens.COPIES -> copies(section)
    }

    /**
     * The user's own groups.
     *
     * Every group is a key even when it holds nothing in this section, so an
     * empty group stays visible in the bar and can still be opened and filled.
     */
    private fun roles(section: List<CardId>, groups: DeckGroups): LensKeying {
        val ordered = groups.ordered()
        val marks = GroupMarks.marks(ordered)

        return LensKeying(
            lens = Lens.ROLES,
            keys = ordered.map { group ->
                LensKey(
                    id = group.id,
                    label = group.name,
                    mark = marks[group.id].orEmpty(),
                    paint = KeyPaint.Hue(group.color),
                )
            },
            keyOfCell = section.map(groups::groupOf),
        )
    }

    /**
     * The archetypes the cards name, largest first.
     *
     * Ties break by name so the same deck always paints the same — a reading
     * that reshuffles itself between sessions teaches nothing.
     */
    private fun archetype(section: List<CardId>, cards: (CardId) -> Card?): LensKeying {
        val named = section.map { cards(it)?.archetype?.takeIf { name -> name.isNotBlank() } }

        val ranked = named.filterNotNull()
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_ARCHETYPES)
            .map { it.key }

        val marks = GroupMarks.marksFor(ranked.map { it to it })

        return LensKeying(
            lens = Lens.ARCHETYPE,
            keys = ranked.mapIndexed { index, name ->
                LensKey(
                    id = name,
                    label = name,
                    mark = marks[name].orEmpty(),
                    paint = KeyPaint.Hue(ARCHETYPE_HUES[index % ARCHETYPE_HUES.size]),
                )
            },
            // Everything past the fourth archetype is unclaimed, which is where
            // the non-engine lives and is most of the point of this lens.
            keyOfCell = named.map { it?.takeIf(ranked::contains) },
        )
    }

    /**
     * How many copies of itself each card is drawn with.
     *
     * Counted inside the section being drawn, so a card run at three in the
     * main deck and one in the side reads as a three there and a singleton
     * here — which is what each of those decks is actually holding.
     */
    private fun copies(section: List<CardId>): LensKeying {
        val counts = section.groupingBy { it }.eachCount()
        val keyOfCell = section.map { copyKeyOf(counts.getValue(it)) }

        return LensKeying(
            lens = Lens.COPIES,
            keys = COPY_KEYS.filter { (id, _, _) -> keyOfCell.any { it == id } }
                .map { (id, label, luminance) ->
                    LensKey(
                        id = id,
                        label = label,
                        // ×1, ×2, ×3 reads better than two letters of the word.
                        mark = "×${id.drop(1)}",
                        paint = KeyPaint.Grey(luminance),
                    )
                },
            keyOfCell = keyOfCell,
        )
    }

    /** Anything past three is the validator's problem, not this lens's. */
    private fun copyKeyOf(count: Int): String = "c${count.coerceIn(1, 3)}"
}
