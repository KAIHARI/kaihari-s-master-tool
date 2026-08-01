package com.kaiharimoto.mastertool.core.deck

/**
 * A two-letter mark per group: EN, ST, EX, NE, BR, HA, BE, OU.
 *
 * The prismatic ramp has six hues and a deck can have more groups than that, so
 * colour alone cannot say which group a piece is — and even inside six, telling
 * violet from magenta across a screen is not reading, it is guessing. The mark
 * is the identity and the colour is the reinforcement: the same two characters
 * appear on the piece, on the legend key and in the assignment menu, so finding
 * "which one is Handtrap" is reading rather than hue-matching.
 *
 * Two letters rather than one because "Engine" and "Extender" both start with E,
 * and rather than a whole word because the label has to sit on a card without
 * covering the art.
 */
object GroupMarks {

    /** Marks for [groups], guaranteed distinct within the list. */
    fun marks(groups: List<DeckGroup>): Map<String, String> =
        marksFor(groups.map { it.id to it.name })

    /**
     * Marks for any list of `id to name`, guaranteed distinct within the list.
     *
     * Groups are not the only thing that gets marked any more: an archetype and
     * a copy count are named on the deck the same way, by the same rules, so
     * that two letters on a card mean the same kind of thing whichever lens
     * drew them.
     */
    fun marksFor(entries: List<Pair<String, String>>): Map<String, String> {
        val taken = mutableSetOf<String>()
        val marks = LinkedHashMap<String, String>()

        entries.forEachIndexed { index, (id, name) ->
            val unique = candidates(name, index).first { it !in taken }
            taken += unique
            marks[id] = unique
        }

        return marks
    }

    /**
     * Marks to try for one group, best first.
     *
     * "Brick" and "Breaker" both want BR, and the fix is the one a person would
     * reach for: keep the initial and take the next letter that tells them
     * apart, so the loser becomes BE rather than B2. Only when the word runs out
     * of letters — or when a dozen groups are all called the same thing — does
     * it fall back to a counter, and even then the mark stays two characters
     * wide so a column of them lines up.
     */
    private fun candidates(name: String, index: Int): Sequence<String> = sequence {
        val base = mark(name)
        yield(base)

        val letters = name.filter { it.isLetter() }
        if (letters.length > 2) {
            val initial = letters.first().uppercaseChar()
            letters.drop(2).forEach { yield("$initial${it.uppercaseChar()}") }
        }

        val initial = base.first()
        // Base 36 so the counter is always one character: G1 … G9, GA, GB.
        yieldAll(generateSequence(index + 1) { it + 1 }.map { "$initial${it.toString(36).uppercase().last()}" })
    }

    /**
     * One group's mark, before collisions are resolved.
     *
     * Two words give their initials — "Non-engine" is NE — and one word gives
     * its first two letters, because ST reads as Starter far better than S does.
     */
    fun mark(name: String): String {
        val words = name.split(*SEPARATORS).filter { it.isNotBlank() }

        return when {
            words.isEmpty() -> "G"
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            words[0].length == 1 -> words[0].uppercase()
            else -> words[0].take(2).uppercase()
        }
    }

    private val SEPARATORS = charArrayOf(' ', '-', '_', '/', '&', '+', '.', ',')
}
