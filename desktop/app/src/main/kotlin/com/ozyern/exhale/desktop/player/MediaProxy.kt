/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A go-between for streams that will only be served in pieces.
 *
 * YouTube's best renditions come from the iOS client, and those URLs answer a bounded range request
 * and nothing else: ask for `bytes=0-1023` and you get it, ask for `bytes=0-` or ask without a range
 * at all and you get 403. Every player built on ffmpeg — mpv included — opens a stream by asking for
 * everything from the start, so it is refused before a frame is read, which is why the same URL that
 * probes fine never plays.
 *
 * So mpv is pointed here instead. This serves an ordinary, well-behaved stream on the loopback
 * interface, and fetches what it needs upstream a piece at a time in the shape YouTube's own players
 * use: the piece named in the URL rather than in a header, the request numbered, a playback nonce
 * along with it. Nothing is rewritten — the bytes handed to mpv are the bytes Google sent, only asked
 * for differently.
 *
 * Two things follow from how these URLs behave. They are strictly sequential, so a seek forward is
 * walked up to rather than jumped to; and one of them will stop answering partway through a long
 * video, so a fresh one is asked for and the stream carries on.
 */
object MediaProxy {
    /**
     * How much to ask for at once, to begin with. Each stream has its own ceiling — a 4K picture will
     * hand over a megabyte at a time where its soundtrack, a hundredth the size, refuses anything past
     * about a hundred kilobytes — so the working size is learned per stream and remembered.
     */
    private const val ChunkSize = 1L * 1024 * 1024
    private const val SmallestChunk = 32L * 1024

    /**
     * A refusal here means "not so fast", not "not so much".
     *
     * Google throttles a burst of range requests from one address, and a picture and its sound are two
     * streams pulling at once — so the answer is to wait and ask for the same bytes again, not to ask
     * for fewer. Shrinking the piece only makes more requests, which is the thing being objected to.
     */
    private val RetryDelaysMs = longArrayOf(200, 500, 1_000, 2_000, 3_000, 4_000)

    /** The largest piece each stream has actually served. */
    private val chunkFor = ConcurrentHashMap<String, Long>()

    /**
     * How far into each stream has been taken. These URLs will only answer a range that carries on
     * from what has already been fetched — a fresh URL asked for a byte deep in the file is refused —
     * so a seek forward has to be walked up to rather than jumped to.
     */
    private val reachedFor = ConcurrentHashMap<String, Long>()

    /** One catch-up at a time per stream, so two of them don't pull against each other. */
    private val walks = ConcurrentHashMap<String, Any>()

    /**
     * The furthest a seek is walked up to. Past this the wait is worse than the refusal: a minute of
     * downloading to reach a point the listener may leave again in a second.
     */
    private const val MaxWalkBytes = 96L * 1024 * 1024

    /**
     * How many fresh URLs one stream is worth before the refusal is taken at its word. A long video
     * can be refused more than once on the way through, and each time a new URL carries it on, so this
     * is generous — it is there to stop an endless round of asking, not to ration.
     */
    private const val MaxRenewals = 12


    /**
     * One stream, and how to be issued it again. A URL is only good for so much: somewhere past a
     * hundred megabytes it stops answering for good, while a freshly issued one for the same track
     * answers straight away. [renew] asks YouTube for that fresh one.
     */
    private class Upstream(@Volatile var url: String, val headers: StreamHeaders, val renew: (() -> String?)?) {
        /** Fresh URLs asked for so far, so a track that is refused however it's asked gives up. */
        var renewals = 0

        /** The playback nonce YouTube's own players put on every media request, and the request count. */
        val cpn: String = (1..16).map { CpnAlphabet.random() }.joinToString("")
        val asks = AtomicLong()
    }

    private const val CpnAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    /** How many times one piece may be sent on to another server before something is wrong. */
    private const val MaxHandoffs = 4

    private val routes = ConcurrentHashMap<String, Upstream>()
    private val ids = AtomicLong(1)

    @Volatile private var server: HttpServer? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * A local address mpv can open for [url]. The registration lives until [forget], because a stream
     * is opened again on every seek that lands outside what mpv has buffered.
     */
    fun serve(url: String, headers: StreamHeaders, renew: (() -> String?)? = null): String {
        val port = ensureStarted() ?: return url
        val id = ids.getAndIncrement().toString(36)
        routes[id] = Upstream(url, headers, renew)
        // More than a handful means songs are piling up; the oldest can't still be playing.
        if (routes.size > 8) {
            routes.keys.sortedBy { it.toLongOrNull(36) ?: 0 }.take(routes.size - 8).forEach(routes::remove)
        }
        return "http://127.0.0.1:$port/s/$id"
    }

    fun forget(localUrl: String) {
        routes.remove(localUrl.substringAfterLast('/'))
    }

