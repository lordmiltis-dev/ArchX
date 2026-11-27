// Tests show serialization, fixture reuse, cell persistence, and checked-in examples.
import patchbuilding.ShowBuilder
import dev.archx.schema.Attribute
import dev.archx.schema.FixtureType
import dev.archx.schema.Showfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ShowfileRoundtripTest: ShowBuilder() {

    // Build a KLS-801 in 15-channel mode: 3 shared channels and 4 RGB cells.
    private fun kls801(): FixtureType {
        val cells = (1..4).map { cell ->
            val first = 4 + (cell - 1) * 3        // First channel for the cell: 4, 7, 10, or 13.
            buildSubFixtureDef(
                cellIndex = cell,
                cellName = "Pixel $cell",
                posX = (4 - cell).toFloat(),      // Cell 1 is on the right; cell 4 is on the left.
                posY = 0f,
                channelProperties = listOf(
                    buildChannelDef(first, Attribute.ATTRIBUTE_RED, "Red $cell"),
                    buildChannelDef(first + 1, Attribute.ATTRIBUTE_GREEN, "Green $cell"),
                    buildChannelDef(first + 2, Attribute.ATTRIBUTE_BLUE, "Blue $cell")
                )
            )
        }
        return buildFixtureType(
            libraryName = "LED KLS-801",
            manufacturerName = "Eurolite",
            modeName = "15-channel",
            libraryKey = "eurolite/led-kls-801/15-channel",
            librarySource = "ofl",
            libraryRevision = "2024-11-23",
            channelCount = 15,
            sharedChannels = listOf(
                buildChannelDef(1, Attribute.ATTRIBUTE_CONTROL, "Preset programs"),
                buildChannelDef(2, Attribute.ATTRIBUTE_INTENSITY, "Master Dimmer", highlightValue = 255),
                buildChannelDef(3, Attribute.ATTRIBUTE_STROBE, "Strobe", highlightValue = 220)
            ),
            subFixtureDefs = cells
        )
    }

    // Serializing and parsing the show should preserve it exactly.
    @Test
    fun showfileSurvivesSerialization() {
        val original = ExampleShow().exampleShow()
        val restored = Showfile.parseFrom(original.toByteArray())
        assertEquals(original, restored)
    }

    // Two identical fixtures share one type.
    @Test
    fun twoPatchesShareOneType() {
        val show = ExampleShow().exampleShow()
        assertEquals(1, show.fixtureTypesCount)
        assertEquals(2, show.patchCount)
        assertEquals(show.patchList[0].fixtureTypeId, show.patchList[1].fixtureTypeId)
        assertEquals(3, show.nextPatchId)
    }

    // Cells and their channels must survive serialization.
    @Test
    fun cellsSurviveSerialization() {
        val builder = ShowBuilder()
        builder.patchFixture("KLS old", 3, kls801(), patchUniverse = 1, patchAddress = 85)
        val show = builder.build("KLS-Test")

        val restored = Showfile.parseFrom(show.toByteArray())
        val type = restored.fixtureTypesList[0]

        assertEquals(15, type.channelCount)
        assertEquals(3, type.channelsCount)
        assertEquals(4, type.subFixturesCount)
        assertEquals(3, type.subFixturesList[0].channelsCount)
        assertEquals(show, restored)
    }

    // A show without patches is valid.
    @Test
    fun emptyShowWorks() {
        val show = ShowBuilder().build("Leer")
        assertEquals(0, show.patchCount)
        assertEquals(1, show.nextPatchId)
        assertEquals(show, Showfile.parseFrom(show.toByteArray()))
    }

    // A six-channel fixture cannot start at address 510.
    @Test
    fun fixtureMustNotExceedUniverse() {
        val type = ExampleShow().exampleShow().fixtureTypesList[0]
        assertFailsWith<IllegalArgumentException> {
            ShowBuilder().patchFixture("Zu weit hinten", 1, type, patchUniverse = 1, patchAddress = 510)
        }
    }
    @Test
    fun goldenFileIsUpToDate() {
        val golden = Showfile.parseFrom(File("../schema/testdata/showfile_example.bin").readBytes())
        assertEquals(ExampleShow().exampleShow(), golden)
    }

    @Test
    fun loadedShowContinuesWithFreshIds() {
        val builder = ShowBuilder()
        builder.load(ExampleShow().exampleShow())

        val type = builder.getShow().fixtureTypesList[0]
        val entry = builder.patchFixture("Par 3", 3, type, patchUniverse = 1, patchAddress = 13)

        assertEquals(3, entry.id)                        // IDs 1 and 2 were already allocated.
        assertEquals(1, builder.getShow().fixtureTypesCount)   // The fixture type is not duplicated.
    }
}
