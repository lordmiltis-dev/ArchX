// Tests DMX frame generation, source precedence, 16-bit values, cells, and virtual dimmers.
package dev.archx.master

import dev.archx.schema.Attribute
import dev.archx.schema.ChannelDef
import dev.archx.schema.FixtureType
import dev.archx.schema.PatchEntry
import dev.archx.schema.Precedence
import dev.archx.schema.Showfile
import dev.archx.schema.SubFixtureDef
import dev.archx.schema.channelDef
import dev.archx.schema.fixtureType
import dev.archx.schema.patchEntry
import dev.archx.schema.showfile
import dev.archx.schema.subFixtureDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueEngineTest {

    // ---------------------------------------------------------------
    // Helpers build test data with the Protobuf DSL, independently of other test helpers.
    // ---------------------------------------------------------------

    private fun channel(
        position: Int,
        attr: Attribute,
        fine: Int = 0,
        defaultVal: Int = 0,
        prec: Precedence = Precedence.PRECEDENCE_UNSPECIFIED
    ): ChannelDef = channelDef {
        offset = position
        attribute = attr
        fineOffset = fine
        defaultValue = defaultVal
        precedence = prec
    }

    private fun cell(idx: Int, vararg cellChannels: ChannelDef): SubFixtureDef = subFixtureDef {
        index = idx
        channels += cellChannels.toList()
    }

    private fun typeOf(
        typeId: Int,
        count: Int,
        shared: List<ChannelDef>,
        cells: List<SubFixtureDef> = emptyList()
    ): FixtureType = fixtureType {
        id = typeId
        name = "Typ $typeId"
        channelCount = count
        channels += shared
        subFixtures += cells
    }

    private fun patchOf(
        patchId: Int,
        typeId: Int,
        universeNumber: Int,
        startAddress: Int,
        virtual: Boolean = false
    ): PatchEntry = patchEntry {
        id = patchId
        fixtureNumber = patchId
        name = "Patch $patchId"
        fixtureTypeId = typeId
        universe = universeNumber
        address = startAddress
        virtualDimmer = virtual
    }

    // Read the DMX value at a one-based address as an unsigned byte.
    private fun ByteArray.at(address: Int): Int = this[address - 1].toInt() and 0xFF

    // ---------------------------------------------------------------
    // The test show:
    //
    // Type 1 rgbPar: 4 channels: intensity, red, green, blue.
    // Type 2 mover: 6 channels: 16-bit pan and tilt, intensity, and other.
    // The pan and tilt defaults are centered at 32768; the other channel defaults to 7.
    // Type 3 bar: shared intensity plus two independently controlled RGB cells.
    // Type 4 rgbOnly: 3 RGB channels without a dimmer.
    // Type 5 htpRed: one red channel explicitly using HTP.
    //
    // Patch 1: type 1, universe 1, address 1 (uses 1..4).
    // Patch 2: type 1, universe 1, address 10 (uses 10..13).
    // Patch 3: type 1, universe 2, address 1.
    // Patch 4: type 2, universe 1, address 20 (uses 20..25).
    // Patch 5: type 3, universe 1, address 40 (uses 40..46).
    // Patch 6: type 4, universe 1, address 60 (uses 60..62), with virtual dimmer.
    // Patch 7: type 4, universe 1, address 70 (uses 70..72), without virtual dimmer.
    // Patch 8: type 5, universe 1, address 80.
    // ---------------------------------------------------------------

    private val rgbPar = typeOf(
        1, 4, listOf(
            channel(1, Attribute.ATTRIBUTE_INTENSITY),
            channel(2, Attribute.ATTRIBUTE_RED),
            channel(3, Attribute.ATTRIBUTE_GREEN),
            channel(4, Attribute.ATTRIBUTE_BLUE)
        )
    )

    private val mover = typeOf(
        2, 6, listOf(
            channel(1, Attribute.ATTRIBUTE_PAN, fine = 2, defaultVal = 32768),
            channel(3, Attribute.ATTRIBUTE_TILT, fine = 4, defaultVal = 32768),
            channel(5, Attribute.ATTRIBUTE_INTENSITY),
            channel(6, Attribute.ATTRIBUTE_OTHER, defaultVal = 7)
        )
    )

    private val bar = typeOf(
        3, 7,
        listOf(channel(1, Attribute.ATTRIBUTE_INTENSITY)),
        listOf(
            cell(
                1,
                channel(2, Attribute.ATTRIBUTE_RED),
                channel(3, Attribute.ATTRIBUTE_GREEN),
                channel(4, Attribute.ATTRIBUTE_BLUE)
            ),
            cell(
                2,
                channel(5, Attribute.ATTRIBUTE_RED),
                channel(6, Attribute.ATTRIBUTE_GREEN),
                channel(7, Attribute.ATTRIBUTE_BLUE)
            )
        )
    )

    private val rgbOnly = typeOf(
        4, 3, listOf(
            channel(1, Attribute.ATTRIBUTE_RED),
            channel(2, Attribute.ATTRIBUTE_GREEN),
            channel(3, Attribute.ATTRIBUTE_BLUE)
        )
    )

    private val htpRed = typeOf(5, 1, listOf(channel(1, Attribute.ATTRIBUTE_RED, prec = Precedence.PRECEDENCE_HTP)))

    private fun testShow(): Showfile = showfile {
        name = "Engine-Test"
        fixtureTypes += listOf(rgbPar, mover, bar, rgbOnly, htpRed)
        patch += listOf(
            patchOf(1, 1, 1, 1),
            patchOf(2, 1, 1, 10),
            patchOf(3, 1, 2, 1),
            patchOf(4, 2, 1, 20),
            patchOf(5, 3, 1, 40),
            patchOf(6, 4, 1, 60, virtual = true),
            patchOf(7, 4, 1, 70),
            patchOf(8, 5, 1, 80)
        )
        nextPatchId = 9
    }

    private fun engine(): ValueEngine = ValueEngine(testShow()).also { it.load() }

    // ---------------------------------------------------------------
    // Frames and addresses
    // ---------------------------------------------------------------

    @Test
    fun frameAlwaysHas512Bytes() {
        assertEquals(512, engine().frame(1).size)
    }

    @Test
    fun universeWithoutPatchesIsAllZero() {
        val frame = engine().frame(3)
        assertEquals(512, frame.size)
        assertEquals(0, frame.count { it.toInt() != 0 })
    }

    @Test
    fun universeWithoutDefaultsStartsDark() {
        val frame = engine().frame(2) // only patch 3, an RGB fixture without defaults
        assertEquals(0, frame.count { it.toInt() != 0 })
    }

    @Test
    fun intensityGoesToTheRightAddress() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_INTENSITY, 1.0f)

        val frame = e.frame(1)
        assertEquals(255, frame.at(1))
        assertEquals(0, frame.at(2))
    }

    @Test
    fun secondPatchUsesItsOwnAddress() {
        val e = engine()
        e.setValue(1, 2, Attribute.ATTRIBUTE_GREEN, 1.0f)

        val frame = e.frame(1)
        assertEquals(255, frame.at(12)) // Address 10 + channel offset 3 - 1.
        assertEquals(0, frame.at(3))    // Patch 1 green channel remains off.
    }

    @Test
    fun universesAreSeparate() {
        val e = engine()
        e.setValue(1, 3, Attribute.ATTRIBUTE_INTENSITY, 1.0f)

        assertEquals(255, e.frame(2).at(1))
        assertEquals(0, e.frame(1).at(1))
    }

    // ---------------------------------------------------------------
    // Values and rounding
    // ---------------------------------------------------------------

    @Test
    fun valuesAreRoundedTo8Bit() {
        val e = engine()
        val expected = mapOf(0.0f to 0, 0.2f to 51, 0.5f to 128, 1.0f to 255)
        for ((value, dmx) in expected) {
            e.setValue(1, 1, Attribute.ATTRIBUTE_RED, value)
            assertEquals(dmx, e.frame(1).at(2), "Wert $value")
        }
    }

    @Test
    fun valuesAreClampedToZeroAndOne() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_RED, 1.5f)
        assertEquals(255, e.frame(1).at(2))

        e.setValue(1, 1, Attribute.ATTRIBUTE_RED, -0.5f)
        assertEquals(0, e.frame(1).at(2))
    }

    @Test
    fun unknownPatchIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            engine().setValue(1, 99, Attribute.ATTRIBUTE_INTENSITY, 1.0f)
        }
    }

    @Test
    fun attributeTheFixtureDoesNotHaveIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            engine().setValue(1, 1, Attribute.ATTRIBUTE_PAN, 0.5f) // The RGB fixture has no pan channel.
        }
    }

    // ---------------------------------------------------------------
    // Default values and 16-bit channels
    // ---------------------------------------------------------------

    @Test
    fun untouchedChannelsOutputTheirDefaultValues() {
        val frame = engine().frame(1)

        // Pan and tilt are centered: 32768 is 0x8000, so coarse is 128 and fine is 0.
        assertEquals(128, frame.at(20))
        assertEquals(0, frame.at(21))
        assertEquals(128, frame.at(22))
        assertEquals(0, frame.at(23))
        assertEquals(0, frame.at(24))  // Intensity defaults to zero when no default is specified.
        assertEquals(7, frame.at(25))  // Default value 7.
    }

    @Test
    fun sixteenBitValueIsSplitIntoTwoBytes() {
        val e = engine()
        val expected = listOf(
            Triple(0.0f, 0, 0),
            Triple(0.25f, 64, 0),      // 16384 = 0x4000
            Triple(0.5f, 128, 0),      // 32768 = 0x8000
            Triple(1.0f, 255, 255)     // 65535 = 0xFFFF
        )
        for ((value, coarse, fine) in expected) {
            e.setValue(1, 4, Attribute.ATTRIBUTE_PAN, value)
            val frame = e.frame(1)
            assertEquals(coarse, frame.at(20), "Hauptkanal bei $value")
            assertEquals(fine, frame.at(21), "Feinkanal bei $value")
        }
    }

    @Test
    fun valueOverridesTheDefault() {
        val e = engine()
        e.setValue(1, 4, Attribute.ATTRIBUTE_OTHER, 1.0f)
        assertEquals(255, e.frame(1).at(25))

        e.clearValue(1, 4, Attribute.ATTRIBUTE_OTHER)
        assertEquals(7, e.frame(1).at(25)) // Without an active value, the channel returns to its default.
    }

    // ---------------------------------------------------------------
    // Cells
    // ---------------------------------------------------------------

    @Test
    fun cellValuesGoToTheirOwnChannels() {
        val e = engine()
        e.setValue(1, 5, Attribute.ATTRIBUTE_RED, 1.0f, cell = 2)

        val frame = e.frame(1)
        assertEquals(255, frame.at(44)) // Address 40 + channel offset 5 - 1.
        assertEquals(0, frame.at(41))   // Cell 1 red remains off.
    }

    @Test
    fun sharedChannelOfABarWorksWithCellZero() {
        val e = engine()
        e.setValue(1, 5, Attribute.ATTRIBUTE_INTENSITY, 1.0f)

        assertEquals(255, e.frame(1).at(40))
    }

    @Test
    fun wrongScopeIsRejected() {
        val e = engine()
        assertFailsWith<IllegalArgumentException> {
            e.setValue(1, 5, Attribute.ATTRIBUTE_INTENSITY, 1.0f, cell = 1) // Cell 1 has no intensity attribute.
        }
        assertFailsWith<IllegalArgumentException> {
            e.setValue(1, 5, Attribute.ATTRIBUTE_RED, 1.0f, cell = 3)       // Cell 3 does not exist.
        }
        assertFailsWith<IllegalArgumentException> {
            e.setValue(1, 5, Attribute.ATTRIBUTE_RED, 1.0f, cell = 0)       // Shared channels do not have a red attribute.
        }
    }

    // ---------------------------------------------------------------
    // Virtual Dimmer
    // ---------------------------------------------------------------

    @Test
    fun virtualDimmerWithoutIntensityIsDark() {
        val e = engine()
        e.setValue(1, 6, Attribute.ATTRIBUTE_RED, 1.0f)
        e.setValue(1, 6, Attribute.ATTRIBUTE_GREEN, 0.5f)

        val frame = e.frame(1)
        assertEquals(0, frame.at(60))
        assertEquals(0, frame.at(61))
    }

    @Test
    fun virtualDimmerScalesTheColorChannels() {
        val e = engine()
        e.setValue(1, 6, Attribute.ATTRIBUTE_RED, 1.0f)
        e.setValue(1, 6, Attribute.ATTRIBUTE_GREEN, 0.5f)

        e.setValue(1, 6, Attribute.ATTRIBUTE_INTENSITY, 1.0f)
        assertEquals(255, e.frame(1).at(60))
        assertEquals(128, e.frame(1).at(61))

        e.setValue(1, 6, Attribute.ATTRIBUTE_INTENSITY, 0.5f)
        assertEquals(128, e.frame(1).at(60)) // 1.0 multiplied by 0.5.
        assertEquals(64, e.frame(1).at(61))  // 0.5 multiplied by 0.5.
        assertEquals(0, e.frame(1).at(62))
    }

    @Test
    fun intensityWithoutDimmerChannelAndWithoutVirtualDimmerIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            engine().setValue(1, 7, Attribute.ATTRIBUTE_INTENSITY, 1.0f)
        }
    }

    // ---------------------------------------------------------------
    // Multiple sources: HTP and LTP
    // ---------------------------------------------------------------

    // Intensity uses HTP: the highest value wins.
    @Test
    fun intensityTakesTheHighestValue() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_INTENSITY, 0.2f)
        e.setValue(2, 1, Attribute.ATTRIBUTE_INTENSITY, 0.8f)
        assertEquals(204, e.frame(1).at(1))

        e.setValue(2, 1, Attribute.ATTRIBUTE_INTENSITY, 0.1f)
        assertEquals(51, e.frame(1).at(1)) // Source 1 now has the highest value.
    }

    // Colors default to LTP: the most recently set value wins.
    @Test
    fun colorTakesTheLatestValue() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_RED, 1.0f)
        e.setValue(2, 1, Attribute.ATTRIBUTE_RED, 0.0f)
        assertEquals(0, e.frame(1).at(2)) // Source 2 was updated most recently, even though its value is lower.

        e.setValue(1, 1, Attribute.ATTRIBUTE_RED, 0.5f)
        assertEquals(128, e.frame(1).at(2)) // Source 1 is active again.
    }

    // An explicit HTP setting also applies to color channels.
    @Test
    fun explicitPrecedenceOverridesTheDefault() {
        val e = engine()
        e.setValue(1, 8, Attribute.ATTRIBUTE_RED, 1.0f)
        e.setValue(2, 8, Attribute.ATTRIBUTE_RED, 0.0f)

        assertEquals(255, e.frame(1).at(80))
    }

    @Test
    fun clearingASourceFallsBackToTheOthers() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_INTENSITY, 0.8f)
        e.setValue(2, 1, Attribute.ATTRIBUTE_INTENSITY, 0.2f)
        assertEquals(204, e.frame(1).at(1))

        e.clearValue(1, 1, Attribute.ATTRIBUTE_INTENSITY)
        assertEquals(51, e.frame(1).at(1))

        e.clearValue(2, 1, Attribute.ATTRIBUTE_INTENSITY)
        assertEquals(0, e.frame(1).at(1))
    }

    @Test
    fun clearingAValueThatWasNeverSetIsHarmless() {
        engine().clearValue(1, 1, Attribute.ATTRIBUTE_RED)
    }

    // ---------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------

    @Test
    fun loadingAShowResetsAllValues() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_INTENSITY, 1.0f)

        e.load()

        assertEquals(0, e.frame(1).at(1))
    }
}
