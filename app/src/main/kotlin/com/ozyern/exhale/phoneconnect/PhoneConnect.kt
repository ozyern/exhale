/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.phoneconnect

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.ozyern.exhale.connect.Beacon
import com.ozyern.exhale.connect.ConnectAction
import com.ozyern.exhale.connect.ConnectClient
import com.ozyern.exhale.connect.ConnectMessage
import com.ozyern.exhale.connect.ConnectStatus
import com.ozyern.exhale.connect.PairStore
import com.ozyern.exhale.connect.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Exhale Connect on the phone: keeps the Wi-Fi link to the paired Exhale for Windows by itself.
 *
 * Pair once from Settings → Connected devices. After that the link comes back on its own whenever
 * the phone is on the same Wi-Fi as the PC (a network change, the app coming back) without anyone
 * pressing connect. The playback service plugs its player in through [attach], so the PC sees what
 * the phone plays and can control it, and either side can hand playback to the other.
 */
object PhoneConnect {
    private val _status = MutableStateFlow<ConnectStatus>(ConnectStatus.Unpaired)
    val status: StateFlow<ConnectStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _pc = MutableStateFlow<ConnectMessage.NowPlaying?>(null)
    /** What the connected PC is playing: drives the phone's remote for it. */
    val pc: StateFlow<ConnectMessage.NowPlaying?> = _pc.asStateFlow()

    /** The playback side: what the phone is playing, and how to obey the PC. */
    interface Player {
        fun nowPlaying(): ConnectMessage.NowPlaying
        fun command(command: ConnectMessage.Command)
        fun handoff(handoff: ConnectMessage.Handoff)
        /** Pauses after handing playback to the PC. */
        fun pauseForHandoff()
    }

    private val main = Handler(Looper.getMainLooper())
    private var library: PhoneLibrarySync? = null
    private var client: ConnectClient? = null
    private var store: PairStore? = null
    @Volatile private var player: Player? = null
    @Volatile private var broadcast: InetAddress? = null
    /** Every other address on the Wi-Fi's subnet, asked one by one when broadcasts are dropped. */
    @Volatile private var subnet: List<InetAddress> = emptyList()

    fun init(context: Context) {
        if (client != null) return
        val app = context.applicationContext
        val pairs = PairStore(app.filesDir)
        store = pairs
        _devices.value = pairs.devices()
        watchWifi(app)
        library = runCatching {
            PhoneLibrarySync(
                directory = app.filesDir,
                database = org.koin.core.context.GlobalContext.get().get<com.ozyern.exhale.db.MusicDatabase>(),
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
                send = { message -> if (client?.isConnected == true) client?.send(message) },
            ).also { it.start() }
        }.getOrNull()
        client = ConnectClient(
            store = pairs,
            selfName = { deviceName(app) },
            broadcastAddress = { broadcast },
            directAddresses = { subnet },
            onStatus = { status ->
                _status.value = status
                _devices.value = pairs.devices()
                if (status is ConnectStatus.Connected) {
                    // Just in: tell the PC what is playing here, and swap libraries.
                    main.post { player?.let { client?.send(it.nowPlaying()) } }
                    library?.let { client?.send(it.snapshot()) }
                } else {
                    _pc.value = null
                }
            },
            onMessage = { message -> main.post { handle(message) } },
        ).also { it.start() }
    }

    /** The playback service plugs in here while it runs. */
    fun attach(player: Player) {
        this.player = player
        if (client?.isConnected == true) client?.send(player.nowPlaying())
    }

    fun detach(player: Player) {
        if (this.player === player) this.player = null
    }

    /** The player changed (song, play/pause, seek, queue): the PC hears about it. */
    fun playerChanged() {
        val p = player ?: return
        if (client?.isConnected == true) client?.send(p.nowPlaying())
    }

    /** Try to reach the PC now rather than after the backoff (the app came back to the front). */
    fun nudge() = client?.nudge()

    suspend fun discover(): List<Beacon> = withContext(Dispatchers.IO) { client?.discover().orEmpty() }

    /** Looks for Exhale at an address the user typed (the PC shows it on its Connected devices page). */
    suspend fun discoverAt(host: String): List<Beacon> = withContext(Dispatchers.IO) { client?.discoverAt(host).orEmpty() }

    /** Pairs with [beacon] using the code on its screen. Null on success. */
    suspend fun pair(beacon: Beacon, code: String): ConnectMessage.Rejected.Reason? = withContext(Dispatchers.IO) {
        val reason = client?.pair(beacon, code.filter(Char::isDigit))
        _devices.value = store?.devices().orEmpty()
        reason
    }

    fun forget(id: String) {
        client?.forget(id)
        _devices.value = store?.devices().orEmpty()
    }

    /** Remote control of the PC. */
    fun commandPc(action: ConnectAction, value: Long? = null) {
        client?.send(ConnectMessage.Command(action, value))
    }

    /** "Continue on PC": the PC takes over this queue where it is, and the phone pauses. */
    fun handOffToPc(): Boolean {
        val p = player ?: return false
        val state = p.nowPlaying()
        if (state.track == null || client?.isConnected != true) return false
        val sent = client?.send(ConnectMessage.Handoff(state.queue, state.index.coerceAtLeast(0), state.positionMs)) == true
        if (sent) p.pauseForHandoff()
        return sent
    }

    private fun handle(message: ConnectMessage) {
        when (message) {
            is ConnectMessage.NowPlaying -> _pc.value = message
            is ConnectMessage.Command -> player?.command(message)
            is ConnectMessage.Handoff -> player?.handoff(message)
            is ConnectMessage.Library -> library?.apply(message)
            else -> Unit
        }
    }

    /**
     * Follows the Wi-Fi: its broadcast address is what the PC is asked on, and a new network is
     * a reason to look for the PC straight away.
     */
    private fun watchWifi(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching {
            connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                    learn(properties)
                    client?.nudge()
                }

                override fun onLost(network: Network) {
                    broadcast = null
                    subnet = emptyList()
                }
            })
        }
        // What the phone is on right now, without waiting for the first callback.
        runCatching {
            connectivity.activeNetwork?.let { network ->
                val caps = connectivity.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                ) connectivity.getLinkProperties(network)?.let(::learn)
            }
        }
    }

    private fun learn(properties: LinkProperties) {
        broadcast = broadcastOf(properties)
        val link = properties.linkAddresses.firstOrNull { it.address is Inet4Address }
        subnet = link?.let { com.ozyern.exhale.connect.DiscoveryFinder.subnet(it.address, it.prefixLength) }.orEmpty()
    }

    private fun broadcastOf(properties: LinkProperties): InetAddress? {
        val link = properties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val address = link.address.address
        val prefix = link.prefixLength.coerceIn(0, 32)
        val ip = address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val bcast = ip or mask.inv()
        return runCatching {
            InetAddress.getByAddress(byteArrayOf((bcast ushr 24).toByte(), (bcast ushr 16).toByte(), (bcast ushr 8).toByte(), bcast.toByte()))
        }.getOrNull()
    }

    private fun deviceName(context: Context): String =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
}
