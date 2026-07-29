package com.kaiharimoto.mastertool.core.export

/**
 * A zlib stream, written by hand.
 *
 * Same reason as [Pdf]: nothing that compresses resolves in the environment this
 * is built in, and `java.util.zip` is not on the common source set anyway — the
 * domain layer is deliberately free of platform APIs so it can be tested
 * everywhere. A PNG needs deflate and there is no way around that, so deflate is
 * written here.
 *
 * Deliberately the simple half of the format: fixed Huffman codes and a
 * hash-chain match finder. Dynamic codes would win maybe another fifteen per
 * cent and cost a code-length tree, a second pass and a great deal of arithmetic
 * that could be subtly wrong in ways no picture would reveal. What is here is
 * decompressed by every reader in existence, because it is the same fixed table
 * every one of them already has.
 */
internal object Deflate {

    /** A zlib wrapper (RFC 1950) around a deflate stream (RFC 1951). */
    fun zlib(data: ByteArray): ByteArray {
        val out = ByteWriter()
        // 0x78: deflate, 32K window. 0x01: fastest, no preset dictionary — the
        // two bytes together must be divisible by 31, which is what the header
        // check is.
        out.byte(0x78)
        out.byte(0x01)
        out.bytes(fixedHuffman(data))
        // Adler-32 goes on big-endian, unlike the CRC in a PNG chunk, which is
        // also big-endian; the one that is little-endian is the gzip trailer,
        // which this is not.
        val sum = adler32(data)
        out.byte((sum ushr 24) and 0xFF)
        out.byte((sum ushr 16) and 0xFF)
        out.byte((sum ushr 8) and 0xFF)
        out.byte(sum and 0xFF)
        return out.toByteArray()
    }

    fun adler32(data: ByteArray): Int {
        var a = 1
        var b = 0
        data.forEach { byte ->
            a = (a + (byte.toInt() and 0xFF)) % ADLER_MOD
            b = (b + a) % ADLER_MOD
        }
        return (b shl 16) or a
    }

    private const val ADLER_MOD = 65521

    /**
     * The compressed body: one block, fixed codes, LZ77 back-references.
     *
     * Bits go out least-significant first, but Huffman codes go out
     * most-significant first. That is not a mistake in the specification and it
     * is the single easiest thing to get wrong here, which is why the two live
     * in separate methods on the writer rather than in one clever one.
     */
    private fun fixedHuffman(data: ByteArray): ByteArray {
        val bits = BitWriter()
        bits.bit(1) // final block
        bits.bits(1, 2) // fixed Huffman

        val heads = IntArray(HASH_SIZE) { -1 }
        // One slot per position *within the window*, not per byte of input. A
        // picture of a deck is twenty megabytes of scanlines, and a chain the
        // length of the input would be another eighty-seven -- allocated on a
        // tablet, to export a file. Nothing further back than the window is ever
        // followed, so positions older than that may safely alias.
        val chain = IntArray(WINDOW) { -1 }

        var at = 0
        while (at < data.size) {
            val match = if (at + MIN_MATCH <= data.size) longestMatch(data, at, heads, chain) else null

            if (match == null) {
                bits.literal(data[at].toInt() and 0xFF)
                // The last two bytes of the input have nothing to hash, and
                // nothing after them will ever want to match them either.
                if (at + MIN_MATCH <= data.size) insert(data, at, heads, chain)
                at++
            } else {
                bits.length(match.length)
                bits.distance(match.distance)
                // Every position inside a match still has to enter the table, or
                // the window develops holes and later matches get shorter the
                // further into the file they are.
                repeat(match.length) { step ->
                    val index = at + step
                    if (index + MIN_MATCH <= data.size) insert(data, index, heads, chain)
                }
                at += match.length
            }
        }

        bits.code(256, 7) // end of block
        return bits.toByteArray()
    }

    private class Match(val length: Int, val distance: Int)

    private fun hash(data: ByteArray, at: Int): Int {
        val a = data[at].toInt() and 0xFF
        val b = data[at + 1].toInt() and 0xFF
        val c = data[at + 2].toInt() and 0xFF
        return ((a shl 10) xor (b shl 5) xor c) and (HASH_SIZE - 1)
    }

    private fun insert(data: ByteArray, at: Int, heads: IntArray, chain: IntArray) {
        val slot = hash(data, at)
        chain[at and WINDOW_MASK] = heads[slot]
        heads[slot] = at
    }

