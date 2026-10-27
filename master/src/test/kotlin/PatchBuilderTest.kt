import dev.archx.schema.patchEntry


class PatchBuilderTest {











    private fun PatchEntryBuilder(patchName: String, patchId: Int, patchNumber: Int, typeId: Int, patchUniverse: Int, patchAddress: Int, vDimmer: Boolean) {
        val patchFixture = patchEntry {
            id = patchId;
            fixtureNumber = patchNumber;
            name = patchName;
            fixtureTypeId = fixtureTypeId;
            universe = patchUniverse;
            address = patchAddress;
            virtualDimmer = vDimmer;
        }
    }

    private fun PatchEntryBuilderMovingHeads(patchName: String, patchId: Int, patchNumber: Int, typeId: Int, patchUniverse: Int, patchAddress: Int, vDimmer: Boolean, panInvert: Boolean, tiltInvert: Boolean ) {
        val patchFixture = patchEntry {
            id = patchId;
            fixtureNumber = patchNumber;
            name = patchName;
            fixtureTypeId = fixtureTypeId;
            universe = patchUniverse;
            address = patchAddress;
            virtualDimmer = vDimmer;
            invertPan = panInvert;
            invertTilt = tiltInvert;
        }
    }

}