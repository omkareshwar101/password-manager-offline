package com.offlinevault.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.BackupManager
import com.offlinevault.app.security.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object ExportFailed : BackupMessage
    data object InvalidCurrentPin : BackupMessage
    data class Imported(val count: Int) : BackupMessage
    data object ImportFailed : BackupMessage
}

data class BackupUiState(
    val isWorking: Boolean = false,
    val message: BackupMessage? = null,
)

class BackupViewModel(
    private val securePrefs: SecurePrefs,
    private val backupManager: BackupManager,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export(uri: Uri, pin: String) {
        if (_state.value.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val validPin = runCatching { securePrefs.verifyPin(pin) }.getOrDefault(false)
            if (!validPin) {
                _state.update { it.copy(isWorking = false, message = BackupMessage.InvalidCurrentPin) }
                return@launch
            }
            val outcome = runCatching { backupManager.exportBackup(uri, pin) }
            _state.update {
                it.copy(
                    isWorking = false,
                    message = if (outcome.isSuccess) BackupMessage.Exported else BackupMessage.ExportFailed,
                )
            }
        }
    }

    fun import(uri: Uri, pin: String) {
        if (_state.value.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val outcome = runCatching { backupManager.importBackup(uri, pin) }
            _state.update {
                it.copy(
                    isWorking = false,
                    message = outcome.fold(
                        onSuccess = { count -> BackupMessage.Imported(count) },
                        onFailure = { BackupMessage.ImportFailed },
                    ),
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
