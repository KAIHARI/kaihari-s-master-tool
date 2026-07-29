package com.kaiharimoto.mastertool.core.export

/**
 * A decompressor, written only so the compressor can be proved.
 *
 * [Deflate] was checked against Python's `zlib` by hand — every chunk, every
 * scanline, every pixel. That check is worth exactly as much as somebody
 * remembering to run it again, which is nothing. This is the same check as a
 * test: it reads the format from the specification independently of the writer,
 * so agreement between the two means both of them match RFC 1951 rather than
 * meaning they share a mistake.
 *
 * Deliberately handles only what [Deflate] emits — one fixed-Huffman block. A
 * stored or dynamic block is an error here rather than an unimplemented branch,
 * which is what makes it a test of the writer and not a second library.
 */
internal object InflateForTest {

    class MalformedStream(message: String) : IllegalStateException(message)

    /** Unwraps a zlib stream and returns what went into it. */
    fun zlib(stream: ByteArray): ByteArray {
        if (stream.size < 6) throw MalformedStream("a zlib stream is at least six bytes")

        val header = ((stream[0].toInt() and 0xFF) shl 8) or (stream[1].toInt() and 0xFF)
        if (header % 31 != 0) throw MalformedStream("header check failed")
        if (stream[0].toInt() and 0x0F != 8) throw MalformedStream("not deflate")

        val out = inflate(stream, from = 2, until = stream.size - 4)

        val trailer = stream.copyOfRange(stream.size - 4, stream.size)
        val expected = Deflate.adler32(out)
        val found = (trailer[0].toInt() and 0xFF shl 24) or
            (trailer[1].toInt() and 0xFF shl 16) or
            (trailer[2].toInt() and 0xFF shl 8) or
            (trailer[3].toInt() and 0xFF)
        if (expected != found) throw MalformedStream("adler-32 disagrees with the data")

        return out
    }

    private fun inflate(stream: ByteArray, from: Int, until: Int): ByteArray {
        val bits = BitReader(stream, from, until)
        val out = ByteWriter(1024)

        val final = bits.bits(1)
        val type = bits.bits(2)
        if (type != 1) throw MalformedStream("only fixed Huffman blocks are written here")

        while (true) {
            val symbol = bits.symbol()
            when {
                symbol == END -> break
                symbol < END -> out.byte(symbol)
                else -> {
                    val slot = symbol - 257
                    if (slot > 28) throw MalformedStream("length symbol $symbol")
                    val length = LENGTH_BASE[slot] + bits.bits(LENGTH_EXTRA[slot])

                    val distanceSlot = bits.code(5)
                    if (distanceSlot > 29) throw MalformedStream("distance symbol $distanceSlot")
                    val distance = DISTANCE_BASE[distanceSlot] + bits.bits(DISTANCE_EXTRA[distanceSlot])

                    if (distance > out.size) throw MalformedStream("a reference past the start")
                    // Byte at a time on purpose: a reference is allowed to reach
                    // into what it is currently writing, which is how a run of
                    // one repeated byte is encoded.
                    repeat(length) { out.byte(out.at(out.size - distance)) }
                }
            }
        }

        if (final != 1) throw MalformedStream("the block was not marked final")
        return out.toByteArray()
    }

    private const val END = 256

    /** Bits least-significant first; Huffman codes most-significant first. */
    private class BitReader(
        private val data: ByteArray,
        private var at: Int,
        private val until: Int,
    ) {
        private var bit = 0

        fun bits(count: Int): Int {
            var value = 0
            repeat(count) { index -> value = value or (next() shl index) }
            return value
        }

        /** [count] bits read as a Huffman code, most significant first. */
        fun code(count: Int): Int {
            var value = 0
            repeat(count) { value = (value shl 1) or next() }
            return value
        }

        /**
         * One literal/length symbol, decoded off the fixed table.
         *
         * The lengths are 7, 8 and 9 bits and the ranges do not overlap, so the
         * decoder reads seven and then extends until the value falls in a range
         * that exists.
         */
        fun symbol(): Int {
            var value = code(7)
            if (value <= 0b0010111) return END + value

            value = (value shl 1) or next()
            if (value in 0b00110000..0b10111111) return value - 0b00110000
            if (value in 0b11000000..0b11000111) return 280 + (value - 0b11000000)

            value = (value shl 1) or next()
            if (value in 0b110010000..0b111111111) return 144 + (value - 0b110010000)

            throw MalformedStream("no symbol has the code $value")
        }

        private fun next(): Int {
            if (at >= until) throw MalformedStream("ran off the end of the stream")
            val value = (data[at].toInt() ushr bit) and 1
            bit++
            if (bit == 8) {
                bit = 0
                at++
            }
            return value
        }
    }

    // Tables 1 and 2 of RFC 1951, transcribed here a second time on purpose: a
    // decoder that shared the writer's tables could not catch a mistake in them.
    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DISTANCE_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
        8193, 12289, 16385, 24577,
    )
    private val DISTANCE_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
}
