package archx.master.dmxlink

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Serializer nur für Tests: 2 Bytes Universum (big-endian) + Daten. Unabhängig von Protobuf. */
private val testSerializer: (Int, ByteArray) -> ByteArray =
    { u, d -> byteArrayOf((u shr 8).toByte(), u.toByte()) + d }

private fun decode(payload: ByteArray): Pair<Int, ByteArray> =
    Pair(((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF), payload.copyOfRange(2, payload.size))

private fun frame(fill: Int) = ByteArray(512) { fill.toByte() }

private fun awaitTrue(timeoutMs: Long = 3000, cond: () -> Boolean): Boolean {
    val end = System.nanoTime() + timeoutMs * 1_000_000
    while (System.nanoTime() < end) {
        if (cond()) return true
        Thread.sleep(5)
    }
    return cond()
}

/** Liest einen Rahmen (4 Bytes Länge LE + Payload). null = Verbindung sauber beendet (EOF am Rahmenanfang). */
private fun Socket.readFrame(): ByteArray? {
    val input = getInputStream()
    val header = input.readNBytes(4)
    if (header.isEmpty()) return null
    check(header.size == 4) { "Header abgeschnitten (${header.size} Bytes)" }
    val len = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8) or
        ((header[2].toInt() and 0xFF) shl 16) or ((header[3].toInt() and 0xFF) shl 24)
    val payload = input.readNBytes(len)
    check(payload.size == len) { "Payload abgeschnitten" }
    return payload
}

/** Liest Rahmen, bis [quietMs] lang nichts mehr kommt (oder EOF). Ergebnis in Ankunftsreihenfolge. */
private fun Socket.drain(quietMs: Int = 300): List<Pair<Int, ByteArray>> {
    soTimeout = quietMs
    val out = mutableListOf<Pair<Int, ByteArray>>()
    try {
        while (true) {
            val p = readFrame() ?: break
            out.add(decode(p))
        }
    } catch (_: SocketTimeoutException) {
        // ruhig geworden
    } catch (_: IOException) {
        // Verbindung weg
    }
    return out
}

private class TestServer : AutoCloseable {
    val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    fun accept(timeoutMs: Int = 3000): Socket {
        server.soTimeout = timeoutMs
        return server.accept().also { it.soTimeout = 3000 }
    }
    override fun close() = server.close()
}

class DmxLinkClientTest {
    private val toClose = mutableListOf<AutoCloseable>()

    private fun <T : AutoCloseable> track(c: T): T = c.also { toClose.add(it) }

    private fun client(port: Int, delayMs: Long = 50, serializer: (Int, ByteArray) -> ByteArray = testSerializer) =
        track(DmxLinkClient(port = port, serializer = serializer, reconnectDelayMs = delayMs))

    @AfterEach
    fun cleanup() {
        toClose.reversed().forEach { runCatching { it.close() } }
        toClose.clear()
    }

    private fun unusedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    // ------------------------------------------------------------ Argumente / Zustand

    @Test
    fun sendFrameRejectsUniverseOutOfRange() {
        val c = client(unusedPort())
        assertFailsWith<IllegalArgumentException> { c.sendFrame(-1, frame(0)) }
        assertFailsWith<IllegalArgumentException> { c.sendFrame(32768, frame(0)) }
        c.sendFrame(0, frame(0))        // Grenzen sind erlaubt
        c.sendFrame(32767, frame(0))
    }

    @Test
    fun sendFrameRejectsWrongDataSize() {
        val c = client(unusedPort())
        for (n in intArrayOf(0, 1, 511, 513, 1024)) {
            assertFailsWith<IllegalArgumentException>("Größe $n") { c.sendFrame(1, ByteArray(n)) }
        }
    }

    @Test
    fun closeIsIdempotentAndSendAfterCloseFails() {
        val c = client(unusedPort())
        c.close()
        c.close()
        assertFailsWith<IllegalStateException> { c.sendFrame(1, frame(0)) }
    }

