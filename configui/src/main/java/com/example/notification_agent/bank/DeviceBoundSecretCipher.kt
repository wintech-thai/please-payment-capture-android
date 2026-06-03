@file:Suppress("unused")

package com.example.notification_agent.bank

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Device-bound encryption for secrets persisted in the local bank config store.
 * The AES key is derived from a SHA-256 hash of the device's ANDROID_ID.
 */
internal object DeviceBoundSecretCipher {

    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val AUTH_TAG_BITS = 128
    private const val PURPOSE = "bank-api-key-v1"

    fun encrypt(context: Context, plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val iv = ByteArray(IV_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(context), GCMParameterSpec(AUTH_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(context: Context, ciphertext: String): String? = runCatching {
        val payload = Base64.decode(ciphertext, Base64.DEFAULT)
        require(payload.size > IV_SIZE_BYTES) { "ciphertext too short" }
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(context), GCMParameterSpec(AUTH_TAG_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    @SuppressLint("HardwareIds")
    private fun secretKey(context: Context): SecretKeySpec {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("${context.packageName}:$PURPOSE:$deviceId".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, KEY_ALGORITHM)
    }
}

