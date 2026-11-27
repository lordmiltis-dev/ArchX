// Provides helpers for constructing Protobuf-backed fixture, channel, and patch records.
package patchbuilding

import dev.archx.schema.Attribute
import dev.archx.schema.ChannelDef
import dev.archx.schema.FixtureType
import dev.archx.schema.PatchEntry
import dev.archx.schema.Precedence
import dev.archx.schema.SubFixtureDef
import dev.archx.schema.SubFixtureSettings
import dev.archx.schema.channelDef
import dev.archx.schema.fixtureType
import dev.archx.schema.patchEntry
import dev.archx.schema.subFixtureDef
import dev.archx.schema.subFixtureSettings



open class PatchBuilderHelper {


    fun buildPatchEntry(patchName: String, patchId: Int, patchNumber: Int, typeId: Int, patchUniverse: Int, patchAddress: Int, vDimmer: Boolean = false, panInvert: Boolean = false, tiltInvert: Boolean = false, subSettings: List<SubFixtureSettings> = emptyList()):
            PatchEntry = patchEntry {
                id = patchId
                fixtureNumber = patchNumber
                name = patchName
                fixtureTypeId = typeId
                universe = patchUniverse
                address = patchAddress
                virtualDimmer = vDimmer
                invertPan = panInvert
                invertTilt = tiltInvert
                subFixtures += subSettings // Append entries to the generated repeated field rather than assigning a list.
    }

     fun buildSubFixtureSettings(subFIndex: Int, panInvert: Boolean = false, tiltInvert: Boolean = false):
            SubFixtureSettings = subFixtureSettings {
                index = subFIndex
                invertPan = panInvert
                invertTilt = tiltInvert
    }

     fun buildFixtureType(libraryName: String, manufacturerName: String, modeName: String, libraryKey: String, channelCount: Int, sharedChannels: List<ChannelDef> = emptyList(), subFixtureDefs: List<SubFixtureDef> = emptyList(), librarySource: String, libraryRevision: String):
            FixtureType = fixtureType {
                name = libraryName
                manufacturer = manufacturerName
                this.modeName = modeName
                this.libraryKey = libraryKey
                this.channelCount = channelCount
                channels += sharedChannels
                subFixtures += subFixtureDefs
                this.librarySource = librarySource
                this.libraryRevision = libraryRevision
    }

     fun buildChannelDef(offset: Int, attributeEnum: Attribute ,name: String = "" ,fineOffset: Int = 0,  defaultValue: Int = 0,highlightValue: Int = 0, precedence: Precedence = Precedence.PRECEDENCE_UNSPECIFIED):
            ChannelDef = channelDef {
                this.offset = offset
                this.attribute = attributeEnum
                this.name = name
                this.fineOffset = fineOffset
                this.defaultValue = defaultValue
                this.highlightValue = highlightValue
                this.precedence = precedence

    }
     fun buildSubFixtureDef(cellIndex: Int, cellName:String, channelProperties: List<ChannelDef> = emptyList(), posX: Float, posY: Float):
            SubFixtureDef = subFixtureDef {
                index = cellIndex
                name = cellName
                channels +=   channelProperties
                this.posX = posX
                this.posY = posY
    }


}
