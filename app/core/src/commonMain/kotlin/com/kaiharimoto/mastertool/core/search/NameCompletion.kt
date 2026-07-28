package com.kaiharimoto.mastertool.core.search

/**
 * The rest of a card's name, offered while it is being typed.
 *
 * Search already ranks fuzzily and forgivingly, which is right for finding a
 * card and wrong for finishing a word: a suggestion that cannot be reached by
 * carrying on typing is a suggestion that makes the field feel possessed. So
 * this deliberately knows nothing about scoring. It offers a completion only
 * when the name literally continues the characters already in the box, which
 * means accepting one is always the same as having typed faster.
 *
 * [typed] is what is in the field, [suffix] is what would be added, and their
 * concatenation is [name] — a property worth stating because the whole
 * interaction rests on it. The field draws the typed part invisibly and the
 * suffix in grey, so the two have to line up character for character.
 */
data class NameCompletion(
    val typed: String,
    val suffix: String,
    val name: String,
)

object Completions {

    /**
     * How far down the results a completion may come from.
     *
     * Far enough that a good name a few places down still gets offered, near
     * enough that the offer is recognisably about what was typed. Past this the
     * suggestion stops feeling like a completion and starts feeling like a
     * guess.
     */
    const val SEARCH_DEPTH = 8

    /**
     * The first of [names] that continues [query], or null if none does.
     *
     * Order matters: [names] arrives ranked, so the first qualifying name is the
     * best one that also happens to be typeable. Everything past [depth] is
     * ignored.
     */
    fun firstIn(query: String, names: List<String>, depth: Int = SEARCH_DEPTH): NameCompletion? {
        if (query.isBlank()) return null
        for (i in 0 until minOf(depth, names.size)) {
            val suffix = suffixFor(query, names[i]) ?: continue
            return NameCompletion(typed = query, suffix = suffix, name = names[i])
        }
        return null
    }

    /**
     * What would have to be appended to [query] to have typed [name].
     *
     * Null when that is not possible, and — importantly — also null when the
     * name is already fully typed. Offering an empty completion would light up
     * the "press Tab" hint for a keystroke that does nothing.
     *
     * Leading whitespace is skipped, because a pasted query often carries it and
     * nobody means it. Trailing whitespace is not: "dark magician " is a
     * different position in the name than "dark magician", and the name itself
     * decides whether a space comes next.
     */
    fun suffixFor(query: String, name: String): String? {
        val typed = query.trimStart()
        if (typed.isEmpty()) return null
        if (name.length <= typed.length) return null
        if (!name.startsWith(typed, ignoreCase = true)) return null
        return name.substring(typed.length)
    }
}
