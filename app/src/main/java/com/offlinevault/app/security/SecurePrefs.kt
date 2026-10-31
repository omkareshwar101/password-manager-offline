package com.offlinevault.app.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.offlinevault.app.data.model.AppSettings
import com.offlinevault.app.data.model.AuthAttemptState
import com.offlinevault.app.data.model.SecurityQuestionAnswer
import com.offlinevault.app.data.model.ThemeMode
import com.offlinevault.app.util.Constants
import com.offlinevault.app.util.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Authentication metadata and preferences live in EncryptedSharedPreferences.
 * PINs and security answers are only ever stored as salted PBKDF2 hashes.
 *
 * This class never logs credentials and never writes them outside app-private,
 * encrypted preferences.
 */
class SecurePrefs(context: Context) {
    companion object {
        private const val FILE_NAME = "offlinevault_secure_prefs"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 210_000
        private const val DERIVED_KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val LOCKOUT_MILLIS = 5L * 60L * 1000L

        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_QUESTIONS = "security_questions_json"
        private const val KEY_PIN_FAILURES = "auth_pin_failures"
        private const val KEY_TOTAL_FAILURES = "auth_total_failures"
        private const val KEY_LOCKOUT_UNTIL = "auth_lockout_until"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_SECONDS = "auto_lock_seconds"
        private const val KEY_CLIPBOARD_SECONDS = "clipboard_seconds"
        private const val KEY_THEME = "theme_mode"
    }

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private data class StoredQuestion(
        val question: String,
        val saltBase64: String,
        val hashBase64: String,
    )

    // ----------------------------- Vault setup -----------------------------

    fun isOnboardingComplete(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false) &&
            prefs.contains(KEY_PIN_HASH) &&
            prefs.contains(KEY_QUESTIONS)

    suspend fun createVault(
        pin: String,
        questions: List<SecurityQuestionAnswer>,
        biometricEnabled: Boolean,
    ) {
        require(Validators.isValidPin(pin)) { "Invalid PIN" }
        require(questions.size == 3) { "Exactly three security questions are required" }
        require(questions.all { it.question.isNotBlank() && it.answer.isNotBlank() }) {
            "Questions and answers cannot be empty"
        }

        withContext(Dispatchers.Default) {
            val pinSalt = randomBytes(SALT_BYTES)
            val pinHash = deriveHash(pin, pinSalt)
            val storedQuestions = questions.map { item ->
                val salt = randomBytes(SALT_BYTES)
                val hash = deriveHash(Validators.normalizeAnswer(item.answer), salt)
                StoredQuestion(
                    question = item.question.trim(),
                    saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                    hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
                )
            }
            val json = JSONArray().apply {
                storedQuestions.forEach { item ->
                    put(
                        JSONObject()
                            .put("q", item.question)
                            .put("s", item.saltBase64)
                            .put("h", item.hashBase64),
                    )
                }
            }

            val editor = prefs.edit()
                .putString(KEY_PIN_SALT, Base64.encodeToString(pinSalt, Base64.NO_WRAP))
                .putString(KEY_PIN_HASH, Base64.encodeToString(pinHash, Base64.NO_WRAP))
                .putString(KEY_QUESTIONS, json.toString())
                .putBoolean(KEY_ONBOARDING_COMPLETE, true)
                .putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled)
                .putInt(KEY_AUTO_LOCK_SECONDS, 30)
                .putInt(KEY_CLIPBOARD_SECONDS, 30)
                .putString(KEY_THEME, ThemeMode.SYSTEM.name)
                .putInt(KEY_PIN_FAILURES, 0)
                .putInt(KEY_TOTAL_FAILURES, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
            check(editor.commit()) { "Could not persist encrypted preferences" }
        }
    }