    private fun longestMatch(data: ByteArray, at: Int, heads: IntArray, chain: IntArray): Match? {
        var candidate = heads[hash(data, at)]
        var bestLength = 0
        var bestDistance = 0
        var tries = 0

        val limit = minOf(data.size - at, MAX_MATCH)

        while (candidate >= 0 && tries < MAX_CHAIN) {
            tries++
            val distance = at - candidate
            if (distance > MAX_DISTANCE) break

            var length = 0
            while (length < limit && data[candidate + length] == data[at + length]) length++

            if (length > bestLength) {
                bestLength = length
                bestDistance = distance
                // Nothing can beat the longest a reference is allowed to be, and
                // the chain on flat colour is very long.
                if (length >= limit) break
            }
            // Safe to alias here: the loop above has already stopped following
            // anything further back than the window.
            candidate = chain[candidate and WINDOW_MASK]
        }

        return if (bestLength >= MIN_MATCH) Match(bestLength, bestDistance) else null
    }

    /** Below three bytes a reference costs more than the literals it replaces. */
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 258
    private const val MAX_DISTANCE = 32768

    /** The window, as a power of two so the wrap is a mask rather than a modulo. */
    private const val WINDOW = 32768
    private const val WINDOW_MASK = WINDOW - 1

    /**
     * How far back to look before settling for what has been found.
     *
     * A deck image is mostly flat mat with card art on it, so the chains under
     * the background colour are enormous and walking all of them would make the
     * export take longer than the deck took to build. Thirty-two is the knee:
     * past it the file barely shrinks.
     */
    private const val MAX_CHAIN = 32
    private const val HASH_SIZE = 1 shl 15

    /** Bits out least-significant-first, Huffman codes most-significant-first. */
    private class BitWriter {
        private val out = ByteWriter()
        private var bitBuffer = 0
        private var bitCount = 0

        fun bit(value: Int) = bits(value, 1)

        /** [count] bits of [value], least significant first. */
        fun bits(value: Int, count: Int) {
            repeat(count) { index ->
                bitBuffer = bitBuffer or (((value ushr index) and 1) shl bitCount)
                bitCount++
                if (bitCount == 8) {
                    out.byte(bitBuffer)
                    bitBuffer = 0
                    bitCount = 0
                }
            }
        }

        /** A Huffman code: [count] bits of [value], most significant first. */
        fun code(value: Int, count: Int) {
            for (index in count - 1 downTo 0) bit((value ushr index) and 1)
        }

        fun literal(byte: Int) {
            when {
                byte <= 143 -> code(0b00110000 + byte, 8)
                else -> code(0b110010000 + (byte - 144), 9)
            }
        }

        fun length(length: Int) {
            val slot = LENGTH_SLOTS.indexOfLast { it <= length }
            val symbol = 257 + slot
            when {
                symbol <= 279 -> code(symbol - 256, 7)
                else -> code(0b11000000 + (symbol - 280), 8)
            }
            val extra = LENGTH_EXTRA[slot]
            if (extra > 0) bits(length - LENGTH_SLOTS[slot], extra)
        }

        fun distance(distance: Int) {
            val slot = DISTANCE_SLOTS.indexOfLast { it <= distance }
            code(slot, 5)
            val extra = DISTANCE_EXTRA[slot]
            if (extra > 0) bits(distance - DISTANCE_SLOTS[slot], extra)
        }

        fun toByteArray(): ByteArray {
            if (bitCount > 0) out.byte(bitBuffer)
            return out.toByteArray()
        }
    }

    // Tables 1 and 2 of RFC 1951, as the smallest length or distance each symbol
    // covers. The extra bits say how far into its range a value sits.
    private val LENGTH_SLOTS = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DISTANCE_SLOTS = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
        8193, 12289, 16385, 24577,
    )
    private val DISTANCE_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
}

/** A growing byte buffer, because `ByteArray + Byte` copies the whole thing. */
internal class ByteWriter(capacity: Int = 1024) {
    private var buffer = ByteArray(capacity.coerceAtLeast(16))
    var size: Int = 0
        private set

    fun byte(value: Int) {
        if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[size++] = value.toByte()
    }

    fun bytes(values: ByteArray) {
        var needed = buffer.size
        while (needed < size + values.size) needed *= 2
        if (needed != buffer.size) buffer = buffer.copyOf(needed)
        values.copyInto(buffer, size)
        size += values.size
    }

    /** Big-endian, which is the byte order every field in a PNG uses. */
    fun int(value: Int) {
        byte((value ushr 24) and 0xFF)
        byte((value ushr 16) and 0xFF)
        byte((value ushr 8) and 0xFF)
        byte(value and 0xFF)
    }

    /** The byte already written at [index], which a back-reference reads. */
    fun at(index: Int): Int = buffer[index].toInt() and 0xFF

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
