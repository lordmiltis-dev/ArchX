// Provides the protobuf wire-format adapter for DMX frames exchanged over the link.

package archx.master.dmxlink

import archx.dmxlink.DmxLink
import com.google.protobuf.ByteString
import java.util.Arrays

/**
 * Serializes archx.dmxlink.DmxFrame (schema/dmx_link.proto).
 */
object DmxFrameProto {
    const val UNIVERSE_SIZE = 512
    const val MAX_UNIVERSE = 32_767



    /**
     * Produces canonical proto3 bytes, omitting [universe] when it has its default value of zero.
     *
     * @throws IllegalArgumentException if [universe] is outside 0..[MAX_UNIVERSE]
     *         or [data] does not contain exactly [UNIVERSE_SIZE] bytes.
     */
    fun serialize(universe: Int, data: ByteArray): ByteArray {
        require(universe in 0..MAX_UNIVERSE){
            "universe must be in 0..$MAX_UNIVERSE, but was $universe"
        }

        require(data.size == UNIVERSE_SIZE){
            "data must contain exactly $UNIVERSE_SIZE bytes, but contained ${data.size}"
        }


        return DmxLink.DmxFrame.newBuilder()
            .setUniverse(universe)
            .setData(ByteString.copyFrom(data))
            .build()
            .toByteArray()

    }
}
