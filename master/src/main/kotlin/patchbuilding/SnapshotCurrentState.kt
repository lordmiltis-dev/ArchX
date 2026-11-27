// Exports the current show state as a serialized Protobuf snapshot.
package patchbuilding

import dev.archx.schema.Showfile
import java.io.File

class SnapshotCurrentState(private val showProvider: () -> Showfile) {




    fun exportToBinary(target: File) {
        val show = showProvider()    // Evaluate the provider on export so the snapshot reflects the latest show state.
        val bytes = show.toByteArray()
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        println("${bytes.size} Bytes nach ${target.absolutePath} geschrieben")
        println(show.toString())

    }



}
