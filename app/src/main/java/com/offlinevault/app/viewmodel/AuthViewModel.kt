package com.offlinevault.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.security.SecurePrefs
import com.offlinevault.app.security.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AuthError {
    INVALID_PIN,
    INVALID_ANSWERS,
    LOCKED_OUT,
    BIOMETRIC_FAILED,
    FORCE_QUESTIONS,
    GENERIC,
}

data class AuthUiState(
    val pin: String = "",
    val showQuestions: Boolean = false,
    val forceQuestions: Boolean = false,
    val lockoutUntil: Long = 0L,
    val lockoutRemainingMillis: Long = 0L,
    val securityQuestions: List<String> = emptyList(),
    val isBusy: Boolean = false,
    val error: AuthError? = null,
)

class AuthViewModel(
    private val securePrefs: SecurePrefs,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        val attempt = securePrefs.getAuthAttemptState()
        _state.value = AuthUiState(
            showQuestions = attempt.forceQuestions || attempt.lockedOut,
            forceQuestions = attempt.forceQuestions,
            lockoutUntil = attempt.lockoutUntil,
            lockoutRemainingMillis = (attempt.lockoutUntil - System.currentTimeMillis())
                .coerceAtLeast(0L),
            securityQuestions = securePrefs.getSecurityQuestions(),
        )
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                updateLockoutClock()
            }
        }
    }

    fun setPin(value: String) {
        if (value.length <= 12 && value.all(Char::isDigit)) {
            _state.update { it.copy(pin = value, error = null) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun submitPin() {
        val current = _state.value
        if (current.isBusy) return
        if (current.forceQuestions) {
            _state.update { it.copy(error = AuthError.FORCE_QUESTIONS, showQuestions = true) }
            return
        }
        if (isLockedOut()) {
            _state.update { it.copy(error = AuthError.LOCKED_OUT) }
            return
        }
        if (current.pin.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            val success = runCatching { securePrefs.verifyPin(current.pin) }.getOrDefault(false)
            if (success) {
                completeUnlock()
            } else {
                val attempt = securePrefs.recordAuthFailure(wasPin = true)
                _state.update {
                    it.copy(
                        pin = "",
                        isBusy = false,
                        forceQuestions = attempt.forceQuestions,
                        showQuestions = it.showQuestions || attempt.forceQuestions || attempt.lockedOut,
                        lockoutUntil = attempt.lockoutUntil,
                        lockoutRemainingMillis = (attempt.lockoutUntil - System.currentTimeMillis())
                            .coerceAtLeast(0L),
                        error = when {
                            attempt.lockedOut -> AuthError.LOCKED_OUT
                            attempt.forceQuestions -> AuthError.FORCE_QUESTIONS
                            else -> AuthError.INVALID_PIN
                        },
                    )
                }
            }
        }
    }

    fun submitSecurityAnswers(answers: List<String>) {
        val current = _state.value
        if (current.isBusy) return
        if (isLockedOut()) {
            _state.update { it.copy(error = AuthError.LOCKED_OUT) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            val correct = runCatching { securePrefs.verifySecurityAnswers(answers) }.getOrDefault(0)
            if (correct >= 2) {
                completeUnlock()
            } else {
                val attempt = securePrefs.recordAuthFailure(wasPin = false)
                _state.update {
                    it.copy(
                        isBusy = false,
                        showQuestions = true,
                        forceQuestions = attempt.forceQuestions,
                        lockoutUntil = attempt.lockoutUntil,
                        lockoutRemainingMillis = (attempt.lockoutUntil - System.currentTimeMillis())
                            .coerceAtLeast(0L),
                        error = if (attempt.lockedOut) AuthError.LOCKED_OUT else AuthError.INVALID_ANSWERS,
                    )
                }
            }
        }
    }

    fun showQuestions() {
        _state.update {
            when {
                it.forceQuestions || isLockedOut() -> it.copy(showQuestions = true)
                else -> it.copy(showQuestions = true, error = null)
            }
        }
    }

    fun showPin() {
        val current = _state.value
        if (current.forceQuestions || isLockedOut()) return
        _state.update { it.copy(showQuestions = false, error = null) }
    }

    fun onBiometricSucceeded() {
        if (isLockedOut()) {
            _state.update { it.copy(error = AuthError.LOCKED_OUT) }
            return
        }
        if (_state.value.forceQuestions) {
            _state.update { it.copy(error = AuthError.FORCE_QUESTIONS, showQuestions = true) }
            return
        }
        completeUnlock()
    }

    fun onBiometricFailed() {
        _state.update { it.copy(error = AuthError.BIOMETRIC_FAILED) }
    }

    fun isLockedOut(now: Long = System.currentTimeMillis()): Boolean =
        _state.value.lockoutUntil > now

    private fun completeUnlock() {
        securePrefs.clearAuthFailures()
        _state.update { it.copy(pin = "", isBusy = false, error = null) }
        sessionManager.unlock()
    }

    private fun updateLockoutClock() {
        val lockoutUntil = _state.value.lockoutUntil
        if (lockoutUntil <= 0L) return
        val remaining = (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remaining == 0L) {
            val attempt = securePrefs.getAuthAttemptState()
            _state.update {
                it.copy(
                    showQuestions = attempt.forceQuestions,
                    forceQuestions = attempt.forceQuestions,
                    lockoutUntil = attempt.lockoutUntil,
                    lockoutRemainingMillis = 0L,
                    error = null,
                )
            }
        } else {
            _state.update {
                if (it.lockoutRemainingMillis == remaining) it
                else it.copy(lockoutRemainingMillis = remaining)
            }
        }
    }
}
