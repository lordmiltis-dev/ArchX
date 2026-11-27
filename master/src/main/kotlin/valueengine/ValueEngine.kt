// Resolves values from multiple sources into complete DMX universe frames.
package dev.archx.master

import patchbuilding.ShowBuilder
import dev.archx.schema.Attribute
import dev.archx.schema.ChannelDef
import dev.archx.schema.PatchEntry
import dev.archx.schema.Precedence
import dev.archx.schema.Showfile

/**
 * Computes DMX frames from a show and the values supplied by control sources.
 * Values range from 0.0 to 1.0; targets are identified by patch, cell, and attribute.
 * Cell 0 refers to shared fixture channels; positive cells use SubFixtureDef.index.
 */
class ValueEngine(val show: Showfile) {
    val attributeSet = mutableSetOf<Attribute>()
    private var updateCounter = 0L
    private val activeValues = mutableMapOf<TargetKey, MutableMap<Int, SourceEntry>>()


    /** Loads the show and clears all values that were previously set. */
    fun load() {
        activeValues.clear()
        updateCounter = 0L
        ShowBuilder().load(show)

    }




    /**
     * Sets a source value, clamped to the normalized range 0.0..1.0.
     * Throws IllegalArgumentException when the patch, cell, or channel attribute is invalid.
     * Virtual-dimmer intensity is allowed even when the fixture has no physical dimmer channel.
     */
    fun setValue(source: Int, patchId: Int, attribute: Attribute, value: Float, cell: Int = 0) {
        val fixtureTypesList = show.fixtureTypesList
        val patchList = show.patchList
        val typesById = show.fixtureTypesList.associateBy { it.id }

        val clampedValue = value.coerceIn(0.0f..1.0f)

        val patch = patchList.find { it.id == patchId }
            ?: throw IllegalArgumentException("Patch with Patch Id $patchId dosent exist")

        val fixture = fixtureTypesList.find { it.id == patch.fixtureTypeId }
            ?: throw IllegalArgumentException("Fixture with Id ${patch.fixtureTypeId} given by Patch dosent exist")

        val target = if (cell == 0){
            fixture.channelsList
        }
        else {
            val subFixture = fixture.subFixturesList.find { it.index == cell }
                ?: throw IllegalArgumentException("Cell $cell does not exist in Patch $patchId.")
            subFixture.channelsList
        }

        val hasAttribute = target.any { it.attribute == attribute }

        val isVirtualDimmer = (cell == 0 && attribute == Attribute.ATTRIBUTE_INTENSITY && patch.virtualDimmer)

        require(hasAttribute || isVirtualDimmer) {
            "Patch $patchId hat in Zelle $cell keinen Kanal für Attribut $attribute."
        }

        val key = TargetKey(patchId, cell, attribute)
        val sourcesForTarget = activeValues.getOrPut(key) { mutableMapOf() }
        sourcesForTarget[source] = SourceEntry(
            value = clampedValue,
            order = ++updateCounter
        )
    }

    /** Removes a source value; clearing a value that was never set is harmless. */
    fun clearValue(source: Int, patchId: Int, attribute: Attribute, cell: Int = 0) {
        val key = TargetKey(patchId, cell, attribute)
        val sourcesForTarget = activeValues[key] ?: return

        sourcesForTarget.remove(source)

        if (sourcesForTarget.isEmpty()) {
            activeValues.remove(key)
        }
    }

    /**
     * Builds a 512-byte frame for one universe; index 0 represents DMX address 1.
     * Universes without patches produce an all-zero frame.
     */
    fun frame(universe: Int): ByteArray {
        val dmxFrame = ByteArray(512)
        val typesById = show.fixtureTypesList.associateBy { it.id }

        val patchesInUniverse = show.patchList.filter { it.universe == universe && it.address in 1..512 }


        for (patch in patchesInUniverse) {
            val fixtureType = typesById[patch.fixtureTypeId] ?: continue

            // Resolve the virtual-dimmer factor: 1.0 when inactive, or 0.0 when active without a value.
            val vDimmerFactor = if (patch.virtualDimmer) {
                resolveValue(
                    patchId = patch.id,
                    cell = 0,
                    attribute = Attribute.ATTRIBUTE_INTENSITY,
                    precedence = Precedence.PRECEDENCE_HTP
                ) ?: 0.0f
            } else {
                1.0f
            }

            // 1. Process channels shared by the fixture (cell = 0).
            for (channel in fixtureType.channelsList) {
                writeChannelToFrame(dmxFrame, patch, channel, cell = 0, vDimmerFactor = vDimmerFactor)
            }

            // 2. Process channels belonging to each cell (cell = subFixture.index).
            for (subFixture in fixtureType.subFixturesList) {
                for (channel in subFixture.channelsList) {
                    writeChannelToFrame(dmxFrame, patch, channel, cell = subFixture.index, vDimmerFactor = vDimmerFactor)
                }
            }
        }

        return dmxFrame


        }

