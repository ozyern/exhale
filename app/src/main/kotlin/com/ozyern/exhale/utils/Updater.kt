/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.App
import com.ozyern.exhale.constants.GitHubReleasesEtagKey
import com.ozyern.exhale.constants.GitHubReleasesFingerprintKey
import com.ozyern.exhale.constants.GitHubReleasesJsonKey
import com.ozyern.exhale.constants.GitHubReleasesLastCheckedAtKey
import com.ozyern.exhale.constants.UpdateChannel
import com.ozyern.exhale.constants.UpdateChannelKey
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class GitCommit(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
    val url: String
)

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String?,
    val publishedAt: String,
    val htmlUrl: String
)

data class NightlyInfo(
    val versionName: String,
    val apkUrl: String,
    val changelog: String?,
    val publishedAt: String
)

/**
 * Resultado de [Updater.checkForUpdate].
 *
 * @param tagName        Tag del release en GitHub (ej. "v1.4.2")
 * @param versionName    Versión normalizada sin prefijo "v" (ej. "1.4.2")
 * @param downloadUrl    Direct URL to the release's universal APK
 * @param releasePageUrl URL de la página HTML del release en GitHub
 * @param releaseNotes   Changelog / body del release (puede ser null)
 * @param publishedAt    Fecha de publicación en ISO 8601
 */
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val releaseNotes: String?,
    val publishedAt: String,
)

/**
 * A snapshot of an in-flight APK download.
 *
 * [totalBytes] is `-1` when the server sends no `Content-Length` — a real case for redirected
 * release assets and for the nightly R2 bucket. That is modelled as a *nullable [fraction]* rather
 * than the more obvious "emit -1f", so the UI cannot accidentally render a negative bar; a null
 * fraction is the explicit signal to show an indeterminate indicator instead.
 */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float?
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            null
        }
}

/**
 * The release asset the updater downloads: `Exhale-1.0.103.apk`.
 *
 * One APK per release, carrying the build it is. Splitting by ABI saved about a
 * megabyte and cost a decision nobody outside this repository can make — a release
 * page that asks which of arm64, armeabi-v7a, x86 and x86_64 you are is a page most
 * people close. The universal build runs everywhere.
 *
 * The name carries the version, which means `releases/latest/download/<name>` can no
 * longer be hard-coded — see [getLatestDownloadUrl]. That is the right trade: the
 * updater resolves assets through the API anyway, and a filename in someone's
 * Downloads folder should say what it is.
 */
private fun apkAssetNameFor(versionName: String): String = "Exhale-$versionName.apk"

/**
 * Anything that would also do.
 *
 * An updater that can only recognise one spelling breaks the day an asset is renamed,
 * and the app in the field is the copy that cannot be fixed. So resolution degrades:
 * the exact name for this version, then any APK at all — which is unambiguous now
 * that a release carries exactly one.
 */
private fun isApkAsset(name: String): Boolean = name.endsWith(".apk", ignoreCase = true)

/** 64 KB reads — large enough that the syscall overhead disappears against network latency. */
private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

/**
 * Only surface progress every 512 KB. On a ~100 MB APK that is ~200 UI updates over the whole
 * download, which is smooth to the eye while keeping recomposition off the critical path.
 */
private const val DOWNLOAD_EMIT_INTERVAL_BYTES = 512 * 1024L
private const val NIGHTLY_JSON_URL = "https://pub-2218e6bbd5b948e1b5d882cf4d92086d.r2.dev/update.json"

private data class ReleasesNetworkResult(
    val status: HttpStatusCode,
    val body: String?,
    val etag: String?,
)

object Updater {
    private val client = HttpClient()
    private const val ReleaseCacheCheckIntervalMs: Long = 6 * 60 * 60 * 1000L
    var lastCheckTime = -1L
        private set

    private var cachedNightlyInfo: NightlyInfo? = null

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: List<PreReleaseIdentifier>,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            val majorCompare = major.compareTo(other.major)
            if (majorCompare != 0) return majorCompare
            val minorCompare = minor.compareTo(other.minor)
            if (minorCompare != 0) return minorCompare
            val patchCompare = patch.compareTo(other.patch)
            if (patchCompare != 0) return patchCompare

            val thisIsStable = preRelease.isEmpty()
            val otherIsStable = other.preRelease.isEmpty()
            if (thisIsStable && !otherIsStable) return 1
            if (!thisIsStable && otherIsStable) return -1

