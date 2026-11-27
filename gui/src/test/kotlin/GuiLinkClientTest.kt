package archx.gui

import archx.gui.guilink.FixtureView
import archx.gui.guilink.GuiCommand
import archx.gui.guilink.GuiLinkClient
import archx.gui.guilink.GuiLinkListener
import archx.gui.guilink.MasterEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ---------------------------------------------------------------- Test-Codec (unabhängig von Protobuf)
private val testSerialize: (GuiCommand) -> ByteArray = { c ->
    when (c) {
        is GuiCommand.SetValue -> "S|${c.patchId}|${c.attribute}|${c.value}".toByteArray()
    }
}
private val testParse: (ByteArray) -> MasterEvent? = { p ->
    val parts = String(p).split("|")
    when {
        parts[0] == "C" && parts.size == 4 -> {
            val patch = parts[1].toIntOrNull()
            val value = parts[3].toFloatOrNull()
            if (patch != null && value != null) MasterEvent.ValueChanged(patch, parts[2], value) else null
        }
        parts[0] == "W" && parts.size == 2 -> MasterEvent.ShowView(listOf(FixtureView(1, parts[1], 0, 1, emptyList())))
        else -> null
    }
}

private fun cmd(patch: Int, attr: String, value: Float) = GuiCommand.SetValue(patch, attr, value)

private fun framed(text: String): ByteArray {
    val p = text.toByteArray()
    return byteArrayOf(p.size.toByte(), (p.size shr 8).toByte(), 0, 0) + p
}

private fun awaitTrue(timeoutMs: Long = 3000, cond: () -> Boolean): Boolean {
    val end = System.nanoTime() + timeoutMs * 1_000_000
    while (System.nanoTime() < end) {
        if (cond()) return true
        Thread.sleep(5)
    }
    return cond()
}

/** Liest einen Rahmen als Text; null = sauberes EOF am Rahmenanfang. */
private fun Socket.readFrameText(): String? {
    val input = getInputStream()
    val header = input.readNBytes(4)
    if (header.isEmpty()) return null
    check(header.size == 4) { "Header abgeschnitten" }
    val len = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
    val payload = input.readNBytes(len)
    check(payload.size == len) { "Payload abgeschnitten" }
    return String(payload)
}

/** Liest Rahmen, bis [quietMs] lang nichts mehr kommt (oder EOF). */
private fun Socket.drainTexts(quietMs: Int = 300): List<String> {
    soTimeout = quietMs
    val out = mutableListOf<String>()
    try {
        while (true) out += readFrameText() ?: break
    } catch (_: SocketTimeoutException) {
    } catch (_: IOException) {
    }
    return out
}

private fun Socket.writeRaw(bytes: ByteArray) {
    getOutputStream().write(bytes)
    getOutputStream().flush()
}

internal class TestServer : AutoCloseable {
    val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    fun accept(timeoutMs: Int = 3000): Socket {
        server.soTimeout = timeoutMs
        return server.accept().also { it.soTimeout = 3000 }
    }
    override fun close() = server.close()
}

internal class RecordingListener : GuiLinkListener {
    val connected = AtomicInteger()
    val disconnected = AtomicInteger()
    val events = CopyOnWriteArrayList<MasterEvent>()
    @Volatile var throwOnNextEvent = false

    override fun onConnected() { connected.incrementAndGet() }
    override fun onDisconnected() { disconnected.incrementAndGet() }
    override fun onEvent(event: MasterEvent) {
        if (throwOnNextEvent) {
            throwOnNextEvent = false
            throw IllegalStateException("Listener-Fehler (Absicht)")
        }
        events += event
    }
}

class GuiLinkClientTest {
    private val toClose = mutableListOf<AutoCloseable>()
    private val listener = RecordingListener()

    private fun <T : AutoCloseable> track(c: T): T = c.also { toClose.add(it) }

    private fun client(port: Int, delayMs: Long = 50) = track(
        GuiLinkClient(
            port = port,
            listener = listener,
            serialize = testSerialize,
            parse = testParse,
            reconnectDelayMs = delayMs,
        ),
    )

    @AfterEach
    fun cleanup() {
        toClose.reversed().forEach { runCatching { it.close() } }
        toClose.clear()
    }

    private fun unusedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    /** Startet Client + Server und wartet, bis beide verbunden sind. */
    private fun connectedPair(): Triple<GuiLinkClient, TestServer, Socket> {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        val s = ts.accept()
        assertTrue(awaitTrue { c.isConnected && listener.connected.get() >= 1 }, "Client wurde nicht verbunden")
        return Triple(c, ts, s)
    }

    // ------------------------------------------------------------ Zustand