    @Test
    fun startTwiceOrAfterCloseFails() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        assertFailsWith<IllegalStateException> { c.start() }
        c.close()
        assertFailsWith<IllegalStateException> { c.start() }
    }

    // ------------------------------------------------------------ Draht-Format

    @Test
    fun wireIsLittleEndianLengthPlusSerializerOutput() {
        val ts = track(TestServer())
        var seenUniverse = -1
        var seenData: ByteArray? = null
        val c = client(ts.port, serializer = { u, d ->
            seenUniverse = u
            seenData = d.copyOf()
            byteArrayOf(1, 2, 3)
        })
        c.sendFrame(7, frame(0x5A))
        c.start()
        val s = ts.accept()
        assertContentEquals(byteArrayOf(3, 0, 0, 0, 1, 2, 3), s.getInputStream().readNBytes(7))
        assertEquals(7, seenUniverse)
        assertContentEquals(frame(0x5A), seenData)
    }

    @Test
    fun sendFrameCopiesTheData() {
        val ts = track(TestServer())
        val c = client(ts.port)
        val data = frame(1)
        c.sendFrame(5, data)
        data.fill(9)                          // darf den gemerkten Rahmen nicht verändern
        c.start()
        val got = ts.accept().drain()
        assertEquals(1, got.size)
        assertContentEquals(frame(1), got[0].second)
    }

    // ------------------------------------------------------------ Zusammenfassen und Reihenfolge

    @Test
    fun framesBeforeStartAreCoalescedPerUniverseAndSentAscending() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.sendFrame(3, frame(0xA3))
        c.sendFrame(1, frame(0xB1))
        c.sendFrame(3, frame(0xC3))   // überschreibt 0xA3
        c.sendFrame(1, frame(0xD1))   // überschreibt 0xB1
        c.start()
        val got = ts.accept().drain()
        assertEquals(listOf(1, 3), got.map { it.first })
        assertContentEquals(frame(0xD1), got[0].second)
        assertContentEquals(frame(0xC3), got[1].second)
    }

    @Test
    fun framesSentAfterConnectingAreDelivered() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        val s = ts.accept()
        c.sendFrame(2, frame(0x22))
        val got = s.drain()
        assertEquals(1, got.size)
        assertEquals(2, got[0].first)
        assertContentEquals(frame(0x22), got[0].second)
    }

    @Test
    fun sendFrameNeverBlocksEvenIfTheServerDoesNotRead() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        ts.accept()                      // angenommen, aber es wird nie gelesen
        val t0 = System.nanoTime()
        repeat(20_000) { i -> c.sendFrame(i % 4, frame(i)) }   // ~10 MB, würde jeden Puffer füllen
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 2000, "sendFrame hat $ms ms gebraucht - blockiert er auf dem Netzwerk?")
    }

    // ------------------------------------------------------------ Wiederverbinden

    @Test
    fun connectsLaterWhenTheServerAppearsAfterStart() {
        val port = unusedPort()
        val c = client(port, delayMs = 50)
        c.sendFrame(1, frame(0x11))
        c.start()
        Thread.sleep(200)                                   // Server noch nicht da
        val server = ServerSocket()
        track(AutoCloseable { server.close() })
        server.reuseAddress = true
        server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
        server.soTimeout = 3000
        val got = server.accept().also { it.soTimeout = 3000 }.drain()
        assertEquals(1, got.size)
        assertContentEquals(frame(0x11), got[0].second)
    }

    @Test
    fun detectsAClosedServerWhileIdleAndResendsTheFullState() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.sendFrame(1, frame(0xA1))
        c.sendFrame(2, frame(0xA2))
        c.start()
        val first = ts.accept()
        assertEquals(2, first.drain().size)
        first.close()                                       // Submaster "stürzt ab"
        // KEIN weiteres sendFrame: der Client muss den Abbruch selbst bemerken
        val second = ts.accept(timeoutMs = 3000)
        val got = second.drain().toMap().mapValues { it.value.first().toInt() and 0xFF }
        assertEquals(mapOf(1 to 0xA1, 2 to 0xA2), got)
    }

    @Test
    fun framesSentWhileTheConnectionIsDeadArriveAfterReconnect() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.sendFrame(1, frame(0x01))
        c.sendFrame(2, frame(0x02))
        c.start()
        val first = ts.accept()
        first.drain()
        first.close()
        c.sendFrame(1, frame(0x99))                         // geht evtl. noch in den toten Socket
        val second = ts.accept(timeoutMs = 3000)
        val got = second.drain().toMap().mapValues { it.value.first().toInt() and 0xFF }
        assertEquals(mapOf(1 to 0x99, 2 to 0x02), got)      // jeweils der NEUESTE Stand
    }

    @Test
    fun reconnectDelayPreventsABusyLoop() {
        val ts = track(TestServer())
        val accepts = java.util.concurrent.atomic.AtomicInteger()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val acceptor = Thread {
            ts.server.soTimeout = 50
            while (running.get()) {
                try {
                    ts.server.accept().close()              // sofort wieder zu
                    accepts.incrementAndGet()
                } catch (_: SocketTimeoutException) {
                } catch (_: IOException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
        val c = client(ts.port, delayMs = 200)
        c.sendFrame(1, frame(1))
        c.start()
        Thread.sleep(1000)
        running.set(false)
        acceptor.join(1000)
        val n = accepts.get()
        assertTrue(n in 2..10, "Verbindungen in 1 s: $n (erwartet ca. 5 bei 200 ms Pause)")
    }

    // ------------------------------------------------------------ Zustand und Schließen

    @Test
    fun isConnectedFollowsTheConnection() {
        val ts = track(TestServer())
        val c = client(ts.port)
        assertFalse(c.isConnected)
        c.start()
        val s = ts.accept()
        assertTrue(awaitTrue { c.isConnected }, "isConnected wurde nicht true")
        c.close()
        assertTrue(awaitTrue { !c.isConnected }, "isConnected wurde nach close() nicht false")
        s.close()
    }

    @Test
    fun closeReturnsQuicklyAndClosesTheSocket() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        val s = ts.accept()
        s.soTimeout = 3000
        val t0 = System.nanoTime()
        c.close()
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2000)
        val eof = try {
            s.readFrame() == null
        } catch (_: IOException) {
            true                                            // Reset zählt auch als geschlossen
        }
        assertTrue(eof)
    }

    @Test
    fun closeWhileStillTryingToConnectDoesNotHang() {
        val c = client(unusedPort(), delayMs = 5000)        // lange Pause: close() muss sie abbrechen
        c.start()
        Thread.sleep(150)
        val t0 = System.nanoTime()
        c.close()
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2000)
    }

    @Test
    fun closedClientSendsNothingMore() {
        val ts = track(TestServer())
        val c = client(ts.port)
        c.start()
        val s = ts.accept()
        c.close()
        assertFailsWith<IllegalStateException> { c.sendFrame(1, frame(1)) }
        assertEquals(0, s.drain(200).size)
    }
}
