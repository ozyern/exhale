/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * One Exhale at a time. The first to start holds a lock file; a second start (a double-click too
 * many, a pinned taskbar icon pressed again) asks the running one to come to the front, says so,
 * and quits instead of opening another window with its own player playing over the first.
 */
object SingleInstance {
    /** Loopback only: nothing off this PC can reach it. */
    private const val PORT = 47825

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    private val _showRequests = MutableStateFlow(0)
    /** Bumped each time another start asked this window to show itself. */
    val showRequests: StateFlow<Int> = _showRequests.asStateFlow()

    /** True when this is the only Exhale running (and now holds the lock). */
    fun acquire(): Boolean {
        val dir = File(System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home")), "Exhale")
        return runCatching {
            dir.mkdirs()
            val opened = RandomAccessFile(File(dir, "instance.lock"), "rw").channel
            val held = opened.tryLock()
            if (held == null) {
                opened.close()
                false
            } else {
                channel = opened
                lock = held
                listen()
                true
            }
        }.getOrDefault(true) // If the lock itself fails, never stop Exhale from starting.
    }

    /** Asks the running Exhale to bring its window forward. */
    fun showRunning() {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 1_000)
                socket.getOutputStream().write("show\n".toByteArray())
            }
        }
    }

    private fun listen() {
        val server = runCatching { ServerSocket(PORT, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        Thread({
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.use {
                        it.soTimeout = 1_000
                        val line = it.getInputStream().bufferedReader().readLine()
                        if (line?.trim() == "show") _showRequests.value++
                    }
                }
            }
        }, "exhale-single-instance").apply { isDaemon = true }.start()
    }
}
