package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Format

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

    /** Monster, spell, trap — the split every decklist is quoted in. */
    TYPE,

    /** How many copies of each card the section holds: singletons, pairs, threes. */
    COPIES,

    /** What the banlist says: forbidden, limited, semi-limited. */
    LEGALITY,
    ;

    val displayName: String
        get() = when (this) {
            DECK -> "Deck"
            ROLES -> "Roles"
            ARCHETYPE -> "Archetype"
            TYPE -> "Type"
            COPIES -> "Copies"
            LEGALITY -> "Legality"
        }

    /** Whether the user can edit this partition, or only read it. */
    val isEditable: Boolean get() = this == ROLES

    fun next(): Lens = entries[(ordinal + 1) % entries.size]

    fun previous(): Lens = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * A colour this app already owns, named rather than spelled.
 *
 * Card type and legality are two of the four things §2 of the handbook lets
 * colour mean, and both were coloured before any lens existed — the statistics
 * panel's type split, the ban badge in the corner of a card. A lens that
 * invented its own hues for them would put two different reds on one screen
 * for one fact. Core names the tone; the theme owns the value.
 */
enum class KeyTone { MONSTER, SPELL, TRAP, FORBIDDEN, LIMITED, SEMI_LIMITED }

/**
 * How a key is coloured.
 *
 * Three kinds, because the three say different things. A [Hue] is nominal —
 * these are different, not more or less — and comes from the prismatic ramp. A
 * [Grey] is ordinal, and reads as a scale: brightest is rarest, because the
 * singleton is the thing you are looking for. A [Tone] is a fact the app has
 * already agreed a colour for, and must not be given a second one.
 */
sealed interface KeyPaint {
    data class Hue(val prismIndex: Int) : KeyPaint
    data class Grey(val luminance: Float) : KeyPaint
    data class Tone(val tone: KeyTone) : KeyPaint
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

    private val TYPE_KEYS = listOf(
        Triple("monster", "Monster", KeyTone.MONSTER),
        Triple("spell", "Spell", KeyTone.SPELL),
        Triple("trap", "Trap", KeyTone.TRAP),
    )

    private val LEGALITY_KEYS = listOf(
        Triple("forbidden", "Forbidden", KeyTone.FORBIDDEN),
        Triple("limited", "Limited", KeyTone.LIMITED),
        Triple("semi", "Semi-limited", KeyTone.SEMI_LIMITED),
    )

    fun key(
        lens: Lens,
        section: List<CardId>,
        cards: (CardId) -> Card?,
        groups: DeckGroups,
        format: Format = Format.TCG,
    ): LensKeying = when (lens) {
        Lens.DECK -> LensKeying.none(section.size)
        Lens.ROLES -> roles(section, groups)
        Lens.ARCHETYPE -> archetype(section, cards)
        Lens.TYPE -> type(section, cards)
        Lens.COPIES -> copies(section)
        Lens.LEGALITY -> legality(section, cards, format)
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

    /**
     * Monster, spell, trap.
     *
     * The oldest reading there is — a decklist has been quoted as three
     * numbers for as long as decklists have existed — and the one the tool had
     * only ever shown as a bar chart in a panel, never on the cards
     * themselves. Order is fixed rather than by size, because "20 / 12 / 8" is
     * read in that order whatever the deck holds.
     */
    private fun type(section: List<CardId>, cards: (CardId) -> Card?): LensKeying {
        val keyed = section.map { id ->
            when (cards(id)?.category) {
                CardCategory.MONSTER -> TYPE_KEYS[0].first
                CardCategory.SPELL -> TYPE_KEYS[1].first
                CardCategory.TRAP -> TYPE_KEYS[2].first
                // A token, a skill, or a passcode the database has never heard
                // of. None of them is a card this deck is playing.
                else -> null
            }
        }

        return keying(Lens.TYPE, TYPE_KEYS, keyed)
    }

    /**
     * What the banlist says.
     *
     * Only the restricted cards are keyed. The unlimited ones are the whole
     * rest of the deck, and drawing them as a fourth block in a fourth colour
     * would say "unlimited" is a category you chose rather than the absence of
     * a restriction — so they stay bare, and what lights up is exactly what
     * the list constrains.
     */
    private fun legality(
        section: List<CardId>,
        cards: (CardId) -> Card?,
        format: Format,
    ): LensKeying {
        val keyed = section.map { id ->
            when (cards(id)?.banStatus(format)) {
                BanStatus.FORBIDDEN -> LEGALITY_KEYS[0].first
                BanStatus.LIMITED -> LEGALITY_KEYS[1].first
                BanStatus.SEMI_LIMITED -> LEGALITY_KEYS[2].first
                else -> null
            }
        }

        return keying(Lens.LEGALITY, LEGALITY_KEYS, keyed)
    }

    /** A keying over a fixed key list, dropping the keys nothing fell in. */
    private fun keying(
        lens: Lens,
        definitions: List<Triple<String, String, KeyTone>>,
        keyOfCell: List<String?>,
    ): LensKeying {
        val marks = GroupMarks.marksFor(definitions.map { (id, label, _) -> id to label })

        return LensKeying(
            lens = lens,
            keys = definitions.filter { (id, _, _) -> keyOfCell.any { it == id } }
                .map { (id, label, tone) ->
                    LensKey(id, label, marks[id].orEmpty(), KeyPaint.Tone(tone))
                },
            keyOfCell = keyOfCell,
        )
    }

    /** Anything past three is the validator's problem, not this lens's. */
    private fun copyKeyOf(count: Int): String = "c${count.coerceIn(1, 3)}"
}
