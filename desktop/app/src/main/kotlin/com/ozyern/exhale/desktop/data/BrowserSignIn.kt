/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A Chromium browser on this PC to sign in with; [fallbackFrom] names the default browser it stands in for. */
data class SignInBrowserApp(val name: String, val exe: File, val fallbackFrom: String?)

data class CapturedSession(val cookie: String, val dataSyncId: String?, val visitorData: String?)

/**
 * Google sign-in in the listener's own browser rather than one bundled into Exhale.
 *
 * The browser opens in a window of its own with a fresh, throwaway profile and the DevTools port on
 * localhost. The listener signs in exactly as they would anywhere; Exhale only reads the youtube.com
 * cookies once the session exists — the same cookies the old built-in window captured — then closes
 * the window and deletes the profile. Nothing on the pages is scripted or automated, which is also
 * why Google treats it as the ordinary browser it is.
 *
 * Firefox can't hand cookies back this way, so when it's the default a Chromium browser that's
 * installed (Chrome, then Edge, which ships with Windows) is used instead.
 */
object BrowserSignIn {
    private const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

    /** YouTube's own sign-in address, which asks Google to finish the hand-off for a session that already exists. */
    private const val HANDOFF_URL =
        "https://www.youtube.com/signin?action_handle_signin=true&app=desktop&hl=en&next=https%3A%2F%2Fmusic.youtube.com%2F"

    private const val DISCORD_LOGIN_URL = "https://discord.com/login"

    /**
     * Discord removes `token` from the page's own localStorage as soon as the client has read it.
     * A fresh iframe on the same origin gets an untouched copy of the store, which is where this
     * looks first; the page's own is tried after, for clients that leave it alone.
     */
    private val DISCORD_TOKEN_SCRIPT = """
        (function () {
          function unwrap(raw) {
            if (!raw) return null;
            try { return JSON.parse(raw); } catch (e) { return raw; }
          }
          try {
            var frame = document.createElement('iframe');
            document.body.appendChild(frame);
            var stored = frame.contentWindow.localStorage.getItem('token');
            frame.remove();
            var token = unwrap(stored);
            if (token) return token;
          } catch (e) {}
          try { return unwrap(window.localStorage.getItem('token')); } catch (e) {}
          return null;
        })()
    """.trimIndent()

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"

    private val chromium = mapOf(
        "chrome.exe" to "Chrome",
        "msedge.exe" to "Microsoft Edge",
        "brave.exe" to "Brave",
        "vivaldi.exe" to "Vivaldi",
        "chromium.exe" to "Chromium",
        "thorium.exe" to "Thorium",
    )

    private val otherNames = mapOf(
        "firefox.exe" to "Firefox",
        "zen.exe" to "Zen",
        "librewolf.exe" to "LibreWolf",
        "floorp.exe" to "Floorp",
        "waterfox.exe" to "Waterfox",
        "opera.exe" to "Opera",
        "launcher.exe" to "Opera",
    )

