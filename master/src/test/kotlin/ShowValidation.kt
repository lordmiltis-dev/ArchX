import dev.archx.schema.FixtureType
import dev.archx.schema.Showfile
import dev.archx.schema.copy
import dev.archx.schema.fixtureType

// Prüft, ob eine Show in sich stimmig ist.
// Bei einem Fehler: IllegalArgumentException mit einer verständlichen Meldung.
// Bei einer gültigen Show passiert nichts.



fun validateShow(show: Showfile) {
    var fixtureTypesList = show.fixtureTypesList
    var patchList = show.patchList
    val typesById = show.fixtureTypesList.associateBy { it.id }


    //unknow Type Ids
    for (patch in patchList){
        if (typesById[patch.fixtureTypeId] == null){
            throw IllegalArgumentException("Unknown Fixture Type Id. Found at ${patch.id} with Fixture Type Id ${patch.fixtureTypeId}")
        }
    }

    //duplicate Patch Ids
    for(i in 0..patchList.lastIndex) {
        for (j in 1..patchList.lastIndex) {
                if (patchList[j].id == patchList[i].id) {
                    if(j == i){
                        continue
                    }
                    throw IllegalArgumentException("Duplicate Patch IDs not allowed. Found at ${patchList[i].name} and ${patchList[j].name} with Ids ${patchList[i].id} and ${patchList[j].id}")
                }
        }
    }






    //Double Type Id in fixtureTypesList
    for(i in 0..fixtureTypesList.lastIndex) {
        for (j in 1..fixtureTypesList.lastIndex) {
            if(j == i){
                continue
            }
            if(fixtureTypesList[i].id == fixtureTypesList[j].id){
                throw IllegalArgumentException("Double Fixture Type Ids found: Type: "+ fixtureTypesList[i].id +" exists Multiple Times")
            }
        }
    }





    //Invalid Universe
    for(i in patchList.indices) {
        if(patchList[i].universe <= 0) {
            throw IllegalArgumentException("Invalid patch universe: ${patchList[i].universe} for Patch ${patchList[i].id}")
        }
    }





    //Invalid Address
    for(patch in patchList) {
        if(patch.address <= 0) {
            throw IllegalArgumentException("Invalid patch address: ${patch.address} for Patch ${patch.id}")
        }
    }





    //Max Universe Address reached
    for (patch in patchList) {
        val type = typesById[patch.fixtureTypeId]
            ?: throw IllegalArgumentException("Unknown type ID ${patch.fixtureTypeId} at  Patch ${patch.id}")
        if(patch.address + type.channelCount -1 > 512){
            throw IllegalArgumentException("Patch ${patch.id} address would overextend this Universes Limits")
        }
    }





    //Overlapping Error
    for (i in patchList.indices) {

        val patch1 = patchList[i]
        val type1 = typesById[patch1.fixtureTypeId]
            ?: throw IllegalArgumentException("Unknown type ID ${patch1.fixtureTypeId} at  Patch ${patch1.id}")


        val endAddress1 = patch1.address + type1.channelCount - 1
        val occupiedAddress1 = patch1.address..endAddress1

        for (j in patchList.indices) {
            val patch2 = patchList[j]
            val type2 = typesById[patch2.fixtureTypeId]
                ?: throw IllegalArgumentException("Unknown type ID ${patch2.fixtureTypeId} at  Patch ${patch2.id}")


            if (patch1.universe != patch2.universe) {
                continue
            }
            if (i == j){
                continue
            }
            val endAddress2 = patch2.address + type2.channelCount - 1
            val occupiedAddress2 = patch2.address..endAddress2

            if (occupiedAddress1.first <= occupiedAddress2.endInclusive &&
                occupiedAddress2.first <= occupiedAddress1.endInclusive) {
                throw IllegalArgumentException("Addresses of Patches ${patch1.id} and ${patch2.id} are Overlapping")
            }
        }
    }


    //FixtureType with Zero channels
    for (fixture in fixtureTypesList) {
        if (fixture.channelCount <= 0){
            throw IllegalArgumentException("Invalid fixtures channelCount: ${fixture.channelCount}")
        }
    }





    for (fixture in fixtureTypesList){
       for (channel in fixture.channelsList){
           //Channel Offset cant be smaller than one
           if(channel.offset <= 0){
               throw IllegalArgumentException("channel Offset cant be smaller than One. Found at:${channel.name} with Offset: ${channel.offset} at Fixture: ${fixture.name}")
           }
           //no offset above Channelcount
           if (channel.offset > fixture.channelCount){
               throw IllegalArgumentException("channel Offset cant be bigger than the Channel count. Found at: ${channel.name} with Offset: ${channel.offset} at Fixture: ${fixture.name}")
           }
       }
    }







}

