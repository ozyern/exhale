/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.integrations

import com.ozyern.exhale.connect.ConnectAction
import com.ozyern.exhale.connect.ConnectHost
import com.ozyern.exhale.connect.ConnectMessage
import com.ozyern.exhale.connect.ConnectTrack
import com.ozyern.exhale.connect.LibrarySync
import com.ozyern.exhale.connect.PlayEntry
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.connect.PairStore
import com.ozyern.exhale.connect.PairedDevice
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Exhale Connect on Windows: the phone side of the Wi-Fi link lands here. Pair once from Settings →
 * Connected devices; after that the phone finds this PC and connects by itself whenever both are on
 * the same Wi-Fi. A connected phone sees what is playing here and can control it, and either side
 * can hand what it is playing over to the other.
 */
object DesktopConnect {
    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    /** Every phone this PC has paired with. */
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of the paired phones linked right now. */
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    /** The code to show while pairing is open. */
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _phone = MutableStateFlow<Pair<String, ConnectMessage.NowPlaying>?>(null)
    /** What the connected phone is playing, and which phone: drives the PC's remote for it. */
    val phone: StateFlow<Pair<String, ConnectMessage.NowPlaying>?> = _phone.asStateFlow()

    private var host: ConnectHost? = null
    private var library: LibrarySync? = null
    /** The history's newest song as last seen, so a change at the top reads as a new play. */
    @Volatile private var historyHead: String? = null
    private var player: DesktopPlayer? = null
    private var scope: CoroutineScope? = null

    fun start(player: DesktopPlayer, scope: CoroutineScope) {
        if (host != null) return
        this.player = player
        this.scope = scope
        val store = PairStore(dataDir())
        val sync = LibrarySync(dataDir())
        library = sync
        _devices.value = store.devices()
        val created = ConnectHost(
            store = store,
            selfName = ::pcName,
            onPeersChanged = { devices ->
                _devices.value = devices
                _connected.value = host?.connectedIds().orEmpty()
                val phone = _phone.value
                if (phone != null && phone.first !in _connected.value) _phone.value = null
            },
            onMessage = { peerId, message -> scope.launch(Dispatchers.Main) { handle(peerId, message) } },
            onPeerJoined = { peerId ->
                // The code is spent the moment a phone pairs with it.
                _pairingCode.value = null
                host?.send(peerId, nowPlaying(player))
                // Likes and history both ways: everything once on arrival, then changes as they happen.
                host?.send(peerId, sync.snapshot())
            },
        )
        host = created
        scope.launch(Dispatchers.IO) { created.start() }

        // What is playing, sent when it changes: the song, play/pause, a seek, the queue, the volume.
        // The phone carries the position forward between messages from [ConnectMessage.NowPlaying.sentAtMs].
        scope.launch {
            player.state
                .map { s -> Snapshot(s.current?.id, s.isPlaying, s.seekGeneration, s.volume, s.index, s.queue.size) }
                .distinctUntilChanged()
                .collect { sendState() }
        }
        // Likes: whatever changed here since last time is told to the phone.
        scope.launch(Dispatchers.IO) {
            DesktopPrefs.favorites.collect { favorites ->
                val changes = sync.localLikes(favorites.map { it.toTrack() })
                if (changes.isNotEmpty()) host?.broadcast(ConnectMessage.Library(likes = changes))
            }
        }
        // Plays: a new song at the top of history is a play here.
        scope.launch(Dispatchers.IO) {
            DesktopPrefs.history.collect { history ->
                val head = history.firstOrNull() ?: return@collect
                if (head.id == historyHead) return@collect
                val first = historyHead == null
                historyHead = head.id
                if (first) return@collect // What was there at launch is not a new play.
                val plays = sync.localPlays(listOf(PlayEntry(head.toTrack(), System.currentTimeMillis())))
                if (plays.isNotEmpty()) host?.broadcast(ConnectMessage.Library(plays = plays))
            }
        }

        // And now and then while playing, so a drift between the two clocks never builds up.
        scope.launch {
            while (true) {
                delay(15_000)
                if (player.state.value.isPlaying) sendState()
            }
        }
    }

    fun stop() {
        host?.stop()
        host = null
    }

    /** Opens pairing: shows a code for two minutes. */
    fun startPairing() {
        _pairingCode.value = host?.openPairing()
        scope?.launch {
            delay(120_000)
            _pairingCode.value = null
        }
    }

    fun cancelPairing() {
        host?.closePairing()
        _pairingCode.value = null
    }

    fun forget(id: String) {
        host?.forget(id)
        _connected.value = host?.connectedIds().orEmpty()
    }

