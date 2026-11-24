import patchbuilding.ShowBuilder
import patchbuilding.validateShow
import dev.archx.schema.PatchEntry
import dev.archx.schema.Showfile
import dev.archx.schema.copy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Die Beispiel-Show hat einen Typ (ID 1, 6 Kanäle) und zwei Patches:
// Par 1 (ID 1, Universum 1, Adresse 1 bis 6) und Par 2 (ID 2, Universum 1, Adresse 7 bis 12)
class ShowValidationTest {

    private fun validShow(): Showfile = ExampleShow().exampleShow()

    // Hilfsfunktion: eine Kopie der Show mit einer anderen Patch-Liste
    private fun Showfile.withPatches(patches: List<PatchEntry>): Showfile = copy {
        patch.clear()
        patch += patches
    }

    // ----- gültige Shows -----

    @Test
    fun validShowPasses() {
        validateShow(validShow())
    }

    @Test
    fun samePatchesInDifferentUniversesAreFine() {
        val show = validShow()
        val ok = show.withPatches(listOf(show.patchList[0], show.patchList[1].copy { universe = 2; address = 4 }))
        validateShow(ok)
    }

    // ----- ungültige Shows -----

    @Test
    fun unknownTypeIsRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { fixtureTypeId = 99 }, show.patchList[1]))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun duplicatePatchIdsAreRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0], show.patchList[1].copy { id = 1 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun duplicateTypeIdsAreRejected() {
        val show = validShow()
        val broken = show.copy { fixtureTypes += show.fixtureTypesList[0] }   // derselbe Typ zweimal, gleiche ID
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Jeder Patch braucht ein Universum (ab 1) und eine Adresse (1 bis 512)
    @Test
    fun patchWithoutUniverseIsRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { universe = 0 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun patchWithoutAddressIsRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { address = 0 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Auch der .ShowBuilder lässt einen Patch ohne Universum und Adresse nicht zu
    @Test
    fun patchFixtureRejectsMissingAddress() {
        val type = validShow().fixtureTypesList[0]
        assertFailsWith<IllegalArgumentException> {
            ShowBuilder().patchFixture("Ohne Adresse", 1, type, patchUniverse = 0, patchAddress = 0)
        }
    }

    @Test
    fun addressAbove512IsRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { address = 513 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun fixtureExceedingUniverseIsRejected() {
        val show = validShow()
        // 6 Kanäle ab Adresse 510 ergäben die Kanäle 510 bis 515
        val broken = show.withPatches(listOf(show.patchList[0].copy { address = 510 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun overlappingPatchesAreRejected() {
        val show = validShow()
        // Par 2 ab Adresse 4 belegt 4 bis 9 und überschneidet sich mit Par 1 (1 bis 6)
        val broken = show.withPatches(listOf(show.patchList[0], show.patchList[1].copy { address = 4 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // ----- Laden -----

    // Eine fehlerhafte Datei darf die bisherige Show nicht zerstören
    @Test
    fun failedLoadKeepsOldShow() {
        val builder = ShowBuilder()
        builder.load(validShow())
        val before = builder.getShow()

        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { fixtureTypeId = 99 }))
        assertFailsWith<IllegalArgumentException> { builder.load(broken) }

        assertEquals(before, builder.getShow())
    }
}
