package patchbuilding

import dev.archx.schema.Showfile


// Prüft, ob eine Show in sich stimmig ist.
// Bei einem Fehler: IllegalArgumentException mit einer verständlichen Meldung.
// Bei einer gültigen Show passiert nichts.



fun validateShow(show: Showfile) {
    val fixtureTypesList = show.fixtureTypesList
    val patchList = show.patchList
    val typesById = show.fixtureTypesList.associateBy { it.id }


    for (fixture in fixtureTypesList) {
        for (channel in fixture.channelsList) {
            //Channel Offset cant be smaller than one
            if (channel.offset <= 0) {
                throw IllegalArgumentException("channel Offset cant be smaller than One. Found at:${channel.name} with Offset: ${channel.offset} at Fixture: ${fixture.name}")
            }
            //no offset above Channel count
            if (channel.offset > fixture.channelCount) {
                throw IllegalArgumentException("channel Offset cant be bigger than the Channel count. Found at: ${channel.name} with Offset: ${channel.offset} at Fixture: ${fixture.name}")
            }
            //no fine Offset above Channel count
            if (channel.fineOffset > fixture.channelCount) {
                throw IllegalArgumentException("channel fine Offset cant be bigger than the Channel count. Found at: ${channel.name} with Offset: ${channel.fineOffset} at Fixture: ${fixture.name}")
            }
            //offset can not be fine offset
            if (channel.fineOffset == channel.offset) {
                throw IllegalArgumentException("fine offset can not be the same as offset. Found at: ${channel.name} with Offset: ${channel.offset} and fine offset: ${channel.fineOffset}")
            }

        }


        //duplicate shared Channels are not allowed
        val allChannels = fixture.channelsList + fixture.subFixturesList.flatMap { it.channelsList }
        val seen = mutableSetOf<Int>()
        for (n in allChannels) {
            if (seen.contains(n.offset)) {
                throw IllegalArgumentException("Duplicate shared channel offsets are not allowed. Found at ${n.name +", "+ fixture.name} with Value ${n.offset}")
            } else if (n.fineOffset > 0 && seen.contains(n.fineOffset)) {
                throw IllegalArgumentException("Duplicate shared channels fine offsets are not allowed. Found at ${n.name +", "+ fixture.name} with Value ${n.fineOffset}")
            } else {
                seen.add(n.offset);
                if (n.fineOffset > 0){
                    seen.add(n.fineOffset);
                }
            }
        }


        val seenCells = mutableSetOf<Int>()
        for (cell in fixture.subFixturesList){
            //duplicate cell indexes
            if (seenCells.contains(cell.index)){
                throw IllegalArgumentException("Duplicate cell Indexes are not allowed. Found at ${cell.name} with Value ${cell.index}")
            }
            else{
                seenCells.add(cell.index);
            }


        }

        //general Checks
        for (channel in allChannels) {
            if (channel.offset <= 0) { throw IllegalArgumentException("Channel offset cant be smaller than 0")}
            if (channel.offset > fixture.channelCount) { throw IllegalArgumentException()}
            if (channel.fineOffset < 0) { throw IllegalArgumentException("Channel fine offset cant be smaller than 0")}
            if (channel.fineOffset > fixture.channelCount) { throw IllegalArgumentException() }
            if (channel.fineOffset != 0 && channel.fineOffset == channel.offset) { throw IllegalArgumentException() }
        }



    }


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
}

