import dev.archx.schema.Showfile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

// Vertragstest: Kotlin liest die .archx-Datei, die der Rust-Interpreter geschrieben hat.
// Die Datei entsteht mit:
//   cargo run -- pack ../schema/testdata/showfile_example.bin ../schema/testdata/showfile_example.archx
// Sie wird einmal erzeugt und committet.
class ArchxFileContractTest {

    private val file = File("../schema/testdata/showfile_example.archx")
    private val headerLength = 17

    // Kopf: 5 Bytes Kennung, 4 Bytes Version, 8 Bytes Länge (beides niedrigstes Byte zuerst)
    @Test
    fun headerIsAsSpecified() {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("ARCHX", String(bytes, 0, 5, Charsets.US_ASCII))
        assertEquals(1, buffer.getInt(5))
        assertEquals((bytes.size - headerLength).toLong(), buffer.getLong(9))
    }

    // Der Block hinter dem Kopf ist die Beispiel-Show
    @Test
    fun payloadIsTheExampleShow() {
        val bytes = file.readBytes()
        val payload = bytes.copyOfRange(headerLength, bytes.size)

        assertEquals(ExampleShow().exampleShow(), Showfile.parseFrom(payload))
    }

    // Die vom Interpreter gespeicherte Show lässt sich in den Master laden
    @Test
    fun showFromRustFileCanBeLoadedIntoTheBuilder() {
        val bytes = file.readBytes()
        val payload = bytes.copyOfRange(headerLength, bytes.size)

        val builder = ShowBuilder()
        builder.load(Showfile.parseFrom(payload))

        assertEquals(ExampleShow().exampleShow(), builder.getShow())
    }
}
