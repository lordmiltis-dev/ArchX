import Tests.ShowBuilder
import dev.archx.schema.Showfile
import java.io.File


class SnapshotCurrentState {
    val SB = ShowBuilder()
    var currentShow = SB.getShow()
    var currentPatch = SB.getPatch()



    fun exportToBinary(){
        val showBytes = currentShow.toByteArray()
        println("Bytes: " + showBytes.joinToString(" ") { "%02x".format(it) })

        val out = File("../schema/testdata/"+currentShow.name+".bin")
        out.parentFile.mkdirs()
        out.writeBytes(showBytes)

        // Zur Kontrolle wieder einlesen
        val back = Showfile.parseFrom(showBytes)
        print(back)
    }



}