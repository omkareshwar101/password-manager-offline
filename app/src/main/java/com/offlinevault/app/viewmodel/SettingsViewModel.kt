package com.offlinevault.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.SettingsRepository
import com.offlinevault.app.data.model.ThemeMode
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.security.SecurePrefs
import com.offlinevault.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PinChangeResult {
    data object Success : PinChangeResult
    data object WrongCurrentPin : PinChangeResult
    data object InvalidNewPin : PinChangeResult
    data object Mismatch : PinChangeResult
    data object Failed : PinChangeResult
}

enum class WipeResult {
    SUCCESS,
    INVALID_PIN,
    FAILED,
}

class SettingsViewModel(
    private val securePrefs: SecurePrefs,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
) : ViewModel() {
    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        settingsRepository.setBiometricEnabled(enabled)
    }

    fun setAutoLockSeconds(seconds: Int) {
        settingsRepository.setAutoLockSeconds(seconds)
    }

    fun setClipboardSeconds(seconds: Int) {
        settingsRepository.setClipboardSeconds(seconds)
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    fun changePin(
        currentPin: String,
        newPin: String,
        confirmPin: String,
        onResult: (PinChangeResult) -> Unit,
    ) {
        if (!Validators.isValidPin(newPin)) {
            onResult(PinChangeResult.InvalidNewPin)
            return
        }
        if (newPin != confirmPin) {
            onResult(PinChangeResult.Mismatch)
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            val changed = runCatching { securePrefs.changePin(currentPin, newPin) }
                .getOrDefault(false)
            _isWorking.value = false
            onResult(
                if (changed) PinChangeResult.Success
                else PinChangeResult.WrongCurrentPin,
            )
        }
    }

    fun deleteAllData(currentPin: String, onResult: (WipeResult) -> Unit) {
        viewModelScope.launch {
            _isWorking.value = true
            val valid = runCatching { securePrefs.verifyPin(currentPin) }.getOrDefault(false)
            if (!valid) {
                _isWorking.value = false
                onResult(WipeResult.INVALID_PIN)
                return@launch
            }
            val result = runCatching { container.wipeEverything() }
                .fold(
                    onSuccess = { WipeResult.SUCCESS },
                    onFailure = { WipeResult.FAILED },
                )
            _isWorking.value = false
            onResult(result)
        }
    }
}
