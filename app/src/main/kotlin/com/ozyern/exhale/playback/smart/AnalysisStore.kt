package com.ozyern.exhale.playback.smart

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists compact track analyses so playback does not repeat decoding and
 * model inference after a restart. Each track has its own file; only planner
 * fields are stored, with curves rounded to milliseconds.
 */
class AnalysisStore(private val context: Context) {

    /**
     * Resolved on first use, not at construction. [TrackAnalyzer] is a field
     * initializer on the playback service, which runs before the service has a
     * base context attached — asking for [Context.getFilesDir] there returns
     * null and takes the whole process down before it can start.
     */
    private val directory by lazy { File(context.filesDir, DIRECTORY) }

    /**
     * Track ids known to have no file, so a track analysed in neither this
     * session nor a previous one does not hit the filesystem on every tick.
     * Only ever grows within a session, and a write clears the entry.
     */
    private val known = ConcurrentHashMap<String, Boolean>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Reads [trackId]'s stored analysis, or null when there isn't one.
     *
     * A device song keeps its analysis when it is moved or renamed: its id (the file's uri) changes
     * but its [fingerprint] does not, so the entry stored under the old id is found through the alias.
     */
    fun load(trackId: String, fingerprint: (() -> String?)? = null): TrackAnalysis? {
        val direct = loadStored(trackId)
        if (direct != null || fingerprint == null) return direct
        val print = fingerprint() ?: return null
        val previousId = runCatching { File(directory, aliasName(print)).readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != trackId } ?: return null
        val moved = loadStored(previousId) ?: return null
        save(trackId, moved, print)
        return moved
    }

    private fun loadStored(trackId: String): TrackAnalysis? {
        if (trackId.isBlank()) return null
        if (known[trackId] == false) return null
        val file = File(directory, fileNameFor(trackId))
        if (!file.exists()) {
            known[trackId] = false
            return null
        }
        return runCatching {
            val stored = json.decodeFromString(Stored.serializer(), file.readText())
            // Older analysis may use different calculations, so re-analyze it instead of migrating.
            require(stored.version == SCHEMA_VERSION) { "schema ${stored.version}" }
            stored.toAnalysis(trackId)
        }
            .onFailure {
                // Discard unreadable data and let the next request re-analyze it.
                Log.w(TAG, "Discarding unreadable analysis for $trackId", it)
                file.delete()
                known[trackId] = false
            }
            .getOrNull()
    }

    /**
     * Stores [analysis]. Silently does nothing for a result with no usable
     * tempo: a failure is cheap to rediscover and worth rediscovering, since
     * the reason for it is usually missing bytes rather than the track itself.
     */
    fun save(trackId: String, analysis: TrackAnalysis, fingerprint: String? = null) {
        if (trackId.isBlank() || !analysis.isUsable) return
        runCatching {
            directory.mkdirs()
            fingerprint?.let { File(directory, aliasName(it)).writeText(trackId) }
            val file = File(directory, fileNameFor(trackId))
            // Written aside and renamed, so a kill mid-write leaves the old
            // entry rather than a truncated one.
            val temporary = File(directory, file.name + ".tmp")
            temporary.writeText(json.encodeToString(Stored.serializer(), Stored.of(analysis)))
            if (!temporary.renameTo(file)) temporary.delete()
            known[trackId] = true
        }.onFailure { Log.w(TAG, "Could not store analysis for $trackId", it) }
        prune()
    }

    /**
     * Keeps the directory under [MAX_ENTRIES], oldest first.
     *
     * Cheap because it only lists when the count is plausibly over — a
     * directory listing per save would otherwise be a filesystem walk on every
     * analysis.
     */
    private fun prune() {
        val files = directory.listFiles { file -> file.extension == "json" } ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ENTRIES)
            .forEach { it.delete() }
    }

    private fun aliasName(fingerprint: String): String = "fp_$fingerprint.alias"

    /** Hashed rather than used raw: a track id is not guaranteed to be a legal filename. */
    private fun fileNameFor(trackId: String): String = "${trackId.hashCode().toUInt()}_${trackId.length}.json"

