package patchbuilding

import dev.archx.schema.Showfile
import java.io.File

class SnapshotCurrentState(private val showProvider: () -> Showfile) {




    fun exportToBinary(target: File) {
        val show = showProvider()    // wird erst beim Aufruf berechnet
        val bytes = show.toByteArray()
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        println("${bytes.size} Bytes nach ${target.absolutePath} geschrieben")
        println(show.toString())

    }



}