/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.integrations

import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.listentogether.CreateRoomPayload
import com.ozyern.exhale.listentogether.ApproveJoinPayload
import com.ozyern.exhale.listentogether.JoinApprovedPayload
import com.ozyern.exhale.listentogether.JoinRejectedPayload
import com.ozyern.exhale.listentogether.JoinRequestPayload
import com.ozyern.exhale.listentogether.JoinRoomPayload
import com.ozyern.exhale.listentogether.ListenTogetherServers
import com.ozyern.exhale.listentogether.Message
import com.ozyern.exhale.listentogether.MessageTypes
import com.ozyern.exhale.listentogether.PlaybackActionPayload
import com.ozyern.exhale.listentogether.PlaybackActions
import com.ozyern.exhale.listentogether.RejectJoinPayload
import com.ozyern.exhale.listentogether.RoomAllocationResponse
import com.ozyern.exhale.listentogether.RoomCreatedPayload
import com.ozyern.exhale.listentogether.RoomState
import com.ozyern.exhale.listentogether.SyncStatePayload
import com.ozyern.exhale.listentogether.TrackInfo
import com.ozyern.exhale.listentogether.UserInfo
import com.ozyern.exhale.listentogether.UserJoinedPayload
import com.ozyern.exhale.listentogether.UserLeftPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Listen Together on Windows: the same rooms, server and wire protocol as the Android app, so a
 * phone and a PC (or anyone's Exhale) can share one. The host's playback is the room's; a guest's
 * player follows it. Join requests come to the host to approve, as on Android.
 */
object DesktopListenTogether {
    data class Session(
        val roomCode: String,
        val userId: String,
        val isHost: Boolean,
        val users: List<UserInfo>,
        val pendingJoins: List<JoinRequestPayload> = emptyList(),
    )

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    /** A line for the UI: connecting, rejected, closed and the like. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var player: DesktopPlayer? = null
    private var scope: CoroutineScope? = null
    private var pingJob: Job? = null
    private var hostJob: Job? = null
    /** While a guest applies the host's change, its own player events are not the room's. */
    @Volatile private var applying = false

    fun attach(player: DesktopPlayer, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
    }

    private val serverUrl: String get() = ListenTogetherServers.defaultServerUrl

    private fun httpBase(): String = serverUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")

    fun createRoom(username: String) {
        val scope = scope ?: return
        leave()
        _status.value = "Creating a room…"
        scope.launch(Dispatchers.IO) {
            // The code exists before the socket opens: the server routes connections by room.
            val code = runCatching {
                http.newCall(Request.Builder().url("${httpBase()}/api/rooms").post(ByteArray(0).toRequestBody()).build())
                    .execute().use { response ->
                        if (!response.isSuccessful) null
                        else json.decodeFromString(RoomAllocationResponse.serializer(), response.body.string()).roomCode
                    }
            }.getOrNull()
            connect(code) { send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username), CreateRoomPayload.serializer()) }
        }
    }

    fun joinRoom(code: String, username: String) {
        leave()
        val room = code.trim().uppercase()
        _status.value = "Asking to join $room…"
        connect(room) { send(MessageTypes.JOIN_ROOM, JoinRoomPayload(room, username), JoinRoomPayload.serializer()) }
    }

    fun approve(userId: String) {
        send(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId), ApproveJoinPayload.serializer())
        _session.value = _session.value?.let { s -> s.copy(pendingJoins = s.pendingJoins.filterNot { it.userId == userId }) }
    }

    fun reject(userId: String) {
        send(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId), RejectJoinPayload.serializer())
        _session.value = _session.value?.let { s -> s.copy(pendingJoins = s.pendingJoins.filterNot { it.userId == userId }) }
    }

    fun leave() {
        if (socket != null) sendBare(MessageTypes.LEAVE_ROOM)
        socket?.close(1000, null)
        socket = null
        pingJob?.cancel()
        hostJob?.cancel()
        _session.value = null
    }

    private fun connect(room: String?, onOpen: () -> Unit) {
        val url = if (room.isNullOrBlank()) serverUrl else "$serverUrl/room/$room"
        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen()
                pingJob?.cancel()
                pingJob = scope?.launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(25_000)
                        sendBare(MessageTypes.PING)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                // JSON is all this client asks for; anything else is not for it.
                if (bytes.size > 0 && bytes[0] == '{'.code.toByte()) handle(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) {
                    _status.value = "Couldn't reach the Listen Together server."
                    _session.value = null
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) _session.value = null
            }
        })
    }

    private fun handle(text: String) {
        val message = runCatching { json.decodeFromString(Message.serializer(), text) }.getOrNull() ?: return
        val payload = message.payload
        fun <T> read(serializer: KSerializer<T>): T? = payload?.let { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }
        scope?.launch(Dispatchers.Main) {
            when (message.type) {
                MessageTypes.ROOM_CREATED -> read(RoomCreatedPayload.serializer())?.let { created ->
                    _status.value = null
                    _session.value = Session(created.roomCode, created.userId, isHost = true, users = created.state?.users.orEmpty())
                    startHosting()
                }
                MessageTypes.JOIN_APPROVED -> read(JoinApprovedPayload.serializer())?.let { approved ->
                    _status.value = null
                    _session.value = Session(approved.roomCode, approved.userId, isHost = false, users = approved.state.users)
                    follow(approved.state)
                }
                MessageTypes.JOIN_REJECTED -> {
                    _status.value = read(JoinRejectedPayload.serializer())?.reason ?: "The host said no."
                    leave()
                }
                MessageTypes.JOIN_REQUEST -> read(JoinRequestPayload.serializer())?.let { request ->
                    _session.value = _session.value?.let { it.copy(pendingJoins = it.pendingJoins + request) }
                }
                MessageTypes.USER_JOINED -> read(UserJoinedPayload.serializer())?.let { joined ->
                    _session.value = _session.value?.let { s ->
                        s.copy(users = s.users.filterNot { it.userId == joined.userId } + UserInfo(joined.userId, joined.username, isHost = false))
                    }
                    // A new guest gets where the room is right now.
                    if (_session.value?.isHost == true) broadcastTrack()
                }
                MessageTypes.USER_LEFT -> read(UserLeftPayload.serializer())?.let { left ->
                    _session.value = _session.value?.let { s -> s.copy(users = s.users.filterNot { it.userId == left.userId }) }
                }
                MessageTypes.SYNC_PLAYBACK -> read(PlaybackActionPayload.serializer())?.let(::followAction)
                MessageTypes.SYNC_STATE -> read(SyncStatePayload.serializer())?.let { state ->
                    follow(state.currentTrack, state.isPlaying, state.position)
                }
                MessageTypes.KICKED, MessageTypes.ROOM_CLOSED -> {
                    _status.value = if (message.type == MessageTypes.KICKED) "You were removed from the room." else "The room closed."
                    leave()
                }
                MessageTypes.ERROR -> _status.value = "Listen Together: ${payload?.toString()?.take(120) ?: "error"}"
            }
        }
    }

    // Host: the room plays what this PC plays.

    private fun startHosting() {
        val player = player ?: return
        hostJob?.cancel()
        hostJob = scope?.launch {
            var lastTrack: String? = null
            player.state
                .map { Triple(it.current?.id, it.isPlaying, it.seekGeneration) }
                .distinctUntilChanged()
                .collect { (track, playing, _) ->
                    if (track != lastTrack) {
                        lastTrack = track
                        broadcastTrack()
                    } else {
                        playback(if (playing) PlaybackActions.PLAY else PlaybackActions.PAUSE)
                    }
                }
        }
    }

    private fun broadcastTrack() {
        val player = player ?: return
        val song = player.state.value.current ?: return
        playback(PlaybackActions.CHANGE_TRACK, track = song.toTrackInfo())
        playback(if (player.state.value.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE)
    }

    private fun playback(action: String, track: TrackInfo? = null) {
        val player = player ?: return
        send(
            MessageTypes.PLAYBACK_ACTION,
            PlaybackActionPayload(
                action = action,
                trackId = track?.id ?: player.state.value.current?.id,
                trackInfo = track,
                position = (player.position.value * 1000).toLong(),
            ),
            PlaybackActionPayload.serializer(),
        )
    }

    // Guest: this PC plays what the room plays.

    private fun follow(state: RoomState) = follow(state.currentTrack, state.isPlaying, state.position)

    private fun follow(track: TrackInfo?, playing: Boolean, positionMs: Long) {
        val player = player ?: return
        if (track == null) return
        withApplying {
            if (player.state.value.current?.id != track.id) {
                player.play(listOf(track.toSongItem()), 0, positionMs / 1000.0)
            } else {
                player.seekTo(positionMs / 1000.0)
            }
            if (player.state.value.isPlaying != playing) player.togglePlayPause()
        }
    }

    private fun followAction(action: PlaybackActionPayload) {
        val session = _session.value ?: return
        if (session.isHost || action.fromUserId == session.userId) return
        val player = player ?: return
        withApplying {
            when (action.action) {
                PlaybackActions.CHANGE_TRACK -> action.trackInfo?.let { player.play(listOf(it.toSongItem()), 0, (action.position ?: 0) / 1000.0) }
                PlaybackActions.PLAY -> {
                    action.position?.let { player.seekTo(it / 1000.0) }
                    if (!player.state.value.isPlaying) player.togglePlayPause()
                }
                PlaybackActions.PAUSE -> if (player.state.value.isPlaying) player.togglePlayPause()
                PlaybackActions.SEEK -> action.position?.let { player.seekTo(it / 1000.0) }
            }
        }
    }

    private inline fun withApplying(block: () -> Unit) {
        applying = true
        try { block() } finally { applying = false }
    }

    private fun <T> send(type: String, payload: T, serializer: KSerializer<T>) {
        val element = json.encodeToJsonElement(serializer, payload)
        socket?.send(json.encodeToString(Message.serializer(), Message(type, element)))
    }

    private fun sendBare(type: String) {
        socket?.send(json.encodeToString(Message.serializer(), Message(type, null)))
    }
}

private fun SongItem.toTrackInfo() = TrackInfo(
    id = id,
    title = title,
    artist = artists.joinToString { it.name },
    album = album?.name,
    duration = (duration ?: 0) * 1000L,
    thumbnail = thumbnail,
)

private fun TrackInfo.toSongItem() = SongItem(
    id = id,
    title = title,
    artists = artist.split(", ").filter { it.isNotBlank() }.map { Artist(it, null) },
    duration = (duration / 1000).toInt().takeIf { it > 0 },
    thumbnail = thumbnail.orEmpty(),
)
