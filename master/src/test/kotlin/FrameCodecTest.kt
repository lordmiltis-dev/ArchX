package archx.master.dmxlink

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class FrameCodecTest {

    @Test
    fun encodePrependsLengthLittleEndian() {
        assertContentEquals(
            byteArrayOf(3, 0, 0, 0, 1, 2, 3),
            FrameCodec.encode(byteArrayOf(1, 2, 3)),
        )
    }

    @Test
    fun encodeUsesAllFourLengthBytesLowestFirst() {
        val out = FrameCodec.encode(ByteArray(258) { it.toByte() })
        assertEquals(262, out.size)
        assertContentEquals(byteArrayOf(0x02, 0x01, 0x00, 0x00), out.copyOfRange(0, 4))
    }

    @Test
    fun encodeAcceptsExactlyMaxPayload() {
        val out = FrameCodec.encode(ByteArray(FrameCodec.MAX_PAYLOAD_SIZE))
        assertEquals(FrameCodec.HEADER_SIZE + FrameCodec.MAX_PAYLOAD_SIZE, out.size)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00), out.copyOfRange(0, 4)) // 65536
    }

    @Test
    fun encodeRejectsEmptyPayload() {
        assertFailsWith<IllegalArgumentException> { FrameCodec.encode(ByteArray(0)) }
    }

    @Test
    fun encodeRejectsPayloadAboveMax() {
        assertFailsWith<IllegalArgumentException> { FrameCodec.encode(ByteArray(FrameCodec.MAX_PAYLOAD_SIZE + 1)) }
    }

    @Test
    fun encodeReturnsIndependentArray() {
        val payload = byteArrayOf(1, 2, 3)
        val out = FrameCodec.encode(payload)
        assertNotSame(payload, out)
        payload[0] = 99
        assertEquals(1, out[4].toInt())   // spätere Änderung der Eingabe wirkt nicht zurück
    }

    @Test
    fun encodeMatchesGoldenVectors() {
        val vectors = TestVectors.frames
        assertTrue(vectors.isNotEmpty(), "keine Referenzwerte gefunden")
        for (v in vectors) {
            assertContentEquals(v.framed, FrameCodec.encode(v.payload), "Vektor ${v.name}")
        }
    }
}
