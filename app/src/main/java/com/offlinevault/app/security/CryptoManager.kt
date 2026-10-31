package com.offlinevault.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM field encryption using a non-exportable key held by Android
 * Keystore. The Keystore automatically uses a hardware-backed implementation
 * when the device provides one.
 *
 * Every encrypted blob is: [12-byte random IV][GCM ciphertext + 128-bit tag].
 * Strings are Base64(NO_WRAP) encoded before being placed in SQLCipher.
 */
class CryptoManager {
    companion object {
        private const val KEY_ALIAS = "offlinevault_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encryptString(plainText: String?): String? {
        if (plainText == null) return null
        return Base64.encodeToString(encryptBytes(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decryptString(cipherText: String?): String? {
        if (cipherText == null) return null
        return String(decryptBytes(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        if (plainBytes.isEmpty()) return ByteArray(0)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected GCM IV size" }
        val encrypted = cipher.doFinal(plainBytes)
        return ByteArrayOutputStream(iv.size + encrypted.size).use { output ->
            output.write(iv)
            output.write(encrypted)
            output.toByteArray()
        }
    }

    fun decryptBytes(blob: ByteArray): ByteArray {
        if (blob.isEmpty()) return ByteArray(0)
        require(blob.size > IV_BYTES + TAG_BITS / 8) { "Encrypted payload is too short" }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipherText = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun encryptOptionalString(value: String?): String? = encryptString(value)

    fun decryptOptionalString(value: String?): String? = decryptString(value)
}
