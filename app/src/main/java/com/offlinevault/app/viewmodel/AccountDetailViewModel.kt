package com.offlinevault.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.AccountRepository
import com.offlinevault.app.data.model.Account
import com.offlinevault.app.security.SessionManager
import com.offlinevault.app.util.Constants
import com.offlinevault.app.util.PasswordStrengthEstimator
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailMessage {
    LOAD_FAILED,
    REVEAL_FAILED,
    FAVORITE_FAILED,
    DELETE_FAILED,
}

data class DetailUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val passwordVisible: Boolean = false,
    val deleted: Boolean = false,
    val isWeak: Boolean = false,
    val isOld: Boolean = false,
    val isDuplicate: Boolean = false,
)

class AccountDetailViewModel(
    private val accountId: Long,
    private val repository: AccountRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var hideJob: Job? = null

    init {
        loadAccount()
        viewModelScope.launch {
            sessionManager.isUnlocked.collect { unlocked ->
                if (!unlocked) hidePassword()
            }
        }
    }

    fun loadAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val account = repository.getAccount(accountId)
                val allAccounts = if (account != null) repository.getAllAccounts() else emptyList()
                val duplicate = account != null && allAccounts.any {
                    it.id != account.id && it.password == account.password && it.password.isNotBlank()
                }
                Triple(account, duplicate, allAccounts)
            }.onSuccess { (account, duplicate, _) ->
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        account = account,
                        isDuplicate = duplicate,
                        isWeak = account?.let { saved ->
                            PasswordStrengthEstimator.isWeak(saved.password)
                        } ?: false,
                        isOld = account?.let { saved ->
                            now - saved.passwordChangedAt >= Constants.PASSWORD_MAX_AGE_MILLIS
                        } ?: false,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _messages.send(DetailMessage.LOAD_FAILED)
            }
        }
    }

    fun showPassword() {
        if (_uiState.value.account == null) return
        hideJob?.cancel()
        _uiState.update { it.copy(passwordVisible = true) }
        hideJob = viewModelScope.launch {
            delay(Constants.PASSWORD_REVEAL_MILLIS)
            hidePassword()
        }
    }

    fun hidePassword() {
        hideJob?.cancel()
        hideJob = null
        _uiState.update { it.copy(passwordVisible = false) }
    }

    fun toggleFavorite() {
        val account = _uiState.value.account ?: return
        viewModelScope.launch {
            val newFavorite = !account.isFavorite
            runCatching { repository.setFavorite(account.id, newFavorite) }
                .onSuccess {
                    _uiState.update { state ->
                        state.account?.let { current ->
                            state.copy(account = current.copy(isFavorite = newFavorite))
                        } ?: state
                    }
                }
                .onFailure { _messages.send(DetailMessage.FAVORITE_FAILED) }
        }
    }

    fun deleteAccount() {
        val account = _uiState.value.account ?: return
        viewModelScope.launch {
            runCatching { repository.deleteAccount(account.id) }
                .onSuccess {
                    _uiState.update { it.copy(deleted = true, account = null) }
                }
                .onFailure { _messages.send(DetailMessage.DELETE_FAILED) }
        }
    }
}
