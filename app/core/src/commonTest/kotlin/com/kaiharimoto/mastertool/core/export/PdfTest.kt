package com.kaiharimoto.mastertool.core.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfTest {

    private fun render(build: Pdf.Page.() -> Unit = {}): String {
        val page = Pdf.Page().apply(build)
        return Pdf.document(listOf(page)).decodeToString()
    }

    @Test
    fun aDocumentIsAPdfFromEndToEnd() {
        val out = render { text(40f, 40f, "Snake-Eye") }

        assertTrue(out.startsWith("%PDF-1.4\n"), "readers look at the first line")
        assertTrue(out.trimEnd().endsWith("%%EOF"), "and at the last")
        assertTrue("/Type /Catalog" in out)
        assertTrue("/Type /Pages" in out)
        assertTrue("(Snake-Eye) Tj" in out)
    }

    @Test
    fun aDocumentWithNoPagesIsRefused() {
        // A reader meeting one shows an error rather than an empty document, so
        // producing one would only ever look like a bug in this program.
        assertFailsWith<IllegalArgumentException> { Pdf.document(emptyList()) }
    }

    @Test
    fun theCrossReferenceTablePointsAtTheObjects() {
        // The whole format hangs off this: every offset in the table has to be
        // the byte position of the object it names, or nothing opens.
        val out = render { text(10f, 10f, "x") }

        val startxref = out.substringAfterLast("startxref\n").substringBefore("\n").trim().toInt()
        assertEquals("xref", out.substring(startxref, startxref + 4))

        val table = out.substring(startxref).lines()
        val count = table[1].split(" ")[1].toInt()
        // table[0] is "xref", table[1] is the range, table[2] is the free head,
        // and objects 1..n follow it.
        for (number in 1 until count) {
            val offset = table[number + 2].take(10).toInt()
            assertEquals(
                "$number 0 obj",
                out.substring(offset, offset + "$number 0 obj".length),
                "object $number is not where the table says",
            )
        }
    }

    @Test
    fun theStreamLengthIsTheLengthOfTheStream() {
        val out = render { text(10f, 10f, "Ash Blossom") }

        val declared = out.substringAfter("<< /Length ").substringBefore(" >>").toInt()
        val stream = out.substringAfter("stream\n").substringBefore("\nendstream")

        assertEquals(stream.length, declared)
    }

    @Test
    fun everyPageIsInTheTree() {
        val out = Pdf.document(List(3) { Pdf.Page() }).decodeToString()

        assertTrue("/Count 3" in out)
        assertEquals(3, Regex("/Type /Page[ /]").findAll(out).count())
    }

    @Test
    fun bracketsInACardNameDoNotEndTheString() {
        // "Number 39: Utopia (Assault Mode)" would otherwise produce a file no
        // reader will open.
        assertEquals("Utopia \\(Assault Mode\\)", Pdf.escape("Utopia (Assault Mode)"))
        assertEquals("back\\\\slash", Pdf.escape("back\\slash"))
    }

    @Test
    fun accentsSurviveAsLatinOneAndTheRestBecomeAQuestionMark() {
        assertEquals("caf\\351", Pdf.escape("café"))
        assertEquals("?", Pdf.escape("一"))
    }

    @Test
    fun newlinesInsideAStringBecomeSpaces() {
        assertEquals("one two", Pdf.escape("one\ntwo"))
    }

    @Test
    fun everythingEmittedIsAscii() {
        // Byte offsets and string lengths are the same number only while this
        // holds, and the cross-reference table is built on that.
        val bytes = Pdf.document(listOf(Pdf.Page().apply { text(10f, 10f, "café 一") }))

        assertTrue(bytes.all { it in 0..127 }, "something non-ASCII reached the file")
    }

    @Test
    fun numbersAreWrittenInAFormAReaderAccepts() {
        // A content stream has no exponents in it, and a reader that meets one
        // stops drawing the page.
        assertEquals("0", Pdf.number(0f))
        assertEquals("12", Pdf.number(12f))
        assertEquals("12.5", Pdf.number(12.5f))
        assertEquals("12.34", Pdf.number(12.339f))
        assertEquals("-4.5", Pdf.number(-4.5f))
        assertTrue("E" !in Pdf.number(0.00001f), Pdf.number(0.00001f))
        assertTrue("E" !in Pdf.number(1e7f), Pdf.number(1e7f))
    }

    @Test
    fun noNumberEverEndsInADecimalPoint() {
        // "12." is not a number and a reader that meets one stops drawing the
        // page, so it is worth being sure across the range that can occur.
        var value = -900f
        while (value <= 900f) {
            val written = Pdf.number(value)
            assertTrue(!written.endsWith("."), "$value became $written")
            assertTrue("E" !in written && "e" !in written, "$value became $written")
            value += 0.01f
        }
    }

    @Test
    fun drawingFromTheTopPutsTheFirstRowNearTheTopOfThePage() {
        // The one thing a caller has to be able to trust: y grows downwards,
        // and PDF's own axis does not.
        val page = Pdf.Page()
        page.line(0f, 10f, 100f, 10f)
        page.line(0f, 800f, 100f, 800f)
        val out = Pdf.document(listOf(page)).decodeToString()

        val stream = out.substringAfter("stream\n").substringBefore("\nendstream")
        val heights = Regex("""0 (\d+) m""").findAll(stream).map { it.groupValues[1].toInt() }.toList()

        assertEquals(listOf(832, 42), heights, stream)
    }

    @Test
    fun anEmptyStringDrawsNothingAtAll() {
        val out = render { text(10f, 10f, "") }

        assertTrue("Tj" !in out)
    }
}

