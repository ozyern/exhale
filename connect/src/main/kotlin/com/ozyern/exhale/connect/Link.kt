/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One open connection: messages out with [send], in on a reader thread through [onMessage].
 * [onClosed] runs once, however the link ends.
 */
class Link(
    private val socket: Socket,
    private val onMessage: (Link, ConnectMessage) -> Unit,
    private val onClosed: (Link) -> Unit,
) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    val remoteHost: String get() = socket.inetAddress?.hostAddress.orEmpty()

    /** Set once the other side has proved who it is. */
    @Volatile var peerId: String? = null
    @Volatile var peerName: String? = null

    fun start(threadName: String) {
        Thread({
            try {
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.length > MAX_LINE) break
                    ConnectCodec.decode(line)?.let { onMessage(this, it) }
                }
            } catch (_: Exception) {
                // A dropped socket ends the link the same way a clean close does.
            } finally {
                close()
            }
        }, threadName).apply { isDaemon = true }.start()
    }

    fun send(message: ConnectMessage): Boolean {
        if (closed.get()) return false
        return try {
            synchronized(writeLock) {
                writer.write(ConnectCodec.encode(message))
                writer.write("\n")
                writer.flush()
            }
            true
        } catch (_: Exception) {
            close()
            false
        }
    }

    val isOpen: Boolean get() = !closed.get()

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        onClosed(this)
    }

    private companion object {
        /** A queue of a few hundred songs fits many times over; anything longer is not ours. */
        const val MAX_LINE = 2_000_000
    }
}
