package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What you wrote on a card.
 *
 * The deck already has notes; this is the smaller, more useful thing next to
 * them — a line on one card, the way you would put a sticky note on the pile.
 * *Only starter that plays through Ash.* *Second copy is for the mirror.* It is
 * the sort of thing you know while you are building and have forgotten by the
 * time you are siding, which is exactly when it is worth having.
 *
 * By passcode, so it belongs to the card rather than to a position: all three
 * copies carry the same note, and cutting one does not take it with them. That
 * is the opposite choice from [Breaks], and for the opposite reason — a gap is
 * about the layout, a note is about the card.
 *
 * Nothing ever sweeps them. A note about a card no longer in the deck is a few
 * bytes; a note that did not survive taking the last copy out and putting it back
 * is a note nobody would trust, and that happens constantly while building. There
 * was a `keepingOnly` here to be called when the file was written, and it was
 * removed once it became clear there is no moment at which it is the right thing
 * to do -- autosave *is* writing the file, all the time.
 *
 * The original tool wrote `notes: { cards: {}, pairs: {} }` into every file it
 * ever exported and never put anything in either. This fills in the first half.
 */
data class CardNotes(val byCard: Map<CardId, String> = emptyMap()) {

    val isEmpty: Boolean get() = byCard.isEmpty()

    val size: Int get() = byCard.size

    operator fun get(id: CardId): String? = byCard[id]

    fun has(id: CardId): Boolean = byCard.containsKey(id)

    /**
     * Writes a note, or takes it away when there is nothing left in it.
     *
     * Blank removes rather than stores, so clearing the box is how you delete
     * one — there is no second gesture to find, and a file does not accumulate
     * empty strings against cards nobody has anything to say about.
     */
    fun with(id: CardId, text: String): CardNotes {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) {
            if (!has(id)) this else CardNotes(byCard - id)
        } else {
            CardNotes(byCard + (id to trimmed))
        }
    }

    /**
     * Drops notes about cards the deck no longer holds.
     *
     * Deliberately *not* called when a card is cut. Taking the last copy out and
     * putting it back is a thing that happens constantly while building, and a
     * note that did not survive that would be a note nobody trusts. This exists
     * for the one moment it is right — writing the file — and even then it is the
     * caller's decision.
     */
    fun keepingOnly(ids: Set<CardId>): CardNotes =
        if (byCard.keys.all { it in ids }) this else CardNotes(byCard.filterKeys { it in ids })

    companion object {
        val NONE = CardNotes()
    }
}

/**
 * Reads and writes card notes inside `#ydkx-extended`.
 *
 * Nested merging, which is the payload rule one level down: the object under
 * `notes` also carries `pairs`, and a codec that rebuilt `notes` from what it
 * understood would delete it. The same argument as the top-level merge, and
 * easier to get wrong because the key being preserved is somebody else's idea
 * rather than an unknown.
 */
object CardNotesCodec {

    fun read(extended: JsonObject?): CardNotes {
        val cards = (extended?.get(NOTES) as? JsonObject)?.get(CARDS) as? JsonObject
            ?: return CardNotes.NONE

        val out = HashMap<CardId, String>(cards.size)
        cards.forEach { (key, value) ->
            val id = key.toIntOrNull() ?: return@forEach
            val text = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (text.isNotEmpty()) out[CardId(id)] = text
        }
        return CardNotes(out)
    }

    fun write(extended: JsonObject?, notes: CardNotes): JsonObject {
        val root: Map<String, JsonElement> = extended ?: emptyMap()
        val existing = root[NOTES] as? JsonObject

        // Sorted, because a map has no order and a file that reorders its own
        // keys between saves is a file that looks like it changed.
        val written = notes.byCard.entries
            .sortedBy { it.key.value }
            .associate { (id, text) -> id.value.toString() to JsonPrimitive(text) }

        val rest = existing.orEmpty().filterKeys { it != CARDS }

        // Nothing written and nothing already there: leave the key out entirely
        // rather than adding an empty object to every file this app touches.
        if (written.isEmpty() && rest.isEmpty()) {
            return JsonObject(root.filterKeys { it != NOTES })
        }

        return JsonObject(root + (NOTES to JsonObject(rest + (CARDS to JsonObject(written)))))
    }

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

    private const val NOTES = "notes"
    private const val CARDS = "cards"
}
