package dev.archx.master

import ShowBuilder
import com.google.protobuf.boolValue
import dev.archx.schema.Attribute
import dev.archx.schema.ChannelDef
import dev.archx.schema.PatchEntry
import dev.archx.schema.Precedence
import dev.archx.schema.Showfile
import dev.archx.schema.fixtureType

/**
 * Rechnet aus der Show und den eingestellten Werten die DMX-Rahmen aus.
 *
 * Werte sind Zahlen von 0.0 bis 1.0. Mehrere Quellen (source) dürfen Werte für denselben Parameter
 * liefern. Der Parameter wird bestimmt durch: Patch, Zelle und Attribut.
 *
 * Zelle 0 bedeutet: die gemeinsamen Kanäle des Fixtures. Zelle 1, 2, ... sind die Zellen des Typs
 * (SubFixtureDef.index).
 */
 class ValueEngine(val show: Showfile) {
    val attributeSet = mutableSetOf<Attribute>()
    private var updateCounter = 0L
    private val activeValues = mutableMapOf<TargetKey, MutableMap<Int, SourceEntry>>()


    /** Übernimmt eine Show. Alle bisher eingestellten Werte werden gelöscht. */
    fun load() {
        activeValues.clear()
        updateCounter = 0L
        ShowBuilder().load(show)

    }




    /**
     * Stellt einen Wert ein. Der Wert wird auf 0.0 bis 1.0 begrenzt.
     * Wirft IllegalArgumentException, wenn es den Patch oder die Zelle nicht gibt oder der Patch
     * in diesem Bereich keinen Kanal mit diesem Attribut hat.
     * Ausnahme: ATTRIBUTE_INTENSITY ist bei einem Patch mit virtualDimmer auch ohne Dimmerkanal erlaubt.
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

    /** Entfernt den Wert einer Quelle. Wurde nie ein Wert gesetzt, passiert nichts. */
    fun clearValue(source: Int, patchId: Int, attribute: Attribute, cell: Int = 0) {
        val key = TargetKey(patchId, cell, attribute)
        val sourcesForTarget = activeValues[key] ?: return

        sourcesForTarget.remove(source)

        if (sourcesForTarget.isEmpty()) {
            activeValues.remove(key)
        }
    }

    /**
     * Der Rahmen eines Universums: immer genau 512 Bytes. Index 0 ist die DMX-Adresse 1.
     * Gibt es im Universum keinen Patch, sind alle Bytes 0.
     */
    fun frame(universe: Int): ByteArray {
        val dmxFrame = ByteArray(512)
        val typesById = show.fixtureTypesList.associateBy { it.id }

        val patchesInUniverse = show.patchList.filter { it.universe == universe && it.address in 1..512 }


        for (patch in patchesInUniverse) {
            val fixtureType = typesById[patch.fixtureTypeId] ?: continue

            // Virtual Dimmer Faktor ermitteln (1.0f wenn nicht aktiv; 0.0f wenn aktiv aber kein Wert gesetzt)
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

            // 1. Gemeinsame Kanäle des Fixtures verarbeiten (cell = 0)
            for (channel in fixtureType.channelsList) {
                writeChannelToFrame(dmxFrame, patch, channel, cell = 0, vDimmerFactor = vDimmerFactor)
            }

            // 2. Kanäle der einzelnen Zellen verarbeiten (cell = subFixture.index)
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
        // 1. Precedence bestimmen (PRECEDENCE_UNSPECIFIED -> Intensität ist HTP, alles andere LTP)
        val effectivePrecedence = when (channel.precedence) {
            Precedence.PRECEDENCE_HTP -> Precedence.PRECEDENCE_HTP
            Precedence.PRECEDENCE_LTP -> Precedence.PRECEDENCE_LTP
            else -> if (channel.attribute == Attribute.ATTRIBUTE_INTENSITY) {
                Precedence.PRECEDENCE_HTP
            } else {
                Precedence.PRECEDENCE_LTP
            }
        }

        // 2. Aktiven Wert (0.0f..1.0f) aus den Quellen ermitteln
        val activeFloat = resolveValue(patch.id, cell, channel.attribute, effectivePrecedence)

        // 3. Skalierung durch Virtual Dimmer (nur für Farbkanäle, wenn virtualDimmer aktiv ist)
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

        // 4. Endgültigen Ganzzahlwert (0..255 oder 0..65535) berechnen
        val rawValue: Int = if (activeFloat != null) {
            val scaledFloat = if (patch.virtualDimmer && isColorChannel) {
                activeFloat * vDimmerFactor
            } else {
                activeFloat
            }
            Math.round(scaledFloat * maxRawValue).coerceIn(0, maxRawValue)
        } else {
            // Kein aktiver Wert gesetzt -> defaultValue aus dem Schema verwenden
            channel.defaultValue.coerceIn(0, maxRawValue)
        }

        // 5. In das 512-Byte-Array schreiben (Adresse und Offset sind 1-basiert)
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


