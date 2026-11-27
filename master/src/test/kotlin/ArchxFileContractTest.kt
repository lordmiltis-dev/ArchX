// Verifies that Kotlin can read the .archx container produced by the Rust interpreter.
import patchbuilding.ShowBuilder
import dev.archx.schema.Showfile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

// Contract test: Kotlin reads the .archx file written by the Rust interpreter.
// Generate the file with:
//   cargo run -- pack ../schema/testdata/showfile_example.bin ../schema/testdata/showfile_example.archx
// The generated fixture is checked into the repository.
class ArchxFileContractTest {

    private val file = File("../schema/testdata/showfile_example.archx")
    private val headerLength = 17

    // Header layout: 5-byte magic, 4-byte version, and 8-byte payload length, all little-endian.
    @Test
    fun headerIsAsSpecified() {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("ARCHX", String(bytes, 0, 5, Charsets.US_ASCII))
        assertEquals(1, buffer.getInt(5))
        assertEquals((bytes.size - headerLength).toLong(), buffer.getLong(9))
    }

    // The payload following the header must match the example show.
    @Test
    fun payloadIsTheExampleShow() {
        val bytes = file.readBytes()
        val payload = bytes.copyOfRange(headerLength, bytes.size)

        assertEquals(ExampleShow().exampleShow(), Showfile.parseFrom(payload))
    }

    // The show written by the interpreter must load into the master builder.
    @Test
    fun showFromRustFileCanBeLoadedIntoTheBuilder() {
        val bytes = file.readBytes()
        val payload = bytes.copyOfRange(headerLength, bytes.size)

        val builder = ShowBuilder()
        builder.load(Showfile.parseFrom(payload))

        assertEquals(ExampleShow().exampleShow(), builder.getShow())
    }
}
