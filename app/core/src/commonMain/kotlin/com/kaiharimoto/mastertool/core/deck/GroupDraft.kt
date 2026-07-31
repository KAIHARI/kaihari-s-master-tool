package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * The roles a decklist is actually talked about in.
 *
 * Offered as a starting point, never imposed: naming what a card is for *is*
 * deckbuilding, and the eight here are the words that come up often enough that
 * typing them out every time is friction. A group made from a preset is an
 * ordinary group the moment it exists — renameable, recolourable, deletable.
 *
 * The ramp has six hues and this list has eight, so two pairs share one:
 * Extender with Breaker and Non-engine with Outs, in both cases the closer pair.
 */
object GroupPresets {

    data class Preset(val name: String, val color: Int)

    val all: List<Preset> = listOf(
        Preset("Engine", 2),
        Preset("Starter", 3),
        Preset("Extender", 4),
        Preset("Non-engine", 1),
        Preset("Brick", 0),
        Preset("Handtrap", 5),
        Preset("Breaker", 4),
        Preset("Outs", 1),
    )

    fun colorFor(name: String): Int? =
        all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.color
}

/**
 * A group being drawn up, before it is committed.
 *
 * Selection is by passcode rather than by copy because that is how a group is
 * meant: all three copies of a card share one role, which is how ratios get
 * reasoned about ("three starters", never "two of them as starters").
 *
 * [editingId] set means the draft is re-opening a group that already exists, so
 * saving replaces its membership wholesale — deselecting a card is how a card
 * leaves a group, which is the same gesture that put it in.
 */
data class GroupDraft(
    val editingId: String? = null,
    val name: String = "",
    val color: Int = 0,
    val selection: Set<CardId> = emptySet(),
) {
    val isNew: Boolean get() = editingId == null

    fun toggle(id: CardId): GroupDraft =
        copy(selection = if (id in selection) selection - id else selection + id)

    fun isSelected(id: CardId): Boolean = id in selection
}

object GroupDrafts {

    /**
     * Opens [group] for editing with everything currently in it selected.
     *
     * Reading membership off the deck rather than off the assignment map: a
     * group can hold assignments for cards that have since left the deck, and
     * saving a draft that silently dropped them would be an edit the user did
     * not make.
     */
    fun edit(groups: DeckGroups, group: DeckGroup, section: List<CardId>): GroupDraft = GroupDraft(
        editingId = group.id,
        name = group.name,
        color = group.color,
        selection = section.filter { groups.assignments[it] == group.id }.toSet(),
    )

    /**
     * Writes the draft into the breakdown.
     *
     * A card can only be in one group, so selecting one already spoken for
     * moves it — the last decision wins, which is what tapping a card twice in
     * two different drafts obviously means.
     */
    fun commit(groups: DeckGroups, draft: GroupDraft, newId: () -> String): DeckGroups {
        val existing = draft.editingId?.let(groups::byId)
        val name = draft.name.trim().ifBlank {
            existing?.name ?: "Group ${groups.groups.size + 1}"
        }

        val target = existing?.copy(name = name, color = draft.color)
            ?: DeckGroup(
                id = newId(),
                name = name,
                color = draft.color,
                order = groups.groups.size,
            )

        val withGroup = groups.upsert(target)
        val kept = withGroup.assignments.filterNot { (card, group) ->
            // Everything this group used to hold and no longer does is freed;
            // everything the draft selected is claimed, from wherever it was.
            group == target.id && card !in draft.selection
        }

        return withGroup.copy(
            assignments = kept + draft.selection.associateWith { target.id },
        )
    }
}
