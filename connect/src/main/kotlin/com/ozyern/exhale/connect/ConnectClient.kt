/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Where the phone's link to its PC stands. */
sealed class ConnectStatus {
    /** Nothing paired yet: the link waits for [ConnectClient.pair]. */
    data object Unpaired : ConnectStatus()
    data object Searching : ConnectStatus()
    data class Connected(val peer: PairedDevice) : ConnectStatus()
}

/**
 * The phone side. Once paired it keeps a link to its PC by itself: tries where the PC answered last,
 * then asks the Wi-Fi, connects, proves the shared key, and after a drop starts over with a short
 * backoff, so nobody ever has to "connect" again. [nudge] skips the wait (network back, app opened).
 */
class ConnectClient(
    private val store: PairStore,
    private val selfName: () -> String,
    /** The Wi-Fi's broadcast address, or null when not on Wi-Fi (then nothing is tried). */
    private val broadcastAddress: () -> InetAddress?,
    /** Addresses to ask one by one as well, for routers that drop broadcasts (the subnet). */
    private val directAddresses: () -> List<InetAddress> = { emptyList() },
    private val onStatus: (ConnectStatus) -> Unit,
    private val onMessage: (ConnectMessage) -> Unit,
) {
    private val lock = Object()
    @Volatile private var running = false
    @Volatile private var link: Link? = null
    @Volatile var status: ConnectStatus = ConnectStatus.Unpaired
        private set

    fun start() {
        if (running) return
        running = true
        Thread(::loop, "exhale-connect-client").apply { isDaemon = true }.start()
        Thread(::heartbeat, "exhale-connect-heartbeat").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        link?.close()
        nudge()
    }

    /** Try again now rather than after the backoff. */
    fun nudge() = synchronized(lock) { lock.notifyAll() }

    fun send(message: ConnectMessage): Boolean = link?.send(message) == true

    val isConnected: Boolean get() = link?.isOpen == true && link?.peerId != null

    /** PCs answering on this Wi-Fi right now, for the pairing screen. Blocks for about a second. */
    fun discover(): List<Beacon> {
        val direct = directAddresses()
        // UDP first; when nothing answers it, the connection port of every address on the Wi-Fi.
        return DiscoveryFinder.find(broadcastAddress(), direct).ifEmpty { DiscoveryFinder.probe(direct) }
    }

    /**
     * Asks one address the user typed in (the PC shows it in Settings → Connected devices): over UDP,
     * then straight at its connection port, so a typed address works whenever the PC can be reached.
     */
    fun discoverAt(host: String): List<Beacon> {
        val address = runCatching { InetAddress.getByName(host.trim()) }.getOrNull() ?: return emptyList()
        return DiscoveryFinder.find(null, listOf(address), timeoutMs = 1_200)
            .ifEmpty { DiscoveryFinder.probe(listOf(address), timeoutMs = 2_000) }
    }

    /**
     * Pairs with [beacon] using the [code] on its screen. Blocks until the PC answers.
     * Returns null on success, or why it did not work.
     */
    fun pair(beacon: Beacon, code: String): ConnectMessage.Rejected.Reason? {
        val result = AtomicReference<ConnectMessage.Rejected.Reason?>(ConnectMessage.Rejected.Reason.NOT_PAIRING)
        val done = CountDownLatch(1)
        val socket = open(beacon.host, beacon.port) ?: return ConnectMessage.Rejected.Reason.NOT_PAIRING
        var nonce: String? = null
        val pairing = Link(socket, onMessage = { l, message ->
            when (message) {
                is ConnectMessage.Hello -> {
                    if (message.version != CONNECT_PROTOCOL_VERSION) {
                        result.set(ConnectMessage.Rejected.Reason.VERSION); done.countDown(); l.close()
                    } else {
                        nonce = message.nonce
                        l.send(ConnectMessage.PairRequest(store.selfId, selfName(), Pairing.codeProof(code, message.nonce, store.selfId)))
                    }
                }
                is ConnectMessage.Welcome -> {
                    val secret = message.sealedSecret?.let { sealed -> nonce?.let { Pairing.unseal(sealed, code, it) } }
                    if (secret != null) {
                        store.save(PairedDevice(beacon.id, beacon.name, secret, beacon.host, beacon.port, System.currentTimeMillis()))
                        result.set(null)
                    }
                    done.countDown()
                    l.close()
                }
                is ConnectMessage.Rejected -> { result.set(message.reason); done.countDown(); l.close() }
                else -> Unit
            }
        }, onClosed = { done.countDown() })
        pairing.start("exhale-connect-pair")
        done.await(10, TimeUnit.SECONDS)
        pairing.close()
        if (result.get() == null) {
            link?.close() // Drop any link to an older PC; the loop picks the new one up at once.
            nudge()
        }
        return result.get()
    }

    fun forget(peerId: String) {
        store.forget(peerId)
        if (link?.peerId == peerId) link?.close()
        setStatus(if (store.devices().isEmpty()) ConnectStatus.Unpaired else ConnectStatus.Searching)
    }

    private fun loop() {
        var backoffMs = 2_000L
        while (running) {
            val paired = store.devices()
            if (paired.isEmpty()) {
                setStatus(ConnectStatus.Unpaired)
                waitFor(30_000)
                continue
            }
            if (status !is ConnectStatus.Connected) setStatus(ConnectStatus.Searching)
            val connected = connectToAny(paired)
            if (connected != null) {
                backoffMs = 2_000L
                // Hold here until the link drops.
                while (running && connected.isOpen) waitFor(5_000)
                if (link === connected) link = null
                setStatus(if (store.devices().isEmpty()) ConnectStatus.Unpaired else ConnectStatus.Searching)
                continue
            }
            waitFor(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    /** The most recently paired PC first; its last address first, then whatever answers on the Wi-Fi. */
    private fun connectToAny(paired: List<PairedDevice>): Link? {
        // No gate on knowing the Wi-Fi: a stale address fails in a moment, and the phone may not
        // have reported its network yet when a paired PC is right there.
        val ordered = paired.sortedByDescending { it.pairedAtMs }
        for (device in ordered) {
            val host = device.lastHost ?: continue
            authenticate(device, host, device.lastPort)?.let { return it }
        }
        val answering = discover().associateBy { it.id }
        for (device in ordered) {
            val beacon = answering[device.id] ?: continue
            authenticate(device, beacon.host, beacon.port)?.let {
                store.seenAt(device.id, beacon.host, beacon.port)
                return it
            }
        }
        return null
    }

    private fun authenticate(device: PairedDevice, host: String, port: Int): Link? {
        val socket = open(host, port) ?: return null
        val admitted = CountDownLatch(1)
        var ok = false
        val candidate = Link(socket, onMessage = { l, message ->
            if (l.peerId != null) {
                if (message !is ConnectMessage.Pong) onMessage(message)
                return@Link
            }
            when (message) {
                is ConnectMessage.Hello -> {
                    if (message.deviceId != device.id || message.version != CONNECT_PROTOCOL_VERSION) {
                        l.close()
                    } else {
                        l.send(ConnectMessage.Auth(store.selfId, selfName(), Pairing.secretProof(device.secret, message.nonce, store.selfId)))
                    }
                }
                is ConnectMessage.Welcome -> {
                    l.peerId = device.id
                    l.peerName = device.name
                    ok = true
                    admitted.countDown()
                }
                is ConnectMessage.Rejected -> {
                    // The PC no longer knows this phone (forgotten there): stop trying it.
                    if (message.reason == ConnectMessage.Rejected.Reason.UNKNOWN_DEVICE) store.forget(device.id)
                    l.close()
                }
                else -> Unit
            }
        }, onClosed = { admitted.countDown(); nudge() })
        candidate.start("exhale-connect-link")
        admitted.await(6, TimeUnit.SECONDS)
        if (!ok) {
            candidate.close()
            return null
        }
        link = candidate
        setStatus(ConnectStatus.Connected(store.device(device.id) ?: device))
        return candidate
    }

    private fun heartbeat() {
        while (running) {
            Thread.sleep(PING_EVERY_MS)
            link?.takeIf { it.peerId != null }?.send(ConnectMessage.Ping)
        }
    }

    private fun open(host: String, port: Int): Socket? = runCatching {
        Socket().apply {
            connect(InetSocketAddress(host, port), 1_500)
            tcpNoDelay = true
            // The PC answers every ping; a minute of silence is a dead link, not a quiet one.
            soTimeout = READ_TIMEOUT_MS
        }
    }.getOrNull()

    private fun waitFor(ms: Long) = synchronized(lock) { runCatching { lock.wait(ms) } }

    private fun setStatus(next: ConnectStatus) {
        if (next == status) return
        status = next
        onStatus(next)
    }

    companion object {
        const val PING_EVERY_MS = 20_000L
        const val READ_TIMEOUT_MS = 60_000
    }
}
