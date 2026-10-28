import dev.archx.schema.Attribute
import dev.archx.schema.FixtureType
import dev.archx.schema.PatchEntry
import dev.archx.schema.Showfile
import dev.archx.schema.SubFixtureSettings
import dev.archx.schema.copy
import dev.archx.schema.showfile
import dev.archx.schema.Precedence

open class ShowBuilder: PatchBuilderHelper() {
    val fixtureTypesList = mutableListOf<FixtureType>();
    val patchList = mutableListOf<PatchEntry>();
    var typeIdCounter = 1
    var patchIdCounter = 1



    fun addFixtureType(type: FixtureType): Int {
        if (type.libraryKey.isNotEmpty()) {
            val existing = fixtureTypesList.find {
                it.librarySource == type.librarySource && it.libraryKey == type.libraryKey
            }
            if (existing != null) return existing.id
        }
        val newId = typeIdCounter++
        fixtureTypesList += type.copy { id = newId }   // copy: Kopie mit geänderter ID
        return newId
    }

    fun patchFixture(
        patchName: String,
        patchNumber: Int,
        type: FixtureType,
        patchUniverse: Int,
        patchAddress: Int,
        vDimmer: Boolean = false,
        panInvert: Boolean = false,
        tiltInvert: Boolean = false,
        subSettings: List<SubFixtureSettings> = emptyList()
    ): PatchEntry {
        // 0 und 0 bedeutet "nicht gepatcht", alles andere wird geprüft
        if (patchUniverse != 0 || patchAddress != 0) {
            require(patchUniverse >= 1) { "Das Universum muss ab 1 gezählt werden" }
            require(patchAddress in 1..512) { "Die Adresse muss zwischen 1 und 512 liegen" }
            require(patchAddress + type.channelCount - 1 <= 512) {
                "Das Fixture ragt über Kanal 512 hinaus"
            }
        }

        val typeId = addFixtureType(type)
        val entry = buildPatchEntry(
            patchName, patchIdCounter++, patchNumber, typeId,
            patchUniverse, patchAddress, vDimmer, panInvert, tiltInvert, subSettings
        )
        patchList += entry
        return entry
    }

    fun build(showName: String): Showfile = showfile {
        name = showName
        fixtureTypes += fixtureTypesList
        patch += patchList
        nextPatchId = patchIdCounter     // der Zähler heißt absichtlich anders als das Feld
    }


// ---------------------------------------------------------------
// Beispiel: ein Typ, zwei Patches
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

        val show = ShowBuilder()
        show.patchFixture("Par 1", 1, miniTriPar, patchUniverse = 1, patchAddress = 1)
        show.patchFixture("Par 2", 2, miniTriPar, patchUniverse = 1, patchAddress = 7)
        return show.build("Testshow")
    }

    fun getShow(): Showfile {
        return exampleShow()
    }

    fun getPatch(): MutableList<PatchEntry> {
        return patchList
    }

}