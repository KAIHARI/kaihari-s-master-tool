package com.kaiharimoto.mastertool.core.export

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PngTest {

    private fun ByteArray.at(offset: Int, length: Int) = copyOfRange(offset, offset + length)

    private fun ByteArray.intAt(offset: Int): Int =
        (this[offset].toInt() and 0xFF shl 24) or
            (this[offset + 1].toInt() and 0xFF shl 16) or
            (this[offset + 2].toInt() and 0xFF shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.typeAt(offset: Int) = at(offset, 4).decodeToString()

    @Test
    fun everyPngStartsWithTheSameEightBytes() {
        val png = Png.encode(IntArray(4) { -1 }, 2, 2)

        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            png.at(0, 8),
        )
    }

    @Test
    fun theHeaderSaysWhatWasAskedFor() {
        val png = Png.encode(IntArray(12), 4, 3)

        assertEquals(13, png.intAt(8), "IHDR is always thirteen bytes")
        assertEquals("IHDR", png.typeAt(12))
        assertEquals(4, png.intAt(16))
        assertEquals(3, png.intAt(20))
        assertEquals(8, png[24].toInt(), "eight bits a channel")
        assertEquals(6, png[25].toInt(), "truecolour with alpha")
    }

    @Test
    fun theChunksComeInTheOrderAReaderExpects() {
        val png = Png.encode(IntArray(12), 4, 3)

        val types = buildList {
            var at = 8
            while (at < png.size) {
                val length = png.intAt(at)
                add(png.typeAt(at + 4))
                at += 12 + length
            }
        }

        assertEquals(listOf("IHDR", "IDAT", "IEND"), types)
    }

    @Test
    fun everyChunkCarriesItsOwnChecksum() {
        // The one thing a reader checks before it will draw anything.
        val png = Png.encode(IntArray(64) { it * 7 }, 8, 8)

        var at = 8
        var chunks = 0
        while (at < png.size) {
            val length = png.intAt(at)
            val body = png.at(at + 4, 4 + length)
            assertEquals(
                Png.crc32(body),
                png.intAt(at + 8 + length),
                "${png.typeAt(at + 4)} checksum",
            )
            chunks++
            at += 12 + length
        }
        assertEquals(3, chunks)
    }

    @Test
    fun theFileEndsWhereItSaysItDoes() {
        val png = Png.encode(IntArray(9), 3, 3)

        assertEquals("IEND", png.typeAt(png.size - 8))
        assertEquals(0, png.intAt(png.size - 12), "IEND carries nothing")
    }

    @Test
    fun aPictureWithNoAreaIsRefusedRatherThanWritten() {
        assertFailsWith<IllegalArgumentException> { Png.encode(IntArray(0), 0, 0) }
        assertFailsWith<IllegalArgumentException> { Png.encode(IntArray(4), 2, 0) }
    }

    @Test
    fun tooFewPixelsIsRefusedRatherThanPadded() {
        // The failure this stops is a picture whose last rows are whatever was
        // in memory, which is a bug that only appears on somebody else's screen.
        assertFailsWith<IllegalArgumentException> { Png.encode(IntArray(10), 4, 3) }
    }

    @Test
    fun flatColourCompressesToAlmostNothing() {
        // The mat behind a deck is one colour over most of the picture, and the
        // whole reason for filtering is that this becomes a run of zeroes.
        val png = Png.encode(IntArray(400 * 400) { 0xFF1B1B1F.toInt() }, 400, 400)

        assertTrue(png.size < 6000, "160,000 flat pixels came to ${png.size} bytes")
    }

    @Test
    fun noiseIsNotMadeMuchWorseByBeingCompressed() {
        // The other end of the same question: nothing here may *grow* a picture
        // meaningfully, or a photo-heavy deck exports larger than it is.
        val random = Random(4)
        val pixels = IntArray(200 * 200) { random.nextInt() }
        val png = Png.encode(pixels, 200, 200)

        assertTrue(
            png.size < pixels.size * 4 * 11 / 10,
            "random pixels grew from ${pixels.size * 4} to ${png.size}",
        )
    }

    @Test
    fun everyRowGetsAFilterByteInFrontOfIt() {
        val filtered = Png.filtered(IntArray(6), width = 3, height = 2)

        assertEquals(2 * (3 * 4 + 1), filtered.size)
        assertTrue(filtered[0] in 0..4)
        assertTrue(filtered[13] in 0..4)
    }

    @Test
    fun theFirstRowIsWrittenInRgbaNotArgb() {
        // Swapping these looks like a theme rather than a bug, which is exactly
        // why it is pinned. One opaque pure red pixel, filtered against nothing.
        val filtered = Png.filtered(intArrayOf(0xFFFF0000.toInt()), width = 1, height = 1)

        val body = filtered.copyOfRange(1, filtered.size).map { it.toInt() and 0xFF }
        when (filtered[0].toInt()) {
            0, 2 -> assertEquals(listOf(255, 0, 0, 255), body)
            // Sub subtracts the pixel to the left, and there is none.
            1 -> assertEquals(listOf(255, 0, 0, 255), body)
            else -> assertTrue(false, "unexpected filter ${filtered[0]}")
        }
    }

    @Test
    fun aRepeatedRowFiltersToNothing() {
        val pixels = IntArray(3 * 2) { 0xFF203040.toInt() }
        val filtered = Png.filtered(pixels, width = 3, height = 2)

        val second = filtered.copyOfRange(13, 26)
        assertEquals(2, second[0].toInt(), "the second row should be filtered against the first")
        assertTrue(second.drop(1).all { it.toInt() == 0 }, "identical rows differ by nothing")
    }

    @Test
    fun theChecksumMatchesTheOneEveryOtherImplementationComputes() {
        // Known values, so a mistake in the table shows up here rather than as a
        // file that opens nowhere.
        assertEquals(0, Png.crc32(ByteArray(0)))
        assertEquals(0xCBF43926.toInt(), Png.crc32("123456789".encodeToByteArray()))
        assertEquals(0xAE426082.toInt(), Png.crc32("IEND".encodeToByteArray()))
    }
}