            val maxIndex = minOf(preRelease.size, other.preRelease.size)
            for (i in 0 until maxIndex) {
                val c = preRelease[i].compareTo(other.preRelease[i])
                if (c != 0) return c
            }
            return preRelease.size.compareTo(other.preRelease.size)
        }

        fun normalizedName(): String =
            if (preRelease.isEmpty()) {
                "$major.$minor.$patch"
            } else {
                "$major.$minor.$patch-" + preRelease.joinToString(".") { it.raw }
            }
    }

    private sealed interface PreReleaseIdentifier : Comparable<PreReleaseIdentifier> {
        val raw: String
    }

    private data class NumericIdentifier(
        override val raw: String,
        val value: Long,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is NumericIdentifier -> value.compareTo(other.value)
                is AlphaIdentifier -> -1
            }
    }

    private data class AlphaIdentifier(
        override val raw: String,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is NumericIdentifier -> 1
                is AlphaIdentifier -> raw.compareTo(other.raw)
            }
    }

    private val semVerRegex =
        Regex("""(?i)\bv?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\b""")

    private fun parseSemVerOrNull(text: String): SemVer? {
        val match = semVerRegex.find(text) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        val preReleaseText = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
        val preRelease =
            preReleaseText
                ?.split('.')
                ?.filter { it.isNotBlank() }
                ?.map { identifier ->
                    if (identifier.all { it.isDigit() }) {
                        NumericIdentifier(raw = identifier, value = identifier.toLong())
                    } else {
                        AlphaIdentifier(raw = identifier)
                    }
                }
                ?: emptyList()
        return SemVer(
            major = major,
            minor = minor,
            patch = patch,
            preRelease = preRelease,
        )
    }

    private fun parseReleaseSemVerOrNull(release: ReleaseInfo): SemVer? =
        parseSemVerOrNull(release.tagName) ?: parseSemVerOrNull(release.name)

    internal fun isSameVersion(a: String, b: String): Boolean {
        val aSemVer = parseSemVerOrNull(a)
        val bSemVer = parseSemVerOrNull(b)
        return if (aSemVer != null && bSemVer != null) {
            aSemVer.major == bSemVer.major &&
                    aSemVer.minor == bSemVer.minor &&
                    aSemVer.patch == bSemVer.patch &&
                    aSemVer.preRelease == bSemVer.preRelease
        } else {
            a.trim() == b.trim()
        }
    }

    /** Major version line of the installed app (e.g. 1 for "1.0.101-beta"). */
    private fun currentAppMajor(): Int? = parseSemVerOrNull(BuildConfig.VERSION_NAME)?.major

    /**
     * True only when [latestVersionName] is a STRICTLY newer version than
     * [currentVersionName] within the SAME major line. Anything outside the app's
     * current major line (e.g. the old Exhale 3.x history) is ignored, so a stale
     * or foreign release never triggers a bogus "update available".
     */
    fun hasUpdate(latestVersionName: String, currentVersionName: String): Boolean {
        val current = parseSemVerOrNull(currentVersionName)
        val latest = parseSemVerOrNull(latestVersionName)
        if (current == null || latest == null) {
            // Fall back to a plain inequality only when we can't parse semver.
            return latestVersionName.trim().isNotEmpty() &&
                    latestVersionName.trim() != currentVersionName.trim()
        }
        if (latest.major != current.major) return false // ignore old/foreign lineage
        return latest > current
    }

    internal fun findLatestRelease(releases: List<ReleaseInfo>): ReleaseInfo? {
        if (releases.isEmpty()) return null
        val appMajor = currentAppMajor()
        val parsed =
            releases
                .mapNotNull { release ->
                    parseReleaseSemVerOrNull(release)?.let { version -> version to release }
                }
                // Only keep releases in the app's current major line. This discards the
                // old Exhale version history (2.x/3.x) entirely.
                .filter { appMajor == null || it.first.major == appMajor }

        // No release in our lineage -> no valid "latest". Do NOT fall back to an
        // arbitrary (possibly old-lineage) release.
        if (parsed.isEmpty()) return null

        val stable = parsed.filter { it.first.preRelease.isEmpty() }
        val candidates = stable.ifEmpty { parsed }
        return candidates.maxWithOrNull(compareBy({ it.first }, { it.second.publishedAt }))?.second
    }

    private fun preferredReleaseVersionNameOrNull(release: ReleaseInfo): String? =
        parseReleaseSemVerOrNull(release)?.normalizedName()

    private fun parseReleasesJson(json: String): List<ReleaseInfo> {
        val jsonArray = JSONArray(json)
        val releases = ArrayList<ReleaseInfo>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            releases.add(
                ReleaseInfo(
                    tagName = item.optString("tag_name", ""),
                    name = item.optString("name", ""),
                    body = if (item.has("body")) item.optString("body") else null,
                    publishedAt = item.optString("published_at", ""),
                    htmlUrl = item.optString("html_url", "")
                )
            )
        }
        return releases
    }

    private fun getTopReleaseFingerprint(releases: List<ReleaseInfo>): String {
        val latest = findLatestRelease(releases) ?: return ""
        return listOf(
            latest.tagName,
            latest.name,
            latest.publishedAt,
            latest.body.orEmpty(),
            latest.htmlUrl,
        ).joinToString("||")
    }

    private suspend fun fetchReleasesNetwork(
        perPage: Int,
        cachedEtag: String?,
    ): ReleasesNetworkResult {
        val response: HttpResponse =
            client.get("https://api.github.com/repos/ozyern/Exhale/releases?per_page=$perPage") {
                headers {
                    append("Accept", "application/vnd.github+json")
                    append("User-Agent", "Exhale")
                    if (!cachedEtag.isNullOrBlank()) {
                        append("If-None-Match", cachedEtag)
                    }
                }
            }
        val etag = response.headers["ETag"]
        return when (response.status) {
            HttpStatusCode.NotModified ->
                ReleasesNetworkResult(
                    status = response.status,
                    body = null,
                    etag = cachedEtag ?: etag,
                )
            else ->
                ReleasesNetworkResult(
                    status = response.status,
                    body = response.bodyAsText(),
                    etag = etag,
                )
        }
    }

    // ─── Canal helpers ─────────────────────────────────────────────────────────

    private suspend fun getCurrentUpdateChannel(): UpdateChannel {
        val channelName = App.instance.dataStore.getAsync(UpdateChannelKey) ?: UpdateChannel.STABLE.name
        return runCatching { UpdateChannel.valueOf(channelName) }.getOrDefault(UpdateChannel.STABLE)
    }

    private suspend fun fetchNightlyJson(): Result<NightlyInfo> = runCatching {
        val response = client.get(NIGHTLY_JSON_URL) {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "Exhale")
            }
        }.bodyAsText()
        val json = JSONObject(response)

        // El JSON está en /nightly/update.json pero el APK debe servirse sin /nightly/
        val rawApkUrl = json.optString("apkUrl", "")
        val fixedApkUrl = rawApkUrl.replace(
            "https://pub-2218e6bbd5b948e1b5d882cf4d92086d.r2.dev/",
            "https://pub-2218e6bbd5b948e1b5d882cf4d92086d.r2.dev/"
        )

        NightlyInfo(
            versionName = json.optString("versionName", "unknown"),
            apkUrl = fixedApkUrl.ifEmpty {
                "https://pub-2218e6bbd5b948e1b5d882cf4d92086d.r2.dev/app-universal-release.apk"
            },
            changelog = json.optString("changelog", "").takeIf { it.isNotEmpty() },
            publishedAt = json.optString("publishedAt", "")
        )
    }

    // ─── Funciones públicas (compatibilidad obligatoria) ───────────────────────

    suspend fun getCachedReleases(): List<ReleaseInfo> {
        if (getCurrentUpdateChannel() == UpdateChannel.NIGHTLY) return emptyList()
        val cachedJson = App.instance.dataStore.getAsync(GitHubReleasesJsonKey)
        return cachedJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun getLatestVersionName(): Result<String> = runCatching {
        when (getCurrentUpdateChannel()) {
            UpdateChannel.STABLE -> {
                val latest = getLatestReleaseInfo().getOrThrow()
                preferredReleaseVersionNameOrNull(latest) ?: latest.name.ifBlank { latest.tagName }
            }
            UpdateChannel.NIGHTLY -> {
                fetchNightlyJson().getOrThrow().versionName
            }
        }
    }

    suspend fun getLatestReleaseNotes(): Result<String?> = runCatching {
        when (getCurrentUpdateChannel()) {
            UpdateChannel.STABLE -> getLatestReleaseInfo().getOrThrow().body
            UpdateChannel.NIGHTLY -> fetchNightlyJson().getOrThrow().changelog
        }
    }

    suspend fun getLatestReleaseInfo(): Result<ReleaseInfo> = runCatching {
        when (getCurrentUpdateChannel()) {
            UpdateChannel.STABLE -> {
                val releases = getAllReleases().getOrThrow()
                val latest = findLatestRelease(releases)
                    ?: throw IllegalStateException("No releases found")
                lastCheckTime = System.currentTimeMillis()
                latest
            }
            UpdateChannel.NIGHTLY -> {
                val nightly = fetchNightlyJson().getOrThrow()
                cachedNightlyInfo = nightly
                ReleaseInfo(
                    tagName = nightly.versionName,
                    name = nightly.versionName,
                    body = nightly.changelog,
                    publishedAt = nightly.publishedAt,
                    htmlUrl = nightly.apkUrl
                )
            }
        }
    }

    // ─── Comprobación de actualización ─────────────────────────────────────────

    /**
     * Comprueba si hay una versión más reciente que [currentVersionName].
     *
     * - Detecta el canal activo automáticamente.
     * - STABLE: Reutiliza la caché existente (ETag / DataStore).
     * - NIGHTLY: Consulta el JSON remoto de Cloudflare R2.
     * - Devuelve `null` dentro del [Result] cuando ya se tiene la versión más
     *   reciente instalada.
     *
     * [forceRefresh] skips the six-hour release cache. A check someone asked for
     * has to ask GitHub: answered from the cache, a release published after the
     * last background check stays invisible for up to six hours, and "Exhale is up
     * to date" is the wrong thing to tell someone looking at the release page. It
     * costs little — the cached ETag goes with it, so an unchanged list comes back
     * as a 304 with no body.
     */
    suspend fun checkForUpdate(
        currentVersionName: String,
        forceRefresh: Boolean = false,
    ): Result<UpdateInfo?> =
        runCatching {
            when (getCurrentUpdateChannel()) {
                UpdateChannel.STABLE -> checkForUpdateStable(currentVersionName, forceRefresh)
                UpdateChannel.NIGHTLY -> checkForUpdateNightly(currentVersionName)
            }
        }

    private suspend fun checkForUpdateStable(
        currentVersionName: String,
        forceRefresh: Boolean,
    ): UpdateInfo? {
        // A repository with no release in this major line is not an error — there is
        // simply nothing newer, which is the same answer as "you are up to date". It
        // was being reported as "update check failed", which reads as a broken app to
        // anyone who installed before the first release was published, and to anyone
        // whose network let the request through to an empty list.
        val releases = getAllReleases(forceRefresh = forceRefresh).getOrThrow()
        val latest = findLatestRelease(releases) ?: return null
        lastCheckTime = System.currentTimeMillis()

        val latestVersionName =
            preferredReleaseVersionNameOrNull(latest)
                ?: latest.name.ifBlank { latest.tagName }

        // Only proceed when the release is strictly newer in our major line.
        if (!hasUpdate(latestVersionName, currentVersionName)) return null

        val downloadUrl = resolveApkDownloadUrl(latest.tagName)

        return UpdateInfo(
            tagName        = latest.tagName,
            versionName    = latestVersionName,
            downloadUrl    = downloadUrl,
            releasePageUrl = latest.htmlUrl,
            releaseNotes   = latest.body,
            publishedAt    = latest.publishedAt,
        )
    }

    private suspend fun checkForUpdateNightly(currentVersionName: String): UpdateInfo? {
        val nightly = fetchNightlyJson().getOrThrow()
        return if (isSameVersion(nightly.versionName, currentVersionName)) {
            null
        } else {
            cachedNightlyInfo = nightly
            UpdateInfo(
                tagName        = nightly.versionName,
                versionName    = nightly.versionName,
                downloadUrl    = nightly.apkUrl,
                releasePageUrl = nightly.apkUrl,
                releaseNotes   = nightly.changelog,
                publishedAt    = nightly.publishedAt,
            )
        }
    }

    /**
     * Finds the APK attached to the release tagged [tagName].
     *
     * Three passes, in order of confidence: the exact asset name we publish, then any
     * universal APK, then any APK. The looser passes exist because this code ships
     * inside the build being replaced — if a future release names its asset something
     * else, the copy already on someone's phone still has to find it, and that copy
     * can no longer be edited.
     *
     * If the call fails entirely, the canonical GitHub download URL is used, which is
     * correct whenever the asset does carry the expected name.
     */
    private suspend fun resolveApkDownloadUrl(tagName: String): String {
        val version = tagName.removePrefix("v")
        val fallback =
            "https://github.com/ozyern/Exhale/releases/download/$tagName/${apkAssetNameFor(version)}"

        return runCatching {
            val response = client.get(
                "https://api.github.com/repos/ozyern/Exhale/releases/tags/$tagName"
            ) {
                headers {
                    append("Accept", "application/vnd.github+json")
                    append("User-Agent", "Exhale")
                }
            }.bodyAsText()

            val assets = JSONObject(response).optJSONArray("assets") ?: return@runCatching fallback

            val names = ArrayList<Pair<String, String>>(assets.length())
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.isNotBlank() && url.isNotBlank()) names.add(name to url)
            }

            names.firstOrNull { it.first.equals(apkAssetNameFor(version), ignoreCase = true) }?.second
                ?: names.firstOrNull { isApkAsset(it.first) }?.second
                ?: fallback
        }.getOrDefault(fallback)
    }

    // ── In-app APK update ────────────────────────────────────────────────────

    /**
     * Streams the APK at [url] into [destinationFile], emitting progress as it goes.
     *
     * The flow completes when the file is fully written; collect it to completion before calling
     * [installApk]. On any failure the partially written file is deleted rather than left behind —
     * a truncated APK would fail to parse at install time with a confusing "app not installed"
     * error, and would be silently reused if the user retried.
     *
     * Emissions are throttled (see [DownloadProgress]); a 100 MB APK read in 64 KB chunks would
     * otherwise emit ~1,600 times, and every emission recomposes the progress UI for a change too
     * small to see.
     */
    fun downloadApk(url: String, destinationFile: File): Flow<DownloadProgress> = flow {
        destinationFile.parentFile?.mkdirs()
        try {
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    error("Download failed: HTTP ${response.status.value}")
                }

                val totalBytes = response.contentLength() ?: -1L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                var downloadedBytes = 0L
                var lastEmittedBytes = 0L

                emit(DownloadProgress(0L, totalBytes))

                FileOutputStream(destinationFile).use { output ->
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (downloadedBytes - lastEmittedBytes >= DOWNLOAD_EMIT_INTERVAL_BYTES) {
                            lastEmittedBytes = downloadedBytes
                            emit(DownloadProgress(downloadedBytes, totalBytes))
                        }
                    }
                    output.fd.sync()
                }

                // Always land on a final, exact emission — the throttle above will usually have
                // skipped the last partial chunk, leaving the bar short of 100%.
                emit(DownloadProgress(downloadedBytes, totalBytes))
            }
        } catch (e: Throwable) {
            destinationFile.delete()
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Hands [apkFile] to the system package installer.
     *
     * Returns `false` when the app is not allowed to install packages, which is the default on
     * API 26+. There is no way to install without that grant, so the caller must send the user to
     * the system settings page rather than firing an intent that silently does nothing — the
     * failure mode this guards against is a button that appears to work and simply never installs.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * Opens the system screen where the user grants this app permission to install packages.
     * Paired with [installApk] returning `false`.
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getCommitHistory(count: Int = 20, branch: String = "master"): Result<List<GitCommit>> =
        runCatching {
            val response =
                client.get("https://api.github.com/repos/ozyern/Exhale/commits?sha=$branch&per_page=$count") {
                    headers {
                        append("Accept", "application/vnd.github+json")
                        append("User-Agent", "Exhale")
                    }
                }.bodyAsText()
            val jsonArray = JSONArray(response)
            val commits = mutableListOf<GitCommit>()
            for (i in 0 until jsonArray.length()) {
                val commitObj = jsonArray.getJSONObject(i)
                val commit = commitObj.getJSONObject("commit")
                val authorObj = commit.optJSONObject("author")
                commits.add(
                    GitCommit(
                        sha = commitObj.optString("sha", "").take(7),
                        message = commit.optString("message", "").lines().firstOrNull() ?: "",
                        author = authorObj?.optString("name", "Unknown") ?: "Unknown",
                        date = authorObj?.optString("date", "") ?: "",
                        url = commitObj.optString("html_url", "")
                    )
                )
            }
            commits
        }

    fun getLatestDownloadUrl(): String {
        val channel = runBlocking {
            kotlin.runCatching { getCurrentUpdateChannel() }.getOrDefault(UpdateChannel.STABLE)
        }
        return when (channel) {
            UpdateChannel.STABLE -> {
                // The release page, not a direct file. `releases/latest/download/<name>`
                // needs a name that never changes, and the asset is named after its
                // version now — a guessed URL would 404 the moment a version ships that
                // this build has never heard of, which is every version after this one.
                // The page always exists and is one tap from the APK.
                "https://github.com/ozyern/Exhale/releases/latest"
            }
            UpdateChannel.NIGHTLY -> {
                cachedNightlyInfo?.apkUrl
                    ?: "https://pub-2218e6bbd5b948e1b5d882cf4d92086d.r2.dev/app-universal-release.apk"
            }
        }
    }

    suspend fun getAllReleases(
        perPage: Int = 30,
        forceRefresh: Boolean = false,
    ): Result<List<ReleaseInfo>> =
        runCatching {
            if (getCurrentUpdateChannel() == UpdateChannel.NIGHTLY) {
                return@runCatching emptyList()
            }

            val now = System.currentTimeMillis()
            val cachedJson = App.instance.dataStore.getAsync(GitHubReleasesJsonKey)
            val cachedEtag = App.instance.dataStore.getAsync(GitHubReleasesEtagKey)
            val lastCheckedAt = App.instance.dataStore.getAsync(GitHubReleasesLastCheckedAtKey, 0L)
            val cachedFingerprint = App.instance.dataStore.getAsync(GitHubReleasesFingerprintKey)

            val cachedReleases =
                cachedJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }

            val shouldCheckNetwork =
                forceRefresh || cachedJson.isNullOrBlank() || (now - lastCheckedAt) >= ReleaseCacheCheckIntervalMs

            if (!shouldCheckNetwork) {
                lastCheckTime = now
                return@runCatching cachedReleases ?: emptyList()
            }

            val networkResult = runCatching {
                fetchReleasesNetwork(perPage = perPage, cachedEtag = cachedEtag)
            }.getOrNull()

            if (networkResult == null) {
                val fallback = cachedReleases
                if (fallback != null) {
                    lastCheckTime = now
                    return@runCatching fallback
                }
                throw IllegalStateException("Failed to fetch releases")
            }

            when {
                networkResult.status == HttpStatusCode.NotModified -> {
                    App.instance.dataStore.edit { settings ->
                        settings[GitHubReleasesLastCheckedAtKey] = now
                        networkResult.etag?.let { settings[GitHubReleasesEtagKey] = it }
                    }
                    val fallback = cachedReleases
                    if (fallback != null) {
                        lastCheckTime = now
                        return@runCatching fallback
                    }
                    throw IllegalStateException("Release cache is empty")
                }

                networkResult.status.value in 200..299 && !networkResult.body.isNullOrBlank() -> {
                    val networkBody = networkResult.body
                    val releases = parseReleasesJson(networkBody)
                    val newFingerprint = getTopReleaseFingerprint(releases)
                    val hasPayloadChanged = cachedJson != networkBody
                    val hasTopReleaseChanged = cachedFingerprint != newFingerprint

                    App.instance.dataStore.edit { settings ->
                        settings[GitHubReleasesLastCheckedAtKey] = now
                        networkResult.etag?.let { settings[GitHubReleasesEtagKey] = it }
                        if (hasPayloadChanged || hasTopReleaseChanged || cachedJson.isNullOrBlank()) {
                            settings[GitHubReleasesJsonKey] = networkBody
                            settings[GitHubReleasesFingerprintKey] = newFingerprint
                        }
                    }
                    lastCheckTime = now
                    releases
                }

                else -> {
                    val fallback = cachedReleases
                    if (fallback != null) {
                        lastCheckTime = now
                        fallback
                    } else {
                        throw IllegalStateException("Failed to fetch releases: HTTP ${networkResult.status.value}")
                    }
                }
            }
        }
}