    /**
     * This PC's addresses on its local networks, for the phone to type in when the Wi-Fi drops the
     * broadcast it would otherwise find the PC with. Physical adapters only: no VPN or virtual ones.
     */
    fun localAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .filterNot { iface ->
                val name = (iface.displayName + " " + iface.name).lowercase()
                listOf("virtual", "vmware", "hyper-v", "vethernet", "wsl", "tap", "tun", "vpn", "vgate", "loopback", "bluetooth").any { it in name }
            }
            .flatMap { iface -> iface.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    /** The connected phone's name, or null when none is linked: names the "Continue on" item. */
    fun connectedPhoneName(): String? {
        val id = _connected.value.firstOrNull() ?: return null
        return _devices.value.firstOrNull { it.id == id }?.name ?: "phone"
    }

    /** Remote control of the phone from the PC. */
    fun commandPhone(action: ConnectAction, value: Long? = null) {
        val id = _phone.value?.first ?: _connected.value.firstOrNull() ?: return
        host?.send(id, ConnectMessage.Command(action, value))
    }

    /** "Continue on phone": the phone takes over this queue where it is, and this PC pauses. */
    fun handOffToPhone(): Boolean {
        val player = player ?: return false
        val id = _connected.value.firstOrNull() ?: return false
        val state = player.state.value
        if (state.current == null) return false
        host?.send(
            id,
            ConnectMessage.Handoff(
                queue = state.queue.map { it.toTrack() },
                index = state.index,
                positionMs = (player.position.value * 1000).toLong(),
            ),
        )
        if (state.isPlaying) player.togglePlayPause()
        return true
    }

    private fun handle(peerId: String, message: ConnectMessage) {
        val player = player ?: return
        when (message) {
            is ConnectMessage.Command -> command(player, message)
            is ConnectMessage.Handoff -> {
                val songs = message.queue.map { it.toSongItem() }
                if (songs.isEmpty()) return
                player.play(songs, message.index.coerceIn(0, songs.lastIndex), message.positionMs / 1000.0)
                if (!message.play) scope?.launch { delay(1_500); if (player.state.value.isPlaying) player.togglePlayPause() }
            }
            is ConnectMessage.NowPlaying -> _phone.value = peerId to message
            is ConnectMessage.Library -> applyLibrary(message)
            else -> Unit
        }
    }

    /** Brings this PC's likes and history in line with what the phone sent. */
    private fun applyLibrary(message: ConnectMessage.Library) {
        val sync = library ?: return
        val merge = sync.merge(message)
        if (merge.isEmpty) return
        merge.like.forEach { track -> if (!DesktopPrefs.isFavorite(track.id)) DesktopPrefs.toggleFavorite(track.toSongItem()) }
        merge.unlike.forEach { id -> DesktopPrefs.favorites.value.firstOrNull { it.id == id }?.let(DesktopPrefs::toggleFavorite) }
        if (merge.plays.isNotEmpty()) {
            // History in play order across both devices; what was here before pairing follows it.
            // This PC's own record of a song where it has one (it carries artist and album ids).
            val own = DesktopPrefs.history.value.associateBy { it.id }
            val timed = sync.recentPlays().map { own[it.track.id] ?: it.track.toSongItem() }
            val timedIds = timed.mapTo(HashSet()) { it.id }
            val rebuilt = timed + DesktopPrefs.history.value.filterNot { it.id in timedIds }
            // The new top of history came from the phone: not a play to report back.
            historyHead = rebuilt.firstOrNull()?.id
            DesktopPrefs.replaceHistory(rebuilt)
        }
    }

    private fun command(player: DesktopPlayer, command: ConnectMessage.Command) {
        val state = player.state.value
        when (command.action) {
            ConnectAction.PLAY -> if (!state.isPlaying) player.togglePlayPause()
            ConnectAction.PAUSE -> if (state.isPlaying) player.togglePlayPause()
            ConnectAction.TOGGLE -> player.togglePlayPause()
            ConnectAction.NEXT -> player.next()
            ConnectAction.PREVIOUS -> player.previous()
            ConnectAction.SEEK -> command.value?.let { player.seekTo(it / 1000.0) }
            ConnectAction.VOLUME -> command.value?.let { player.setVolume(it.toInt().coerceIn(0, 100)) }
            // Counted from the playing song, as the queue in [nowPlaying] is.
            ConnectAction.SKIP_TO -> command.value?.let { player.skipTo(state.index + it.toInt()) }
        }
    }

    private fun sendState() {
        val player = player ?: return
        host?.broadcast(nowPlaying(player))
    }

    private fun nowPlaying(player: DesktopPlayer): ConnectMessage.NowPlaying {
        val state: PlayerState = player.state.value
        return ConnectMessage.NowPlaying(
            track = state.current?.toTrack(),
            playing = state.isPlaying,
            positionMs = (player.position.value * 1000).toLong(),
            volume = state.volume,
            // The phone shows what is up next; a few dozen is plenty and keeps the message small.
            queue = state.queue.drop(state.index.coerceAtLeast(0)).take(QUEUE_SENT).map { it.toTrack() },
            index = 0,
            sentAtMs = System.currentTimeMillis(),
        )
    }

    private data class Snapshot(val id: String?, val playing: Boolean, val seek: Int, val volume: Int, val index: Int, val size: Int)

    private fun pcName(): String = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?.let { name -> name.lowercase().replaceFirstChar { it.uppercase() } } ?: "Exhale for Windows"

    private fun dataDir(): File = File(
        System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home")),
        "Exhale",
    )

    private const val QUEUE_SENT = 50
}

internal fun SongItem.toTrack() = ConnectTrack(
    id = id,
    title = title,
    artists = artists.joinToString { it.name },
    album = album?.name,
    artworkUrl = thumbnail,
    durationMs = (duration ?: 0) * 1000L,
)

internal fun ConnectTrack.toSongItem() = SongItem(
    id = id,
    title = title,
    artists = artists.split(", ").filter { it.isNotBlank() }.map { Artist(it, null) },
    // No album: the phone sends its name but not its id, and an album page needs the id.
    duration = (durationMs / 1000).toInt().takeIf { it > 0 },
    thumbnail = artworkUrl.orEmpty(),
)
