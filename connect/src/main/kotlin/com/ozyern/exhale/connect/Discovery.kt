/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** What the Windows app answers a phone's "is Exhale here?" with. */
@Serializable
data class Beacon(
    val id: String,
    val name: String,
    val port: Int = CONNECT_TCP_PORT,
    val version: Int = CONNECT_PROTOCOL_VERSION,
) {
    /** Filled in by the finder: the address the answer came from. */
    @kotlinx.serialization.Transient var host: String = ""
}

private const val QUESTION = "EXHALE_CONNECT?"
private val beaconJson = Json { ignoreUnknownKeys = true }

/**
 * The Windows side of finding each other: answers every broadcast question on the Wi-Fi with its
 * [Beacon]. A plain UDP broadcast rather than mDNS, so neither app needs another library, and it
 * works on the home routers that block multicast but pass broadcast.
 */
class DiscoveryResponder(private val port: Int = CONNECT_DISCOVERY_PORT, private val beacon: () -> Beacon) {
    @Volatile private var socket: DatagramSocket? = null

    fun start() {
        if (socket != null) return
        val bound = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        }.getOrNull() ?: return
        socket = bound
        Thread({
            val buffer = ByteArray(512)
            while (socket === bound) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    bound.receive(packet)
                    if (String(packet.data, 0, packet.length, Charsets.UTF_8).trim() != QUESTION) continue
                    val answer = beaconJson.encodeToString(Beacon.serializer(), beacon()).toByteArray(Charsets.UTF_8)
                    bound.send(DatagramPacket(answer, answer.size, packet.address, packet.port))
                } catch (_: Exception) {
                    if (bound.isClosed) break
                }
            }
        }, "exhale-connect-discovery").apply { isDaemon = true }.start()
    }

    fun stop() {
        socket?.let { socket = null; it.close() }
    }
}

/**
 * The phone side: asks the Wi-Fi and collects every Exhale for Windows that answers within
 * [timeoutMs]. [broadcast] is the network's broadcast address; [direct] are addresses asked one by
 * one as well, because plenty of home routers (Jio's among them) drop broadcasts between Wi-Fi
 * clients while letting ordinary packets through. A typed-in PC address is just a [direct] of one.
 */
object DiscoveryFinder {
    fun find(broadcast: InetAddress?, direct: List<InetAddress> = emptyList(), timeoutMs: Int = 2_000): List<Beacon> {
        val found = LinkedHashMap<String, Beacon>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 200
                val question = QUESTION.toByteArray(Charsets.UTF_8)
                fun ask(to: InetAddress) {
                    runCatching { socket.send(DatagramPacket(question, question.size, to, CONNECT_DISCOVERY_PORT)) }
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                var asks = 0
                var nextAsk = 0L
                val buffer = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    // Asked a few times: one UDP packet is easily lost on Wi-Fi.
                    if (asks < 3 && System.currentTimeMillis() >= nextAsk) {
                        broadcast?.let(::ask)
                        runCatching { ask(InetAddress.getByName("255.255.255.255")) }
                        direct.forEach(::ask)
                        asks++
                        nextAsk = System.currentTimeMillis() + 500
                    }
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val beacon = runCatching {
                            beaconJson.decodeFromString(Beacon.serializer(), String(packet.data, 0, packet.length, Charsets.UTF_8))
                        }.getOrNull() ?: continue
                        beacon.host = packet.address.hostAddress
                        found[beacon.id] = beacon
                    } catch (_: SocketTimeoutException) {
                        // Ask again, or stop at the deadline.
                    }
                }
            }
        }
        return found.values.toList()
    }

    /**
     * Every other address on this device's subnet, for [find]'s one-by-one asking. Only for
     * home-sized networks (a /22 or smaller, at most about a thousand addresses).
     */
    fun subnet(self: InetAddress, prefixLength: Int): List<InetAddress> {
        val bytes = self.address
        if (bytes.size != 4 || prefixLength !in 22..30) return emptyList()
        val ip = bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
        val mask = -1 shl (32 - prefixLength)
        val network = ip and mask
        val count = 1 shl (32 - prefixLength)
        return (1 until count - 1).mapNotNull { offset ->
            val host = network + offset
            if (host == ip) null
            else InetAddress.getByAddress(byteArrayOf((host ushr 24).toByte(), (host ushr 16).toByte(), (host ushr 8).toByte(), host.toByte()))
        }
    }
}
