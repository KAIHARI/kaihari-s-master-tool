package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Two cards, in no particular order.
 *
 * Normalised on the way in, because *Poplar and Oak* and *Oak and Poplar* are
 * the same thing to say and storing both would mean a note written from one card
 * being invisible from the other.
 */
data class PairKey private constructor(val first: CardId, val second: CardId) {

    /** The key as it goes in the file: two passcodes, smaller one first. */
    val key: String get() = "${first.value}|${second.value}"

    companion object {
        /** Null for a card paired with itself, which is not a pair. */
        fun of(a: CardId, b: CardId): PairKey? = when {
            a.value == b.value -> null
            a.value < b.value -> PairKey(a, b)
            else -> PairKey(b, a)
        }

        fun parse(raw: String): PairKey? {
            val halves = raw.split('|')
            if (halves.size != 2) return null
            val a = halves[0].trim().toIntOrNull() ?: return null
            val b = halves[1].trim().toIntOrNull() ?: return null
            return of(CardId(a), CardId(b))
        }
    }
}

/**
 * What you wrote about two cards together.
 *
 * The half of deck building a decklist cannot hold at all. A deck is not forty
 * cards, it is a handful of two-card openings and thirty-odd cards that make
 * them more likely — and *Poplar plus any Level 1 is Apollousa* is the sort of
 * thing you work out once, rely on for a month, and cannot reconstruct after
 * three weeks away from the deck.
 *
 * The original tool wrote `notes: { cards: {}, pairs: {} }` into every file it
 * exported and filled in neither. [CardNotes] took the first half; this is the
 * second, and with it the placeholder is finally what it always said it was.
 */
data class PairNotes(val byPair: Map<PairKey, String> = emptyMap()) {

    val isEmpty: Boolean get() = byPair.isEmpty()

    val size: Int get() = byPair.size

    operator fun get(pair: PairKey): String? = byPair[pair]

    fun of(a: CardId, b: CardId): String? = PairKey.of(a, b)?.let(byPair::get)

    /** Every pair this card is half of, which is what a card's own sheet lists. */
    fun about(id: CardId): List<Pair<PairKey, String>> =
        byPair.entries
            .filter { it.key.first == id || it.key.second == id }
            .sortedBy { it.key.key }
            .map { it.key to it.value }

    /** The other card in a pair, given one of them. */
    fun other(pair: PairKey, than: CardId): CardId =
        if (pair.first == than) pair.second else pair.first

    /**
     * Writes a note about two cards, or takes it away when the box is emptied.
     *
     * Same rule as a card's own note: blank removes, so clearing the box is how
     * a note is deleted and there is no second gesture to find.
     */
    fun with(a: CardId, b: CardId, text: String): PairNotes {
        val pair = PairKey.of(a, b) ?: return this
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) {
            if (pair !in byPair) this else PairNotes(byPair - pair)
        } else {
            PairNotes(byPair + (pair to trimmed))
        }
    }

    companion object {
        val NONE = PairNotes()
    }
}

/**
 * Reads and writes pair notes inside `#ydkx-extended`.
 *
 * Symmetrical with [CardNotesCodec] and deliberately separate: each owns one key
 * under `notes` and preserves everything else it finds there, so the two compose
 * in either order and neither can destroy the other's half. That is the same
 * merging rule the whole payload follows, applied to two codecs that now share
 * an object.
 */
object PairNotesCodec {

    fun read(extended: JsonObject?): PairNotes {
        val pairs = (extended?.get(NOTES) as? JsonObject)?.get(PAIRS) as? JsonObject
            ?: return PairNotes.NONE

        val out = HashMap<PairKey, String>(pairs.size)
        pairs.forEach { (key, value) ->
            val pair = PairKey.parse(key) ?: return@forEach
            val text = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (text.isNotEmpty()) out[pair] = text
        }
        return PairNotes(out)
    }

    fun write(extended: JsonObject?, notes: PairNotes): JsonObject {
        val root: Map<String, JsonElement> = extended ?: emptyMap()
        val existing = root[NOTES] as? JsonObject

        // Sorted, because a map has no order and a file that reorders its own
        // keys between saves is a file that looks like it changed.
        val written = notes.byPair.entries
            .sortedBy { it.key.key }
            .associate { (pair, text) -> pair.key to JsonPrimitive(text) }

        val rest = (existing ?: emptyMap<String, JsonElement>()).filterKeys { it != PAIRS }

        if (written.isEmpty() && rest.isEmpty()) {
            return JsonObject(root.filterKeys { it != NOTES })
        }

        return JsonObject(root + (NOTES to JsonObject(rest + (PAIRS to JsonObject(written)))))
    }

    private const val NOTES = "notes"
    private const val PAIRS = "pairs"
}