    private fun writeChannelToFrame(
        dmxFrame: ByteArray,
        patch: PatchEntry,
        channel: ChannelDef,
        cell: Int,
        vDimmerFactor: Float
    ) {
        // 1. Select precedence: unspecified intensity uses HTP; other unspecified attributes use LTP.
        val effectivePrecedence = when (channel.precedence) {
            Precedence.PRECEDENCE_HTP -> Precedence.PRECEDENCE_HTP
            Precedence.PRECEDENCE_LTP -> Precedence.PRECEDENCE_LTP
            else -> if (channel.attribute == Attribute.ATTRIBUTE_INTENSITY) {
                Precedence.PRECEDENCE_HTP
            } else {
                Precedence.PRECEDENCE_LTP
            }
        }

        // 2. Resolve the active normalized value from all sources.
        val activeFloat = resolveValue(patch.id, cell, channel.attribute, effectivePrecedence)

        // 3. Apply the virtual dimmer to color channels when enabled.
        val isColorChannel = channel.attribute in setOf(
            Attribute.ATTRIBUTE_RED,
            Attribute.ATTRIBUTE_GREEN,
            Attribute.ATTRIBUTE_BLUE,
            Attribute.ATTRIBUTE_WHITE,
            Attribute.ATTRIBUTE_AMBER,
            Attribute.ATTRIBUTE_UV,
            Attribute.ATTRIBUTE_CYAN,
            Attribute.ATTRIBUTE_MAGENTA,
            Attribute.ATTRIBUTE_YELLOW
        )

        val is16Bit = channel.fineOffset > 0
        val maxRawValue = if (is16Bit) 65535 else 255

        // 4. Convert the final value to an 8-bit or 16-bit integer.
        val rawValue: Int = if (activeFloat != null) {
            val scaledFloat = if (patch.virtualDimmer && isColorChannel) {
                activeFloat * vDimmerFactor
            } else {
                activeFloat
            }
            Math.round(scaledFloat * maxRawValue).coerceIn(0, maxRawValue)
        } else {
            // No active value is set; use the schema default value.
            channel.defaultValue.coerceIn(0, maxRawValue)
        }

        // 5. Write bytes into the 512-byte frame; DMX addresses and channel offsets are one-based.
        val coarseIndex = (patch.address - 1) + (channel.offset - 1)
        if (coarseIndex in 0..511) {
            if (is16Bit) {
                dmxFrame[coarseIndex] = ((rawValue shr 8) and 0xFF).toByte()
                val fineIndex = (patch.address - 1) + (channel.fineOffset - 1)
                if (fineIndex in 0..511) {
                    dmxFrame[fineIndex] = (rawValue and 0xFF).toByte()
                }
            } else {
                dmxFrame[coarseIndex] = (rawValue and 0xFF).toByte()
            }
        }
    }

    private fun resolveValue(
        patchId: Int,
        cell: Int,
        attribute: Attribute,
        precedence: Precedence
    ): Float? {
        val sources = activeValues[TargetKey(patchId, cell, attribute)]
        if (sources.isNullOrEmpty()) return null

        return if (precedence == Precedence.PRECEDENCE_HTP) {
            sources.values.maxOf { it.value }
        } else {
            sources.values.maxBy { it.order }.value
        }
    }

}

private data class TargetKey(
    val patchId: Int,
    val cell: Int,
    val attribute: Attribute
)

private data class SourceEntry(
    val value: Float,
    val order: Long
)


