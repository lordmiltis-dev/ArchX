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

// Tests für die Regeln, die den Fixture-Typ selbst betreffen.
// Jeder ungültige Test verletzt genau EINE Regel, damit ein grüner Test auch wirklich
// von dieser Regel kommt und nicht von einer anderen.
class FixtureTypeValidationTest {

    // Ein Typ mit 7 Kanälen: ein gemeinsamer Kanal (Position 1) und zwei Zellen.
    // Zelle 1 belegt die Positionen 2 bis 4, Zelle 2 die Positionen 5 bis 7.
    // Über die Parameter lässt sich genau eine Eigenschaft verändern.
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

    // Eine Show mit diesem Typ und einem Patch auf Universum 1, Adresse 1
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

    // ----- gültige Typen -----

    @Test
    fun typeWithCellsIsValid() {
        validateShow(showWith(typeWith()))
    }

    // Der Feinkanal liegt auf Position 8 und damit im Typ (8 Kanäle)
    @Test
    fun fineChannelInsideTheTypeIsValid() {
        validateShow(showWith(typeWith(count = 8, fineOfFirstShared = 8)))
    }

    // ----- Kanalzahl -----

    // Ein Typ ohne Kanäle und mit der Kanalzahl 0 ergibt keinen sinnvollen Adressbereich
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

    // ----- Positionen im Typ -----

    // Position 8 gibt es bei 7 Kanälen nicht
    @Test
    fun offsetAboveChannelCountIsRejected() {
        val type = typeWith(count = 7, sharedOffsets = listOf(8))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Positionen zählen ab 1, die 0 ist ungültig
    @Test
    fun offsetZeroIsRejected() {
        val type = typeWith(sharedOffsets = listOf(0))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Der Feinkanal liegt auf Position 9, der Typ hat aber nur 7 Kanäle
    @Test
    fun fineOffsetAboveChannelCountIsRejected() {
        val type = typeWith(count = 7, fineOfFirstShared = 9)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // ----- Doppelt belegte Positionen -----

    // Zwei gemeinsame Kanäle auf Position 1
    @Test
    fun sameOffsetTwiceInSharedChannelsIsRejected() {
        val type = typeWith(sharedOffsets = listOf(1, 1))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Der gemeinsame Kanal liegt auf Position 2, die schon Zelle 1 gehört
    @Test
    fun sameOffsetInSharedAndCellIsRejected() {
        val type = typeWith(sharedOffsets = listOf(2))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Zelle 2 belegt die Position 4, die schon Zelle 1 gehört
    @Test
    fun sameOffsetInTwoCellsIsRejected() {
        val type = typeWith(cell2Offsets = listOf(4, 6, 7))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Der Feinkanal liegt auf Position 3, die schon ein Kanal von Zelle 1 belegt
    @Test
    fun fineOffsetCollidingWithAnotherChannelIsRejected() {
        val type = typeWith(fineOfFirstShared = 3)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Der Feinkanal liegt auf derselben Position wie sein eigener Hauptkanal
    @Test
    fun fineOffsetEqualToOwnOffsetIsRejected() {
        val type = typeWith(fineOfFirstShared = 1)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // ----- Zellen -----

    // Beide Zellen haben die Nummer 1
    @Test
    fun duplicateCellIndexIsRejected() {
        val type = typeWith(cell2Index = 1)
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Ein Kanal von Zelle 2 liegt auf Position 8, der Typ hat aber nur 7 Kanäle
    @Test
    fun cellOffsetAboveChannelCountIsRejected() {
        val type = typeWith(cell2Offsets = listOf(5, 6, 8))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }
    }

    // Ein Kanal von Zelle 1 liegt auf Position 0
    @Test
    fun cellOffsetZeroIsRejected() {
        val type = typeWith(cell1Offsets = listOf(0, 3, 4))
        assertFailsWith<IllegalArgumentException> { validateShow(showWith(type)) }

    }

    @Test
    fun twoTypesWithCellsOfDifferentSizeAreValid() {
        val small = typeWith()                                                // 7 Kanäle, Positionen bis 7
        val big = typeWith(count = 14, cell2Offsets = listOf(12, 13, 14))     // 14 Kanäle, Positionen bis 14
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
