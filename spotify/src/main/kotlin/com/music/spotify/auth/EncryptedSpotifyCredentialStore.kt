package com.music.spotify.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSpotifyCredentialStore(context: Context) : SpotifyCredentialStore {
    private val preferences by lazy { context.applicationContext.getSharedPreferences("spotify_provider_credentials", Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(purpose: SpotifyCredentialPurpose): SpotifyTokens? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = preferences.getString(purpose.name, null) ?: return@withLock null
            protect {
                val payload = Base64.decode(saved, Base64.NO_WRAP)
                require(payload.size in 28..65_536)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
                cipher.updateAAD(purpose.name.toByteArray(Charsets.UTF_8))
                val decoded = cipher.doFinal(payload, 12, payload.size - 12).toString(Charsets.UTF_8)
                json.decodeFromString<SpotifyTokens>(decoded)
            }
        }
    }

    override suspend fun save(purpose: SpotifyCredentialPurpose, tokens: SpotifyTokens) = withContext(Dispatchers.IO) {
        mutex.withLock {
            protect {
                val plaintext = json.encodeToString(tokens).toByteArray(Charsets.UTF_8)
                require(plaintext.size <= 48_000)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                cipher.updateAAD(purpose.name.toByteArray(Charsets.UTF_8))
                val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext), Base64.NO_WRAP)
                check(preferences.edit().putString(purpose.name, encoded).commit())
            }
        }
    }

    override suspend fun clear(purpose: SpotifyCredentialPurpose) = withContext(Dispatchers.IO) {
        mutex.withLock { protect { check(preferences.edit().remove(purpose.name).commit()) } }
    }

    private fun <T> protect(block: () -> T): T = try { block() } catch (error: Exception) { throw SpotifyAuthException(SpotifyAuthFailure.STORAGE) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object { const val KeyAlias = "exhale.spotify-provider.v1" }
}
