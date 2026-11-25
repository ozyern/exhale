/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.connect

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The cryptography of pairing, kept small and standard (HMAC-SHA256, AES-GCM from the JDK).
 *
 * Pairing: the PC shows a six-digit code. The phone proves it knows the code ([codeProof]) without
 * sending it, and the PC sends back a fresh 256-bit shared key sealed with a key derived from the
 * code and the link's nonce ([seal]), so the key never crosses the Wi-Fi in the clear. From then on
 * every link opens with [secretProof] over a new nonce: the phone reconnects on its own, and nothing
 * on the network that lacks the key can pass for it. The code only lives for a couple of minutes;
 * someone recording the pairing could try all million codes against it offline, which is the
 * accepted cost of a code short enough to type, on a home network.
 */
object Pairing {
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    fun newCode(): String = (random.nextInt(900_000) + 100_000).toString()

    fun newNonce(): String = randomBase64(24)

    fun newSecret(): String = randomBase64(32)

    fun newDeviceId(): String = randomBase64(16).replace('/', '_').replace('+', '-')

    /** What a phone sends to show it knows [code], bound to this link's [nonce] and itself. */
    fun codeProof(code: String, nonce: String, deviceId: String): String =
        hmac(derive("exhale-connect-pair", code, nonce), "$nonce|$deviceId")

    /** What a paired phone sends to open a link: the shared key over this link's [nonce]. */
    fun secretProof(secret: String, nonce: String, deviceId: String): String =
        hmac(decoder.decode(secret), "$nonce|$deviceId")

    /** Seals the shared [secret] for a phone that proved [code] on the link with [nonce]. */
    fun seal(secret: String, code: String, nonce: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive("exhale-connect-seal", code, nonce), "AES"), GCMParameterSpec(128, iv))
        return encoder.encodeToString(iv + cipher.doFinal(secret.toByteArray()))
    }

    /** The shared key back out of [sealed], or null if it was not sealed with this code and nonce. */
    fun unseal(sealed: String, code: String, nonce: String): String? = runCatching {
        val bytes = decoder.decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derive("exhale-connect-seal", code, nonce), "AES"), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    /** Compares proofs in constant time, so timing says nothing about how close a guess was. */
    fun matches(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())

    private fun derive(purpose: String, code: String, nonce: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$purpose|$code|$nonce".toByteArray())

    private fun hmac(key: ByteArray, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return encoder.encodeToString(mac.doFinal(data.toByteArray()))
    }

    private fun randomBase64(bytes: Int): String = encoder.encodeToString(ByteArray(bytes).also(random::nextBytes))
}
