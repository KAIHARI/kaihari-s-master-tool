package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull

/**
 * Groups live inside the `#ydkx-extended` payload, under their own key.
 *
 * The payload is otherwise opaque to this app and must stay that way: it
 * carries the legacy tool's siding patterns and whatever future keys either
 * tool invents, and a deck file that loses a key it didn't understand is a
 * deck file nobody can trust. Reading tolerates any malformed shape by
 * returning empty; writing replaces exactly one key and copies every other
 * verbatim. The legacy reserved `notes` slot is deliberately left alone.
 *
 * Shape, kept boring on purpose:
 * ```json
 * "groups": {
 *   "defs": [{ "id": "g1", "name": "Handtraps", "color": 3, "order": 0 }],
 *   "cards": { "14558127": "g1" },
 *   "breakdown": true
 * }
 * ```
 */
object DeckGroupsCodec {

    private const val KEY = "groups"

    fun read(extended: JsonObject?): StoredGroups {
        val node = extended?.get(KEY) as? JsonObject ?: return StoredGroups.EMPTY

        val defs = (node["defs"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            DeckGroup(
                id = id,
                name = (obj["name"] as? JsonPrimitive)?.content ?: "Group",
                color = (obj["color"] as? JsonPrimitive)?.intOrNull ?: 0,
                order = (obj["order"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE,
            )
        }.orEmpty()

        val cards = (node["cards"] as? JsonObject)?.entries?.mapNotNull { (key, value) ->
            val passcode = key.toIntOrNull() ?: return@mapNotNull null
            val group = (value as? JsonPrimitive)?.content ?: return@mapNotNull null
            CardId(passcode) to group
        }?.toMap().orEmpty()

        val breakdown = (node["breakdown"] as? JsonPrimitive)?.content == "true"

        return StoredGroups(DeckGroups(defs, cards), breakdown)
    }

    /**
     * Returns [extended] with the groups key rewritten — or removed when there
     * is nothing to store, so a deck that never used groups round-trips
     * byte-identically. Every other key is carried over untouched.
     */
    fun write(extended: JsonObject?, stored: StoredGroups): JsonObject? {
        val others = extended?.filterKeys { it != KEY } ?: emptyMap()

        if (stored.groups.isEmpty && !stored.breakdown) {
            return if (others.isEmpty()) null else JsonObject(others)
        }

        val node = buildJsonObject {
            put(
                "defs",
                buildJsonArray {
                    stored.groups.ordered().forEach { group ->
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive(group.id))
                                put("name", JsonPrimitive(group.name))
                                put("color", JsonPrimitive(group.color))
                                put("order", JsonPrimitive(group.order))
                            }
                        )
                    }
                },
            )
            put(
                "cards",
                buildJsonObject {
                    stored.groups.assignments.forEach { (card, group) ->
                        put(card.value.toString(), JsonPrimitive(group))
                    }
                },
            )
            put("breakdown", JsonPrimitive(stored.breakdown))
        }

        return JsonObject(others + (KEY to node))
    }
}

/** What the payload stores: the breakdown itself, and whether it is showing. */
data class StoredGroups(
    val groups: DeckGroups,
    /**
     * The view toggle travels with the deck rather than the install: a deck
     * organised into groups is meant to open organised.
     */
    val breakdown: Boolean,
) {
    companion object {
        val EMPTY = StoredGroups(DeckGroups.EMPTY, breakdown = false)
    }
}
