package archx.master.dmxlink

import java.util.TreeMap
import java.util.TreeSet

/**
 * Sendet DMX-Rahmen vom Master an den DMX-Submaster (TCP 127.0.0.1, siehe docs/step-2-4.md).
 *
 * Regeln (jede wird von DmxLinkClientTest geprüft):
 *  - [sendFrame] blockiert nie auf dem Netzwerk: es validiert, KOPIERT die Daten, merkt sie
 *    sich als "letzter Rahmen dieses Universums" und weckt den Sende-Thread. Kein
 *    unbegrenzt wachsender Puffer: pro Universum zählt nur der neueste Rahmen.
 *  - Der Sende-Thread verbindet sich selbst und wieder neu, mit [reconnectDelayMs] Pause
 *    zwischen den Versuchen (kein Busy-Loop).
 *  - Nach JEDEM (Wieder-)Verbinden werden zuerst die letzten Rahmen ALLER Universen
 *    gesendet (aufsteigend nach Universum), damit ein neu gestarteter Submaster sofort den
 *    vollen Stand hat. Danach nur noch geänderte Universen (ebenfalls aufsteigend).
 *  - Ein geschlossener Peer wird auch im Leerlauf bemerkt (ohne neues sendFrame), sonst
 *    bliebe der letzte Rahmen nach einem Submaster-Neustart verloren.
 *  - [close] ist idempotent, kehrt zügig zurück (auch während Verbinden/Warten) und
 *    schließt den Socket.
 *
 * Aufbau auf dem Draht: [FrameCodec.encode] angewendet auf `serializer(universe, data)`.
 *
 * @param serializer austauschbar, damit Tests ohne Protobuf laufen.
 */
class DmxLinkClient(
    private val port: Int,
    private val host: String = "127.0.0.1",
    private val serializer: (universe: Int, data: ByteArray) -> ByteArray = DmxFrameProto::serialize,
    private val reconnectDelayMs: Long = 500,
) : AutoCloseable {

    // Vorschlag für den Zustand (anpassen, wenn du es anders löst):
    private val lock = Object()
    private val latest = TreeMap<Int, ByteArray>()   // letzter Rahmen je Universum (Kopie)
    private val dirty = TreeSet<Int>()               // seit dem letzten Senden geändert
    private var started = false
    private var closed = false
    @Volatile private var connected = false
    private var worker: Thread? = null

    /** true, solange eine TCP-Verbindung steht (best effort, nur zur Anzeige). */
    val isConnected: Boolean get() = connected

    /**
     * Startet den Sende-Thread (Daemon-Thread, damit er die JVM nicht am Beenden hindert).
     *
     * @throws IllegalStateException wenn schon gestartet oder geschlossen.
     */
    fun start() {
        // TODO: Zustand prüfen (ISE), Thread anlegen und starten, der run() ausführt.
        TODO("DmxLinkClient.start")
    }

    /**
     * Merkt den Rahmen und kehrt sofort zurück. Darf VOR [start] aufgerufen werden
     * (der Rahmen wird nach dem ersten Verbinden gesendet).
     *
     * @throws IllegalArgumentException [universe] nicht in 0..32767 oder [data] nicht 512 Bytes.
     * @throws IllegalStateException nach [close].
     */
    fun sendFrame(universe: Int, data: ByteArray) {
        // TODO: 1. Argumente prüfen (zuerst, auch wenn geschlossen? -> Reihenfolge ist
        //          egal, beides wird getestet, aber nicht zusammen).
        //       2. data.copyOf(), unter lock in latest ablegen, universe in dirty,
        //          lock.notifyAll().
        TODO("DmxLinkClient.sendFrame")
    }

    override fun close() {
        // TODO: Idempotent. closed = true, Socket schließen (weckt blockierte Reads/Writes),
        //       lock.notifyAll(), Thread joinen (mit Zeitlimit, z.B. 2 s).
        //       Darf nicht werfen, auch nicht beim zweiten Aufruf.
    }

    private fun run() {
        // TODO: Schleife bis closed:
        //   1. Socket verbinden (127.0.0.1:port, Timeout setzen). Fehler -> reconnectDelayMs
        //      warten (lock.wait(reconnectDelayMs), damit close() es aufwecken kann).
        //   2. connected = true; ALLE latest-Rahmen senden (dirty entsprechend leeren).
        //   3. Warten auf dirty ODER auf EOF vom Peer. Tipp: ein zweiter kleiner Thread
        //      (oder socket.soTimeout + read) liest vom Socket; read() == -1 bedeutet,
        //      der Submaster hat zu gemacht -> Verbindung abbauen, zu 1.
        //   4. Geänderte Universen aufsteigend senden: FrameCodec.encode(serializer(u, data)),
        //      in EINEN write-Aufruf, dann flush().
        //   5. Bei IOException: connected = false, Socket schließen, zurück zu 1.
        //      Der Rahmen, bei dem es schiefging, bleibt in latest (wird beim
        //      nächsten Verbinden ohnehin erneut gesendet).
        //   6. In finally: connected = false.
        // Achtung Falle: Nicht unter dem lock schreiben/lesen, sonst blockiert sendFrame.
    }
}
