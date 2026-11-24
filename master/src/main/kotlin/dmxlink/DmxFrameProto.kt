package archx.master.dmxlink

/**
 * Serialisiert archx.dmxlink.DmxFrame (schema/dmx_link.proto).
 *
 * TODO: Das Schema in den Gradle-Build einbinden (protobuf-gradle-plugin,
 *       Ausgabe in build/, nicht ins Repo - ADR 0003) und hier den GENERIERTEN
 *       Code verwenden. Keine handgeschriebene Wire-Kodierung im Produktcode.
 */
object DmxFrameProto {
    const val UNIVERSE_SIZE = 512
    const val MAX_UNIVERSE = 32_767

    /**
     * Kanonische proto3-Serialisierung ([universe] 0 wird nicht geschrieben).
     *
     * @throws IllegalArgumentException wenn [universe] nicht in 0..[MAX_UNIVERSE] liegt
     *         oder [data] nicht genau [UNIVERSE_SIZE] Bytes hat.
     */
    fun serialize(universe: Int, data: ByteArray): ByteArray {
        // TODO: require(universe in 0..MAX_UNIVERSE), require(data.size == UNIVERSE_SIZE)
        // TODO: DmxFrame.newBuilder()....build().toByteArray()
        //       Bei protobuf-kotlin: ByteString.copyFrom(data) kopiert, das ist hier gewollt.
        TODO("DmxFrameProto.serialize")
    }
}
