package com.kaiharimoto.mastertool.core.export

/**
 * A very small PDF writer.
 *
 * Written rather than depended on, because no PDF library resolves in the
 * environment this is built in — and because a siding sheet is text in a grid,
 * which is about a tenth of what any library does. PDF is a text format; the
 * hard part is not drawing, it is the cross-reference table, and that is
 * arithmetic that can be tested.
 *
 * Deliberately greyscale and two weights of one font. A siding sheet is a thing
 * you print and put next to your deck box between rounds, and every feature not
 * in that sentence is a feature that can be wrong.
 *
 * Everything it emits is ASCII: characters outside Latin-1 become `?` and the
 * rest are escaped to octal, which is what makes "string length" and "byte
 * offset" the same number. The whole cross-reference table depends on that being
 * true, so it is enforced in one place rather than assumed everywhere.
 */
object Pdf {

    /** A4 in points, which is what a PDF measures in. */
    const val PAGE_WIDTH = 595f
    const val PAGE_HEIGHT = 842f

    fun document(pages: List<Page>): ByteArray {
        require(pages.isNotEmpty()) { "a document with no pages cannot be opened" }

        val body = StringBuilder()
        val offsets = ArrayList<Int>()

        fun obj(number: Int, content: String) {
            offsets.add(HEADER.length + body.length)
            body.append("$number 0 obj\n").append(content).append("\nendobj\n")
        }

        // 1 catalog, 2 page tree, then a page and a content stream for each,
        // then the two fonts. Numbered up front so the references can be
        // written before the objects exist.
        val firstPage = 3
        val fontRegular = firstPage + pages.size * 2
        val fontBold = fontRegular + 1

        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")

        val kids = pages.indices.joinToString(" ") { "${firstPage + it * 2} 0 R" }
        obj(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")

        pages.forEachIndexed { i, page ->
            val pageNumber = firstPage + i * 2
            val contentNumber = pageNumber + 1
            obj(
                pageNumber,
                "<< /Type /Page /Parent 2 0 R " +
                    "/MediaBox [0 0 ${number(page.width)} ${number(page.height)}] " +
                    "/Resources << /Font << /F1 $fontRegular 0 R /F2 $fontBold 0 R >> >> " +
                    "/Contents $contentNumber 0 R >>",
            )
            val stream = page.stream()
            obj(contentNumber, "<< /Length ${stream.length} >>\nstream\n$stream\nendstream")
        }

        obj(fontRegular, font("Helvetica"))
        obj(fontBold, font("Helvetica-Bold"))

        val xrefAt = HEADER.length + body.length
        val total = offsets.size + 1

        val out = StringBuilder(HEADER).append(body)
        out.append("xref\n0 $total\n")
        // The free head of the chain, which every file has and nothing uses.
        out.append("0000000000 65535 f \n")
        offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        out.append("trailer\n<< /Size $total /Root 1 0 R >>\nstartxref\n$xrefAt\n%%EOF\n")

        return out.toString().encodeToByteArray()
    }

    private fun font(name: String) =
        "<< /Type /Font /Subtype /Type1 /BaseFont /$name /Encoding /WinAnsiEncoding >>"

    private const val HEADER = "%PDF-1.4\n"

    /**
     * One page, drawn from the top left.
     *
     * PDF measures from the bottom left, which is correct and useless: every
     * layout in this file is written downwards. The flip happens here, once,
     * rather than in every caller's arithmetic.
     */
    class Page(val width: Float = PAGE_WIDTH, val height: Float = PAGE_HEIGHT) {
        private val ops = StringBuilder()

        fun text(
            x: Float,
            y: Float,
            text: String,
            size: Float = 10f,
            bold: Boolean = false,
            grey: Float = 0f,
        ) {
            if (text.isEmpty()) return
            val font = if (bold) "/F2" else "/F1"
            ops.append("${number(grey)} g\n")
                .append("BT $font ${number(size)} Tf ")
                // The y a caller gives is the top of the text; PDF places the
                // baseline, and a caller thinking in rows should not have to
                // know that.
                .append("1 0 0 1 ${number(x)} ${number(flip(y + size * 0.8f))} Tm ")
                .append("(${escape(text)}) Tj ET\n")
        }

        fun line(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            width: Float = 0.5f,
            grey: Float = 0.7f,
        ) {
            ops.append("${number(grey)} G ${number(width)} w\n")
                .append("${number(x1)} ${number(flip(y1))} m ${number(x2)} ${number(flip(y2))} l S\n")
        }

        /** A filled rectangle, for a header band or a row stripe. */
        fun rect(x: Float, y: Float, w: Float, h: Float, grey: Float = 0.92f) {
            ops.append("${number(grey)} g\n")
                .append("${number(x)} ${number(flip(y + h))} ${number(w)} ${number(h)} re f\n")
        }

        internal fun stream(): String = ops.toString()

        private fun flip(y: Float) = height - y
    }

    /**
     * Text as a PDF string.
     *
     * Parentheses and backslashes end the string early if they are not escaped,
     * which is how a card called "Number 39: Utopia (Assault Mode)" would
     * otherwise produce a file no reader will open.
     */
    internal fun escape(text: String): String = buildString {
        text.forEach { char ->
            when {
                char == '(' || char == ')' || char == '\\' -> append('\\').append(char)
                char == '\n' || char == '\r' || char == '\t' -> append(' ')
                char.code in 32..126 -> append(char)
                // Latin-1 covers the accents in the card pool; everything past
                // it is a character this font has no glyph for anyway, and a
                // visible question mark beats a silently dropped letter.
                char.code in 128..255 -> append('\\').append(char.code.toString(8).padStart(3, '0'))
                else -> append('?')
            }
        }
    }

    /**
     * A number PDF will accept.
     *
     * No exponents, and no trailing noise from float printing: `1.0E-5` is not a
     * number in a content stream, and a reader that meets one stops drawing the
     * page.
     */
    internal fun number(value: Float): String {
        val rounded = kotlin.math.round(value * 100f) / 100f
        if (rounded == rounded.toInt().toFloat()) return rounded.toInt().toString()
        val whole = rounded.toInt()
        val fraction = kotlin.math.abs(kotlin.math.round((rounded - whole) * 100f).toInt())
        val digits = fraction.toString().padStart(2, '0').trimEnd('0')
        val sign = if (rounded < 0 && whole == 0) "-" else ""
        // "12." is not a number, and a reader that meets one stops drawing the
        // page. Reachable only through float error, which is exactly the kind of
        // thing that would happen once, on somebody else's deck.
        if (digits.isEmpty()) return "$sign$whole"
        return "$sign$whole.$digits"
    }
}