class PdfFitTest {

    private val room = 363f

    @Test
    fun aShortNameIsWrittenFullSize() {
        val fitted = Pdf.fit("Snake-Eye", room)

        assertEquals("Snake-Eye", fitted.text)
        assertEquals(14f, fitted.size)
    }

    @Test
    fun aLongNameShrinksBeforeItIsCut() {
        // A judge would rather have the whole name small than most of it large.
        val name = "Snake-Eye Fiendsmith Unchained, Regionals build"
        val fitted = Pdf.fit(name, room)

        assertEquals(name, fitted.text, "nothing was cut")
        assertTrue(fitted.size < 14f, "it got there by shrinking")
    }

    @Test
    fun nothingEverOverrunsTheRoomItWasGiven() {
        val names = listOf(
            "S",
            "Snake-Eye",
            "Snake-Eye Fiendsmith Unchained, Regionals build",
            "a".repeat(200),
            "Branded Despia Albaz Lubellion Fallen of Albaz Mirrorjade the Iceblade Dragon",
        )

        names.forEach { name ->
            val fitted = Pdf.fit(name, room)
            assertTrue(
                fitted.text.length * fitted.size * 0.58f <= room + 0.01f,
                "\"$name\" came out ${fitted.text.length} chars at ${fitted.size}pt",
            )
        }
    }

    @Test
    fun aNameTooLongToShrinkIntoIsCutAndSaysSo() {
        val fitted = Pdf.fit("a".repeat(200), room)

        assertTrue(fitted.text.endsWith("..."))
        assertEquals(9f, fitted.size, "cut only once shrinking has reached the floor")
    }

    @Test
    fun theCutIsAsciiLikeEverythingElseThisWriterEmits() {
        // A real ellipsis is not in the font this writer declares, and would come
        // out as the substitute glyph in the middle of a title.
        val fitted = Pdf.fit("a".repeat(200), room)

        assertTrue(fitted.text.all { it.code in 32..126 })
    }

    @Test
    fun noRoomAtAllIsNotACrash() {
        assertEquals("", Pdf.fit("", room).text)
        assertEquals("Snake-Eye", Pdf.fit("Snake-Eye", 0f).text)
        assertEquals("Snake-Eye", Pdf.fit("Snake-Eye", -5f).text)
    }

    @Test
    fun aCutNameDoesNotEndInASpaceBeforeItsDots() {
        val fitted = Pdf.fit("Snake  " + "b".repeat(200), room)

        assertFalse(fitted.text.contains(" ..."))
    }
}
