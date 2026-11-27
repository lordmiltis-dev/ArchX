// Builds a representative show used by the master tests and sample tools.
import patchbuilding.ShowBuilder
import dev.archx.schema.Attribute
import dev.archx.schema.Precedence
import dev.archx.schema.Showfile

class ExampleShow: ShowBuilder() {
    // ---------------------------------------------------------------
// Example fixture type with two patched instances.
// ---------------------------------------------------------------

    fun exampleShow(): Showfile {
        val miniTriPar = buildFixtureType(
            libraryName = "Platinum Mini TRI-PAR",
            manufacturerName = "lightmaXX",
            modeName = "6-channel",
            libraryKey = "lightmaxx/platinum-mini-tri-par/6-channel",
            librarySource = "ofl",
            libraryRevision = "2018-09-22",
            channelCount = 6,
            sharedChannels = listOf(
                buildChannelDef(
                    1,
                    Attribute.ATTRIBUTE_INTENSITY,
                    "Intensity",
                    highlightValue = 255,
                    precedence = Precedence.PRECEDENCE_HTP,

                    ),
                buildChannelDef(
                    2,
                    Attribute.ATTRIBUTE_RED,
                    "Red",
                    highlightValue = 255,
                    precedence = Precedence.PRECEDENCE_HTP
                ),
                buildChannelDef(
                    3,
                    Attribute.ATTRIBUTE_GREEN,
                    "Green",
                    highlightValue = 255,
                    precedence = Precedence.PRECEDENCE_HTP
                ),
                buildChannelDef(
                    4,
                    Attribute.ATTRIBUTE_BLUE,
                    "Blue",
                    highlightValue = 255,
                    precedence = Precedence.PRECEDENCE_HTP
                ),
                buildChannelDef(5, Attribute.ATTRIBUTE_EFFECT, "Macro"),
                buildChannelDef(6, Attribute.ATTRIBUTE_STROBE, "Strobe", highlightValue = 200)
            )
        )

        val show = ShowBuilder("Testshow")
        show.patchFixture("Par 1", 1, miniTriPar, patchUniverse = 1, patchAddress = 1)
        show.patchFixture("Par 2", 2, miniTriPar, patchUniverse = 1, patchAddress = 7)
        return show.getShow()
    }
}
