/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

object AppInfo {
    /** The same version as the Android app (versionName in app/build.gradle.kts), written by the build. */
    val VERSION: String by lazy { buildInfo.getProperty("version")?.takeIf { it.isNotBlank() } ?: "0.0.0" }
    const val TAGLINE = "Music, exhaled."
    const val GITHUB_URL = "https://github.com/ozyern/exhale"
    const val RELEASES_URL = "$GITHUB_URL/releases"
    const val ISSUES_URL = "$GITHUB_URL/issues"
    const val LICENSE_URL = "$GITHUB_URL/blob/master/LICENSE"

    /** The short commit this build was made from, written into the jar by the build (writeBuildInfo). */
    val commit: String by lazy { buildInfo.getProperty("commit")?.takeIf { it.isNotBlank() } ?: "—" }

    private val buildInfo: java.util.Properties by lazy {
        java.util.Properties().apply {
            runCatching { AppInfo::class.java.getResourceAsStream("/exhale-build.properties")?.use { load(it) } }
        }
    }
}

data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** A GitHub release with a Windows installer attached. */
data class ReleaseInfo(
    val tag: String,
    val name: String?,
    val url: String,
    val notes: String = "",
    val publishedAt: Instant? = null,
    val installer: ReleaseAsset? = null,
    val checksums: ReleaseAsset? = null,
)

/** Where an in-app update download is. */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Downloading(val release: ReleaseInfo, val bytes: Long, val total: Long, val bytesPerSecond: Long) : UpdateDownload
    data class Verifying(val release: ReleaseInfo) : UpdateDownload
    data class Ready(val release: ReleaseInfo, val file: File, val verified: Boolean) : UpdateDownload
    data class Failed(val release: ReleaseInfo, val message: String) : UpdateDownload
}

/** How the app closes itself — set by Main, used when an update's installer takes over. */
object AppLifecycle {
    @Volatile var exit: (() -> Unit)? = null
}

/**
 * Updates from GitHub releases, as the Android app fetches its own. One release per version carries
 * both apps: the APK, and the Windows setup attached once it is built. Only releases with a setup
 * count here, so a release whose Windows build has not landed yet is not offered. Newest first.
 */
object UpdateChecker {
    private val http = OkHttpClient()
    private val UserAgent = "Exhale-Windows/${AppInfo.VERSION}"

    /** Set when a check finds a newer Windows release. */
    val available = MutableStateFlow<ReleaseInfo?>(null)

    /** When GitHub was last asked, successfully. */
    val lastChecked = MutableStateFlow(DesktopPrefs.raw("last_update_check")?.toLongOrNull())

