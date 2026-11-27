// Tests validation of fixture channel offsets, cell indexes, and channel counts.
import patchbuilding.validateShow
import dev.archx.schema.Attribute
import dev.archx.schema.FixtureType
import dev.archx.schema.Showfile
import dev.archx.schema.channelDef
import dev.archx.schema.copy
import dev.archx.schema.fixtureType
import dev.archx.schema.patchEntry
import dev.archx.schema.showfile
import dev.archx.schema.subFixtureDef
import kotlin.test.Test
import kotlin.test.assertFailsWith

// Tests the validation rules that apply to fixture types.
// Each invalid fixture violates exactly one rule so failures identify the rule under test.

class FixtureTypeValidationTest {

    // A seven-channel type with one shared channel and two cells.
    // Cell 1 uses offsets 2..4; cell 2 uses offsets 5..7.
    // Parameters allow each test to change a single property.
    private fun typeWith(
        count: Int = 7,
        sharedOffsets: List<Int> = listOf(1),
        cell1Offsets: List<Int> = listOf(2, 3, 4),
        cell2Offsets: List<Int> = listOf(5, 6, 7),
        cell2Index: Int = 2,
        fineOfFirstShared: Int = 0
    ): FixtureType = fixtureType {
        id = 1
        name = "Testtyp"
        manufacturer = "Test"
        modeName = "7-channel"
        libraryKey = "test/testtyp/7-channel"
        librarySource = "test"
        channelCount = count

        sharedOffsets.forEachIndexed { n, pos ->
            channels += channelDef {
                offset = pos
                attribute = Attribute.ATTRIBUTE_OTHER
                if (n == 0) fineOffset = fineOfFirstShared
            }
        }
        subFixtures += subFixtureDef {
            index = 1
            cell1Offsets.forEach { pos ->
                channels += channelDef { offset = pos; attribute = Attribute.ATTRIBUTE_RED }
            }
        }
        subFixtures += subFixtureDef {
            index = cell2Index
            cell2Offsets.forEach { pos ->
                channels += channelDef { offset = pos; attribute = Attribute.ATTRIBUTE_RED }
            }
        }
    }

    // Build a show with this fixture type patched at universe 1, address 1.
    private fun showWith(type: FixtureType): Showfile = showfile {
        name = "Typtest"
        fixtureTypes += type
        patch += patchEntry {
            id = 1
            fixtureNumber = 1
            name = "Patch"
            fixtureTypeId = type.id
            universe = 1
            address = 1
        }
        nextPatchId = 2
    }

    // ----- Valid fixture types -----

    @Test
    fun typeWithCellsIsValid() {
        validateShow(showWith(typeWith()))
    }

    // The fine channel at offset 8 is valid when the type has eight channels.
    @Test
    fun fineChannelInsideTheTypeIsValid() {
        validateShow(showWith(typeWith(count = 8, fineOfFirstShared = 8)))
    }

    // Channel count

    // A fixture type with zero channels has no valid DMX address range.
    @Test
    fun channelCountZeroIsRejected() {
        val type = typeWith(
            count = 0,
            sharedOffsets = emptyList(),
            cell1Offsets = emptyList(),
            cell2Offsets = emptyList()
        )
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Offsets within the type

    // Offset 8 is outside a seven-channel type.
    @Test
    fun offsetAboveChannelCountIsRejected() {
        val type = typeWith(count = 7, sharedOffsets = listOf(8))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Offsets are one-based, so zero is invalid.
    @Test
    fun offsetZeroIsRejected() {
        val type = typeWith(sharedOffsets = listOf(0))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // The fine channel at offset 9 is outside a seven-channel type.
    @Test
    fun fineOffsetAboveChannelCountIsRejected() {
        val type = typeWith(count = 7, fineOfFirstShared = 9)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Duplicate offsets

    // Two shared channels use the same offset.
    @Test
    fun sameOffsetTwiceInSharedChannelsIsRejected() {
        val type = typeWith(sharedOffsets = listOf(1, 1))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // The shared channel uses offset 2, which is already occupied by cell 1.
    @Test
    fun sameOffsetInSharedAndCellIsRejected() {
        val type = typeWith(sharedOffsets = listOf(2))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Cell 2 uses offset 4, which is already occupied by cell 1.
    @Test
    fun sameOffsetInTwoCellsIsRejected() {
        val type = typeWith(cell2Offsets = listOf(4, 6, 7))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // The fine channel uses offset 3, which is already occupied by cell 1.
    @Test
    fun fineOffsetCollidingWithAnotherChannelIsRejected() {
        val type = typeWith(fineOfFirstShared = 3)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // The fine channel cannot use the same offset as its coarse channel.
    @Test
    fun fineOffsetEqualToOwnOffsetIsRejected() {
        val type = typeWith(fineOfFirstShared = 1)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Cells

    // Both cells use index 1.
    @Test
    fun duplicateCellIndexIsRejected() {
        val type = typeWith(cell2Index = 1)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // A channel in cell 2 uses offset 8, outside the seven-channel type.
    @Test
    fun cellOffsetAboveChannelCountIsRejected() {
        val type = typeWith(cell2Offsets = listOf(5, 6, 8))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // A channel in cell 1 uses invalid offset 0.
    @Test
    fun cellOffsetZeroIsRejected() {
        val type = typeWith(cell1Offsets = listOf(0, 3, 4))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }

    }

    @Test
    fun twoTypesWithCellsOfDifferentSizeAreValid() {
        val small = typeWith()                                                // 7 channels, with offsets through 7
        val big = typeWith(count = 14, cell2Offsets = listOf(12, 13, 14))     // 14 channels, with offsets through 14
            .copy { id = 2; libraryKey = "test/gross/14-channel" }
        val show = showfile {
            name = "Zwei Typen"
            fixtureTypes += small
            fixtureTypes += big
            patch += patchEntry { id = 1; fixtureNumber = 1; name = "Klein"; fixtureTypeId = 1; universe = 1; address = 1 }
            patch += patchEntry { id = 2; fixtureNumber = 2; name = "Gross"; fixtureTypeId = 2; universe = 1; address = 20 }
            nextPatchId = 3
        }
        validateShow(show)
    }
}
