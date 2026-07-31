package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupDraftTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(75500286)
    private val fiend = CardId(60764609)
    private val section = listOf(ash, ash, fiend, maxx)

    private var minted = 0
    private fun newId(): String = "g-new-${++minted}"

    @Test
    fun savingADraftCreatesTheGroupAndClaimsEverythingSelected() {
        val draft = GroupDraft(name = "Handtrap", color = 5, selection = setOf(ash, maxx))
        val groups = GroupDrafts.commit(DeckGroups.EMPTY, draft, ::newId)

        val group = groups.ordered().single()
        assertEquals("Handtrap", group.name)
        assertEquals(5, group.color)
        assertEquals(group.id, groups.assignments[ash])
        assertEquals(group.id, groups.assignments[maxx])
        assertNull(groups.assignments[fiend])
    }

    @Test
    fun aDraftWithNoNameStillMakesAUsableGroup() {
        val groups = GroupDrafts.commit(
            DeckGroups.EMPTY,
            GroupDraft(name = "   ", selection = setOf(ash)),
            ::newId,
        )

        assertTrue(groups.ordered().single().name.isNotBlank())
    }

    @Test
    fun reopeningAGroupSelectsWhatIsAlreadyInIt() {
        val first = GroupDrafts.commit(
            DeckGroups.EMPTY,
            GroupDraft(name = "Engine", selection = setOf(ash, fiend)),
            ::newId,
        )
        val group = first.ordered().single()

        val reopened = GroupDrafts.edit(first, group, section)
        assertEquals(setOf(ash, fiend), reopened.selection)
        assertEquals("Engine", reopened.name)
    }

    @Test
    fun deselectingIsHowACardLeavesAGroup() {
        val first = GroupDrafts.commit(
            DeckGroups.EMPTY,
            GroupDraft(name = "Engine", selection = setOf(ash, fiend)),
            ::newId,
        )
        val group = first.ordered().single()

        val second = GroupDrafts.commit(
            first,
            GroupDrafts.edit(first, group, section).toggle(ash),
            ::newId,
        )

        assertNull(second.assignments[ash])
        assertEquals(group.id, second.assignments[fiend])
        // Editing a group does not mint a second one.
        assertEquals(1, second.groups.size)
    }

    @Test
    fun aCardSelectedIntoASecondGroupMoves() {
        val engine = GroupDrafts.commit(
            DeckGroups.EMPTY,
            GroupDraft(name = "Engine", selection = setOf(ash, fiend)),
            ::newId,
        )
        val both = GroupDrafts.commit(
            engine,
            GroupDraft(name = "Handtraps", selection = setOf(ash)),
            ::newId,
        )

        val handtraps = both.ordered().first { it.name == "Handtraps" }
        assertEquals(handtraps.id, both.assignments[ash])
        assertEquals(2, both.groups.size)
        // And the group it left keeps everything else.
        assertEquals(1, both.countIn(section, both.ordered().first { it.name == "Engine" }.id))
    }

    @Test
    fun renamingThroughADraftKeepsTheGroupAndItsMembers() {
        val first = GroupDrafts.commit(
            DeckGroups.EMPTY,
            GroupDraft(name = "Engine", color = 2, selection = setOf(fiend)),
            ::newId,
        )
        val group = first.ordered().single()

        val renamed = GroupDrafts.commit(
            first,
            GroupDrafts.edit(first, group, section).copy(name = "Core engine", color = 4),
            ::newId,
        )

        assertEquals(group.id, renamed.ordered().single().id)
        assertEquals("Core engine", renamed.ordered().single().name)
        assertEquals(4, renamed.ordered().single().color)
        assertEquals(group.id, renamed.assignments[fiend])
    }

    @Test
    fun presetsCoverTheWordsDecklistsAreTalkedAboutIn() {
        assertEquals(8, GroupPresets.all.size)
        assertEquals(GroupPresets.all.map { it.name }, GroupPresets.all.map { it.name }.distinct())
        assertTrue(GroupPresets.all.all { it.color in 0..5 })
        assertEquals(5, GroupPresets.colorFor("handtrap"))
        assertNull(GroupPresets.colorFor("something of my own"))
    }
}
