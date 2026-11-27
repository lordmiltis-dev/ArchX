// Creates a sample FaderMoved message and writes it for the C++ test reader.


import dev.archx.schema.FaderMoved
import dev.archx.schema.faderMoved
import java.io.File


fun main() {
    // Create a sample message with the generated Kotlin DSL.
    val msg = faderMoved {
        faderId = 3
        value = 0.75f
        touched = true
    }

    // Serialize the message and print its bytes.
    val bytes = msg.toByteArray()
    println("Bytes: " + bytes.joinToString(" ") { "%02x".format(it) })

    // Write a fixture that the C++ test reader can load.
    val out = File("../schema/testdata/fader_moved.bin")
    out.parentFile.mkdirs()
    out.writeBytes(bytes)

    // Read the serialized bytes back for verification.
    val back = FaderMoved.parseFrom(bytes)
    print(back)


}
