package archx.gui.guilink

/**
 * Rahmen auf dem Draht: `[4 Bytes Länge, little-endian, vorzeichenlos][Payload]`, 1..[MAX_PAYLOAD_SIZE].
 * Identisch zu master/.../dmxlink/FrameCodec.kt (Absicht: kein gemeinsames Modul, siehe docs/step-2-5.md;
 * beide Seiten prüfen gegen schema/testdata/dmx_link_vectors.txt).
 */
object FrameCodec {
    const val HEADER_SIZE = 4
    const val MAX_PAYLOAD_SIZE = 65_536

    /**
     * @throws IllegalArgumentException bei leerer Payload oder mehr als [MAX_PAYLOAD_SIZE] Bytes.
     */
    fun encode(payload: ByteArray): ByteArray {
        // TODO: Aus master/src/main/kotlin/dmxlink/FrameCodec.kt übernehmen (Package anpassen).
        TODO("FrameCodec.encode")
    }
}