    private val profileDir: File
        get() = File(System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home")), "Exhale/sign-in-window")

    /** The default browser when it's Chromium; otherwise the first Chromium browser installed. */
    fun findBrowser(): SignInBrowserApp? {
        val default = defaultBrowserExe()
        if (default != null && default.isFile) {
            chromium[default.name.lowercase()]?.let { return SignInBrowserApp(it, default, null) }
        }
        val defaultName = default?.let { otherNames[it.name.lowercase()] ?: it.nameWithoutExtension.replaceFirstChar(Char::uppercase) }
        return installedChromium().firstOrNull()?.let { (name, exe) -> SignInBrowserApp(name, exe, defaultName) }
    }

    /**
     * Opens [browser] on Google's sign-in and waits for the youtube.com session. [onCaptured] runs once
     * it exists, while the page's ids are still being read. The window closes and its profile is
     * deleted however this ends — signed in, failed, or cancelled.
     */
    suspend fun signIn(browser: SignInBrowserApp, onCaptured: () -> Unit): Result<CapturedSession> {
        val dir = profileDir
        var process: Process? = null
        var cdp: CdpConnection? = null
        try {
            process = withContext(Dispatchers.IO) {
                dir.deleteRecursively()
                dir.mkdirs()
                ProcessBuilder(
                    browser.exe.absolutePath,
                    "--user-data-dir=${dir.absolutePath}",
                    "--remote-debugging-port=0",
                    // The debugging port makes Chromium report navigator.webdriver = true, which is what
                    // Google reads as an automated browser and answers with "this app may not be secure".
                    "--disable-blink-features=AutomationControlled",
                    // Chrome on Windows binds a Google session to a key that never leaves this profile,
                    // and keeps it alive by re-signing with it every few minutes. The profile is deleted
                    // the moment the cookies are read, so a bound session dies soon after — by the next
                    // launch Exhale was sending a cookie YouTube had already dropped. Unbound, it lasts.
                    "--disable-features=EnableBoundSessionCredentials,DeviceBoundSessions",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-sync",
                    "--new-window",
                    "--window-size=560,800",
                    LOGIN_URL,
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }
            val connection = CdpConnection(awaitDevTools(dir, process))
            cdp = connection
            val cookie = awaitSession(connection)
            onCaptured()
            val (dataSyncId, visitorData) = withContext(Dispatchers.IO) { pageIds(cookie) }
            return Result.success(CapturedSession(cookie, dataSyncId, visitorData))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            withContext(NonCancellable) { closeWindow(cdp, process, dir) }
        }
    }

    /**
     * Discord's login in the listener's own browser, for rich presence.
     *
     * Discord hands its web client a token and then deletes the property from the page's own
     * `localStorage`, which is why this reads it back through a fresh same-origin iframe — the
     * property is still there, just not on the window that asked. Nothing is typed or clicked for
     * them: they log in as they would anywhere, and the window closes once the token exists.
     */
    suspend fun discordToken(browser: SignInBrowserApp, onCaptured: () -> Unit): Result<String> {
        val dir = File(profileDir.parentFile, "discord-window")
        var process: Process? = null
        var cdp: CdpConnection? = null
        try {
            process = withContext(Dispatchers.IO) {
                dir.deleteRecursively()
                dir.mkdirs()
                ProcessBuilder(
                    browser.exe.absolutePath,
                    "--user-data-dir=${dir.absolutePath}",
                    "--remote-debugging-port=0",
                    // The debugging port makes Chromium report navigator.webdriver = true, which is what
                    // Google reads as an automated browser and answers with "this app may not be secure".
                    "--disable-blink-features=AutomationControlled",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-sync",
                    "--new-window",
                    "--window-size=560,820",
                    DISCORD_LOGIN_URL,
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }
            val connection = CdpConnection(awaitDevTools(dir, process))
            cdp = connection
            val token = awaitDiscordToken(connection)
            onCaptured()
            return Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            withContext(NonCancellable) { closeWindow(cdp, process, dir) }
        }
    }

    private suspend fun awaitDiscordToken(cdp: CdpConnection): String {
        val deadline = System.currentTimeMillis() + 20 * 60_000L
        while (true) {
            if (cdp.closed) throw IOException("The browser window was closed before the log-in finished.")
            if (System.currentTimeMillis() > deadline) throw IOException("Logging in timed out. Try again when you're ready.")
            val token = runCatching { readDiscordToken(cdp) }.getOrNull()
            if (!token.isNullOrBlank()) return token
            delay(1_500)
        }
    }

    /** Evaluates in whichever discord.com page is open; there is nothing to read before one is. */
    private suspend fun readDiscordToken(cdp: CdpConnection): String? {
        val targets = cdp.call("Target.getTargets").optJSONArray("targetInfos") ?: return null
        for (i in 0 until targets.length()) {
            val target = targets.optJSONObject(i) ?: continue
            if (target.optString("type") != "page" || "discord.com" !in target.optString("url")) continue
            val session = cdp.call(
                "Target.attachToTarget",
                JSONObject().put("targetId", target.optString("targetId")).put("flatten", true),
            ).optString("sessionId").takeIf { it.isNotBlank() } ?: continue
            val result = cdp.call(
                "Runtime.evaluate",
                JSONObject().put("expression", DISCORD_TOKEN_SCRIPT).put("returnByValue", true),
                sessionId = session,
            )
            val value = result.optJSONObject("result")?.opt("value") as? String
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** Chrome writes its DevTools port and path to the profile folder once it's listening. */
    private suspend fun awaitDevTools(dir: File, process: Process): String = withContext(Dispatchers.IO) {
        val file = File(dir, "DevToolsActivePort")
        val deadline = System.currentTimeMillis() + 30_000
        var exitedAt = 0L
        var endpoint: String? = null
        while (endpoint == null) {
            ensureActive()
            val lines = runCatching { file.readLines() }.getOrNull()
            val port = lines?.getOrNull(0)?.trim()?.toIntOrNull()
            val path = lines?.getOrNull(1)?.trim()
            if (port != null && !path.isNullOrEmpty()) {
                endpoint = "ws://127.0.0.1:$port$path"
                break
            }
            val now = System.currentTimeMillis()
            if (!process.isAlive) {
                if (exitedAt == 0L) exitedAt = now
                if (now - exitedAt > 4_000) throw IOException("The browser closed before the sign-in page opened.")
            }
            if (now > deadline) throw IOException("The browser took too long to start.")
            delay(200)
        }
        checkNotNull(endpoint)
    }

    /**
     * Polls the profile's cookies until youtube.com has a signed-in session, or the window goes away.
     *
     * Signing in to Google is not the same as signing in to YouTube: the second is a hand-off that the
     * `continue` address is meant to carry out, and Google sometimes drops that address and finishes
     * on "Manage your Google Account" instead. The listener is then signed in to Google and nothing
     * here ever sees a YouTube cookie. So once Google has a session and YouTube still doesn't, the
     * same window is sent through the hand-off again — which, with the session already there, needs no
     * password and goes straight through.
     */
    private suspend fun awaitSession(cdp: CdpConnection): String {
        val deadline = System.currentTimeMillis() + 20 * 60_000L
        var googleSince = 0L
        var lastNudge = 0L
        var nudges = 0
        while (true) {
            if (cdp.closed) throw IOException("The browser window was closed before sign-in finished.")
            val now = System.currentTimeMillis()
            if (now > deadline) throw IOException("Sign-in timed out. Try again when you're ready.")
            val cookies = runCatching { youtubeCookies(cdp) }.getOrNull()
            if (cookies != null && "SAPISID" in cookies) {
                // The last redirects are still setting the rest of the session; let them land first.
                delay(2_000)
                val settled = runCatching { youtubeCookies(cdp) }.getOrNull()?.takeIf { "SAPISID" in it } ?: cookies
                return settled.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
            if (runCatching { googleSignedIn(cdp) }.getOrDefault(false)) {
                if (googleSince == 0L) googleSince = now
                // Give the ordinary redirect its chance first; then try the hand-off, and a second route to it.
                if (now - googleSince > 5_000 && now - lastNudge > 12_000 && nudges < 4) {
                    lastNudge = now
                    val target = if (nudges % 2 == 0) LOGIN_URL else HANDOFF_URL
                    nudges++
                    runCatching { navigateSignInPage(cdp, target) }
                }
            }
            delay(1_200)
        }
    }

    /** Whether the profile holds a Google account session, whichever page the window happens to be on. */
    private suspend fun googleSignedIn(cdp: CdpConnection): Boolean {
        val list = cdp.call("Storage.getCookies").optJSONArray("cookies") ?: return false
        for (i in 0 until list.length()) {
            val cookie = list.optJSONObject(i) ?: continue
            if (cookie.optString("domain").trimStart('.').endsWith("google.com") && cookie.optString("name") == "SAPISID") return true
        }
        return false
    }

    /** Points the window's page at [url] — the one page a sign-in window has. */
    private suspend fun navigateSignInPage(cdp: CdpConnection, url: String) {
        val targets = cdp.call("Target.getTargets").optJSONArray("targetInfos") ?: return
        for (i in 0 until targets.length()) {
            val target = targets.optJSONObject(i) ?: continue
            if (target.optString("type") != "page") continue
            val session = cdp.call(
                "Target.attachToTarget",
                JSONObject().put("targetId", target.optString("targetId")).put("flatten", true),
            ).optString("sessionId").takeIf { it.isNotBlank() } ?: continue
            cdp.call("Page.navigate", JSONObject().put("url", url), sessionId = session)
            return
        }
    }

    private suspend fun youtubeCookies(cdp: CdpConnection): Map<String, String> {
        val list = cdp.call("Storage.getCookies").optJSONArray("cookies") ?: return emptyMap()
        val cookies = LinkedHashMap<String, String>()
        for (i in 0 until list.length()) {
            val cookie = list.optJSONObject(i) ?: continue
            if (cookie.optString("domain").trimStart('.').endsWith("youtube.com")) cookies[cookie.optString("name")] = cookie.optString("value")
        }
        return cookies
    }

    /** DATASYNC_ID and VISITOR_DATA, from YouTube Music's own page as the new session sees it. */
    private fun pageIds(cookie: String): Pair<String?, String?> = runCatching {
        val http = OkHttpClient.Builder().proxy(YouTube.proxy).callTimeout(15, TimeUnit.SECONDS).build()
        val request = Request.Builder()
            .url("https://music.youtube.com/")
            .header("Cookie", cookie)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        val html = http.newCall(request).execute().use { it.body.string() }
        fun config(key: String) = Regex("\"$key\":\"([^\"]*)\"").find(html)?.groupValues?.get(1)?.replace("\\u003d", "=")?.takeIf { it.isNotBlank() }
        config("DATASYNC_ID") to config("VISITOR_DATA")
    }.getOrDefault(null to null)

    private suspend fun closeWindow(cdp: CdpConnection?, process: Process?, dir: File) {
        if (cdp != null) {
            runCatching { withTimeout(2_000) { cdp.call("Browser.close") } }
            cdp.close()
        }
        withContext(Dispatchers.IO) {
            if (process != null && !process.waitFor(4, TimeUnit.SECONDS)) {
                process.descendants().forEach { it.destroy() }
                process.destroy()
            }
            // The profile holds the Google session; none of it stays behind. Chrome's helper processes
            // can hold a file for a moment after the window has gone.
            repeat(12) {
                if (!dir.exists() || dir.deleteRecursively()) return@withContext
                Thread.sleep(300)
            }
        }
    }

    private fun defaultBrowserExe(): File? {
        val progId = regQuery("""HKCU\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\https\UserChoice""", "ProgId") ?: return null
        val command = regQuery("""HKCR\$progId\shell\open\command""", null) ?: return null
        val exe = executableOf(command) ?: return null
        return File(Regex("%([^%]+)%").replace(exe) { System.getenv(it.groupValues[1]) ?: it.value })
    }

    private fun installedChromium(): List<Pair<String, File>> {
        val programFiles = System.getenv("ProgramFiles")
        val programFilesX86 = System.getenv("ProgramFiles(x86)")
        val localAppData = System.getenv("LOCALAPPDATA")
        val candidates = listOf(
            "Chrome" to listOfNotNull(programFiles, programFilesX86, localAppData).map { "$it\\Google\\Chrome\\Application\\chrome.exe" },
            "Microsoft Edge" to listOfNotNull(programFilesX86, programFiles).map { "$it\\Microsoft\\Edge\\Application\\msedge.exe" },
            "Brave" to listOfNotNull(programFiles, localAppData).map { "$it\\BraveSoftware\\Brave-Browser\\Application\\brave.exe" },
        )
        return candidates.mapNotNull { (name, paths) -> paths.map(::File).firstOrNull { it.isFile }?.let { name to it } }
    }

    private fun regQuery(key: String, value: String?): String? = runCatching {
        val args = buildList {
            add("reg")
            add("query")
            add(key)
            if (value == null) add("/ve") else {
                add("/v")
                add(value)
            }
        }
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, TimeUnit.SECONDS)
        Regex("""REG_(?:EXPAND_)?SZ\s+(.+)""").find(output)?.groupValues?.get(1)?.trim()
    }.getOrNull()

    private fun executableOf(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.startsWith("\"")) return trimmed.drop(1).substringBefore('"')
        val end = trimmed.lowercase().indexOf(".exe")
        return if (end > 0) trimmed.substring(0, end + 4) else null
    }
}

/** Just enough of the Chrome DevTools Protocol: numbered calls over one WebSocket, answered by id. */
private class CdpConnection(url: String) : AutoCloseable {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val ids = AtomicInteger(1)
    private val opened = CompletableDeferred<Unit>()

    @Volatile var closed = false
        private set

    private val socket: WebSocket = client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                val id = message.optInt("id", -1)
                if (id >= 0) pending.remove(id)?.complete(message)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                shutDown(IOException("The browser closed."))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                shutDown(t)
            }
        },
    )

    private fun shutDown(cause: Throwable) {
        closed = true
        opened.completeExceptionally(cause)
        pending.values.forEach { it.completeExceptionally(cause) }
        pending.clear()
    }

    suspend fun call(method: String, params: JSONObject = JSONObject(), sessionId: String? = null): JSONObject {
        withTimeout(10_000) { opened.await() }
        if (closed) throw IOException("The browser closed.")
        val id = ids.getAndIncrement()
        val reply = CompletableDeferred<JSONObject>()
        pending[id] = reply
        val message = JSONObject().put("id", id).put("method", method).put("params", params)
        if (sessionId != null) message.put("sessionId", sessionId)
        socket.send(message.toString())
        val answer = try {
            withTimeout(10_000) { reply.await() }
        } finally {
            pending.remove(id)
        }
        answer.optJSONObject("error")?.let { throw IOException(it.optString("message", "DevTools error")) }
        return answer.optJSONObject("result") ?: JSONObject()
    }

    override fun close() {
        closed = true
        socket.close(1000, null)
        client.dispatcher.executorService.shutdown()
    }
}