    /** Whether this URL is one of ours, so callers can tell a proxied stream from a direct one. */
    fun isOurs(url: String): Boolean = url.startsWith("http://127.0.0.1:")

    @Synchronized
    private fun ensureStarted(): Int? {
        server?.let { return it.address.port }
        return runCatching {
            // Loopback only: this is for mpv on this machine, and the URLs it serves are signed.
            val created = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            created.createContext("/s/") { exchange -> handle(exchange) }
            created.executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "exhale-media-proxy").apply { isDaemon = true }
            }
            created.start()
            server = created
            println("Exhale media: proxy on 127.0.0.1:${created.address.port}")
            created.address.port
        }.getOrElse {
            println("Exhale media: no proxy — ${it.message}")
            null
        }
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val upstream = routes[exchange.requestURI.path.substringAfterLast('/')]
            if (upstream == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            val total = totalLength(upstream)
            if (total <= 0) {
                exchange.sendResponseHeaders(502, -1)
                return
            }

            val (from, to) = requestedRange(exchange.requestHeaders.getFirst("Range"), total)
            // Before a byte is promised: a seek forward has to be caught up to, and that can fail.
            if (!exchange.requestMethod.equals("HEAD", ignoreCase = true) && !walkUpTo(upstream, from)) {
                exchange.sendResponseHeaders(503, -1)
                return
            }
            val length = to - from + 1
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            if (exchange.requestHeaders.containsKey("Range")) {
                exchange.responseHeaders.add("Content-Range", "bytes $from-$to/$total")
                exchange.sendResponseHeaders(206, length)
            } else {
                exchange.sendResponseHeaders(200, length)
            }
            if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) return

            runCatching { pump(upstream, from, to, exchange) }
                .onFailure {
                    // mpv closing the stream mid-seek is the ordinary way this ends, not a fault.
                    if (it !is IOException) println("Exhale media: proxy stopped — ${it.message}")
                }
        }
    }

    /**
     * Pulls the stream forward to [target], throwing the bytes away, so that a range starting there
     * will be answered. Returns whether the stream now reaches it — a gap too big to be worth walking,
     * or an upstream that stopped answering, both come back false and the request is turned down
     * rather than left hanging.
     */
    private fun walkUpTo(upstream: Upstream, target: Long): Boolean {
        if (target <= (reachedFor[upstream.url] ?: 0L)) return true
        synchronized(walks.getOrPut(upstream.url) { Any() }) {
            var position = reachedFor[upstream.url] ?: 0L
            if (target <= position) return true
            if (target - position > MaxWalkBytes) return false
            val sink = java.io.OutputStream.nullOutputStream()
            return runCatching {
                while (position < target) {
                    val chunk = chunkFor.getOrPut(upstream.url) { ChunkSize }
                    val end = minOf(position + chunk - 1, target - 1)
                    position += fetch(upstream, position, end, sink)
                    remember(upstream.url, position)
                }
                true
            }.getOrDefault(false)
        }
    }

    /** Fetches [from]..[to] upstream in pieces YouTube will answer, writing each straight through. */
    private fun pump(upstream: Upstream, from: Long, to: Long, exchange: HttpExchange) {
        var position = from
        exchange.responseBody.use { out ->
            while (position <= to) {
                val chunk = chunkFor.getOrPut(upstream.url) { ChunkSize }
                val end = minOf(position + chunk - 1, to)
                position += fetch(upstream, position, end, out)
                remember(upstream.url, position)
            }
            out.flush()
        }
    }

    private fun remember(url: String, position: Long) {
        if (reachedFor.size > 32) reachedFor.clear()
        reachedFor.merge(url, position, ::maxOf)
    }

    private class Refused(val code: Int) : IOException("upstream $code")

    /**
     * One piece, returning how much of it was delivered.
     *
     * A refusal means one of two things and the difference isn't stated: the piece is too big for this
     * stream, or too many are being asked for at once. So the size is halved first — cheap, and it
     * sticks for the rest of the stream — and once it can't go lower, the answer must be the second,
     * which only wants waiting out.
     */
    private fun fetch(upstream: Upstream, from: Long, to: Long, out: java.io.OutputStream): Long {
        var end = to
        var waits = 0
        while (true) {
            val outcome = runCatching { fetchOnce(upstream, from, end, out) }
            if (outcome.isSuccess) return end - from + 1
            val failure = outcome.exceptionOrNull()
            // Bytes already on their way to mpv can't be asked for twice.
            if (failure !is Refused) throw failure ?: IOException("upstream stopped")

            val size = end - from + 1
            if (size > SmallestChunk) {
                val smaller = maxOf(size / 2, SmallestChunk)
                chunkFor[upstream.url] = smaller
                end = from + smaller - 1
                continue
            }
            if (waits >= RetryDelaysMs.size) {
                // Waiting it out hasn't worked, so this URL is spent rather than busy. Ask for another.
                if (!renew(upstream, from)) throw failure
                end = minOf(from + chunkFor.getOrPut(upstream.url) { ChunkSize } - 1, to)
                waits = 0
                continue
            }
            Thread.sleep(RetryDelaysMs[waits])
            waits++
        }
    }

    /**
     * Swaps in a freshly issued URL for the same track and brings it up to [upTo], since a URL nothing
     * has read from will not answer a byte in the middle any more than the old one would.
     */
    private fun renew(upstream: Upstream, upTo: Long): Boolean {
        val issue = upstream.renew ?: return false
        if (upstream.renewals >= MaxRenewals) return false
        val fresh = runCatching { issue() }.getOrNull()?.takeIf { it != upstream.url } ?: return false
        upstream.renewals++
        val spent = upstream.url
        upstream.url = fresh
        lengths.remove(spent)
        chunkFor.remove(spent)
        reachedFor.remove(spent)
        if (upTo <= 0) return true
        if (upTo > MaxWalkBytes) return false
        val sink = java.io.OutputStream.nullOutputStream()
        var position = 0L
        return runCatching {
            while (position < upTo) {
                val chunk = chunkFor.getOrPut(fresh) { ChunkSize }
                val end = minOf(position + chunk - 1, upTo - 1)
                position += fetch(upstream, position, end, sink)
                remember(fresh, position)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * One piece, asked for the way YouTube's own players ask: the range named in the URL, the request
     * numbered, a playback nonce along with it, and `alr=yes`.
     *
     * That last one is the difference between a stream that plays and one that stops a third of the way
     * through. Asked with a Range header — as every ffmpeg-based player does — these URLs serve about
     * the first thirty per cent of the file and then refuse everything, and a freshly issued URL refuses
     * at exactly the same place. With `alr=yes` the server hands back the address of another one instead
     * of refusing, and the whole file comes down.
     */
    private fun fetchOnce(upstream: Upstream, from: Long, to: Long, out: java.io.OutputStream) {
        var handoffs = 0
        while (true) {
            val response = http.newCall(mediaRequest(upstream, from, to)).execute()
            val elsewhere = try {
                if (!response.isSuccessful) throw Refused(response.code)
                // The handoff is a plain-text body holding the address to ask instead, not media.
                if (response.header("Content-Type")?.startsWith("text/plain") == true) {
                    response.body.string().trim().takeIf { it.startsWith("http") }
                } else {
                    null
                }
            } catch (failure: Throwable) {
                response.close()
                throw failure
            }
            if (elsewhere == null) {
                response.use { it.body.byteStream().copyTo(out, DEFAULT_BUFFER_SIZE) }
                return
            }
            response.close()
            if (++handoffs > MaxHandoffs) throw Refused(508)
            upstream.url = elsewhere.substringBefore("&range=")
        }
    }

    private fun mediaRequest(upstream: Upstream, from: Long, to: Long): Request =
        Request.Builder()
            .url("${upstream.url}&range=$from-$to&rn=${upstream.asks.getAndIncrement()}&rbuf=0&cpn=${upstream.cpn}&alr=yes")
            .header("User-Agent", upstream.headers.userAgent)
            .apply {
                upstream.headers.origin?.let { header("Origin", it) }
                upstream.headers.referer?.let { header("Referer", it) }
            }
            .build()

    /** What mpv asked for, clamped to the stream. No range means all of it. */
    private fun requestedRange(header: String?, total: Long): Pair<Long, Long> {
        val spec = header?.removePrefix("bytes=")?.trim() ?: return 0L to total - 1
        val from = spec.substringBefore('-').toLongOrNull() ?: 0L
        val to = spec.substringAfter('-').toLongOrNull() ?: (total - 1)
        return from.coerceIn(0, total - 1) to to.coerceIn(from, total - 1)
    }

    private val lengths = ConcurrentHashMap<String, Long>()

    /**
     * How long the stream is. It's in the URL as `clen`, and where it isn't, one byte is asked for and
     * the total read off the Content-Range that comes back.
     */
    private fun totalLength(upstream: Upstream): Long {
        lengths[upstream.url]?.let { return it }
        val declared = Regex("""[?&]clen=(\d+)""").find(upstream.url)?.groupValues?.get(1)?.toLongOrNull()
        val total = declared ?: runCatching {
            val request = Request.Builder()
                .url(upstream.url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", upstream.headers.userAgent)
                .build()
            http.newCall(request).execute().use { response ->
                response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
        if (total > 0) {
            if (lengths.size > 32) lengths.clear()
            lengths[upstream.url] = total
        }
        return total
    }
}
