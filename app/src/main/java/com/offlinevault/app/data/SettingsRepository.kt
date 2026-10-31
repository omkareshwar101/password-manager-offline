package com.offlinevault.app.data

import com.offlinevault.app.data.model.AppSettings
import com.offlinevault.app.data.model.ThemeMode
import com.offlinevault.app.security.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val securePrefs: SecurePrefs) {
    private val _state = MutableStateFlow(securePrefs.loadSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun reload() {
        _state.value = securePrefs.loadSettings()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securePrefs.setBiometricEnabled(enabled)
        reload()
    }

    fun setAutoLockSeconds(seconds: Int) {
        securePrefs.setAutoLockSeconds(seconds)
        reload()
    }

    fun setClipboardSeconds(seconds: Int) {
        securePrefs.setClipboardSeconds(seconds)
        reload()
    }

    fun setThemeMode(mode: ThemeMode) {
        securePrefs.setThemeMode(mode)
        reload()
    }
}
