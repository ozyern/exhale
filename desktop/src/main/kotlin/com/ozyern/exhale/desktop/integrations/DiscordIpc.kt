/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.integrations

import org.json.JSONObject
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What Discord is asked to show. Null times leave the progress bar out. */
internal data class DiscordActivity(
    val name: String,
    val title: String,
    val artist: String?,
    val image: String?,
    val imageText: String?,
    val badge: String?,
    val badgeText: String?,
    val startMs: Long?,
    val endMs: Long?,
)

/**
 * Discord's own local connection: the pipe the desktop app opens for anything on the same PC.
 *
 * It needs no account token and goes through the Discord that is already signed in, so what it sets
 * shows up on the listener's profile straight away. A second gateway connection posting as the same
 * account has no such standing beside a running Discord, and its failures were never visible anywhere.
 */
internal class DiscordIpc(private val applicationId: String) : AutoCloseable {
    private var pipe: RandomAccessFile? = null
    private var input: FileInputStream? = null

    val isConnected: Boolean get() = pipe != null

    /** Opens the pipe if Discord is running. Quick either way: with no Discord the open just fails. */
    fun connect(): Boolean {
        if (pipe != null) return true
        for (index in 0..9) {
            val opened = runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$index", "rw") }.getOrNull() ?: continue
            pipe = opened
            input = FileInputStream(opened.fd)
            val ready = runCatching {
                write(OP_HANDSHAKE, JSONObject().put("v", 1).put("client_id", applicationId))
                read()?.optString("evt") == "READY"
            }.getOrDefault(false)
            if (ready) return true
            close()
        }
        return false
    }

    /** Sets the activity, or clears it when [activity] is null. False means this connection is finished. */
    fun set(activity: DiscordActivity?): Boolean = runCatching {
        val args = JSONObject().put("pid", ProcessHandle.current().pid())
        activity?.let { args.put("activity", activityJson(it)) }
        write(OP_FRAME, JSONObject().put("cmd", "SET_ACTIVITY").put("nonce", System.nanoTime().toString()).put("args", args))
        val reply = read() ?: error("no reply")
        if (reply.optString("evt") == "ERROR") println("Exhale Discord: refused — ${reply.optJSONObject("data")?.optString("message")}")
        true
    }.getOrElse {
        println("Exhale Discord: local connection lost — ${it.message}")
        close()
        false
    }

    private fun write(op: Int, payload: JSONObject) {
        (pipe ?: throw IOException("not connected")).write(encodeFrame(op, payload.toString()))
    }

    /**
     * The next message, polled rather than blocked on. The pipe's handle is synchronous, so a read left
     * waiting would hold up every write behind it; asking how much has arrived never waits.
     */
    private fun read(): JSONObject? {
        val stream = input ?: return null
        val deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS
        fun await(bytes: Int): Boolean {
            while (stream.available() < bytes) {
                if (System.currentTimeMillis() > deadline) return false
                Thread.sleep(15)
            }
            return true
        }
        if (!await(HEADER_BYTES)) return null
        val header = ByteBuffer.wrap(stream.readNBytes(HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN)
        header.int
        val length = header.int
        if (length !in 0..MAX_FRAME_BYTES || !await(length)) return null
        return JSONObject(String(stream.readNBytes(length), Charsets.UTF_8))
    }

    override fun close() {
        runCatching { pipe?.close() }
        pipe = null
        input = null
    }

    internal companion object {
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val HEADER_BYTES = 8
        private const val MAX_FRAME_BYTES = 1 shl 20
        private const val REPLY_TIMEOUT_MS = 3_000L

        /** Discord refuses a whole activity over one field outside these. */
        private const val MIN_TEXT = 2
        private const val MAX_TEXT = 128
        private const val MAX_IMAGE_URL = 256

        internal fun encodeFrame(op: Int, json: String): ByteArray {
            val body = json.toByteArray(Charsets.UTF_8)
            return ByteBuffer.allocate(HEADER_BYTES + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(op).putInt(body.size).put(body).array()
        }

        private fun text(value: String?): String? {
            val trimmed = value?.trim().orEmpty().take(MAX_TEXT)
            return when {
                trimmed.isEmpty() -> null
                // A one-character title ("7") is a real song and still has to be shown.
                trimmed.length < MIN_TEXT -> trimmed.padEnd(MIN_TEXT, '​')
                else -> trimmed
            }
        }

        internal fun activityJson(activity: DiscordActivity): JSONObject = JSONObject().apply {
            put("name", activity.name)
            put("type", TYPE_LISTENING)
            put("details", text(activity.title) ?: activity.name)
            text(activity.artist)?.let { put("state", it) }
            val assets = JSONObject()
            activity.image?.takeIf { it.startsWith("http") && it.length <= MAX_IMAGE_URL }?.let { image ->
                assets.put("large_image", image)
                text(activity.imageText)?.let { assets.put("large_text", it) }
            }
            activity.badge?.let { badge ->
                assets.put("small_image", badge)
                text(activity.badgeText)?.let { assets.put("small_text", it) }
            }
            if (assets.length() > 0) put("assets", assets)
            activity.startMs?.let { start ->
                val times = JSONObject().put("start", start)
                activity.endMs?.let { times.put("end", it) }
                put("timestamps", times)
            }
        }

        private const val TYPE_LISTENING = 2
    }
}
