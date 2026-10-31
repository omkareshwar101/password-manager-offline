package com.offlinevault.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.AccountRepository
import com.offlinevault.app.data.ImageStore
import com.offlinevault.app.data.model.AccountCategory
import com.offlinevault.app.data.model.AccountDraft
import com.offlinevault.app.util.Constants
import com.offlinevault.app.util.Validators
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val name: String = "",
    val imageRef: String? = null,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val category: AccountCategory = AccountCategory.OTHER,
    val nameError: Boolean = false,
    val passwordError: Boolean = false,
    val urlError: Boolean = false,
    val imageBusy: Boolean = false,
    val imageError: Boolean = false,
    val saveError: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

class AddEditViewModel(
    private val accountId: Long?,
    private val repository: AccountRepository,
    private val imageStore: ImageStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AddEditUiState(isLoading = accountId != null),
    )
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var imageJob: Job? = null

    /**
     * A pending image that has already been moved into permanent app storage
     * during a failed save. It is owned by this draft until the save succeeds.
     */
    private var committedThisSession: String? = null

    init {
        if (accountId != null) {
            viewModelScope.launch {
                runCatching { repository.getAccount(accountId) }
                    .onSuccess { account ->
                        if (account == null) {
                            _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    name = account.name,
                                    imageRef = account.imageRef,
                                    url = account.url.orEmpty(),
                                    username = account.username.orEmpty(),
                                    password = account.password,
                                    notes = account.notes.orEmpty(),
                                    category = account.category,
                                )
                            }
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    }
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value, nameError = false) }
    fun setUrl(value: String) = _uiState.update { it.copy(url = value, urlError = false) }
    fun setUsername(value: String) = _uiState.update { it.copy(username = value) }
    fun setPassword(value: String) = _uiState.update {
        it.copy(password = value, passwordError = false)
    }

    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun setCategory(category: AccountCategory) = _uiState.update { it.copy(category = category) }

    fun importImage(uri: Uri) {
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            _uiState.update { it.copy(imageBusy = true, imageError = false) }
            val previous = _uiState.value.imageRef
            val discardablePrevious = previous?.takeIf {
                it.startsWith(Constants.PENDING_IMAGE_PREFIX) || it == committedThisSession
            }
            runCatching { imageStore.importToPending(uri) }
                .onSuccess { ref ->
                    discardablePrevious?.let(imageStore::delete)
                    if (discardablePrevious == committedThisSession) committedThisSession = null
                    _uiState.update { it.copy(imageRef = ref, imageBusy = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(imageBusy = false, imageError = true) }
                }
        }
    }

    fun selectBuiltInIcon(iconRef: String) {
        discardDraftImage()
        _uiState.update { it.copy(imageRef = iconRef, imageError = false) }
    }

    fun removeImage() {
        discardDraftImage()
        _uiState.update { it.copy(imageRef = null) }
    }

    fun clearImageError() {
        _uiState.update { it.copy(imageError = false) }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = false) }
    }

    private fun discardDraftImage() {
        val previous = _uiState.value.imageRef
        if (previous?.startsWith(Constants.PENDING_IMAGE_PREFIX) == true) {
            imageStore.delete(previous)
        } else if (previous != null && previous == committedThisSession) {
            imageStore.delete(previous)
            committedThisSession = null
        }
    }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return
        val nameError = current.name.isBlank()
        val passwordError = current.password.isBlank()
        val urlError = current.url.isNotBlank() && !Validators.isValidUrl(current.url)
        if (nameError || passwordError || urlError) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    passwordError = passwordError,
                    urlError = urlError,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = false) }
            runCatching {
                val state = _uiState.value
                val finalImageRef = state.imageRef?.let { ref ->
                    if (ref.startsWith(Constants.PENDING_IMAGE_PREFIX)) {
                        imageStore.commitPending(ref).also { committed ->
                            committedThisSession = committed
                            // Keep the draft pointing at the committed file so a
                            // retry after a failed database write can reuse it.
                            _uiState.update { it.copy(imageRef = committed) }
                        }
                    } else {
                        ref
                    }
                }
                repository.saveAccount(
                    draft = AccountDraft(
                        name = state.name.trim(),
                        imageRef = finalImageRef,
                        url = state.url.trim().takeIf { it.isNotEmpty() },
                        username = state.username.trim().takeIf { it.isNotEmpty() },
                        password = state.password,
                        notes = state.notes.trim().takeIf { it.isNotEmpty() },
                        category = state.category,
                    ),
                    accountId = accountId,
                )
            }.onSuccess {
                committedThisSession = null
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, saveError = true) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val current = _uiState.value
        if (!current.saved) {
            current.imageRef
                ?.takeIf { it.startsWith(Constants.PENDING_IMAGE_PREFIX) }
                ?.let(imageStore::delete)
            committedThisSession?.let(imageStore::delete)
        }
        imageJob?.cancel()
    }
}
