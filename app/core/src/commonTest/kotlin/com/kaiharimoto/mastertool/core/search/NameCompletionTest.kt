package com.kaiharimoto.mastertool.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NameCompletionTest {

    private val ranked = listOf(
        "Ash Blossom & Joyous Spring",
        "Ashened Kingdom",
        "Ghost Ogre & Snow Rabbit",
    )

    @Test
    fun theSuffixIsTheRestOfTheName() {
        assertEquals(
            " Blossom & Joyous Spring",
            Completions.suffixFor("Ash", "Ash Blossom & Joyous Spring"),
        )
    }

    @Test
    fun typingAndTheSuffixAlwaysRebuildTheName() {
        // The field draws the typed text invisibly and the suffix over the top of
        // it, so anything but exact concatenation shows up as smeared letters.
        val name = "Nibiru, the Primal Being"

        (1..name.length).forEach { cut ->
            val typed = name.take(cut)
            val completion = Completions.firstIn(typed, listOf(name))
            if (completion != null) {
                assertEquals(name, completion.typed + completion.suffix)
            }
        }
    }

    @Test
    fun caseDoesNotMatter() {
        // Nobody capitalises while searching, and the suffix still has to be the
        // name's own casing rather than the query's.
        assertEquals(
            " Blossom & Joyous Spring",
            Completions.suffixFor("ASH", "Ash Blossom & Joyous Spring"),
        )
    }

    @Test
    fun aFullyTypedNameOffersNothing() {
        // Not the empty string: an empty completion would light up the "Tab"
        // hint for a key that does nothing.
        assertNull(Completions.suffixFor("Ash Blossom & Joyous Spring", "Ash Blossom & Joyous Spring"))
        assertNull(Completions.suffixFor("ash blossom & joyous spring", "Ash Blossom & Joyous Spring"))
    }

    @Test
    fun aNameThatDoesNotContinueTheQueryOffersNothing() {
        assertNull(Completions.suffixFor("blossom", "Ash Blossom & Joyous Spring"))
        assertNull(Completions.suffixFor("ash x", "Ash Blossom & Joyous Spring"))
    }

    @Test
    fun aNameShorterThanTheQueryOffersNothing() {
        assertNull(Completions.suffixFor("Ash Blossom & Joyous Spring and more", "Ash Blossom"))
    }

    @Test
    fun punctuationIsNotSkippedOver() {
        // Search normalises punctuation away so "nibiru the" finds the card. A
        // completion cannot: the comma is a character that has to be typed, and
        // claiming otherwise would produce text the field cannot reach.
        assertNull(Completions.suffixFor("Nibiru the", "Nibiru, the Primal Being"))
        assertEquals(" the Primal Being", Completions.suffixFor("Nibiru,", "Nibiru, the Primal Being"))
    }

    @Test
    fun aTrailingSpaceIsPartOfThePosition() {
        assertEquals("Blossom & Joyous Spring", Completions.suffixFor("Ash ", "Ash Blossom & Joyous Spring"))
    }

    @Test
    fun leadingWhitespaceIsIgnored() {
        // Pasted queries carry it and nobody means it.
        val completion = Completions.firstIn("  ash", ranked)

        assertEquals("Ash Blossom & Joyous Spring", completion?.name)
        assertEquals(" Blossom & Joyous Spring", completion?.suffix)
    }

    @Test
    fun leadingWhitespaceStillRebuildsTheName() {
        // `typed` is the raw query including its spaces, because that is what the
        // field is drawing. So the concatenation identity holds only if the
        // trimming is accounted for -- which it is, by not trimming `typed`.
        val completion = Completions.firstIn("  ash", ranked)!!

        assertEquals("  ash", completion.typed)
        assertEquals("  ash Blossom & Joyous Spring", completion.typed + completion.suffix)
    }

    @Test
    fun theFirstNameThatQualifiesWins() {
        // Ranking has already decided what is best; this only decides what is
        // typeable, and takes the best of those.
        assertEquals("Ashened Kingdom", Completions.firstIn("Ashe", ranked)?.name)
    }

    @Test
    fun anEmptyQueryOffersNothing() {
        assertNull(Completions.firstIn("", ranked))
        assertNull(Completions.firstIn("   ", ranked))
    }

    @Test
    fun noNamesOfferNothing() {
        assertNull(Completions.firstIn("ash", emptyList()))
    }

    @Test
    fun nothingBeyondTheSearchDepthIsOffered() {
        val padding = List(20) { "Zzz $it" }

        assertNull(Completions.firstIn("ash", padding + "Ash Blossom & Joyous Spring"))
        assertEquals(
            "Ash Blossom & Joyous Spring",
            Completions.firstIn("ash", padding + "Ash Blossom & Joyous Spring", depth = 30)?.name,
        )
    }

    @Test
    fun staleResultsUnderOfferRatherThanMisOffer() {
        // Results are debounced, so for a moment after a keystroke the names are
        // the previous query's. The rule that a completion must literally
        // continue the query is what makes that safe: the worst it can do is
        // offer nothing.
        assertNull(Completions.firstIn("ghost o", ranked.take(2)))
    }
}
