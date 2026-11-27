package archx.gui

import archx.gui.guilink.AttributeView
import archx.gui.guilink.FixtureView
import java.io.File

/** Hilfe: liest die Referenzwerte aus schema/testdata/ (vom Arbeitsverzeichnis aus nach oben gesucht). */
internal object TestVectors {
    class FrameVector(val name: String, val payload: ByteArray, val framed: ByteArray)
    class CmdVector(val name: String, val patch: Int, val attr: String, val value: Float, val wire: ByteArray)
    class ViewVector(val name: String, val fixtures: List<FixtureView>, val wire: ByteArray)

    fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }

    private fun locate(name: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "schema/testdata/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("schema/testdata/$name nicht gefunden (vom Arbeitsverzeichnis aus nach oben gesucht)")
    }

    private fun lines(name: String): List<List<String>> =
        locate(name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("|") }

    val frames: List<FrameVector> by lazy {
        lines("dmx_link_vectors.txt").filter { it[0] == "frame" }
            .map { FrameVector(it[1], hex(it[2]), hex(it[3])) }
    }

    private fun cmds(kind: String) = lines("gui_link_vectors.txt").filter { it[0] == kind }
        .map { CmdVector(it[1], it[2].toInt(), it[3], it[4].toFloat(), hex(it[5])) }

    val commands: List<CmdVector> by lazy { cmds("cmd") }
    val changes: List<CmdVector> by lazy { cmds("changed") }

    val views: List<ViewVector> by lazy {
        lines("gui_link_vectors.txt").filter { it[0] == "view" }
            .map { ViewVector(it[1], parseSpec(it[2]), hex(it[3])) }
    }

    /** Fixtures getrennt durch ';', Fixture = patch:name:universe:address:attr=wert,attr=wert ('-' = keine). */
    private fun parseSpec(spec: String): List<FixtureView> =
        if (spec == "-") emptyList() else spec.split(";").map { f ->
            val p = f.split(":")
            FixtureView(
                p[0].toInt(), p[1], p[2].toInt(), p[3].toInt(),
                p[4].split(",").filter { it.isNotEmpty() }.map {
                    val kv = it.split("=")
                    AttributeView(kv[0], kv[1].toFloat())
                },
            )
        }
}
