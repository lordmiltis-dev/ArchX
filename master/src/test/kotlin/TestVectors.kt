package archx.master.dmxlink

import java.io.File

/** Hilfe: liest schema/testdata/dmx_link_vectors.txt (gleiche Datei wie die C++-Tests). */
internal object TestVectors {
    class FrameVector(val name: String, val payload: ByteArray, val framed: ByteArray)
    class ProtoVector(val name: String, val universe: Int, val fill: Int, val wire: ByteArray)

    fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }

    private fun locate(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "schema/testdata/dmx_link_vectors.txt")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("schema/testdata/dmx_link_vectors.txt nicht gefunden (vom Arbeitsverzeichnis aus nach oben gesucht)")
    }

    private val lines: List<List<String>> by lazy {
        locate().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("|") }
    }

    val frames: List<FrameVector> by lazy {
        lines.filter { it[0] == "frame" }.map { FrameVector(it[1], hex(it[2]), hex(it[3])) }
    }

    val protos: List<ProtoVector> by lazy {
        lines.filter { it[0] == "proto" }.map { ProtoVector(it[1], it[2].toInt(), it[3].toInt(16), hex(it[4])) }
    }
}
