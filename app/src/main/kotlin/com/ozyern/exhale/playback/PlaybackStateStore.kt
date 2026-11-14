/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import android.util.AtomicFile
import com.ozyern.exhale.models.PersistedPlaybackSnapshot
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

internal class PlaybackStateStore(file: File) {
    private val atomicFile = AtomicFile(file)

    fun read(): PersistedPlaybackSnapshot? {
        if (!atomicFile.baseFile.exists()) return null
        return atomicFile.openRead().use { input ->
            ObjectInputStream(input).use { it.readObject() as PersistedPlaybackSnapshot }
        }
    }

    fun write(snapshot: PersistedPlaybackSnapshot) {
        val output = atomicFile.startWrite()
        try {
            val objectOutput = ObjectOutputStream(output)
            objectOutput.writeObject(snapshot)
            objectOutput.flush()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun delete() = atomicFile.delete()
}
