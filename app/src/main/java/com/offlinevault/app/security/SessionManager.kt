package com.offlinevault.app.security

import android.os.SystemClock
import com.offlinevault.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the in-memory unlocked state. Nothing about an unlocked session is
 * persisted: process death always returns the app to the lock screen.
 *
 * Auto-lock is enforced both while the app is in the foreground (inactivity)
 * and after it is backgrounded (grace period). "Immediate" locks on background.
 */
class SessionManager(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastInteractionAt: Long = SystemClock.elapsedRealtime()
    private var backgroundedAt: Long? = null
    private var inForeground: Boolean = false

    init {
        scope.launch {
            while (isActive) {
                delay(1_000)
                enforceTimeout()
            }
        }
    }

    fun unlock() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun onUserInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        inForeground = true
        val now = SystemClock.elapsedRealtime()
        val timeoutMillis = settingsRepository.state.value.autoLockSeconds * 1_000L
        val backgrounded = backgroundedAt
        if (_isUnlocked.value && backgrounded != null) {
            val awayFor = now - backgrounded
            if (timeoutMillis == 0L || awayFor >= timeoutMillis) {
                lock()
            }
        }
        backgroundedAt = null
    }

    fun onBackground() {
        inForeground = false
        backgroundedAt = SystemClock.elapsedRealtime()
        if (_isUnlocked.value && settingsRepository.state.value.autoLockSeconds == 0) {
            lock()
        }
    }

    /** Screen off is treated as a hard lock, independent of the timeout. */
    fun onScreenOff() {
        if (_isUnlocked.value) lock()
    }

    private fun enforceTimeout() {
        if (!_isUnlocked.value) return
        val timeoutSeconds = settingsRepository.state.value.autoLockSeconds
        if (timeoutSeconds <= 0) {
            // Immediate mode only applies to leaving the app / screen off.
            return
        }
        val timeoutMillis = timeoutSeconds * 1_000L
        val now = SystemClock.elapsedRealtime()
        if (inForeground) {
            if (now - lastInteractionAt >= timeoutMillis) lock()
        } else {
            val backgrounded = backgroundedAt ?: return
            if (now - backgrounded >= timeoutMillis) lock()
        }
    }
}
