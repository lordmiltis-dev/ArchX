// Tests protobuf serialization and parsing against the shared golden vectors.

package archx.master.dmxlink

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DmxFrameProtoTest {

    private fun data(fill: Int = 0) = ByteArray(DmxFrameProto.UNIVERSE_SIZE) { fill.toByte() }

    @Test
    fun serializeMatchesGoldenVectors() {
        val vectors = TestVectors.protos
        assertTrue(vectors.isNotEmpty(), "keine Referenzwerte gefunden")
        for (v in vectors) {
            assertContentEquals(v.wire, DmxFrameProto.serialize(v.universe, data(v.fill)), "Vektor ${v.name}")
        }
    }

    @Test
    fun universeZeroIsOmittedFromTheWire() {
        val wire = DmxFrameProto.serialize(0, data(1))
        assertContentEquals(byteArrayOf(0x12, 0x80.toByte(), 0x04), wire.copyOfRange(0, 3)) // field 2 is emitted immediately
    }

    @Test
    fun acceptsUniverseBounds() {
        DmxFrameProto.serialize(0, data())
        DmxFrameProto.serialize(DmxFrameProto.MAX_UNIVERSE, data())
    }

    @Test
    fun rejectsUniverseOutOfRange() {
        assertFailsWith<IllegalArgumentException> { DmxFrameProto.serialize(-1, data()) }
        assertFailsWith<IllegalArgumentException> { DmxFrameProto.serialize(DmxFrameProto.MAX_UNIVERSE + 1, data()) }
        assertFailsWith<IllegalArgumentException> { DmxFrameProto.serialize(Int.MAX_VALUE, data()) }
    }

    @Test
    fun rejectsWrongDataSize() {
        for (n in intArrayOf(0, 1, 511, 513, 1024)) {
            assertFailsWith<IllegalArgumentException>("Größe $n") { DmxFrameProto.serialize(1, ByteArray(n)) }
        }
    }

    @Test
    fun doesNotKeepAReferenceToTheInputArray() {
        val input = data(5)
        val wire = DmxFrameProto.serialize(1, input)
        input.fill(9)
        // The payload follows the 08 01 12 80 04 field prefix.
        assertTrue(wire.copyOfRange(5, wire.size).all { it.toInt() == 5 })
    }
}
