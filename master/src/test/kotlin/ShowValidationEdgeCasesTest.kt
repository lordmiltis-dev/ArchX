// Tests show validation at boundary conditions and across multiple patches.
import patchbuilding.ShowBuilder
import patchbuilding.validateShow
import dev.archx.schema.PatchEntry
import dev.archx.schema.Showfile
import dev.archx.schema.copy
import kotlin.test.Test
import kotlin.test.assertFailsWith

// Additional validateShow tests for multiple types, multiple patches, and boundary values.
class ShowValidationEdgeCasesTest {

    private fun validShow(): Showfile = ExampleShow().exampleShow()

    private fun Showfile.withPatches(patches: List<PatchEntry>): Showfile = copy {
        patch.clear()
        patch += patches
    }

    // Build six patches that share a type, including address and universe boundaries.
    private fun sixPatchShow(): Showfile {
        val type = validShow().fixtureTypesList[0]      // 6 channels
        val builder = ShowBuilder()
        builder.patchFixture("Par 1", 1, type, patchUniverse = 1, patchAddress = 1)
        builder.patchFixture("Par 2", 2, type, patchUniverse = 1, patchAddress = 7)
        builder.patchFixture("Par 3", 3, type, patchUniverse = 1, patchAddress = 13)
        builder.patchFixture("Par 4", 4, type, patchUniverse = 1, patchAddress = 19)
        builder.patchFixture("Par 5", 5, type, patchUniverse = 1, patchAddress = 506)
        builder.patchFixture("Par 6", 6, type, patchUniverse = 2, patchAddress = 1)
        return builder.getShow()
    }

    // ----- Valid shows -----

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

    // Six channels from address 507 occupy exactly addresses 507 through 512.
    @Test
    fun fixtureEndingExactlyAtChannel512IsValid() {
        val type = validShow().fixtureTypesList[0]
        val builder = ShowBuilder()
        builder.patchFixture("Am Ende", 1, type, patchUniverse = 1, patchAddress = 507)
        validateShow(builder.getShow())
    }

    // ----- Invalid shows -----

    // An invalid final patch must still be detected.
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

    // Six channels from address 508 would extend past channel 512.
    @Test
    fun fixtureOneChannelTooFarIsRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0].copy { address = 508 }))
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Patch IDs must be unique across the entire show, not only within one universe.
    @Test
    fun duplicatePatchIdsInDifferentUniversesAreRejected() {
        val show = validShow()
        val broken = show.withPatches(
            listOf(show.patchList[0], show.patchList[1].copy { id = 1; universe = 2 })
        )
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // Two types have the same ID while only one patch remains.
    @Test
    fun duplicateTypeIdsWithSinglePatchAreRejected() {
        val show = validShow()
        val broken = show.withPatches(listOf(show.patchList[0]))
            .copy { fixtureTypes += show.fixtureTypesList[0] }
        assertFailsWith<IllegalArgumentException> { validateShow(broken) }
    }

    // ----- Overlapping patches -----

    // Patch 1 occupies 1..6; patch 2 starts at 6, so channel 6 overlaps.
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

    // Use each patch type channel count when checking overlaps.
    @Test
    fun overlapUsesTheTypeOfEachPatch() {
        val small = validShow().fixtureTypesList[0]                                   // 6 channels
        val big = small.copy { libraryKey = "gross"; name = "Grosser Par"; channelCount = 12 }
        val builder = ShowBuilder()
        builder.patchFixture("Klein 1", 1, small, patchUniverse = 1, patchAddress = 30)   // occupies 30..35
        builder.patchFixture("Gross", 2, big, patchUniverse = 1, patchAddress = 10)       // occupies 10..21
        builder.patchFixture("Klein 2", 3, small, patchUniverse = 1, patchAddress = 20)   // occupies 20..25 and overlaps the larger fixture
        assertFailsWith<IllegalArgumentException> { validateShow(builder.getShow()) }
    }
}
