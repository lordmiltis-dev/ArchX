package archx.gui

import archx.gui.guilink.FrameCodec
import archx.gui.guilink.FrameDecoder
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unabhängiges Orakel: eigener Rahmenbau, nicht FrameCodec. */
private fun framed(payload: ByteArray): ByteArray {
    val n = payload.size
    return byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte()) + payload
}

private fun pattern(n: Int, seed: Int = 0) = ByteArray(n) { (seed + it * 7).toByte() }

class FrameDecoderTest {
    @Test
    fun singleFrameInOneChunk() {
        val d = FrameDecoder()
        val out = d.feed(framed(byteArrayOf(9, 8, 7)))
        assertEquals(1, out.size)
        assertContentEquals(byteArrayOf(9, 8, 7), out[0])
        assertEquals(0, d.bufferedBytes)
        assertFalse(d.failed)
    }

    @Test
    fun twoFramesInOneChunkKeepOrder() {
        val out = FrameDecoder().feed(framed(byteArrayOf(1)) + framed(byteArrayOf(2, 2)))
        assertEquals(2, out.size)
        assertContentEquals(byteArrayOf(1), out[0])
        assertContentEquals(byteArrayOf(2, 2), out[1])
    }

    @Test
    fun headerSplitAcrossCalls() {
        val d = FrameDecoder()
        val frame = framed(byteArrayOf(5, 6, 7, 8))
        assertEquals(0, d.feed(frame, 0, 2).size)
        assertEquals(2, d.bufferedBytes)
        val out = d.feed(frame, 2, frame.size - 2)
        assertEquals(1, out.size)
        assertContentEquals(byteArrayOf(5, 6, 7, 8), out[0])
    }

    @Test
    fun payloadSplitAcrossCalls() {
        val d = FrameDecoder()
        val frame = framed(pattern(10))
        assertEquals(0, d.feed(frame, 0, 6).size)   // Header + 2 Payload-Bytes
        assertEquals(6, d.bufferedBytes)
        val out = d.feed(frame, 6, frame.size - 6)
        assertEquals(1, out.size)
        assertContentEquals(pattern(10), out[0])
        assertEquals(0, d.bufferedBytes)
    }

    @Test
    fun emptyFeedIsANoop() {
        val d = FrameDecoder()
        assertEquals(0, d.feed(ByteArray(0)).size)
        assertEquals(0, d.bufferedBytes)
        assertFalse(d.failed)
    }

    @Test
    fun offsetAndLengthAreRespected() {
        val frame = framed(byteArrayOf(4, 5, 6))
        val buf = byteArrayOf(0x7F, 0x7F, 0x7F) + frame + byteArrayOf(0x7F, 0x7F)   // Müll drumherum
        val out = FrameDecoder().feed(buf, 3, frame.size)
        assertEquals(1, out.size)
        assertContentEquals(byteArrayOf(4, 5, 6), out[0])
    }

    @Test
    fun frameEndAndNextHeaderInSameChunk() {
        val d = FrameDecoder()
        val a = framed(byteArrayOf(1, 2))
        val b = framed(byteArrayOf(3))
        val out1 = d.feed(a + b.copyOfRange(0, 2))
        assertEquals(1, out1.size)
        assertEquals(2, d.bufferedBytes)
        val out2 = d.feed(b, 2, b.size - 2)
        assertEquals(1, out2.size)
        assertContentEquals(byteArrayOf(3), out2[0])
    }

    @Test
    fun returnedPayloadsDoNotAliasTheInputArray() {
        val buf = framed(byteArrayOf(1, 2, 3))
        val out = FrameDecoder().feed(buf)
        buf.fill(0)
        assertContentEquals(byteArrayOf(1, 2, 3), out[0])
    }

    @Test
    fun goldenVectorsForEveryChunkSize() {
        assertTrue(TestVectors.frames.isNotEmpty(), "keine Referenzwerte gefunden")
        for (v in TestVectors.frames) {
            for (chunk in intArrayOf(1, 2, 3, 7, v.framed.size)) {
                val d = FrameDecoder()
                val all = mutableListOf<ByteArray>()
                var off = 0
                while (off < v.framed.size) {
                    val n = minOf(chunk, v.framed.size - off)
                    all += d.feed(v.framed, off, n)
                    off += n
                }
                assertEquals(1, all.size, "Vektor ${v.name}, Stück $chunk")
                assertContentEquals(v.payload, all[0], "Vektor ${v.name}, Stück $chunk")
                assertEquals(0, d.bufferedBytes)
            }
        }
    }

