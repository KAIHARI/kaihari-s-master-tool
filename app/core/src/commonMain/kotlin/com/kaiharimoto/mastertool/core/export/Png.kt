package com.kaiharimoto.mastertool.core.export

/**
 * A PNG writer.
 *
 * The deck laid out the way its owner arranged it is the one thing this program
 * makes that is worth showing somebody, and every other tool that exports a
 * decklist picture sorts it first. So the picture has to be able to leave, and
 * leaving means a format anything can open.
 *
 * PNG is four chunks and a checksum around a zlib stream — see [Deflate] for why
 * that is written here too. The part that does the real work is the *filter*: a
 * row is stored as its difference from the row above or the pixel to the left,
 * which turns a flat mat into a run of zeroes and gives the compressor something
 * it can actually shorten.
 */
object Png {

    /**
     * Encodes straight ARGB pixels, row-major, [width] per row.
     *
     * ARGB because that is what every graphics layer in this program hands back.
     * PNG stores RGBA, so the shuffle happens here rather than in the caller —
     * getting it wrong swaps red and blue, which is a bug that looks like a
     * theme.
     */
    fun encode(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "a picture with no area cannot be written" }
        require(pixels.size >= width * height) {
            "expected ${width * height} pixels, got ${pixels.size}"
        }

        val out = ByteWriter(pixels.size / 2 + 1024)
        SIGNATURE.forEach { out.byte(it) }

        val header = ByteWriter(13)
        header.int(width)
        header.int(height)
        header.byte(8) // bits per channel
        header.byte(6) // colour type: truecolour with alpha
        header.byte(0) // deflate, the only compression PNG has
        header.byte(0) // adaptive filtering, the only filtering PNG has
        header.byte(0) // not interlaced
        chunk(out, "IHDR", header.toByteArray())

        chunk(out, "IDAT", Deflate.zlib(filtered(pixels, width, height)))
        chunk(out, "IEND", ByteArray(0))

        return out.toByteArray()
    }

    /**
     * The scanlines, each with a filter byte in front of it.
     *
     * Filters are chosen per row by the heuristic the specification suggests:
     * try each and keep the one whose bytes sum smallest treated as signed,
     * because small differences are what the compressor can shorten. Only Up and
     * Sub are offered — Paeth wins a little more on photographs and costs a
     * branch per byte on an image this size, and the rows here are either flat
     * mat or a card sitting directly under the same card.
     */
    internal fun filtered(pixels: IntArray, width: Int, height: Int): ByteArray {
        val stride = width * 4
        val out = ByteArray(height * (stride + 1))
        val raw = ByteArray(stride)
        val previous = ByteArray(stride)

        val sub = ByteArray(stride)
        val up = ByteArray(stride)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val at = x * 4
                raw[at] = ((argb ushr 16) and 0xFF).toByte()
                raw[at + 1] = ((argb ushr 8) and 0xFF).toByte()
                raw[at + 2] = (argb and 0xFF).toByte()
                raw[at + 3] = ((argb ushr 24) and 0xFF).toByte()
            }

            for (i in 0 until stride) {
                val left = if (i >= 4) raw[i - 4].toInt() else 0
                sub[i] = (raw[i] - left).toByte()
                up[i] = (raw[i] - previous[i]).toByte()
            }

            val upCost = cost(up)
            val subCost = cost(sub)
            val noneCost = cost(raw)

            val row = y * (stride + 1)
            // Up first on a tie: a card is drawn directly under the same card in
            // the row above far more often than it repeats along a row.
            val chosen = when {
                upCost <= subCost && upCost <= noneCost -> 2 to up
                subCost <= noneCost -> 1 to sub
                else -> 0 to raw
            }
            out[row] = chosen.first.toByte()
            chosen.second.copyInto(out, row + 1)

            raw.copyInto(previous)
        }

        return out
    }

    /** Sum of absolute signed values, which is the specification's own guess. */
    private fun cost(row: ByteArray): Long {
        var total = 0L
        row.forEach { total += if (it < 0) -it.toLong() else it.toLong() }
        return total
    }

    private fun chunk(out: ByteWriter, type: String, data: ByteArray) {
        out.int(data.size)
        val body = ByteWriter(type.length + data.size)
        type.forEach { body.byte(it.code) }
        body.bytes(data)
        val bytes = body.toByteArray()
        out.bytes(bytes)
        // The length is not covered by the checksum; the type is.
        out.int(crc32(bytes))
    }

    internal fun crc32(data: ByteArray): Int {
        var crc = -1
        data.forEach { byte ->
            crc = CRC_TABLE[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
        }
        return crc.inv()
    }

    private val CRC_TABLE = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) 0xEDB88320.toInt() xor (value ushr 1) else value ushr 1
        }
        value
    }

    private val SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
