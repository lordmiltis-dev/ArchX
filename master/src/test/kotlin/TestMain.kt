package test


import ExampleShow
import SnapshotCurrentState
import java.io.File


fun main(){
    val show = ExampleShow()
    val snapshot = SnapshotCurrentState({ show.exampleShow() })       //    die Klasse bekommt ihn hier
    snapshot.exportToBinary(File("../schema/testdata/showfile_example.bin"))
}
