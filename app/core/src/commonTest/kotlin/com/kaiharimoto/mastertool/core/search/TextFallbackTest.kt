package com.kaiharimoto.mastertool.core.search

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the cards say, when nothing is called it.
 *
 * A fallback rather than a mode, so it needs no control and costs nothing until
 * a name search has already failed — which is exactly when somebody was looking
 * for something else.
 */
class TextFallbackTest {

    private fun card(id: Int, name: String, description: String, type: String = "Effect Monster") =
        Card(
            id = CardId(id),
            name = name,
            type = type,
            frameType = if (type.contains("Spell")) "spell" else "effect",
            description = description,
        )

    private val banisher = card(1, "Kashtira Fenrir", "banish 1 card from your opponent's field")
    private val negater = card(2, "Ash Blossom", "negate the activation")
    private val spell = card(3, "Pot of Prosperity", "Banish 3, 4 or 6 cards from your Extra Deck", "Spell Card")
    private val plain = card(4, "Banish Everything", "does nothing at all")

    private val index = CardIndex.build(listOf(banisher, negater, spell, plain))

    @Test
    fun aNameStillWinsWhenThereIsOne() {
        // The card actually called "Banish Everything" is what somebody typing
        // that wanted, even though two other cards say the word.
        val outcome = index.search("banish")

        assertEquals(listOf(plain), outcome.cards)
        assertFalse(outcome.matchedText, "this was a name match")
    }

    @Test
    fun whenNothingIsCalledItTheTextIsSearched() {
        val outcome = index.search("negate the activation")

        assertEquals(listOf(negater), outcome.cards)
        assertTrue(outcome.matchedText)
    }

    @Test
    fun theCaseOfTheQuestionDoesNotMatter() {
        assertEquals(1, index.search("NEGATE THE ACTIVATION").matchCount)
    }

    @Test
    fun itSaysWhenItHasAnsweredADifferentQuestion() {
        // Blending these in with name results would be a screen quietly
        // substituting one question for another.
        assertTrue(index.search("from your extra deck").matchedText)
        assertFalse(index.search("ash").matchedText)
        assertFalse(index.search("").matchedText)
    }

    @Test
    fun aFilterStillApplies() {
        val spellsOnly = CardFilter(categories = setOf(CardCategory.SPELL))
        val outcome = index.search("cards from your", spellsOnly)

        assertEquals(listOf(spell), outcome.cards)
        assertTrue(outcome.matchedText)
    }

    @Test
    fun resultsComeBackInNameOrder() {
        // Asking the same question twice has to give the same answer, and there
        // is no ranking to speak of once the match is literal.
        val outcome = index.search("from your")

        assertEquals(listOf("Kashtira Fenrir", "Pot of Prosperity"), outcome.cards.map { it.name })
    }

    @Test
    fun aQueryTooShortToMeanAnythingIsNotAnswered() {
        // "at" is in "does nothing at all" and in no card's name here. Every
        // card says something like it, so a two-letter fallback would fire
        // constantly on the way to typing a name.
        val outcome = index.search("at")

        assertTrue(outcome.cards.isEmpty())
        assertFalse(outcome.matchedText)
    }

    @Test
    fun aShortQueryThatIsPartOfANameStillFindsIt() {
        // The guard is on the fallback, not on searching. "of" is two letters
        // and "Pot of Prosperity" is still called that.
        val outcome = index.search("of")

        assertEquals(listOf(spell), outcome.cards)
        assertFalse(outcome.matchedText)
    }

    @Test
    fun nothingSayingItIsStillNothing() {
        val outcome = index.search("summon a dragon from the moon")

        assertTrue(outcome.cards.isEmpty())
        assertFalse(outcome.matchedText, "no results is not a text match")
    }

    @Test
    fun theCountIsTheWholeMatchNotThePage() {
        val many = (1..300).map { card(100 + it, "Card $it", "banish something") }
        val big = CardIndex.build(many)

        val outcome = big.search("banish something", limit = 20)

        assertEquals(20, outcome.cards.size)
        assertEquals(300, outcome.matchCount)
        assertTrue(outcome.truncated)
    }

    @Test
    fun anEmptyPoolAnswersNothing() {
        assertEquals(SearchOutcome.EMPTY, CardIndex.EMPTY.search("banish"))
    }
}
