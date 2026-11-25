/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

/**
 * Keeps likes and listening history the same on the phone and the PC.
 *
 * Each app tells this what its own library looks like now ([localLikes], [localPlays]); it works out
 * what changed since last time, timestamps it, and hands back the changes to send. What arrives from
 * the other side goes through [merge], which says what to change locally. The rule is simple and the
 * same on both sides: for each song, the newest like-or-unlike wins. Likes made before pairing carry
 * time 0 and are only ever added together, so pairing never deletes anything from either library.
 *
 * Nothing here touches an app's own storage: it keeps its own small ledger in [directory], which is
 * how a change made while the two were apart is still told to the other side when they meet again.
 */
class LibrarySync(directory: File) {
    @Serializable
    private data class Ledger(
        val seeded: Boolean = false,
        val likes: Map<String, LikeEntry> = emptyMap(),
        val plays: List<PlayEntry> = emptyList(),
    )

    /** What to do locally after hearing from the other side. */
    data class Merge(val like: List<ConnectTrack>, val unlike: List<String>, val plays: List<PlayEntry>) {
        val isEmpty: Boolean get() = like.isEmpty() && unlike.isEmpty() && plays.isEmpty()
    }

    private val file = File(directory, "library-sync.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var ledger: Ledger = runCatching { json.decodeFromString(Ledger.serializer(), file.readText()) }.getOrNull() ?: Ledger()

    /**
     * The app's liked songs right now. Returns what changed since the last call, to send across.
     * The first call ever records them all as liked-before-pairing.
     */
    fun localLikes(current: List<ConnectTrack>, nowMs: Long = System.currentTimeMillis()): List<LikeEntry> = synchronized(lock) {
        val byId = current.associateBy { it.id }
        val changes = mutableListOf<LikeEntry>()
        val likes = ledger.likes.toMutableMap()
        if (!ledger.seeded) {
            current.forEach { track -> LikeEntry(track, liked = true, atMs = 0).also { likes[track.id] = it; changes += it } }
        } else {
            for ((id, track) in byId) {
                if (likes[id]?.liked != true) LikeEntry(track, liked = true, atMs = nowMs).also { likes[id] = it; changes += it }
            }
            for ((id, entry) in ledger.likes) {
                if (entry.liked && id !in byId) LikeEntry(entry.track, liked = false, atMs = nowMs).also { likes[id] = it; changes += it }
            }
        }
        if (changes.isNotEmpty() || !ledger.seeded) {
            ledger = ledger.copy(seeded = true, likes = likes)
            save()
        }
        changes
    }

    /** Songs played here, newest first or not. Returns the ones not yet told to the other side. */
    fun localPlays(recent: List<PlayEntry>): List<PlayEntry> = synchronized(lock) {
        val fresh = recent.filterNot(::known)
        if (fresh.isNotEmpty()) {
            ledger = ledger.copy(plays = (ledger.plays + fresh).sortedByDescending { it.atMs }.take(MAX_PLAYS))
            save()
        }
        fresh
    }

    /** Every play known on either side since pairing, newest first: for an app to order its history by. */
    fun recentPlays(): List<PlayEntry> = synchronized(lock) { ledger.plays }

    /** The whole picture, sent when a link opens: every like and unlike, and the latest plays. */
    fun snapshot(): ConnectMessage.Library = synchronized(lock) {
        ConnectMessage.Library(likes = ledger.likes.values.toList(), plays = ledger.plays.take(PLAYS_SENT), full = true)
    }

    /** What the other side sent; returns what to change here so both libraries agree. */
    fun merge(remote: ConnectMessage.Library): Merge = synchronized(lock) {
        val likes = ledger.likes.toMutableMap()
        val like = mutableListOf<ConnectTrack>()
        val unlike = mutableListOf<String>()
        for (entry in remote.likes) {
            val mine = likes[entry.track.id]
            val adopt = mine == null || entry.atMs > mine.atMs || (entry.atMs == mine.atMs && entry.liked && !mine.liked)
            if (!adopt) continue
            likes[entry.track.id] = entry
            if (entry.liked && mine?.liked != true) like += entry.track
            if (!entry.liked && mine?.liked == true) unlike += entry.track.id
        }
        val plays = remote.plays.filterNot(::known)
        ledger = ledger.copy(
            seeded = true,
            likes = likes,
            plays = (ledger.plays + plays).sortedByDescending { it.atMs }.take(MAX_PLAYS),
        )
        save()
        Merge(like, unlike, plays)
    }

    /** The same song within a minute of a play already here is that play, told twice. */
    private fun known(play: PlayEntry): Boolean =
        ledger.plays.any { it.track.id == play.track.id && abs(it.atMs - play.atMs) < SAME_PLAY_MS }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(Ledger.serializer(), ledger))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private companion object {
        const val MAX_PLAYS = 500
        const val PLAYS_SENT = 150
        const val SAME_PLAY_MS = 60_000L
    }
}
