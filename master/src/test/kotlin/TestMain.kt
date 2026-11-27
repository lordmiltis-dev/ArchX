// Generates the checked-in serialized example show used by cross-language tests.
package test


import ExampleShow
import patchbuilding.SnapshotCurrentState
import java.io.File


fun main(){
    val show = ExampleShow()
    val snapshot = SnapshotCurrentState({ show.exampleShow() })       // The snapshot provider supplies the example show when export is requested.
    snapshot.exportToBinary(File("../schema/testdata/showfile_example.bin"))
}