class DeflateTest {

    @Test
    fun theStreamAnnouncesItselfAsDeflateWithATwoByteHeader() {
        val out = Deflate.zlib("hello".encodeToByteArray())

        assertEquals(0x78, out[0].toInt() and 0xFF)
        val header = ((out[0].toInt() and 0xFF) shl 8) or (out[1].toInt() and 0xFF)
        assertEquals(0, header % 31, "the two header bytes must divide by 31")
    }

    @Test
    fun theStreamEndsWithItsChecksum() {
        val data = "the arrangement is the deck".encodeToByteArray()
        val out = Deflate.zlib(data)

        val trailer = out.copyOfRange(out.size - 4, out.size)
        val sum = Deflate.adler32(data)
        assertContentEquals(
            byteArrayOf(
                (sum ushr 24).toByte(),
                (sum ushr 16).toByte(),
                (sum ushr 8).toByte(),
                sum.toByte(),
            ),
            trailer,
        )
    }

    @Test
    fun theChecksumMatchesTheOneEveryOtherImplementationComputes() {
        assertEquals(1, Deflate.adler32(ByteArray(0)))
        assertEquals(0x11E60398, Deflate.adler32("Wikipedia".encodeToByteArray()))
    }

    @Test
    fun anEmptyInputStillProducesAValidStream() {
        // Reachable: a zero-height picture is refused, but a caller compressing
        // nothing should get a stream a reader can open rather than a crash.
        val out = Deflate.zlib(ByteArray(0))

        assertTrue(out.size >= 6, "header, one empty block and a checksum")
        assertEquals(1, Deflate.adler32(ByteArray(0)))
    }

    @Test
    fun repetitionIsWhatItIsForAndItTakesIt() {
        val repeated = "arrange".repeat(2000).encodeToByteArray()

        assertTrue(
            Deflate.zlib(repeated).size < repeated.size / 20,
            "14,000 repeating bytes came to ${Deflate.zlib(repeated).size}",
        )
    }

    @Test
    fun aRunLongerThanTheLongestReferenceIsStillEncoded() {
        // The largest length a single reference can carry is 258, and the loop
        // that emits several in a row is the part most likely to be off by one.
        val flat = ByteArray(5000)

        val out = Deflate.zlib(flat)
        assertTrue(out.size < 100, "five thousand zeroes came to ${out.size} bytes")
    }

    @Test
    fun somethingShorterThanAReferenceIsWrittenOutAsItIs() {
        // Below three bytes there is nothing a back-reference could shorten, and
        // the match finder must not read past the end looking for one.
        listOf("", "a", "ab", "abc").forEach { text ->
            val out = Deflate.zlib(text.encodeToByteArray())
            assertTrue(out.size >= 6, "'$text' produced ${out.size} bytes")
        }
    }

    @Test
    fun everyByteValueSurvivesBeingEncoded() {
        // 144 is where the literal table changes from eight bits to nine, and a
        // mistake there corrupts exactly the high half of the alphabet.
        val all = ByteArray(256) { it.toByte() }

        val out = Deflate.zlib(all)
        assertEquals(Deflate.adler32(all), out.intAt(out.size - 4))
    }

    private fun ByteArray.intAt(offset: Int): Int =
        (this[offset].toInt() and 0xFF shl 24) or
            (this[offset + 1].toInt() and 0xFF shl 16) or
            (this[offset + 2].toInt() and 0xFF shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}
