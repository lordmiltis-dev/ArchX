// Encodes payloads with the length-prefixed framing shared by the Kotlin and C++ clients.

package archx.master.dmxlink

/**
 * Wire format shared with the C++ implementation (see frame_codec.h):
 * `[4-byte unsigned little-endian length][payload]`, with length 1..[MAX_PAYLOAD_SIZE].
 */
object FrameCodec {
    const val HEADER_SIZE = 4
    const val MAX_PAYLOAD_SIZE = 65_536

    /**
     * Builds a new array containing the header and payload; it does not share
     * storage with [payload].
     *
     * @throws IllegalArgumentException if the payload is empty or exceeds [MAX_PAYLOAD_SIZE] bytes.
     */
    fun encode(payload: ByteArray): ByteArray {
        require(payload.size in 1..MAX_PAYLOAD_SIZE){
            "payload size must be between 1 and $MAX_PAYLOAD_SIZE"
        }

        val frame = ByteArray(HEADER_SIZE + payload.size)

        for (i in 0 until HEADER_SIZE) {
            frame[i] = ((payload.size shr (8*i)) and  0xFF).toByte()
        }

        payload.copyInto(frame, HEADER_SIZE)

        return frame
    }
}
