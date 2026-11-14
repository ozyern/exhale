package com.ozyern.exhale.jam

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Talks to a real party server with two devices. Off unless `JAM_LIVE_TEST` is set, because it needs
 * the network and a server that may be asleep: `JAM_LIVE_TEST=1 gradlew testUniversalFossDebugUnitTest`.
 */
class JamLiveProbeTest {
    private val server = System.getenv("JAM_SERVER") ?: JamManager.DEFAULT_SERVER
    private val client = JamClient()

    private class Device(val frames: LinkedBlockingQueue<kotlinx.serialization.json.JsonObject> = LinkedBlockingQueue()) {
        lateinit var socket: WebSocket
    }

    private fun connect(device: Device, code: String, token: String) {
        device.socket = client.open(server, code, token, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                device.frames.add(client.json.parseToJsonElement(text).jsonObject)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                device.frames.add(buildJsonObject { put("type", "failure"); put("message", t.message.orEmpty()) })
            }
        })
    }

    private fun Device.next(type: String, timeoutSeconds: Long = 20): kotlinx.serialization.json.JsonObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            val frame = frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val kind = frame["type"]?.jsonPrimitive?.content
            if (kind == type) return frame
            if (kind == "failure") error("socket failed: ${frame["message"]}")
        }
        error("no '$type' frame within ${timeoutSeconds}s")
    }

    private fun Device.awaitState(accept: (PartyPlayback) -> Boolean): PartyPlayback {
        repeat(10) {
            val state = client.json.decodeFromJsonElement(PartyPlayback.serializer(), next("state")["playback"]!!)
            if (accept(state)) return state
        }
        error("the state never matched")
    }

    private fun identity() = UUID.randomUUID().toString().let { JoinRequest(userId = it, deviceId = it, displayName = "probe-${it.take(4)}") }

    @Test
    fun twoDevicesStayInStep() = runBlocking {
        assumeTrue(System.getenv("JAM_LIVE_TEST") != null)
        client.warm(server)

        val host = client.create(server, identity())
        assertTrue(host.you.isHost)
        val guest = client.join(server, host.code.lowercase(), identity())
        assertEquals(host.code, guest.code)

        val a = Device().also { connect(it, host.code, host.token) }
        val b = Device().also { connect(it, guest.code, guest.token) }
        a.next("welcome"); b.next("welcome")

        val track = PartyTrack(videoId = "dQw4w9WgXcQ", title = "Probe", artist = "Test", durationMs = 212_000)
        client.send(a.socket, buildJsonObject {
            put("type", "control"); put("action", "setTrack")
            put("track", client.json.encodeToJsonElement(track))
            put("positionMs", 0L); put("isPlaying", true)
        })
        val first = b.awaitState { it.track?.videoId == track.videoId }
        assertTrue(first.isPlaying)

        client.send(a.socket, buildJsonObject { put("type", "control"); put("action", "seek"); put("positionMs", 90_000L) })
        val moved = b.awaitState { it.seq > first.seq && it.positionMs >= 90_000 }
        assertTrue("seq must rise", moved.seq > first.seq)

        a.socket.close(1000, null); b.socket.close(1000, null)
        client.leave(server, guest.code, guest.token)
        client.leave(server, host.code, host.token)
    }
}
