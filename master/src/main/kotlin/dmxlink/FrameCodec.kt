package archx.master.dmxlink

/**
 * Rahmen auf dem Draht (identisch zur C++-Seite, siehe frame_codec.h):
 * `[4 Bytes Länge, little-endian, vorzeichenlos][Payload]`, Länge 1..[MAX_PAYLOAD_SIZE].
 */
object FrameCodec {
    const val HEADER_SIZE = 4
    const val MAX_PAYLOAD_SIZE = 65_536

    /**
     * Baut Header + Payload als NEUES Array (kein Teilen von Speicher mit [payload]).
     *
     * @throws IllegalArgumentException bei leerer Payload oder mehr als [MAX_PAYLOAD_SIZE] Bytes.
     */
    fun encode(payload: ByteArray): ByteArray {
        // TODO: Größe prüfen (require(...)).
        // TODO: Länge Byte für Byte schreiben, niedrigstes Byte zuerst:
        //       (size shr (8 * i)) and 0xFF. Kein ByteBuffer-Zauber nötig,
        //       aber ByteBuffer.order(ByteOrder.LITTLE_ENDIAN) wäre auch ok.
        // Tipp: ByteArray(HEADER_SIZE + payload.size) anlegen und
        //       payload.copyInto(ziel, HEADER_SIZE).
        TODO("FrameCodec.encode")
    }
}
