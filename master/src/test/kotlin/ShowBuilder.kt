import dev.archx.schema.Attribute
import dev.archx.schema.FixtureType
import dev.archx.schema.PatchEntry
import dev.archx.schema.Showfile
import dev.archx.schema.SubFixtureSettings
import dev.archx.schema.copy
import dev.archx.schema.showfile
import dev.archx.schema.Precedence

open class ShowBuilder(private var showName: String = "Neue Show"): PatchBuilderHelper() {
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
        require(patchUniverse >= 1) { "Das Universum muss ab 1 gezählt werden" }
        require(patchAddress in 1..512) { "Die Adresse muss zwischen 1 und 512 liegen" }
        require(patchAddress + type.channelCount - 1 <= 512) {
                "Das Fixture ragt über Kanal 512 hinaus"
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

    fun load(show: Showfile) {
             validateShow(show)
            fixtureTypesList.clear()
            patchList.clear()
            fixtureTypesList += show.fixtureTypesList
            patchList += show.patchList
            typeIdCounter = (show.fixtureTypesList.maxOfOrNull { it.id } ?: 0) + 1
            patchIdCounter = maxOf(show.nextPatchId, (show.patchList.maxOfOrNull { it.id } ?: 0) + 1)
            showName = show.name


    }




    fun getShow(): Showfile {
        return build(showName)
    }

    fun getPatch(): List<PatchEntry> {
        return patchList.toList()
    }

    fun getfixtureTypes(): List<FixtureType> {
        return fixtureTypesList.toList()
    }

}