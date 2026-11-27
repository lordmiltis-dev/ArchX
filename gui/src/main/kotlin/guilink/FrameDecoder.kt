package archx.gui.guilink

/**
 * Setzt aus einem TCP-Datenstrom wieder Rahmen zusammen (Gegenstück zu [FrameCodec.encode]).
 * TCP liefert Bytes in beliebigen Stücken: ein Rahmen kann auf mehrere [feed]-Aufrufe verteilt sein,
 * ein Aufruf kann mehrere Rahmen enthalten.
 *
 * Fehlerverhalten (Protokollfehler = Länge 0 oder > [FrameCodec.MAX_PAYLOAD_SIZE]):
 *  - [failed] wird true, sobald der Header komplett ist (nicht erst nach dem Payload).
 *  - Rahmen, die VOR dem Fehler vollständig waren, werden von diesem [feed]-Aufruf noch geliefert.
 *  - Danach liefert [feed] nur noch leere Listen, bis [reset] aufgerufen wird.
 *  - Bei ungültiger Länge wird nie ein Puffer in der angekündigten Größe angelegt.
 * Nicht thread-sicher: ein Decoder pro Verbindung, nur aus einem Thread benutzen.
 */
class FrameDecoder {
    // TODO: Zustand (Vorschlag: Header-Puffer + Zähler, erwartete Länge, ByteArrayOutputStream
    //       oder ByteArray + Position für den Payload, failed-Flag).

    /** true nach einem Protokollfehler. */
    val failed: Boolean get() = TODO("FrameDecoder.failed")

    /** Bytes eines angefangenen, noch unvollständigen Rahmens, Header eingerechnet. 0 an einer Rahmengrenze. */
    val bufferedBytes: Int get() = TODO("FrameDecoder.bufferedBytes")

    /** Verarbeitet `data[offset until offset + length]` und liefert die dabei fertig gewordenen Payloads. */
    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<ByteArray> {
        // TODO: Zwei Zustände: Header sammeln / Payload sammeln, Schleife über die Eingabe.
        //       Gleiche Logik wie FrameDecoder::feed in native/dmx/link/frame_codec.cpp.
        TODO("FrameDecoder.feed")
    }

    /** Verwirft angefangene Daten und den Fehlerzustand (neue Verbindung). */
    fun reset() {
        TODO("FrameDecoder.reset")
    }
}
