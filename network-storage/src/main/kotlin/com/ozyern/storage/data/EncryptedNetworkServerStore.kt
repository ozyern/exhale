package com.ozyern.storage.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ozyern.storage.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedNetworkServerStore(context: Context) : NetworkServerStore {
    private val preferences by lazy { context.applicationContext.getSharedPreferences("network_music_sources", Context.MODE_PRIVATE) }
    private val state = MutableStateFlow<List<NetworkServer>>(emptyList())
    override val servers: StateFlow<List<NetworkServer>> = state.asStateFlow()
    private var loaded = false
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): List<NetworkServer> = withContext(Dispatchers.IO) { synchronized(lock) { read() } }
    override fun find(id: String): NetworkServer? = synchronized(lock) { read().firstOrNull { it.id == id } }
    override suspend fun save(server: NetworkServer): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = read()
            require(current.size < 16 || current.any { it.id == server.id }) { "Too many network sources" }
            val next = current.filterNot { it.id == server.id } + server
            persist(next)
        }
    }
    override suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) { persist(read().filterNot { it.id == id }) }
    }

    private fun read(): List<NetworkServer> {
        if (loaded) return state.value
        val saved = preferences.getString("sources", null)
        val records = if (saved == null) emptyList() else try {
            val payload = Base64.decode(saved, Base64.NO_WRAP)
            require(payload.size >= 13)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            val decoded = cipher.doFinal(payload, 12, payload.size - 12).toString(Charsets.UTF_8)
            json.decodeFromString<List<ServerRecord>>(decoded).map { it.toServer() }
        } catch (error: Exception) { throw StorageException(StorageError.CREDENTIALS_UNAVAILABLE, error) }
        state.value = records
        loaded = true
        return records
    }

    private fun persist(servers: List<NetworkServer>) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = json.encodeToString(servers.map(ServerRecord::from)).toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(body), Base64.NO_WRAP)
        if (!preferences.edit().putString("sources", encoded).commit()) throw StorageException(StorageError.CONNECTION)
        state.value = servers
        loaded = true
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Serializable
    private data class ServerRecord(val id: String, val name: String, val protocol: String, val address: String, val username: String, val password: String, val domain: String) {
        fun toServer(): NetworkServer = NetworkServer.create(NetworkProtocol.valueOf(protocol), name, address, SourceCredentials(username, password, domain), id)
        companion object {
            fun from(server: NetworkServer) = ServerRecord(server.id, server.name, server.protocol.name, server.address,
                server.credentials.username, server.credentials.password, server.credentials.domain)
        }
    }

    companion object { private const val KeyAlias = "exhale.network-storage.v1" }
}
