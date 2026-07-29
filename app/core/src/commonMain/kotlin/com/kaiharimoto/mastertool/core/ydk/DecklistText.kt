package com.kaiharimoto.mastertool.core.ydk

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.deck.Breaks
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.search.TextMatching

/**
 * Reading a decklist that arrives as text rather than as a file.
 *
 * This is how decklists are actually passed around: in a message, in a forum
 * post, under a tournament report, copied off a website. A program that can only
 * open `.ydk` can only accept a list from somebody who already had this program,
 * which is the wrong half of the world to be able to talk to.
 *
 * None of it is a format so much as a family of habits, so this is forgiving in
 * the ways people are actually inconsistent and unforgiving about the one thing
 * that matters: it never invents a card. A line it cannot place comes back as a
 * line it could not place, so whoever pasted can see what did not arrive rather
 * than counting to sixty afterwards.
 */
object DecklistText {

    /** A line that named something no card could be found for. */
    data class Missing(val text: String, val copies: Int)

    data class Parsed(
        val deck: Deck,
        /** In the order they appeared, so the report reads down the paste. */
        val missing: List<Missing>,
    ) {
        val isEmpty: Boolean get() = deck.isEmpty && missing.isEmpty()
    }

    /**
     * Reads [text] into a deck.
     *
     * Two resolvers, because the two questions this asks are not the same one.
     * [exactly] is *is this line, whole and unsplit, the name of a card* — which
     * is only ever asked to tell `8-Claws Scorpion` from a count and a name, and
     * wants a dictionary lookup rather than a search. [orNearly] is *what did
     * they mean by this*, asked once a line has been taken apart, and is where
     * forgiveness belongs.
     *
     * Keeping them apart is worth a parameter twice over. A pool scan per line
     * before anything is even split is sixty scans of thirteen thousand names
     * for a list that was spelled correctly — and a fuzzy match against a whole
     * *unsplit* line is a match against text with a number on the front of it,
     * which is not a question anybody meant to ask.
     */
    fun parse(
        text: String,
        exactly: (String) -> Card?,
        orNearly: (String) -> Card? = exactly,
    ): Parsed {
        val main = ArrayList<CardId>()
        val extra = ArrayList<CardId>()
        val side = ArrayList<CardId>()
        val missing = ArrayList<Missing>()

        // Where the list currently is. Null until something says, in which case
        // a card goes where its own frame puts it.
        var section: DeckSection? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim().removeSuffix(",").trim()
            if (line.isEmpty()) continue

            // Headings before comments, because the YDK format's own section
            // markers are `#main`, `#extra` and `!side` — and a `.ydk` opened in
            // a text editor and pasted straight in is one of the commonest
            // shapes this will ever be handed. Read as comments, its Extra deck
            // would arrive in the Main deck.
            val header = headerFor(line)
            if (header != null) {
                section = header
                continue
            }
            if (isComment(line)) continue

            // The whole line first, before it is taken apart. "8-Claws Scorpion"
            // and "7 Completed" are cards whose names begin with a number and a
            // separator, which is exactly the shape a count is written in — and
            // asking whether the line is already a card is the only thing that
            // tells the two apart. "3 8-Claws Scorpion" still splits, because
            // the whole of it is not a card.
            // A line of nothing but digits is a passcode, and a passcode needs
            // no database to be understood. This is what makes the round trip
            // total: a card the pool has never heard of is written back out as
            // its number, and read back in as the same card in the same place.
            val (bareCount, bareName) = split(line)
            val passcode = bareName.takeIf { it.length >= 5 && it.all(Char::isDigit) }?.toIntOrNull()
            if (passcode != null) {
                val into = when (section ?: DeckSection.MAIN) {
                    DeckSection.MAIN -> main
                    DeckSection.EXTRA -> extra
                    DeckSection.SIDE -> side
                }
                repeat(bareCount) { into += CardId(passcode) }
                continue
            }

            val whole = exactly(line)
            val copies: Int
            val card: Card
            if (whole != null) {
                copies = 1
                card = whole
            } else {
                val (count, name) = split(line)
                val found = orNearly(name)
                if (found == null) {
                    missing += Missing(name, count)
                    continue
                }
                copies = count
                card = found
            }

            // An explicit heading wins over the card's own frame. A list that
            // says "Extra Deck" and then names a Main-deck card has a mistake in
            // it, and quietly moving the card hides the mistake rather than
            // fixing it.
            val into = when (section ?: card.requiredSection()) {
                DeckSection.MAIN -> main
                DeckSection.EXTRA -> extra
                DeckSection.SIDE -> side
            }
            repeat(copies) { into += card.id }
        }