    // ----------------------------- Authentication -----------------------------

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return@withContext false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return@withContext false
        val salt = decodeBase64(storedSalt) ?: return@withContext false
        val expected = decodeBase64(storedHash) ?: return@withContext false
        val actual = deriveHash(pin, salt)
        MessageDigest.isEqual(expected, actual)
    }

    /** Returns how many of the three supplied answers match. */
    suspend fun verifySecurityAnswers(answers: List<String>): Int = withContext(Dispatchers.Default) {
        val stored = readQuestions()
        if (stored.isEmpty()) return@withContext 0
        var correct = 0
        stored.forEachIndexed { index, item ->
            val hash = decodeBase64(item.hashBase64) ?: return@forEachIndexed
            val salt = decodeBase64(item.saltBase64) ?: return@forEachIndexed
            val supplied = answers.getOrNull(index).orEmpty()
            if (supplied.isBlank()) return@forEachIndexed
            val actual = deriveHash(Validators.normalizeAnswer(supplied), salt)
            if (MessageDigest.isEqual(hash, actual)) correct++
        }
        correct
    }

    suspend fun changePin(currentPin: String, newPin: String): Boolean {
        require(Validators.isValidPin(newPin)) { "Invalid new PIN" }
        if (!verifyPin(currentPin)) return false
        withContext(Dispatchers.Default) {
            val salt = randomBytes(SALT_BYTES)
            val hash = deriveHash(newPin, salt)
            val success = prefs.edit()
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .putInt(KEY_PIN_FAILURES, 0)
                .putInt(KEY_TOTAL_FAILURES, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .commit()
            check(success) { "Could not update PIN" }
        }
        return true
    }

    fun getSecurityQuestions(): List<String> = readQuestions().map { it.question }

    suspend fun updateSecurityQuestions(questions: List<SecurityQuestionAnswer>) {
        require(questions.size == 3)
        require(questions.all { it.question.isNotBlank() && it.answer.isNotBlank() })
        withContext(Dispatchers.Default) {
            val stored = questions.map { item ->
                val salt = randomBytes(SALT_BYTES)
                val hash = deriveHash(Validators.normalizeAnswer(item.answer), salt)
                JSONObject()
                    .put("q", item.question.trim())
                    .put("s", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .put("h", Base64.encodeToString(hash, Base64.NO_WRAP))
            }
            val json = JSONArray().apply { stored.forEach(::put) }
            check(prefs.edit().putString(KEY_QUESTIONS, json.toString()).commit()) {
                "Could not update security questions"
            }
        }
    }

    // ----------------------------- Brute-force defence -----------------------------

    fun getAuthAttemptState(now: Long = System.currentTimeMillis()): AuthAttemptState {
        var lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil in 1 until now) {
            prefs.edit()
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .putInt(KEY_PIN_FAILURES, 0)
                .putInt(KEY_TOTAL_FAILURES, 0)
                .apply()
            lockoutUntil = 0L
        }
        val pinFailures = prefs.getInt(KEY_PIN_FAILURES, 0)
        val totalFailures = prefs.getInt(KEY_TOTAL_FAILURES, 0)
        return AuthAttemptState(
            pinFailures = pinFailures,
            totalFailures = totalFailures,
            lockoutUntil = lockoutUntil,
            forceQuestions = pinFailures >= 5,
            lockedOut = lockoutUntil > now,
        )
    }

    /**
     * Records one failed authentication submission. After ten total failures
     * the vault is locked for five minutes and counters reset afterwards.
     */
    @SuppressLint("ApplySharedPref")
    fun recordAuthFailure(wasPin: Boolean): AuthAttemptState {
        val now = System.currentTimeMillis()
        val current = getAuthAttemptState(now)
        if (current.lockedOut) return current

        var pinFailures = current.pinFailures + if (wasPin) 1 else 0
        var totalFailures = current.totalFailures + 1
        var lockoutUntil = 0L

        if (totalFailures >= 10) {
            lockoutUntil = now + LOCKOUT_MILLIS
            pinFailures = 0
            totalFailures = 0
        }
        prefs.edit()
            .putInt(KEY_PIN_FAILURES, pinFailures)
            .putInt(KEY_TOTAL_FAILURES, totalFailures)
            .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
            .commit()
        return AuthAttemptState(
            pinFailures = pinFailures,
            totalFailures = totalFailures,
            lockoutUntil = lockoutUntil,
            forceQuestions = pinFailures >= 5,
            lockedOut = lockoutUntil > now,
        )
    }

    fun clearAuthFailures() {
        prefs.edit()
            .putInt(KEY_PIN_FAILURES, 0)
            .putInt(KEY_TOTAL_FAILURES, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    // ----------------------------- Preferences -----------------------------

    fun loadSettings(): AppSettings = AppSettings(
        biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false),
        autoLockSeconds = sanitizeAutoLock(prefs.getInt(KEY_AUTO_LOCK_SECONDS, 30)),
        clipboardSeconds = sanitizeClipboard(prefs.getInt(KEY_CLIPBOARD_SECONDS, 30)),
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
    )

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_SECONDS, sanitizeAutoLock(seconds)).apply()
    }

    fun setClipboardSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_SECONDS, sanitizeClipboard(seconds)).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private fun sanitizeAutoLock(value: Int): Int =
        if (value in setOf(0, 15, 30, 60, 300)) value else 30

    private fun sanitizeClipboard(value: Int): Int =
        if (value in setOf(0, 15, 30, 60)) value else 30

    // ----------------------------- Database key -----------------------------

    @SuppressLint("ApplySharedPref")
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val existing = prefs.getString(Constants.DB_PASSPHRASE_PREF, null)
        if (existing != null) {
            decodeBase64(existing)?.let { return it }
        }
        val generated = randomBytes(32)
        prefs.edit()
            .putString(
                Constants.DB_PASSPHRASE_PREF,
                Base64.encodeToString(generated, Base64.NO_WRAP),
            )
            .commit()
        return generated
    }

    // ----------------------------- Destructive reset -----------------------------

    /**
     * Removes all user data and auth material while preserving the random
     * database passphrase. The repository is vacuumed before this is called so
     * SQLite never leaves recoverable row pages behind.
     */
    fun clearUserDataKeepingDatabaseKey() {
        val databasePassphrase = prefs.getString(Constants.DB_PASSPHRASE_PREF, null)
        val editor = prefs.edit()
        prefs.all.keys
            .filterNot { it == Constants.DB_PASSPHRASE_PREF }
            .forEach(editor::remove)
        if (databasePassphrase != null) {
            editor.putString(Constants.DB_PASSPHRASE_PREF, databasePassphrase)
        }
        check(editor.commit()) { "Could not erase secure preferences" }
    }

    // ----------------------------- Hashing helpers -----------------------------

    private fun readQuestions(): List<StoredQuestion> {
        val raw = prefs.getString(KEY_QUESTIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredQuestion(
                            question = item.getString("q"),
                            saltBase64 = item.getString("s"),
                            hashBase64 = item.getString("h"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun deriveHash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, DERIVED_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { SecureRandom().nextBytes(it) }

    private fun decodeBase64(value: String): ByteArray? =
        runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
}
