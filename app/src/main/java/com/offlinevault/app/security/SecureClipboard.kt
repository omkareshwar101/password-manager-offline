package com.offlinevault.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.offlinevault.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Copies secrets with the system sensitive hint (Android 13+) and removes the
 * clipboard entry after the user's configured timeout.
 */
class SecureClipboard(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    /** Returns the number of seconds after which the value will be cleared, or 0. */
    fun copySensitive(value: String): Int {
        if (value.isEmpty()) return 0
        val clip = ClipData.newPlainText("OfflineVault", value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)

        val seconds = settingsRepository.state.value.clipboardSeconds
        clearJob?.cancel()
        if (seconds > 0) {
            val digest = digest(value)
            clearJob = scope.launch {
                delay(seconds * 1_000L)
                clearIfMatches(digest)
            }
        }
        return seconds
    }

    private fun clearIfMatches(expectedDigest: ByteArray) {
        val current = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?: return
        if (MessageDigest.isEqual(expectedDigest, digest(current))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    private fun digest(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