    @Test
    fun zeroLengthIsAProtocolError() {
        val d = FrameDecoder()
        assertEquals(0, d.feed(byteArrayOf(0, 0, 0, 0)).size)
        assertTrue(d.failed)
    }

    @Test
    fun oversizeLengthFailsAsSoonAsTheHeaderIsComplete() {
        val d = FrameDecoder()
        d.feed(byteArrayOf(-1, -1, -1, -1))   // 4 GiB: nie Speicher dafür anlegen, nie darauf warten
        assertTrue(d.failed)
    }

    @Test
    fun lengthMaxPlusOneFailsAndExactMaxIsAccepted() {
        val tooBig = FrameDecoder()
        tooBig.feed(byteArrayOf(0x01, 0x00, 0x01, 0x00))   // 65537
        assertTrue(tooBig.failed)

        val ok = FrameDecoder()
        val frame = framed(ByteArray(FrameCodec.MAX_PAYLOAD_SIZE) { 0x11 })
        val out = ok.feed(frame)
        assertFalse(ok.failed)
        assertEquals(1, out.size)
        assertEquals(FrameCodec.MAX_PAYLOAD_SIZE, out[0].size)
    }

    @Test
    fun errorInSplitHeaderIsDetectedOnLastHeaderByte() {
        val d = FrameDecoder()
        d.feed(byteArrayOf(0, 0, 0), 0, 3)
        assertFalse(d.failed)
        d.feed(byteArrayOf(0), 0, 1)
        assertTrue(d.failed)
    }

    @Test
    fun failedStateIsSticky() {
        val d = FrameDecoder()
        d.feed(byteArrayOf(0, 0, 0, 0))
        assertEquals(0, d.feed(framed(byteArrayOf(1))).size)
        assertTrue(d.failed)
    }

    @Test
    fun deliversCompleteFramesBeforeTheError() {
        val d = FrameDecoder()
        val out = d.feed(framed(byteArrayOf(1)) + framed(byteArrayOf(2)) + byteArrayOf(0, 0, 0, 0) + framed(byteArrayOf(3)))
        assertEquals(2, out.size)               // der Rahmen NACH dem Fehler zählt nicht
        assertContentEquals(byteArrayOf(1), out[0])
        assertContentEquals(byteArrayOf(2), out[1])
        assertTrue(d.failed)
    }

    @Test
    fun resetClearsPartialFrameAndFailure() {
        val d = FrameDecoder()
        d.feed(framed(byteArrayOf(1, 2, 3, 4)), 0, 5)
        assertEquals(5, d.bufferedBytes)
        d.reset()
        assertEquals(0, d.bufferedBytes)
        d.feed(byteArrayOf(0, 0, 0, 0))
        assertTrue(d.failed)
        d.reset()
        assertFalse(d.failed)
        val out = d.feed(framed(byteArrayOf(9)))
        assertEquals(1, out.size)
        assertContentEquals(byteArrayOf(9), out[0])   // kein Rest vom alten halben Rahmen
    }

    @Test
    fun survivesRandomChunkingOfManyFrames() {
        val rng = Random(12345)
        val expected = mutableListOf<ByteArray>()
        var stream = ByteArray(0)
        repeat(500) { i ->
            val p = pattern(1 + rng.nextInt(700), i)
            expected += p
            stream += framed(p)
        }
        val d = FrameDecoder()
        val out = mutableListOf<ByteArray>()
        var off = 0
        while (off < stream.size) {
            val n = minOf(1 + rng.nextInt(40), stream.size - off)
            out += d.feed(stream, off, n)
            off += n
        }
        assertEquals(expected.size, out.size)
        for (i in expected.indices) assertContentEquals(expected[i], out[i], "Rahmen $i")
        assertEquals(0, d.bufferedBytes)
    }
}
