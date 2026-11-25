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
class DiscoveryResponder(private val beacon: () -> Beacon) {
    @Volatile private var socket: DatagramSocket? = null

    fun start() {
        if (socket != null) return
        val bound = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(CONNECT_DISCOVERY_PORT))
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
 * The phone side: asks the whole Wi-Fi once and collects every Exhale for Windows that answers
 * within [timeoutMs]. [broadcast] is the network's broadcast address (Android knows it from its
 * DHCP lease); 255.255.255.255 reaches the same machines on most home networks.
 */
object DiscoveryFinder {
    fun find(broadcast: InetAddress, timeoutMs: Int = 1_500): List<Beacon> {
        val found = LinkedHashMap<String, Beacon>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                val question = QUESTION.toByteArray(Charsets.UTF_8)
                val deadline = System.currentTimeMillis() + timeoutMs
                var nextAsk = 0L
                val buffer = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    // Asked a few times: one broadcast packet is easily lost on Wi-Fi.
                    if (System.currentTimeMillis() >= nextAsk) {
                        runCatching { socket.send(DatagramPacket(question, question.size, broadcast, CONNECT_DISCOVERY_PORT)) }
                        nextAsk = System.currentTimeMillis() + 400
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
}
