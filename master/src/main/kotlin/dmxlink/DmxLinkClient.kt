// Maintains and streams the latest DMX frames from the master to the submaster.

package archx.master.dmxlink

import archx.dmxlink.DmxLink
import archx.master.dmxlink.DmxFrameProto.MAX_UNIVERSE
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Sends DMX frames from the master to the DMX submaster over TCP.
 *
 * [sendFrame] validates and copies the data, stores only the latest frame per
 * universe, and wakes the sender thread without blocking on network I/O.
 * The sender reconnects with a [reconnectDelayMs] pause between attempts to
 * avoid a busy loop. After every connection, it sends all stored frames in
 * ascending universe order, then sends only changed frames in that order.
 * Peer closure is detected even while idle so the complete state can be
 * restored after a submaster restart. [close] is idempotent and closes the
 * socket promptly, including while connecting or waiting.
 *
 * Wire format: [FrameCodec.encode] applied to `serializer(universe, data)`.
 *
 * @param serializer injectable so tests can run without Protobuf.
 */
class DmxLinkClient(
    private val port: Int,
    private val host: String = "127.0.0.1",
    private val serializer: (universe: Int, data: ByteArray) -> ByteArray = DmxFrameProto::serialize,
    private val reconnectDelayMs: Long = 500,
) : AutoCloseable {

    private val lock = Object()
    private val latest = TreeMap<Int, ByteArray>()   // Latest copied frame per universe.
    private val dirty = TreeSet<Int>()               // Universes changed since the last send.
    private var started = false
    private var closed = false
    @Volatile private var connected = false
    @Volatile private var socket: java.net.Socket? = null
    private var worker: Thread? = null

    /** True while a TCP connection appears to be active; intended for display only. */
    val isConnected: Boolean get() = connected

    /**
     * Starts the sender thread as a daemon so it does not keep the JVM alive.
     *
     * @throws IllegalStateException if the client has already been started or closed.
     */
    fun start() {
        synchronized(lock) {
            check(!closed) { "sender is already closed" }
            check(!started) { "sender is already started" }
            started = true
            worker = Thread(::run).apply {
                isDaemon = true
                start()
            }
        }
    }

    /**
     * Stores a frame and returns immediately. May be called before [start];
     * the frame is sent after the first connection.
     *
     * @throws IllegalArgumentException if [universe] is outside 0..32767 or
     *         [data] does not contain 512 bytes.
     * @throws IllegalStateException if called after [close].
     */
    fun sendFrame(universe: Int, data: ByteArray) {
        require(universe in 0..MAX_UNIVERSE){
            "universe must be in 0..$MAX_UNIVERSE, but was $universe"
        }

        require(data.size == 512){
            "data size must be in 512 range [${512 - data.size}]"
        }

        synchronized(lock) {
            check(!closed) { "sender is already closed" }

            latest[universe] = data.copyOf()
            dirty += universe
            lock.notifyAll()
        }

    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            lock.notifyAll()
        }
        runCatching { socket?.close() }
        worker?.join(2000)
    }
        private fun run() {
            while (!isClosed()) {
                val s = Socket()
                socket = s
                try {
                    s.connect(InetSocketAddress(host, port), 5000)
                    if (isClosed()) break
                    connected = true

                    val peerGone = java.util.concurrent.atomic.AtomicBoolean(false)
                    watchForPeerClose(s, peerGone)

                    sendAllLatest(s)

                    while (true) {
                        val stop = synchronized(lock) {
                            while (!closed && !peerGone.get() && dirty.isEmpty()) {
                                lock.wait()
                            }
                            closed || peerGone.get()
                        }
                        if (stop) break
                        sendDirtyFrames(s)
                    }
                } catch (_: IOException) {
                    // Connection or write failed, or close() closed the socket.
                } finally {
                    connected = false
                    runCatching { s.close() }
                }
                waitReconnectDelay()
            }
            socket = null
        }

        private fun isClosed(): Boolean = synchronized(lock) { closed }

        private fun watchForPeerClose(s: Socket, peerGone: AtomicBoolean) {
            thread(isDaemon = true, name = "dmx-link-reader") {
                try {
                    val input = s.getInputStream()
                    while (input.read() != -1) {
                        // The submaster does not send responses; ignore any received bytes.
                    }
                } catch (_: IOException) {
                    // The socket was closed locally or by the peer.
                }
                peerGone.set(true)
                synchronized(lock) { lock.notifyAll() }
            }
        }

        private fun waitReconnectDelay() {
            val deadline = System.nanoTime() + reconnectDelayMs * 1_000_000
            synchronized(lock) {
                while (!closed) {
                    val remainingMs = (deadline - System.nanoTime()) / 1_000_000
                    if (remainingMs <= 0) break
                    lock.wait(remainingMs)
                }
            }
        }

    private fun sendAllLatest(s: Socket) {
        val frames = synchronized(lock) {
            val copy = latest.map { (u, d) -> u to d }
            dirty.clear()
            copy
        }
        if (frames.isNotEmpty()) writeFrames(s, frames)
    }

    private fun sendDirtyFrames(s: Socket) {
        val frames = mutableListOf<Pair<Int, ByteArray>>()

        synchronized(lock) {
            for (universe in dirty) {
                latest[universe]?.let { data ->
                    frames += universe to data
                }
            }

            dirty.clear()
        }

        if (frames.isEmpty()) {
            return
        }

        writeFrames(s, frames)
    }

    private fun writeFrames(s: Socket, frames: List<Pair<Int, ByteArray>>) {
        val payload = ByteArrayOutputStream()
        for ((universe, data) in frames) {
            payload.write(FrameCodec.encode(serializer(universe, data)))
        }
        val output = s.getOutputStream()
        output.write(payload.toByteArray())
        output.flush()
    }
}