    @Test
    fun startTwiceOrAfterCloseFails() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        assertFailsWith<IllegalStateException> { c.start() }
        c.close()
        assertFailsWith<IllegalStateException> { c.start() }
    }

    @Test
    fun closeIsIdempotentAndSendAfterCloseFails() {
        val c = client(unusedPort())
        c.close()
        c.close()
        assertFailsWith<IllegalStateException> { c.sendCommand(cmd(1, "Rot", 0.5f)) }
    }

    @Test
    fun sendCommandReturnsFalseWhileNotConnected() {
        val c = client(unusedPort())
        assertFalse(c.sendCommand(cmd(1, "Rot", 0.5f)))     // nicht gestartet
        c.start()
        Thread.sleep(100)                                    // Server gibt es nicht
        assertFalse(c.sendCommand(cmd(1, "Rot", 0.5f)))
        assertFalse(c.isConnected)
    }

    @Test
    fun onConnectedIsCalledAfterConnecting() {
        val (c, _, _) = connectedPair()
        assertTrue(c.isConnected)
        assertEquals(1, listener.connected.get())
        assertEquals(0, listener.disconnected.get())
    }

    // ------------------------------------------------------------ Befehle (GUI -> Master)

    @Test
    fun sendCommandDeliversAFramedCommand() {
        val (c, _, s) = connectedPair()
        assertTrue(c.sendCommand(cmd(7, "Rot", 0.5f)))
        assertEquals(listOf("S|7|Rot|0.5"), s.drainTexts())
    }

    @Test
    fun commandsForTheSameFaderAreCoalescedLatestWins() {
        val (c, _, s) = connectedPair()
        repeat(200) { i -> c.sendCommand(cmd(1, "Rot", i / 200f)) }
        c.sendCommand(cmd(1, "Rot", 1f))
        c.sendCommand(cmd(2, "Blau", 0.25f))
        val got = s.drainTexts()
        assertEquals("S|1|Rot|1.0", got.last { it.startsWith("S|1|Rot") })
        assertEquals(1, got.count { it == "S|2|Blau|0.25" })
        assertTrue(got.size < 100, "keine Zusammenfassung: ${got.size} Nachrichten")
    }

    @Test
    fun sendCommandNeverBlocksEvenIfTheMasterDoesNotRead() {
        val (c, _, _) = connectedPair()          // Server nimmt an, liest aber nie
        val t0 = System.nanoTime()
        repeat(20_000) { i -> c.sendCommand(cmd(i % 4, "Rot", i / 20_000f)) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 2000, "sendCommand hat $ms ms gebraucht - blockiert es auf dem Netzwerk?")
    }

    // ------------------------------------------------------------ Ereignisse (Master -> GUI)

    @Test
    fun eventsAreDeliveredInArrivalOrder() {
        val (_, _, s) = connectedPair()
        s.writeRaw(framed("C|1|Rot|0.25") + framed("W|Par"))     // zwei Rahmen in EINEM Schreiben
        assertTrue(awaitTrue { listener.events.size == 2 })
        assertEquals(MasterEvent.ValueChanged(1, "Rot", 0.25f), listener.events[0])
        assertEquals(MasterEvent.ShowView(listOf(FixtureView(1, "Par", 0, 1, emptyList()))), listener.events[1])
    }

    @Test
    fun aFrameSplitAcrossWritesIsReassembled() {
        val (_, _, s) = connectedPair()
        val frame = framed("C|3|Dimmer|0.5")
        s.writeRaw(frame.copyOfRange(0, 2))          // mitten im Header
        Thread.sleep(80)
        s.writeRaw(frame.copyOfRange(2, 8))          // mitten im Payload
        Thread.sleep(80)
        s.writeRaw(frame.copyOfRange(8, frame.size))
        assertTrue(awaitTrue { listener.events.size == 1 })
        assertEquals(MasterEvent.ValueChanged(3, "Dimmer", 0.5f), listener.events[0])
    }

    @Test
    fun anUnparseableEventIsSkippedAndTheConnectionStaysOpen() {
        val (c, _, s) = connectedPair()
        s.writeRaw(framed("X|kaputt") + framed("C|1|Rot|0.75"))
        assertTrue(awaitTrue { listener.events.size == 1 })
        assertEquals(MasterEvent.ValueChanged(1, "Rot", 0.75f), listener.events[0])
        assertTrue(c.isConnected)
        assertEquals(0, listener.disconnected.get())
    }

    @Test
    fun aListenerExceptionDoesNotKillTheReader() {
        val (_, _, s) = connectedPair()
        listener.throwOnNextEvent = true
        s.writeRaw(framed("C|1|Rot|0.1"))             // Listener wirft
        s.writeRaw(framed("C|1|Rot|0.2"))
        assertTrue(awaitTrue { listener.events.size == 1 }, "zweites Ereignis kam nicht an")
        assertEquals(MasterEvent.ValueChanged(1, "Rot", 0.2f), listener.events[0])
    }

    // ------------------------------------------------------------ Verbindungsabbruch / Wiederverbinden

    @Test
    fun aFramingErrorClosesTheConnectionAndTheClientReconnects() {
        val (_, ts, s) = connectedPair()
        s.writeRaw(byteArrayOf(0, 0, 0, 0))          // Länge 0 = Protokollfehler
        val closed = try {
            s.soTimeout = 3000
            s.getInputStream().read() == -1
        } catch (_: IOException) {
            true
        }
        assertTrue(closed, "Client hat die Verbindung nach dem Framing-Fehler nicht geschlossen")
        val second = ts.accept(3000)                 // verbindet sich von selbst neu
        assertTrue(awaitTrue { listener.connected.get() >= 2 })
        assertTrue(listener.disconnected.get() >= 1)
        second.close()
    }

    @Test
    fun detectsAClosedMasterWhileIdleAndReconnects() {
        val (c, ts, first) = connectedPair()
        first.close()                                // Master "stürzt ab"; kein sendCommand nötig
        assertTrue(awaitTrue { listener.disconnected.get() == 1 }, "onDisconnected kam nicht")
        val second = ts.accept(3000)
        assertTrue(awaitTrue { c.isConnected && listener.connected.get() == 2 })
        assertEquals(1, listener.disconnected.get())  // nur einmal pro verlorener Verbindung
        second.close()
    }

    @Test
    fun connectsLaterWhenTheMasterAppearsAndEarlierCommandsWereDiscarded() {
        val port = unusedPort()
        val c = client(port, delayMs = 50)
        c.start()
        Thread.sleep(200)
        assertFalse(c.sendCommand(cmd(1, "Rot", 0.5f)))      // verworfen
        val server = ServerSocket()
        track(AutoCloseable { server.close() })
        server.reuseAddress = true
        server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
        server.soTimeout = 3000
        val s = server.accept().also { it.soTimeout = 3000 }
        assertTrue(awaitTrue { listener.connected.get() == 1 })
        assertEquals(emptyList(), s.drainTexts(300))         // der alte Befehl taucht nicht auf
    }

    @Test
    fun reconnectDelayPreventsABusyLoop() {
        val ts = track(TestServer())
        val accepts = AtomicInteger()
        val running = AtomicBoolean(true)
        val acceptor = Thread {
            ts.server.soTimeout = 50
            while (running.get()) {
                try {
                    ts.server.accept().close()               // sofort wieder zu
                    accepts.incrementAndGet()
                } catch (_: SocketTimeoutException) {
                } catch (_: IOException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
        val c = client(ts.port, delayMs = 200)
        c.start()
        // Auch ein Dauerfeuer von sendCommand darf die Pause nicht verkürzen:
        val t0 = System.nanoTime()
        while ((System.nanoTime() - t0) < 1_000_000_000L) {
            runCatching { c.sendCommand(cmd(1, "Rot", 0.5f)) }
            Thread.sleep(2)
        }
        running.set(false)
        acceptor.join(1000)
        val n = accepts.get()
        assertTrue(n in 2..10, "Verbindungen in 1 s: $n (erwartet ca. 5 bei 200 ms Pause)")
    }

    // ------------------------------------------------------------ Schließen

    @Test
    fun closeReturnsQuicklyAndClosesTheSocket() {
        val (c, _, s) = connectedPair()
        val t0 = System.nanoTime()
        c.close()
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2000)
        val eof = try {
            s.readFrameText() == null
        } catch (_: IOException) {
            true
        }
        assertTrue(eof)
    }

    @Test
    fun closeWhileStillTryingToConnectDoesNotHang() {
        val c = client(unusedPort(), delayMs = 5000)         // lange Pause: close() muss sie abbrechen
        c.start()
        Thread.sleep(150)
        val t0 = System.nanoTime()
        c.close()
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2000)
    }

    @Test
    fun noListenerCallsAfterClose() {
        val (c, _, s) = connectedPair()
        c.close()
        val connected = listener.connected.get()
        val disconnected = listener.disconnected.get()
        val events = listener.events.size
        runCatching { s.writeRaw(framed("C|1|Rot|0.5")) }
        Thread.sleep(300)
        assertEquals(connected, listener.connected.get())
        assertEquals(disconnected, listener.disconnected.get())
        assertEquals(events, listener.events.size)
    }
}
