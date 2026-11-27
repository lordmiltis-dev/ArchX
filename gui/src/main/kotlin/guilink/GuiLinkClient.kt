package archx.gui.guilink

import java.net.Socket

/**
 * Verbindung der GUI zum Master (TCP 127.0.0.1, Rahmenformat siehe [FrameCodec]).
 * Gegenstück zum DmxLinkClient aus master/ (gleiche Bauweise), aber in BEIDE Richtungen.
 *
 * Regeln (jede wird von GuiLinkClientTest geprüft):
 *  - [sendCommand] blockiert nie auf dem Netzwerk. Es gibt true zurück, wenn der Befehl zum
 *    Senden vorgemerkt wurde, und false, solange keine Verbindung steht. Befehle ohne Verbindung
 *    werden VERWORFEN (der Master ist die Wahrheit; nach dem Verbinden schickt er eine ShowView).
 *  - Gleiche Regler (patchId + attribute) werden zusammengefasst: pro Regler zählt nur der neueste
 *    Wert. Kein unbegrenzt wachsender Puffer, auch wenn der Master nicht liest.
 *  - Der Netzwerk-Thread verbindet sich selbst und wieder neu, mit [reconnectDelayMs] Pause
 *    zwischen den Versuchen (kein Busy-Loop; die Pause läuft auch bei vielen sendCommand-Aufrufen
 *    voll ab).
 *  - Eingehende Rahmen werden mit [FrameDecoder] zusammengesetzt und mit [parse] gelesen.
 *    Nicht lesbare Nachrichten (parse == null) werden übersprungen, die Verbindung bleibt offen.
 *    Ein Fehler im Framing beendet die Verbindung (danach Wiederverbinden).
 *  - [listener]: onConnected() nach dem Verbinden, onDisconnected() einmal pro verlorener
 *    Verbindung (nur wenn onConnected() vorher kam), onEvent() je Nachricht in Ankunftsreihenfolge.
 *    Wirft der Listener, wird das abgefangen und der Thread läuft weiter. Nach [close] kommen keine
 *    Aufrufe mehr.
 *  - Ein geschlossener Master wird auch im Leerlauf bemerkt (Lesen vom Socket).
 *  - [close] ist idempotent, kehrt zügig zurück (auch beim Verbinden/Warten), schließt den Socket.
 *
 * @param serialize / parse austauschbar, damit Tests ohne Protobuf laufen.
 */
class GuiLinkClient(
    private val port: Int,
    private val listener: GuiLinkListener,
    private val host: String = "127.0.0.1",
    private val serialize: (GuiCommand) -> ByteArray = GuiProto::serialize,
    private val parse: (ByteArray) -> MasterEvent? = GuiProto::parse,
    private val reconnectDelayMs: Long = 500,
) : AutoCloseable {

    // Vorschlag für den Zustand (anpassen, wenn du es anders löst):
    private val lock = Object()
    private val pending = LinkedHashMap<Pair<Int, String>, GuiCommand>()  // neuester Befehl je Regler
    private var started = false
    private var closed = false
    @Volatile private var connected = false
    @Volatile private var socket: Socket? = null
    @Volatile private var worker: Thread? = null

    /** true, solange eine TCP-Verbindung steht (best effort). */
    val isConnected: Boolean get() = connected

    /** @throws IllegalStateException wenn schon gestartet oder geschlossen. */
    fun start() {
        // TODO: Zustand unter lock prüfen und started = true setzen, Daemon-Thread für run() starten.
        TODO("GuiLinkClient.start")
    }

    /**
     * Merkt den Befehl zum Senden (neuester je Regler) und weckt den Sende-Thread.
     * @return false, wenn nicht verbunden (Befehl verworfen).
     * @throws IllegalStateException nach [close].
     */
    fun sendCommand(command: GuiCommand): Boolean {
        // TODO: unter lock: closed -> ISE; !connected -> return false; sonst in pending ablegen
        //       (gleicher Schlüssel ersetzt den alten Eintrag), lock.notifyAll(), return true.
        TODO("GuiLinkClient.sendCommand")
    }

    override fun close() {
        // TODO: Idempotent, darf nicht werfen. closed = true, socket?.close() (weckt blockierte
        //       Reads/Writes/connect), lock.notifyAll(), Thread mit Zeitlimit joinen (z. B. 2 s).
    }

    private fun run() {
        // TODO: Schleife bis closed, pro Durchlauf EIN neues Socket (nach close() nicht wiederverwendbar):
        //   1. connect (Timeout!), socket-Feld setzen, connected = true, listener.onConnected().
        //   2. Reader-Thread: liest Bytes -> FrameDecoder.feed -> parse -> listener.onEvent
        //      (try/catch um den Listener!). read() == -1 oder IOException: Verbindung beenden.
        //      decoder.failed: Verbindung beenden.
        //   3. Writer-Schleife (dieser Thread): auf pending warten (lock.wait), Befehle unter
        //      lock herausnehmen, AUSSERHALB des locks serialisieren und schreiben (ein write je
        //      Rahmen: FrameCodec.encode(serialize(cmd)), flush).
        //   4. Verbindung weg -> connected = false, pending leeren, listener.onDisconnected()
        //      (nur wenn onConnected kam und nicht closed), Socket schließen.
        //   5. Pause bis zum Zeitpunkt (deadline), nicht bis zum nächsten notifyAll:
        //      sendCommand weckt sonst jedes Mal die Wartezeit auf (siehe DmxLinkClient.waitReconnectDelay).
        // Falle: nie unter dem lock schreiben, lesen oder den Listener aufrufen.
    }
}
