/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Bumped when a message changes shape; a peer on another version is told so and left alone. */
const val CONNECT_PROTOCOL_VERSION = 1

/** Where the Windows app listens for the phone's connection. */
const val CONNECT_TCP_PORT = 47823

/** Where the Windows app answers "is Exhale here?" broadcasts from phones on the same Wi-Fi. */
const val CONNECT_DISCOVERY_PORT = 47824

/**
 * A song as both apps know it. Both play YouTube Music catalogue songs, so the video id is the song;
 * the rest is what the other side needs to show it before it has looked the song up itself.
 */
@Serializable
data class ConnectTrack(
    val id: String,
    val title: String,
    val artists: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
)

/** A song liked or unliked at [atMs]. 0 means "already liked before the two apps were paired". */
@Serializable
data class LikeEntry(val track: ConnectTrack, val liked: Boolean, val atMs: Long)

/** A song played at [atMs] (wall clock, ms). */
@Serializable
data class PlayEntry(val track: ConnectTrack, val atMs: Long)

/** What a remote can ask a player to do. */
@Serializable
enum class ConnectAction { PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS, SEEK, VOLUME, SKIP_TO }

/**
 * Everything sent over the link: one JSON object per line, the kind in its "type" field.
 *
 * Opening a link: the Windows app sends [Hello] with a fresh nonce. A phone it has paired with
 * answers [Auth]; a new phone answers [PairRequest] with the code on the PC's screen. Either way
 * [Welcome] or [Rejected] follows, and only after [Welcome] does anything else count.
 */
@Serializable
sealed class ConnectMessage {
    @Serializable @SerialName("hello")
    data class Hello(val deviceId: String, val deviceName: String, val nonce: String, val version: Int = CONNECT_PROTOCOL_VERSION) : ConnectMessage()

    @Serializable @SerialName("pair")
    data class PairRequest(val deviceId: String, val deviceName: String, val proof: String) : ConnectMessage()

    @Serializable @SerialName("auth")
    data class Auth(val deviceId: String, val deviceName: String, val proof: String) : ConnectMessage()

    /** [sealedSecret] is set when pairing: the shared key, sealed with the pairing code. */
    @Serializable @SerialName("welcome")
    data class Welcome(val sealedSecret: String? = null) : ConnectMessage()

    @Serializable @SerialName("rejected")
    data class Rejected(val reason: Reason) : ConnectMessage() {
        @Serializable enum class Reason { WRONG_CODE, NOT_PAIRING, UNKNOWN_DEVICE, BAD_PROOF, VERSION }
    }

    /**
     * What a player is doing. [positionMs] was true at [sentAtMs] on the sender's clock; the other
     * side carries it forward while [playing] rather than being sent every tick.
     */
    @Serializable @SerialName("now_playing")
    data class NowPlaying(
        val track: ConnectTrack? = null,
        val playing: Boolean = false,
        val positionMs: Long = 0,
        val volume: Int = 100,
        val queue: List<ConnectTrack> = emptyList(),
        val index: Int = -1,
        val sentAtMs: Long = 0,
    ) : ConnectMessage()

    /** Remote control. [value] is the position for SEEK, 0..100 for VOLUME, the queue index for SKIP_TO. */
    @Serializable @SerialName("command")
    data class Command(val action: ConnectAction, val value: Long? = null) : ConnectMessage()

    /** "Carry on over there": play [queue] from [index] at [positionMs]. The sender pauses itself. */
    @Serializable @SerialName("handoff")
    data class Handoff(val queue: List<ConnectTrack>, val index: Int, val positionMs: Long, val play: Boolean = true) : ConnectMessage()

    /**
     * The library both ways: likes (with unlikes, so a removal carries across too) and plays.
     * [full] is the whole picture, sent when a link opens; otherwise it is just what changed.
     */
    @Serializable @SerialName("library")
    data class Library(val likes: List<LikeEntry> = emptyList(), val plays: List<PlayEntry> = emptyList(), val full: Boolean = false) : ConnectMessage()

    @Serializable @SerialName("ping")
    data object Ping : ConnectMessage()

    @Serializable @SerialName("pong")
    data object Pong : ConnectMessage()
}

/** One message per line, so a reader never has to find where a message ends. */
object ConnectCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(message: ConnectMessage): String = json.encodeToString(ConnectMessage.serializer(), message)

    /** Null for a line that is not a message this version knows, so one odd line never drops the link. */
    fun decode(line: String): ConnectMessage? = runCatching {
        json.decodeFromString(ConnectMessage.serializer(), line)
    }.getOrNull()
}
