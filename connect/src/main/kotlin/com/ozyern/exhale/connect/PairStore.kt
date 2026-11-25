/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A device this one has paired with, and where it was last reached. */
@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val secret: String,
    val lastHost: String? = null,
    val lastPort: Int = CONNECT_TCP_PORT,
    val pairedAtMs: Long = 0,
)

@Serializable
private data class StoreFile(
    val selfId: String,
    val devices: List<PairedDevice> = emptyList(),
)

/**
 * This device's id and the devices it has paired with, in one small JSON file under [directory]
 * (the app's own data folder on either side). Pairing once is the whole point: what is here is
 * what lets the two apps find each other again every time without asking.
 */
class PairStore(directory: File) {
    private val file = File(directory, "connect.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()
    private var data: StoreFile = load()

    val selfId: String get() = data.selfId

    fun devices(): List<PairedDevice> = synchronized(lock) { data.devices }

    fun device(id: String): PairedDevice? = synchronized(lock) { data.devices.firstOrNull { it.id == id } }

    fun save(device: PairedDevice) = update { devices ->
        devices.filterNot { it.id == device.id } + device
    }

    /** Remembers where [id] answered last, so the next link tries there first. */
    fun seenAt(id: String, host: String, port: Int) = update { devices ->
        devices.map { if (it.id == id) it.copy(lastHost = host, lastPort = port) else it }
    }

    fun forget(id: String) = update { devices -> devices.filterNot { it.id == id } }

    private fun update(change: (List<PairedDevice>) -> List<PairedDevice>) = synchronized(lock) {
        data = data.copy(devices = change(data.devices))
        write()
    }

    private fun load(): StoreFile = runCatching {
        json.decodeFromString(StoreFile.serializer(), file.readText())
    }.getOrNull() ?: StoreFile(selfId = Pairing.newDeviceId()).also { data = it; write() }

    private fun write() {
        runCatching {
            file.parentFile?.mkdirs()
            // Written beside, then moved over: a crash mid-write never leaves a half file.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(StoreFile.serializer(), data))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }
}
