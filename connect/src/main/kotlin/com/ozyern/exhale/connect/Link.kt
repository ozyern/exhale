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
    /** A message this side could not handle. It is logged and dropped; the link stays up. */
    private val onHandlerError: (Link, ConnectMessage, Throwable) -> Unit = { _, _, _ -> },
) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    val remoteHost: String get() = socket.inetAddress?.hostAddress.orEmpty()

    /** Set once the other side has proved who it is. */
    @Volatile var peerId: String? = null
    @Volatile var peerName: String? = null

    /** Why the link ended, once it has: what a flaky connection is diagnosed from. */
    @Volatile var closeReason: String? = null
        private set

    fun start(threadName: String) {
        Thread({
            var reason = "closed here"
            try {
                while (!closed.get()) {
                    val line = reader.readLine()
                    if (line == null) {
                        reason = "the other side hung up"
                        break
                    }
                    if (line.length > MAX_LINE) {
                        reason = "a message of ${line.length} chars, over the limit"
                        break
                    }
                    val message = ConnectCodec.decode(line) ?: continue
                    // A handler that throws is a bug on this side, not a dead socket: the message is
                    // dropped and the link goes on. Ending the link for it made one bad message into a
                    // reconnect, and the same message on every reconnect into a loop.
                    runCatching { onMessage(this, message) }.onFailure { error ->
                        onHandlerError(this, message, error)
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                reason = "nothing heard for ${socket.soTimeout / 1000} s"
            } catch (error: Exception) {
                if (!closed.get()) reason = "read failed: ${error.javaClass.simpleName}: ${error.message}"
            } finally {
                close(reason)
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
        } catch (error: Exception) {
            close("write failed: ${error.javaClass.simpleName}: ${error.message}")
            false
        }
    }

    val isOpen: Boolean get() = !closed.get()

    fun close(reason: String = "closed here") {
        if (!closed.compareAndSet(false, true)) return
        closeReason = reason
        runCatching { socket.close() }
        onClosed(this)
    }

    private companion object {
        /** A queue of a few hundred songs fits many times over; anything longer is not ours. */
        const val MAX_LINE = 2_000_000
    }
}
