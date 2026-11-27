// Tests show validation and failure-safe loading in ShowBuilder.
import patchbuilding.ShowBuilder
import patchbuilding.validateShow
import dev.archx.schema.PatchEntry
import dev.archx.schema.Showfile
import dev.archx.schema.copy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// The example show has one six-channel type and two patches:
// Par 1 (ID 1, universe 1, addresses 1..6) and Par 2 (ID 2, universe 1, addresses 7..12).
class ShowValidationTest {

    private fun validShow(): Showfile = ExampleShow().exampleShow()

    // Helper that copies the show with a replacement patch list.
    private fun Showfile.withPatches(patches: List<PatchEntry>): Showfile = copy {
        patch.clear()
        patch += patches
    }

    // ----- Valid shows -----

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

    // ----- Invalid shows -----

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
        val broken = show.copy { fixtureTypes += show.fixtureTypesList[0] }   // the same type appears twice with the same ID
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Each patch requires a one-based universe and an address from 1 through 512.
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

    // ShowBuilder must also reject a patch without a universe or address.
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
        // Six channels starting at address 510 would occupy addresses 510 through 515.
        val broken = show.withPatches(listOf(show.patchList[0].copy { address = 510 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    @Test
    fun overlappingPatchesAreRejected() {
        val show = validShow()
        // Par 2 starting at address 4 occupies 4..9 and overlaps Par 1 at 1..6.
        val broken = show.withPatches(listOf(show.patchList[0], show.patchList[1].copy { address = 4 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // ----- Loading -----

    // A failed load must not corrupt the current show.
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
