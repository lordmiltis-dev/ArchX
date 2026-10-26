package dev.archx

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import dev.archx.schema.FaderMoved
import dev.archx.schema.faderMoved
import java.io.File


fun main() {
    // Nachricht mit der generierten Kotlin-DSL erzeugen
    val msg = faderMoved {
        faderId = 3
        value = 0.75f
        touched = true
    }

    // In Bytes umwandeln und anzeigen
    val bytes = msg.toByteArray()
    println("Bytes: " + bytes.joinToString(" ") { "%02x".format(it) })

    // Als Testdatei speichern, damit die C++-Seite sie später lesen kann
    val out = File("../schema/testdata/fader_moved.bin")
    out.parentFile.mkdirs()
    out.writeBytes(bytes)

    // Zur Kontrolle wieder einlesen
    val back = FaderMoved.parseFrom(bytes)
    print(back)
}