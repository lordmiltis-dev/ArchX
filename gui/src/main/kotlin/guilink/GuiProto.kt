package archx.gui.guilink

/**
 * Übersetzt zwischen den Datenklassen aus GuiMessages.kt und schema/gui_link.proto.
 *
 * TODO: gui_link.proto ist im Gradle-Build schon eingebunden (build.gradle.kts). Hier den
 *       GENERIERTEN Code verwenden (archx.guilink.proto.*, Kotlin-DSL oder Java-Builder), keine
 *       handgeschriebene Kodierung im Produktcode (ADR 0003).
 */
object GuiProto {
    /**
     * Serialisiert einen Befehl als `GuiToMaster` (kanonisch proto3: Standardwerte weglassen
     * macht protobuf von selbst).
     */
    fun serialize(command: GuiCommand): ByteArray {
        // TODO: when (command) { is GuiCommand.SetValue -> GuiToMaster ... setValue { ... } }
        TODO("GuiProto.serialize")
    }

    /**
     * Liest eine `MasterToGui`-Nachricht.
     *  - kaputte Bytes (abgeschnitten, ungültiger Tag) -> null
     *  - gültig, aber kein oneof-Feld gesetzt (leere Nachricht) -> null
     *  - unbekannte Felder werden von protobuf ignoriert
     */
    fun parse(payload: ByteArray): MasterEvent? {
        // TODO: MasterToGui.parseFrom(payload) in try/catch (InvalidProtocolBufferException -> null);
        //       dann msgCase auswerten (SHOW_VIEW, VALUE_CHANGED, MSG_NOT_SET -> null).
        TODO("GuiProto.parse")
    }
}
