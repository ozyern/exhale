/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Windows side: listens for phones, pairs new ones while a code is showing, lets paired ones
 * straight back in, and passes on what they send once they are in.
 *
 * Callbacks arrive on the link's reader thread; the app hops to its own scope from there.
 */
class ConnectHost(
    private val store: PairStore,
    private val selfName: () -> String,
    private val onPeersChanged: (List<PairedDevice>) -> Unit,
    private val onMessage: (peerId: String, ConnectMessage) -> Unit,
    /** A phone just came in (new or back): the app sends it what is playing. */
    private val onPeerJoined: (peerId: String) -> Unit = {},
    /** Where to listen. The apps use the fixed ports; tests pass free ones. */
    private val port: Int = CONNECT_TCP_PORT,
    discoveryPort: Int = CONNECT_DISCOVERY_PORT,
    /** Why links come and go, for a log file; the apps' only window onto a flaky connection. */
    private val log: (String) -> Unit = {},
) {
    /** When each phone's current link was admitted. */
    private val admittedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val links = CopyOnWriteArrayList<Link>()
    private val nonces = HashMap<Link, String>()
    @Volatile private var server: ServerSocket? = null
    private val responder = DiscoveryResponder(discoveryPort) { Beacon(store.selfId, selfName(), port) }

    /** The code on screen while pairing is open, and when it stops working. */
    @Volatile private var pairing: Pair<String, Long>? = null
    private var wrongCodes = 0

    fun start() {
        if (server != null) return
        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        }.getOrNull() ?: return
        server = socket
        responder.start()
        Thread({
            while (server === socket) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.tcpNoDelay = true
                // Phones ping every 20s; a minute of silence is a phone that left the Wi-Fi.
                client.soTimeout = ConnectClient.READ_TIMEOUT_MS
                val link = Link(client, ::handle, ::closed) { l, message, error ->
                    log("could not handle ${message::class.simpleName} from ${l.peerName ?: l.remoteHost}: ${error.javaClass.simpleName}: ${error.message}")
                }
                val nonce = Pairing.newNonce()
                synchronized(nonces) { nonces[link] = nonce }
                links += link
                link.start("exhale-connect-host")
                link.send(ConnectMessage.Hello(store.selfId, selfName(), nonce))
            }
        }, "exhale-connect-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        responder.stop()
        server?.let { server = null; runCatching { it.close() } }
        links.forEach { it.close("this PC stopped listening") }
    }

    /** Opens pairing for two minutes and returns the code to show. */
    fun openPairing(): String {
        val code = Pairing.newCode()
        pairing = code to System.currentTimeMillis() + PAIRING_WINDOW_MS
        wrongCodes = 0
        return code
    }

    fun closePairing() {
        pairing = null
    }

    fun forget(peerId: String) {
        store.forget(peerId)
        links.filter { it.peerId == peerId }.forEach { it.close("forgotten on this PC") }
        onPeersChanged(store.devices())
    }

    /** Ids of the paired phones linked right now. */
    fun connectedIds(): Set<String> = links.mapNotNullTo(HashSet()) { it.peerId }

    fun send(peerId: String, message: ConnectMessage) {
        links.filter { it.peerId == peerId }.forEach { it.send(message) }
    }

    fun broadcast(message: ConnectMessage) {
        links.filter { it.peerId != null }.forEach { it.send(message) }
    }

    private fun handle(link: Link, message: ConnectMessage) {
        val peer = link.peerId
        if (peer != null) {
            if (message is ConnectMessage.Ping) link.send(ConnectMessage.Pong) else onMessage(peer, message)
            return
        }
        val nonce = synchronized(nonces) { nonces[link] } ?: return link.close("no handshake on record")
        when (message) {
            is ConnectMessage.Auth -> {
                val known = store.device(message.deviceId)
                when {
                    known == null -> reject(link, ConnectMessage.Rejected.Reason.UNKNOWN_DEVICE)
                    !Pairing.matches(Pairing.secretProof(known.secret, nonce, message.deviceId), message.proof) ->
                        reject(link, ConnectMessage.Rejected.Reason.BAD_PROOF)
                    else -> {
                        store.save(known.copy(name = message.deviceName, lastHost = link.remoteHost))
                        admit(link, message.deviceId, message.deviceName, ConnectMessage.Welcome())
                    }
                }
            }
            is ConnectMessage.PairRequest -> {
                val open = pairing?.takeIf { System.currentTimeMillis() < it.second }
                when {
                    open == null -> reject(link, ConnectMessage.Rejected.Reason.NOT_PAIRING)
                    !Pairing.matches(Pairing.codeProof(open.first, nonce, message.deviceId), message.proof) -> {
                        // A few tries for a mistyped code, then the code is spent: no guessing it here.
                        if (++wrongCodes >= MAX_WRONG_CODES) pairing = null
                        reject(link, ConnectMessage.Rejected.Reason.WRONG_CODE)
                    }
                    else -> {
                        pairing = null
                        val secret = Pairing.newSecret()
                        store.save(
                            PairedDevice(
                                id = message.deviceId,
                                name = message.deviceName,
                                secret = secret,
                                lastHost = link.remoteHost,
                                pairedAtMs = System.currentTimeMillis(),
                            ),
                        )
                        admit(link, message.deviceId, message.deviceName, ConnectMessage.Welcome(Pairing.seal(secret, open.first, nonce)))
                    }
                }
            }
            else -> Unit // Nothing counts before a phone has said who it is.
        }
    }

    private fun admit(link: Link, id: String, name: String, welcome: ConnectMessage.Welcome) {
        val previous = links.filter { it !== link && it.peerId == id && it.isOpen }
        // The same phone in twice at once (two copies of the app, a reconnect racing the old link):
        // replacing a link admitted moments ago only had the other copy replace this one in turn, over
        // and over. A link that has just come up keeps its place; the newcomer is turned away.
        val since = admittedAt[id]?.let { System.currentTimeMillis() - it }
        if (previous.isNotEmpty() && since != null && since < REPLACE_GRACE_MS) {
            log("turned away a second link from $name ($id) from ${link.remoteHost}: the current one is ${since} ms old")
            link.close("turned away: this phone's current link is still new")
            return
        }
        // One link per phone: a reconnect replaces the one it left behind.
        previous.forEach { it.close("replaced by a newer link") }
        admittedAt[id] = System.currentTimeMillis()
        log("admitted $name ($id) from ${link.remoteHost}" + if (previous.isNotEmpty()) ", replacing ${previous.size} older link(s)" else "")
        link.peerId = id
        link.peerName = name
        link.send(welcome)
        onPeersChanged(store.devices())
        onPeerJoined(id)
    }

    private fun reject(link: Link, reason: ConnectMessage.Rejected.Reason) {
        link.send(ConnectMessage.Rejected(reason))
        link.close("rejected: $reason")
    }

    private fun closed(link: Link) {
        links -= link
        synchronized(nonces) { nonces.remove(link) }
        link.peerId?.let { id ->
            val lived = admittedAt[id]?.let { System.currentTimeMillis() - it }
            log("link from ${link.peerName ?: id} (${link.remoteHost}) closed" + (lived?.let { " after $it ms" } ?: "") + ": ${link.closeReason}")
        }
        if (link.peerId != null) onPeersChanged(store.devices())
    }

    private companion object {
        const val PAIRING_WINDOW_MS = 120_000L
        const val REPLACE_GRACE_MS = 2_000L
        const val MAX_WRONG_CODES = 5
    }
}
