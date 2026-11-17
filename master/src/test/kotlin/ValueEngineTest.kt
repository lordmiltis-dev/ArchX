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
    // Hilfen zum Bauen der Testdaten (mit der Protobuf-DSL, unabhängig von deinen anderen Hilfsklassen)
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

    // DMX-Wert an einer Adresse (ab 1) als Zahl von 0 bis 255
    private fun ByteArray.at(address: Int): Int = this[address - 1].toInt() and 0xFF

    // ---------------------------------------------------------------
    // Die Testshow
    //
    //  Typ 1  rgbPar    4 Kanäle: 1 Intensität, 2 Rot, 3 Grün, 4 Blau
    //  Typ 2  mover     6 Kanäle: 1 Pan (fein 2, Grundwert 32768), 3 Tilt (fein 4, Grundwert 32768),
    //                             5 Intensität, 6 Sonstiges (Grundwert 7)
    //  Typ 3  bar       7 Kanäle: 1 Intensität (gemeinsam), Zelle 1: 2 Rot, 3 Grün, 4 Blau, Zelle 2: 5 Rot, 6 Grün, 7 Blau
    //  Typ 4  rgbOnly   3 Kanäle: 1 Rot, 2 Grün, 3 Blau (ohne Dimmer)
    //  Typ 5  htpRed    1 Kanal: 1 Rot mit ausdrücklich HTP
    //
    //  Patch 1  Typ 1, Universum 1, Adresse 1   (belegt 1 bis 4)
    //  Patch 2  Typ 1, Universum 1, Adresse 10  (10 bis 13)
    //  Patch 3  Typ 1, Universum 2, Adresse 1
    //  Patch 4  Typ 2, Universum 1, Adresse 20  (20 bis 25)
    //  Patch 5  Typ 3, Universum 1, Adresse 40  (40 bis 46)
    //  Patch 6  Typ 4, Universum 1, Adresse 60  (60 bis 62), mit Virtual Dimmer
    //  Patch 7  Typ 4, Universum 1, Adresse 70  (70 bis 72), ohne Virtual Dimmer
    //  Patch 8  Typ 5, Universum 1, Adresse 80
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
    // Rahmen und Adressen
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
        val frame = engine().frame(2) // nur Patch 3, ein RGB-Scheinwerfer ohne Grundwerte
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
        assertEquals(255, frame.at(12)) // Adresse 10 plus Position 3 minus 1
        assertEquals(0, frame.at(3))    // der Grünkanal von Patch 1 bleibt aus
    }

    @Test
    fun universesAreSeparate() {
        val e = engine()
        e.setValue(1, 3, Attribute.ATTRIBUTE_INTENSITY, 1.0f)

        assertEquals(255, e.frame(2).at(1))
        assertEquals(0, e.frame(1).at(1))
    }

    // ---------------------------------------------------------------
    // Werte und Rundung
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
            engine().setValue(1, 1, Attribute.ATTRIBUTE_PAN, 0.5f) // der RGB-Scheinwerfer hat kein Pan
        }
    }

    // ---------------------------------------------------------------
    // Grundwerte und 16 Bit
    // ---------------------------------------------------------------

    @Test
    fun untouchedChannelsOutputTheirDefaultValues() {
        val frame = engine().frame(1)

        // Pan und Tilt stehen in der Mitte: 32768 ist 0x8000, also Hauptkanal 128 und Feinkanal 0
        assertEquals(128, frame.at(20))
        assertEquals(0, frame.at(21))
        assertEquals(128, frame.at(22))
        assertEquals(0, frame.at(23))
        assertEquals(0, frame.at(24))  // Intensität ohne Grundwert
        assertEquals(7, frame.at(25))  // Grundwert 7
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
        assertEquals(7, e.frame(1).at(25)) // ohne Wert gilt wieder der Grundwert
    }

    // ---------------------------------------------------------------
    // Zellen
    // ---------------------------------------------------------------

    @Test
    fun cellValuesGoToTheirOwnChannels() {
        val e = engine()
        e.setValue(1, 5, Attribute.ATTRIBUTE_RED, 1.0f, cell = 2)

        val frame = e.frame(1)
        assertEquals(255, frame.at(44)) // Adresse 40 plus Position 5 minus 1
        assertEquals(0, frame.at(41))   // Rot von Zelle 1 bleibt aus
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
            e.setValue(1, 5, Attribute.ATTRIBUTE_INTENSITY, 1.0f, cell = 1) // Zellen haben keine Intensität
        }
        assertFailsWith<IllegalArgumentException> {
            e.setValue(1, 5, Attribute.ATTRIBUTE_RED, 1.0f, cell = 3)       // Zelle 3 gibt es nicht
        }
        assertFailsWith<IllegalArgumentException> {
            e.setValue(1, 5, Attribute.ATTRIBUTE_RED, 1.0f, cell = 0)       // die gemeinsamen Kanäle haben kein Rot
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
        assertEquals(128, e.frame(1).at(60)) // 1.0 mal 0.5
        assertEquals(64, e.frame(1).at(61))  // 0.5 mal 0.5
        assertEquals(0, e.frame(1).at(62))
    }

    @Test
    fun intensityWithoutDimmerChannelAndWithoutVirtualDimmerIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            engine().setValue(1, 7, Attribute.ATTRIBUTE_INTENSITY, 1.0f)
        }
    }

    // ---------------------------------------------------------------
    // Mehrere Quellen: HTP und LTP
    // ---------------------------------------------------------------

    // Intensität: der höchste Wert gewinnt (HTP)
    @Test
    fun intensityTakesTheHighestValue() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_INTENSITY, 0.2f)
        e.setValue(2, 1, Attribute.ATTRIBUTE_INTENSITY, 0.8f)
        assertEquals(204, e.frame(1).at(1))

        e.setValue(2, 1, Attribute.ATTRIBUTE_INTENSITY, 0.1f)
        assertEquals(51, e.frame(1).at(1)) // jetzt hat Quelle 1 den höchsten Wert
    }

    // Farben ohne ausdrückliche Angabe: der zuletzt gesetzte Wert gewinnt (LTP)
    @Test
    fun colorTakesTheLatestValue() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_RED, 1.0f)
        e.setValue(2, 1, Attribute.ATTRIBUTE_RED, 0.0f)
        assertEquals(0, e.frame(1).at(2)) // Quelle 2 war zuletzt dran, auch wenn ihr Wert niedriger ist

        e.setValue(1, 1, Attribute.ATTRIBUTE_RED, 0.5f)
        assertEquals(128, e.frame(1).at(2)) // jetzt wieder Quelle 1
    }

    // Steht an einem Kanal ausdrücklich HTP, gilt HTP, auch bei einer Farbe
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
    // Laden
    // ---------------------------------------------------------------

    @Test
    fun loadingAShowResetsAllValues() {
        val e = engine()
        e.setValue(1, 1, Attribute.ATTRIBUTE_INTENSITY, 1.0f)

        e.load()

        assertEquals(0, e.frame(1).at(1))
    }
}