    val download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)

    /** An older tag form (v1.0.xxx-windows); still read as its version wherever it turns up. */
    const val WindowsTagSuffix = "-windows"

    /** Windows releases, newest version first. */
    suspend fun releases(): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/ozyern/exhale/releases?per_page=40")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", UserAgent)
                .build()
            val body = http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub answered ${response.code}" }
                response.body.string()
            }
            fun JSONObject.text(key: String): String? = optString(key).takeUnless { it.isBlank() || it == "null" }
            val array = JSONArray(body)
            (0 until array.length()).map { array.getJSONObject(it) }
                .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                .mapNotNull { release ->
                    val assets = release.optJSONArray("assets") ?: JSONArray()
                    val files = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .map { ReleaseAsset(it.optString("name"), it.optString("browser_download_url"), it.optLong("size")) }
                    val installer = files.filter { it.name.endsWith(".exe", true) || it.name.endsWith(".msi", true) }
                        .minByOrNull { if (it.name.endsWith(".exe", true)) 0 else 1 } ?: return@mapNotNull null
                    ReleaseInfo(
                        tag = release.text("tag_name") ?: return@mapNotNull null,
                        name = release.text("name"),
                        url = release.text("html_url") ?: AppInfo.RELEASES_URL,
                        notes = release.text("body").orEmpty(),
                        publishedAt = release.text("published_at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                        installer = installer,
                        checksums = files.firstOrNull { it.name.contains("sha256", ignoreCase = true) },
                    )
                }
                // By version, not by publishing order: a re-published older release mustn't come first.
                .sortedWith { a, b -> if (isNewer(a.tag, b.tag)) -1 else if (isNewer(b.tag, a.tag)) 1 else 0 }
        }.onSuccess {
            val now = System.currentTimeMillis()
            lastChecked.value = now
            DesktopPrefs.putAll(mapOf("last_update_check" to now.toString()))
        }
    }

    suspend fun latest(): Result<ReleaseInfo> =
        releases().mapCatching { it.firstOrNull() ?: error("No Windows release has been published yet") }

    /** Downloads [release]'s installer to the temp folder, checking it against the release's SHA-256 list when there is one. */
    suspend fun downloadInstaller(release: ReleaseInfo) = withContext(Dispatchers.IO) {
        val asset = release.installer ?: return@withContext
        try {
            val folder = File(System.getProperty("java.io.tmpdir"), "Exhale-Update").apply { mkdirs() }
            val target = File(folder, asset.name)
            val partial = File(folder, asset.name + ".part")
            download.value = UpdateDownload.Downloading(release, 0, asset.size, 0)
            val request = Request.Builder().url(asset.url).header("User-Agent", UserAgent).build()
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub answered ${response.code}" }
                val total = response.body.contentLength().takeIf { it > 0 } ?: asset.size
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var done = 0L
                        var windowStart = System.nanoTime()
                        var windowBytes = 0L
                        var rate = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            windowBytes += read
                            val now = System.nanoTime()
                            if (now - windowStart > 600_000_000L) {
                                rate = windowBytes * 1_000_000_000L / (now - windowStart)
                                windowStart = now
                                windowBytes = 0
                            }
                            if (now - lastReport > 120_000_000L) {
                                lastReport = now
                                download.value = UpdateDownload.Downloading(release, done, total, rate)
                            }
                        }
                    }
                }
            }
            download.value = UpdateDownload.Verifying(release)
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val expected = release.checksums?.let { sums ->
                runCatching {
                    http.newCall(Request.Builder().url(sums.url).header("User-Agent", UserAgent).build()).execute().use { it.body.string() }
                }.getOrNull()
                    ?.lineSequence()
                    ?.map { it.trim() }
                    ?.firstOrNull { it.endsWith(asset.name) }
                    ?.substringBefore(' ')
                    ?.lowercase()
            }
            if (expected != null && expected != actual) {
                partial.delete()
                error("The download didn't match its published checksum. Try again.")
            }
            target.delete()
            check(partial.renameTo(target)) { "Couldn't save the installer" }
            download.value = UpdateDownload.Ready(release, target, verified = expected != null)
        } catch (e: CancellationException) {
            download.value = UpdateDownload.Idle
            throw e
        } catch (e: Exception) {
            download.value = UpdateDownload.Failed(release, e.message ?: "The download failed")
        }
    }

    /** Starts the downloaded installer and closes Exhale, so it can be replaced. False if Windows wouldn't start it. */
    fun install(installer: File): Boolean =
        runCatching {
            // An .msi isn't a program Windows will start by itself; msiexec runs it.
            val command = if (installer.name.endsWith(".msi", ignoreCase = true)) {
                listOf("msiexec", "/i", installer.absolutePath)
            } else {
                listOf(installer.absolutePath)
            }
            ProcessBuilder(command).start()
        }
            .onSuccess { AppLifecycle.exit?.invoke() }
            .isSuccess

    /** Whether [tag] (v1.0.xxx-windows, or a bare version) is a later version than [than]. */
    fun isNewer(tag: String, than: String = AppInfo.VERSION): Boolean {
        fun parts(version: String) =
            version.trimStart('v', 'V').removeSuffix(WindowsTagSuffix).split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(tag)
        val b = parts(than)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/** Caches on disk, for the Storage settings. */
object AppStorage {
    val imageCacheDir: File get() = File(DesktopPrefs.dataDir, "image-cache")
    val browserDir: File get() = File(DesktopPrefs.dataDir, "jcef")

    fun sizeOf(dir: File): Long = if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun format(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format("%.0f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    fun clearImageCache() {
        runCatching {
            val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }

    fun deleteBrowserData(): Boolean = browserDir.deleteRecursively()
}

/** Talking to Windows: opening links and picking files. */
object SystemShell {
    fun open(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    /** Swing's chooser, because AWT's FileDialog can't pick a folder on Windows. */
    fun chooseFolder(title: String): File? {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun chooseFile(title: String, save: Boolean, suggestedName: String = ""): File? {
        val dialog = FileDialog(null as Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
        if (suggestedName.isNotEmpty()) dialog.file = suggestedName
        dialog.isVisible = true
        return dialog.files.firstOrNull()
    }
}
