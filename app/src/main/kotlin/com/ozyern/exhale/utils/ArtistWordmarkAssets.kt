package com.ozyern.exhale.utils

import android.content.Context
import com.ozyern.exhale.canvas.AppleMusicCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Persistent artist wordmark files, kept outside Coil's evictable image cache. */
object ArtistWordmarkAssets {
    data class Bundle(val appleLogo: File?, val signature: File?, val signatureUrl: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)

    /** Start saving wordmarks for artists attached to an album or playlist download. */
    fun prefetch(context: Context, artistNames: Iterable<String?>) {
        val appContext = context.applicationContext
        artistNames.asSequence().filterNotNull().map(String::trim).filter(String::isNotBlank)
            .distinctBy(AppleMusicCatalog::normalize).forEach { name ->
                scope.launch { gate.withPermit { bundle(appContext, name) } }
            }
    }

    fun saved(context: Context, artistName: String): Bundle {
        val directory = directory(context, artistName)
        val signature = directory.resolve("signature.svg").takeIf(File::isFile)
        return Bundle(
            appleLogo = directory.resolve("apple-logo.png").takeIf(File::isFile),
            signature = signature,
            signatureUrl = signature?.toURI()?.toString(),
        )
    }

    suspend fun bundle(context: Context, artistName: String): Bundle = withContext(Dispatchers.IO) {
        val name = artistName.trim()
        if (name.isBlank()) return@withContext Bundle(null, null)
        val directory = directory(context, name)
        val lock = locks.getOrPut(directory.path) { Mutex() }
        lock.withLock {
            directory.mkdirs()
            var logo = directory.resolve("apple-logo.png").takeIf(File::isFile)
            var signature = directory.resolve("signature.svg").takeIf(File::isFile)

            if (logo == null) {
                runCatching { AppleMusicCatalog.artist(name)?.logo?.sized(600, png = true) }
                    .getOrNull()?.let { url ->
                        download(url, directory.resolve("apple-logo.png"))
                        logo = directory.resolve("apple-logo.png").takeIf(File::isFile)
                    }
            }
            if (signature == null) {
                val signatureUrl = ArtistSignature.findUrl(name)
                signatureUrl?.let { url ->
                    download(url, directory.resolve("signature.svg"))
                    signature = directory.resolve("signature.svg").takeIf(File::isFile)
                }
                if (signature == null) return@withLock Bundle(logo, null, signatureUrl)
            }
            Bundle(logo, signature, signature?.toURI()?.toString())
        }
    }

    private fun download(url: String, destination: File) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                if (body.contentLength() > MAX_ASSET_BYTES) return
                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size > MAX_ASSET_BYTES) return
                val temporary = File(destination.parentFile, destination.name + ".tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    error("Could not save artist wordmark asset")
                }
            }
        }
    }

    private fun directory(context: Context, name: String): File {
        val normalized = AppleMusicCatalog.normalize(name)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.applicationContext.filesDir, "artist-wordmarks/$digest")
    }

    private const val MAX_ASSET_BYTES = 3 * 1024 * 1024
}
