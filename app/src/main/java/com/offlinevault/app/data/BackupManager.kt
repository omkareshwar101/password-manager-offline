package com.offlinevault.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.offlinevault.app.data.model.AccountCategory
import com.offlinevault.app.data.model.RestoredAccount
import com.offlinevault.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, encrypted .vault backup using AES-256-GCM and a PBKDF2-SHA256 key
 * derived from the PIN. It deliberately does not depend on Android Keystore, so
 * a backup can be restored on a new device.
 *
 * No plaintext backup is ever written to disk. Plaintext exists only in memory
 * while the export/import operation is running.
 */
class BackupManager(
    private val context: Context,
    private val repository: AccountRepository,
    private val imageStore: ImageStore,
) {
    companion object {
        private val MAGIC = byteArrayOf('O'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
        private const val VERSION = 1
        private const val ITERATIONS = 210_000
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val AAD = "OfflineVaultBackup:v1"
        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    }

    suspend fun exportBackup(uri: Uri, pin: String): Int = withContext(Dispatchers.IO) {
        require(pin.isNotEmpty()) { "PIN required" }
        val accounts = repository.getAllAccounts()
        val json = JSONObject().apply {
            put("format", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("accounts", JSONArray().apply {
                accounts.forEach { account ->
                    val imageBytes = imageStore.readImageBytes(account.imageRef)
                    val imageData = imageBytes?.takeIf { it.isNotEmpty() }?.let {
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                    put(
                        JSONObject().apply {
                            put("name", account.name)
                            put("imageRef", account.imageRef ?: JSONObject.NULL)
                            put("imageData", imageData ?: JSONObject.NULL)
                            put("url", account.url ?: JSONObject.NULL)
                            put("username", account.username ?: JSONObject.NULL)
                            put("password", account.password)
                            put("notes", account.notes ?: JSONObject.NULL)
                            put("category", account.category.storageValue)
                            put("favorite", account.isFavorite)
                            put("createdAt", account.createdAt)
                            put("updatedAt", account.updatedAt)
                            put("passwordChangedAt", account.passwordChangedAt)
                        },
                    )
                }
            })
        }
        val plain = json.toString().toByteArray(Charsets.UTF_8)
        val encryptedFile = encrypt(pin, plain)
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(encryptedFile)
            output.flush()
        } ?: throw IllegalStateException("Could not open backup destination")
        accounts.size
    }

    suspend fun importBackup(uri: Uri, pin: String): Int = withContext(Dispatchers.IO) {
        require(pin.isNotEmpty()) { "PIN required" }
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readLimited(Constants.MAX_BACKUP_BYTES)
        } ?: throw IllegalStateException("Could not read backup")
        val plain = decrypt(pin, raw)
        val json = JSONObject(String(plain, Charsets.UTF_8))
        val array = json.optJSONArray("accounts") ?: throw IllegalArgumentException("Invalid backup")
        val newImages = mutableListOf<String>()
        try {
            val restored = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    val password = item.optString("password")
                    if (password.isEmpty()) continue
                    val imageData = item.optNullableString("imageData")
                    val importedImageRef = if (imageData != null) {
                        val bytes = Base64.decode(imageData, Base64.NO_WRAP)
                        if (bytes.size > MAX_IMAGE_BYTES) {
                            throw IllegalArgumentException("Backup image is too large")
                        }
                        imageStore.writeRestoredImage(bytes).also(newImages::add)
                    } else {
                        item.optNullableString("imageRef")
                            ?.takeIf { it.startsWith(Constants.BUILTIN_ICON_PREFIX) }
                    }

                    val now = System.currentTimeMillis()
                    add(
                        RestoredAccount(
                            name = name,
                            imageRef = importedImageRef,
                            url = item.optNullableString("url"),
                            username = item.optNullableString("username"),
                            password = password,
                            notes = item.optNullableString("notes"),
                            category = AccountCategory.fromStorage(item.optString("category")),
                            isFavorite = item.optBoolean("favorite", false),
                            createdAt = item.optLong("createdAt", now).takeIf { it in 1..now } ?: now,
                            updatedAt = item.optLong("updatedAt", now).takeIf { it in 1..now } ?: now,
                            passwordChangedAt = item.optLong("passwordChangedAt", now)
                                .takeIf { it in 1..now } ?: now,
                        ),
                    )
                }
            }
            repository.replaceAllWithBackup(restored)
            restored.size
        } catch (failure: Exception) {
            newImages.forEach(imageStore::delete)
            throw failure
        }
    }

    private fun encrypt(pin: String, plain: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val cipherText = cipher.doFinal(plain)
        val iv = cipher.iv
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(ITERATIONS)
                output.writeByte(SALT_BYTES)
                output.write(salt)
                output.writeByte(iv.size)
                output.write(iv)
                output.write(cipherText)
                output.flush()
            }
            bytes.toByteArray()
        }
    }

    private fun decrypt(pin: String, raw: ByteArray): ByteArray {
        if (raw.size < 32) throw IllegalArgumentException("Invalid backup header")
        DataInputStream(ByteArrayInputStream(raw)).use { input ->
            val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "Invalid backup header" }
            val version = input.readInt()
            require(version == VERSION) { "Unsupported backup version" }
            val iterations = input.readInt()
            require(iterations in 50_000..1_000_000) { "Invalid key derivation parameters" }
            val saltLength = input.readUnsignedByte()
            require(saltLength == SALT_BYTES) { "Invalid salt" }
            val salt = ByteArray(saltLength).also { input.readFully(it) }
            val ivLength = input.readUnsignedByte()
            require(ivLength == IV_BYTES) { "Invalid IV" }
            val iv = ByteArray(ivLength).also { input.readFully(it) }
            val cipherText = input.readBytes()
            require(cipherText.size > TAG_BITS / 8) { "Invalid backup payload" }
            val key = deriveKey(pin, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(cipherText)
        }
    }

    private fun deriveKey(
        pin: String,
        salt: ByteArray,
        iterations: Int = ITERATIONS,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw IllegalArgumentException("Backup is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