        return Parsed(Deck(main = main, extra = extra, side = side), missing)
    }

    /**
     * Writes [deck] out as the text people actually paste to each other.
     *
     * The inverse of [parse], and tested against it: anything this writes, that
     * reads back as the same deck. Not as the same *arrangement* — a text list
     * counts copies, which is what everyone means by one, and three copies
     * gathered onto one line is three copies that were not necessarily next to
     * each other. Order is by first appearance, so the card an engine starts on
     * still leads its section.
     *
     * The deck's name goes out as a comment, because a name is not part of a
     * decklist and reading one back in as a card would be absurd.
     */
    fun write(
        deck: Deck,
        name: String,
        cardName: (CardId) -> String?,
        /**
         * The gaps, so the piles come out as piles.
         *
         * Every other builder's text export is a flat count of sixty cards.
         * This one can say *these nine are the engine and those six are the
         * handtraps*, because somebody pushed them apart and said so — and it
         * costs nothing to read back, since the headings go out as comments.
         */
        arrangement: Map<DeckSection, Breaks> = emptyMap(),
    ): String = buildString {
        if (name.isNotBlank()) appendLine("# ${name.trim()}")

        DeckSection.entries.forEach { section ->
            val ids = deck[section]
            if (ids.isEmpty()) return@forEach

            if (isNotEmpty()) appendLine()
            appendLine("${section.displayName} Deck")

            val marks = arrangement[section]?.clampedTo(ids.size)
            if (marks == null || marks.isEmpty) {
                counted(ids, cardName)
                return@forEach
            }

            // Pile by pile, and counted *within* each pile. Two copies in the
            // engine and one in the handtraps is three copies of the card and
            // two different decisions, and a flat "3" throws the second one away.
            marks.groups(ids.size).forEach { range ->
                marks.nameOf(range.first)?.let { appendLine("$PILE $it") }
                counted(ids.slice(range), cardName)
            }
        }
    }

    /**
     * What a pile's name is written behind.
     *
     * A comment, so a reader that knows nothing about piles skips it — and one
     * that cannot be mistaken for a heading, which `# Extra` would have been.
     * A pile is allowed to be called Extra; the rest of the section is not
     * allowed to end up in the Extra deck because of it.
     */
    private const val PILE = "# -"

    private fun StringBuilder.counted(ids: List<CardId>, cardName: (CardId) -> String?) {
        // First appearance, counted. `distinct()` keeps the order the deck is in,
        // which is the closest a counted list gets to the arrangement it came
        // from.
        ids.distinct().forEach { id ->
            appendLine("${ids.count { it == id }} ${cardName(id) ?: id.value}")
        }
    }

    /**
     * Whether a card the pool offered is close enough to be the name that was
     * typed, rather than merely the nearest thing in thirteen thousand.
     *
     * The search that finds cards while you type is deliberately forgiving,
     * because you are watching it and can see what it found. Nothing is watching
     * here: sixty lines go past at once, and a line that is not a card at all —
     * a heading nobody recognised, a note somebody left in — will still have a
     * nearest neighbour. So the pool's answer is checked rather than taken.
     *
     * Two ways through. A typo or two, measured against the whole name; or a
     * name that was cut short, which is what a list copied out of a table does
     * to the long ones. Six characters before a truncation is trusted, which is
     * past the point where a prefix is the start of many different cards.
     */
    fun isTheSameName(typed: String, candidate: String): Boolean {
        val a = TextMatching.normalize(typed)
        val b = TextMatching.normalize(candidate)
        if (a.isEmpty()) return false
        if (a == b) return true
        if (a.length >= SHORTEST_PREFIX && b.startsWith(a)) return true
        return TextMatching.editDistanceAtMost(b, a, TYPO) <= TYPO
    }

    /** Edits allowed across a whole card name before it is a different card. */
    const val TYPO = 2

    /** How much of a name has to be there before the rest may be missing. */
    const val SHORTEST_PREFIX = 6

    /**
     * Whether [text] looks enough like a decklist to be worth offering to read.
     *
     * Two lines that could name something is a low bar on purpose: the cost of
     * offering wrongly is a dialog somebody dismisses, and the cost of not
     * offering is a feature nobody finds.
     */
    fun looksLikeAList(text: String): Boolean =
        text.lineSequence()
            .map { it.trim().removeSuffix(",").trim() }
            .count { it.isNotEmpty() && !isComment(it) && headerFor(it) == null } >= 2

    private fun isComment(line: String): Boolean =
        line.startsWith("#") || line.startsWith("//") || line.startsWith("!")

    /**
     * The section a heading names, or null when the line is not one.
     *
     * Monsters, spells and traps are headings a registration sheet uses and all
     * three mean the Main deck — the split inside it is by card type, which this
     * has the cards themselves to work out.
     */
    private fun headerFor(line: String): DeckSection? {
        val cleaned = line.trim().trimStart('#', '!').trim().trimEnd(':').trim()
            .removeSurrounding("[", "]")
            .removeSurrounding("*")
            .trim()
            .lowercase()
        val words = cleaned.split(' ', '(', ')', ':').filter { it.isNotEmpty() }
        val head = words.firstOrNull() ?: return null

        // A heading is a heading, not a card whose name starts with one. "Main
        // Deck" and "Monsters (17)" are headings; "Extra Foolish Burial" is not.
        val rest = words.drop(1).filter { it !in DECORATION && it.toIntOrNull() == null }
        if (rest.isNotEmpty()) return null

        return when (head.trimEnd('s')) {
            "main", "monster", "spell", "magic", "trap" -> DeckSection.MAIN
            "extra" -> DeckSection.EXTRA
            "side" -> DeckSection.SIDE
            else -> null
        }
    }

    private val DECORATION = setOf("deck", "cards", "card")

    /**
     * Splits a line into a count and a name, defaulting to one copy.
     *
     * Every shape people write it in: `3 Ash Blossom`, `3x Ash Blossom`,
     * `3. Ash Blossom`, `Ash Blossom x3`, `Ash Blossom (3)`.
     */
    private fun split(line: String): Pair<Int, String> {
        LEADING.matchEntire(line)?.let { match ->
            val count = match.groupValues[1].toIntOrNull()
            if (count != null && count > 0) return count to match.groupValues[2].trim()
        }
        TRAILING_TIMES.matchEntire(line)?.let { match ->
            val count = match.groupValues[2].toIntOrNull()
            if (count != null && count > 0) return count to match.groupValues[1].trim()
        }
        TRAILING_BRACKET.matchEntire(line)?.let { match ->
            val count = match.groupValues[2].toIntOrNull()
            if (count != null && count > 0) return count to match.groupValues[1].trim()
        }
        return 1 to line
    }

    // A separator is required, so "3xAsh" is left alone rather than guessed at,
    // and a hyphen is not one of them: "8-Claws Scorpion" is a card.
    private val LEADING = Regex("""^(\d{1,2})\s*[xX*]?[\s.):]+\s*(.+)$""")

    private val TRAILING_TIMES = Regex("""^(.+?)\s*[xX*]\s*(\d{1,2})$""")
    private val TRAILING_BRACKET = Regex("""^(.+?)\s*\((\d{1,2})\)$""")
}