    /**
     * The persisted subset, kept separate from [TrackAnalysis] so that adding a
     * field to the in-memory type is not silently a schema change.
     *
     * [version] is checked on read through [ignoreUnknownKeys] plus an explicit
     * comparison: an entry written by an older build may hold numbers computed a
     * different way, and a wrong beat grid is worse than no beat grid.
     */
    @Serializable
    private data class Stored(
        val version: Int = SCHEMA_VERSION,
        val duration: Double = 0.0,
        val bpm: Double = 0.0,
        val beatInterval: Double = 0.0,
        val beatConfidence: Double = 0.0,
        val firstBeat: Double = 0.0,
        val downbeats: List<Double> = emptyList(),
        val phraseBoundaries: List<Double> = emptyList(),
        val key: String = "",
        val keyConfidence: Double = 0.0,
        val audibleStartTime: Double? = null,
        val pickupTime: Double? = null,
        val introEndTime: Double = 0.0,
        val outroStartTime: Double = 0.0,
        val contentEndTime: Double = 0.0,
        val mixInTime: Double = 0.0,
        val mixOutTime: Double = 0.0,
        val mixInCandidates: List<StoredCue> = emptyList(),
        val mixOutCandidates: List<StoredCue> = emptyList(),
        val energyCurve: List<StoredEnergy> = emptyList(),
        val lowEnergyCurve: List<StoredEnergy> = emptyList(),
        val vocalActivityMask: List<Double> = emptyList(),
        val vocalProbability: Double = 0.0,
    ) {
        fun toAnalysis(trackId: String) = TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = duration,
            bpm = bpm,
            beatInterval = beatInterval,
            beatConfidence = beatConfidence,
            firstBeat = firstBeat,
            downbeats = downbeats,
            phraseBoundaries = phraseBoundaries,
            key = key,
            keyConfidence = keyConfidence,
            audibleStartTime = audibleStartTime,
            pickupTime = pickupTime,
            introEndTime = introEndTime,
            outroStartTime = outroStartTime,
            contentEndTime = contentEndTime,
            mixInTime = mixInTime,
            mixOutTime = mixOutTime,
            mixInCandidates = mixInCandidates.map { it.toCue() },
            mixOutCandidates = mixOutCandidates.map { it.toCue() },
            energyCurve = energyCurve.map { it.toSample() },
            lowEnergyCurve = lowEnergyCurve.map { it.toSample() },
            vocalActivityMask = vocalActivityMask,
            vocalProbability = vocalProbability,
        )

        companion object {
            fun of(analysis: TrackAnalysis) = Stored(
                duration = analysis.duration,
                bpm = analysis.bpm,
                beatInterval = analysis.beatInterval,
                beatConfidence = analysis.beatConfidence,
                firstBeat = analysis.firstBeat,
                downbeats = analysis.downbeats.map(::round),
                phraseBoundaries = analysis.phraseBoundaries.map(::round),
                key = analysis.key,
                keyConfidence = analysis.keyConfidence,
                audibleStartTime = analysis.audibleStartTime,
                pickupTime = analysis.pickupTime,
                introEndTime = analysis.introEndTime,
                outroStartTime = analysis.outroStartTime,
                contentEndTime = analysis.contentEndTime,
                mixInTime = analysis.mixInTime,
                mixOutTime = analysis.mixOutTime,
                mixInCandidates = analysis.mixInCandidates.map(StoredCue::of),
                mixOutCandidates = analysis.mixOutCandidates.map(StoredCue::of),
                energyCurve = analysis.energyCurve.map(StoredEnergy::of),
                lowEnergyCurve = analysis.lowEnergyCurve.map(StoredEnergy::of),
                vocalActivityMask = analysis.vocalActivityMask.map(::round),
                vocalProbability = analysis.vocalProbability,
            )
        }
    }

    @Serializable
    private data class StoredCue(val time: Double, val score: Double, val type: String) {
        fun toCue() = MixCandidate(time = time, score = score, type = type)

        companion object {
            fun of(cue: MixCandidate) = StoredCue(round(cue.time), round(cue.score), cue.type)
        }
    }

    @Serializable
    private data class StoredEnergy(val time: Double, val energy: Double) {
        fun toSample() = EnergySample(time = time, energy = energy)

        companion object {
            fun of(sample: EnergySample) = StoredEnergy(round(sample.time), round(sample.energy))
        }
    }

    private companion object {
        const val TAG = "ExhaleAnalysisStore"
        const val DIRECTORY = "smart_analysis"

        /**
         * Bump whenever a stored number starts being computed differently.
         * Entries from an older schema are ignored rather than migrated: a
         * re-analysis costs seconds, and a beat grid interpreted under the wrong
         * assumptions is silently wrong for the life of the file.
         */
        const val SCHEMA_VERSION = 1

        /** Sized so a whole library analysed in one pass is not evicted as it is written. */
        const val MAX_ENTRIES = 6_000

        /** Milliseconds is finer than anything downstream distinguishes. */
        fun round(value: Double): Double =
            if (value.isFinite()) Math.round(value * 1000.0) / 1000.0 else 0.0
    }
}

/** Identity of a song file that survives a move or rename: its size plus a hash of its first and last 64 KB. */
object SongFingerprint {
    private const val SAMPLE = 64 * 1024

    /** Null when the file can't be read. */
    fun of(context: Context, uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                val size = channel.size()
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                digest.update(size.toString().toByteArray())
                val buffer = java.nio.ByteBuffer.allocate(SAMPLE)
                for (position in listOf(0L, (size - SAMPLE).coerceAtLeast(0L))) {
                    buffer.clear()
                    val read = channel.read(buffer, position)
                    if (read > 0) digest.update(buffer.array(), 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }.getOrNull()
}
