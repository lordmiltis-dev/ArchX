package archx.gui

import archx.gui.guilink.GuiCommand
import archx.gui.guilink.GuiProto
import archx.gui.guilink.MasterEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Referenzwerte stammen aus tools/gen_gui_link_vectors.py (von Hand erzeugt, unabhängig von protobuf). */
class GuiProtoTest {
    @Test
    fun serializeMatchesGoldenCommands() {
        assertTrue(TestVectors.commands.isNotEmpty(), "keine Referenzwerte gefunden")
        for (v in TestVectors.commands) {
            val wire = GuiProto.serialize(GuiCommand.SetValue(v.patch, v.attr, v.value))
            assertContentEquals(v.wire, wire, "Vektor ${v.name}")
        }
    }

    @Test
    fun parseMatchesGoldenValueChanges() {
        assertTrue(TestVectors.changes.isNotEmpty())
        for (v in TestVectors.changes) {
            assertEquals(MasterEvent.ValueChanged(v.patch, v.attr, v.value), GuiProto.parse(v.wire), "Vektor ${v.name}")
        }
    }

    @Test
    fun parseMatchesGoldenShowViews() {
        assertTrue(TestVectors.views.isNotEmpty())
        for (v in TestVectors.views) {
            assertEquals(MasterEvent.ShowView(v.fixtures), GuiProto.parse(v.wire), "Vektor ${v.name}")
        }
    }

    @Test
    fun emptyShowViewIsAnEventNotNull() {
        // 0a 00: oneof gesetzt, aber ohne Fixtures. Etwas anderes als "gar nichts gesetzt".
        assertEquals(MasterEvent.ShowView(emptyList()), GuiProto.parse(byteArrayOf(0x0A, 0x00)))
    }

    @Test
    fun emptyPayloadHasNoEvent() {
        assertNull(GuiProto.parse(ByteArray(0)))
    }

    @Test
    fun truncatedPayloadReturnsNull() {
        val wire = TestVectors.views.first { it.name == "view_one_rgb" }.wire
        assertNull(GuiProto.parse(wire.copyOfRange(0, wire.size - 3)))
    }

    @Test
    fun garbageReturnsNullInsteadOfThrowing() {
        assertNull(GuiProto.parse(byteArrayOf(-1, -1, -1)))
        assertNull(GuiProto.parse(byteArrayOf(0x0A)))   // Tag ohne Länge
    }

    @Test
    fun unknownTopLevelFieldsAreIgnored() {
        val base = TestVectors.changes.first { it.name == "changed_basic" }
        val withExtra = base.wire + byteArrayOf(0x78, 0x01)   // Feld 15, Varint 1
        assertNotNull(GuiProto.parse(withExtra))
        assertEquals(MasterEvent.ValueChanged(base.patch, base.attr, base.value), GuiProto.parse(withExtra))
    }
}
