import dev.archx.schema.PatchEntry
import dev.archx.schema.Showfile
import dev.archx.schema.copy
import kotlin.test.Test
import kotlin.test.assertFailsWith

// Zusätzliche Tests für validateShow: mehrere Typen, mehrere Patches, Randwerte
class ShowValidationEdgeCasesTest {

    private fun validShow(): Showfile = ExampleShow().exampleShow()

    private fun Showfile.withPatches(patches: List<PatchEntry>): Showfile = copy {
        patch.clear()
        patch += patches
    }

    // Eine Show mit sechs Patches, die sich einen Typ teilen (Adressen 1, 7 und 13)
    private fun sixPatchShow(): Showfile {
        val type = validShow().fixtureTypesList[0]      // 6 Kanäle
        val builder = ShowBuilder()
        builder.patchFixture("Par 1", 1, type, patchUniverse = 1, patchAddress = 1)
        builder.patchFixture("Par 2", 2, type, patchUniverse = 1, patchAddress = 7)
        builder.patchFixture("Par 3", 3, type, patchUniverse = 1, patchAddress = 13)
        builder.patchFixture("Par 4", 4, type, patchUniverse = 1, patchAddress = 19)
        builder.patchFixture("Par 5", 5, type, patchUniverse = 1, patchAddress = 506)
        builder.patchFixture("Par 6", 6, type, patchUniverse = 2, patchAddress = 1)
        return builder.getShow()
    }

    // ----- gültige Shows -----

    @Test
    fun threePatchesSharingOneTypeAreValid() {
        validateShow(sixPatchShow())
    }

    @Test
    fun twoDifferentTypesAreValid() {
        val type = validShow().fixtureTypesList[0]
        val other = type.copy { libraryKey = "andere/bibliothek/schluessel"; name = "Anderer Par" }
        val builder = ShowBuilder()
        builder.patchFixture("Par 1", 1, type, patchUniverse = 1, patchAddress = 1)
        builder.patchFixture("Par 2", 2, type, patchUniverse = 1, patchAddress = 7)
        builder.patchFixture("Anderer", 3, other, patchUniverse = 1, patchAddress = 13)
        validateShow(builder.getShow())
    }

    // 6 Kanäle ab Adresse 507 belegen genau 507 bis 512
    @Test
    fun fixtureEndingExactlyAtChannel512IsValid() {
        val type = validShow().fixtureTypesList[0]
        val builder = ShowBuilder()
        builder.patchFixture("Am Ende", 1, type, patchUniverse = 1, patchAddress = 507)
        validateShow(builder.getShow())
    }

    // ----- ungültige Shows -----

    // Fehler im letzten Patch müssen auch gefunden werden
    @Test
    fun errorInLastPatchIsFound() {
        val show = sixPatchShow()
        val broken = show.withPatches(
            listOf(show.patchList[0], show.patchList[1], show.patchList[2].copy { address = 513 })
        )
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun unknownTypeInLastPatchIsFound() {
        val show = sixPatchShow()
        val broken = show.withPatches(
            listOf(show.patchList[0], show.patchList[1], show.patchList[2].copy { fixtureTypeId = 99 })
        )
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // 6 Kanäle ab Adresse 508 ergäben 508 bis 513, also einen Kanal zu viel
    @Test
    fun fixtureOneChannelTooFarIsRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { address = 508 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Patch-IDs müssen in der ganzen Show eindeutig sein, nicht nur in einem Universum
    @Test
    fun duplicatePatchIdsInDifferentUniversesAreRejected() {
        val show = validShow()
        val broken = show.withPatches(
            listOf(show.patchList[0], show.patchList[1].copy { id = 1; universe = 2 })
        )
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Zwei Typen mit derselben ID, aber nur ein Patch
    @Test
    fun duplicateTypeIdsWithSinglePatchAreRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0]))
            .copy { fixtureTypes += show.fixtureTypesList[0] }
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // ----- Überschneidungen -----

    // Par 1 belegt 1 bis 6, Par 2 ab Adresse 6 belegt 6 bis 11: Kanal 6 liegt doppelt
    @Test
    fun patchesSharingOneChannelAreRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0], show.patchList[1].copy { address = 6 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun patchesWithIdenticalAddressAreRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0], show.patchList[1].copy { address = 1 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Jeder Patch muss mit der Kanalzahl seines eigenen Typs gerechnet werden
    @Test
    fun overlapUsesTheTypeOfEachPatch() {
        val small = validShow().fixtureTypesList[0]                                   // 6 Kanäle
        val big = small.copy { libraryKey = "gross"; name = "Grosser Par"; channelCount = 12 }
        val builder = ShowBuilder()
        builder.patchFixture("Klein 1", 1, small, patchUniverse = 1, patchAddress = 30)   // 30 bis 35
        builder.patchFixture("Gross", 2, big, patchUniverse = 1, patchAddress = 10)       // 10 bis 21
        builder.patchFixture("Klein 2", 3, small, patchUniverse = 1, patchAddress = 20)   // 20 bis 25, überschneidet sich mit "Gross"
        assertFailsWith<IllegalArgumentException> { validateShow(builder.getShow()) }
    }
}
