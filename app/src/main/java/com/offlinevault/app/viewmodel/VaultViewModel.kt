package com.offlinevault.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.AccountRepository
import com.offlinevault.app.data.model.Account
import com.offlinevault.app.data.model.AccountCategory
import com.offlinevault.app.data.model.AccountSummary
import com.offlinevault.app.security.SessionManager
import com.offlinevault.app.util.Constants
import com.offlinevault.app.util.PasswordStrengthEstimator
import com.offlinevault.app.util.SearchMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VaultMessage {
    LOAD_FAILED,
    REVEAL_FAILED,
}

data class VaultUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val query: String = "",
    val selectedCategory: AccountCategory? = null,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

data class RevealedPassword(
    val accountId: Long,
    val password: String,
)

class VaultViewModel(
    private val repository: AccountRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val allAccounts = MutableStateFlow<List<AccountSummary>?>(null)
    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<AccountCategory?>(null)
    private val _revealed = MutableStateFlow<RevealedPassword?>(null)
    val revealed: StateFlow<RevealedPassword?> = _revealed.asStateFlow()

    private val _messages = Channel<VaultMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var revealJob: Job? = null

    val uiState: StateFlow<VaultUiState> =
        combine(allAccounts, query, selectedCategory) { accounts, currentQuery, category ->
            val loading = accounts == null
            val source = accounts.orEmpty()
            val filtered = source.filter { account ->
                (category == null || account.category == category) &&
                    SearchMatcher.matches(
                        currentQuery,
                        account.name,
                        account.username,
                        account.category.storageValue,
                    )
            }
            VaultUiState(
                accounts = filtered,
                query = currentQuery,
                selectedCategory = category,
                totalCount = source.size,
                isLoading = loading,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VaultUiState(),
        )

    init {
        viewModelScope.launch {
            repository.observeAccounts()
                .catch {
                    allAccounts.value = emptyList()
                    _messages.send(VaultMessage.LOAD_FAILED)
                }
                .collect { accounts ->
                    allAccounts.value = buildSummaries(accounts)
                }
        }
        viewModelScope.launch {
            sessionManager.isUnlocked.collect { unlocked ->
                if (!unlocked) hideReveal()
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectCategory(category: AccountCategory?) {
        selectedCategory.value = category
    }

    fun reveal(accountId: Long) {
        revealJob?.cancel()
        viewModelScope.launch {
            runCatching { repository.getAccount(accountId) }
                .onSuccess { account ->
                    if (account != null) {
                        _revealed.value = RevealedPassword(accountId, account.password)
                        revealJob = launch {
                            delay(Constants.PASSWORD_REVEAL_MILLIS)
                            if (_revealed.value?.accountId == accountId) hideReveal()
                        }
                    } else {
                        _messages.send(VaultMessage.REVEAL_FAILED)
                    }
                }
                .onFailure { _messages.send(VaultMessage.REVEAL_FAILED) }
        }
    }

    fun hideReveal() {
        revealJob?.cancel()
        revealJob = null
        _revealed.value = null
    }

    fun lockVault() {
        sessionManager.lock()
    }

    private fun buildSummaries(accounts: List<Account>): List<AccountSummary> {
        val duplicatePasswords = accounts
            .filter { it.password.isNotBlank() }
            .groupBy(Account::password)
            .values
            .filter { it.size > 1 }
            .flatten()
            .map(Account::id)
            .toSet()
        val now = System.currentTimeMillis()
        return accounts.map { account ->
            AccountSummary(
                id = account.id,
                name = account.name,
                username = account.username,
                imageRef = account.imageRef,
                category = account.category,
                isFavorite = account.isFavorite,
                passwordChangedAt = account.passwordChangedAt,
                isWeak = PasswordStrengthEstimator.isWeak(account.password),
                isDuplicate = account.id in duplicatePasswords,
                isOld = now - account.passwordChangedAt >= Constants.PASSWORD_MAX_AGE_MILLIS,
            )
        }
    }
}
