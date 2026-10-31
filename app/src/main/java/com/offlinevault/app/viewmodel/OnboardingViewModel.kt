package com.offlinevault.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.SettingsRepository
import com.offlinevault.app.data.model.SecurityQuestionAnswer
import com.offlinevault.app.data.model.SecurityQuestionDraft
import com.offlinevault.app.data.model.SecurityQuestionPresets
import com.offlinevault.app.security.SecurePrefs
import com.offlinevault.app.security.SessionManager
import com.offlinevault.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingError {
    PIN_FORMAT,
    PIN_MISMATCH,
    QUESTIONS_REQUIRED,
    QUESTIONS_DUPLICATE,
    SETUP_FAILED,
}

data class OnboardingUiState(
    val step: Int = 0,
    val pin: String = "",
    val pinConfirm: String = "",
    val questions: List<SecurityQuestionDraft> = listOf(
        SecurityQuestionDraft(question = SecurityQuestionPresets.questions[0]),
        SecurityQuestionDraft(question = SecurityQuestionPresets.questions[1]),
        SecurityQuestionDraft(question = SecurityQuestionPresets.questions[2]),
    ),
    val biometricEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: OnboardingError? = null,
    val completed: Boolean = false,
)

class OnboardingViewModel(
    private val securePrefs: SecurePrefs,
    private val settingsRepository: SettingsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setPin(value: String) {
        if (value.length <= 12 && value.all(Char::isDigit)) {
            _state.update { it.copy(pin = value, error = null) }
        }
    }

    fun setPinConfirm(value: String) {
        if (value.length <= 12 && value.all(Char::isDigit)) {
            _state.update { it.copy(pinConfirm = value, error = null) }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    fun selectQuestion(index: Int, question: String) {
        if (index !in 0..2) return
        _state.update { current ->
            val updated = current.questions.toMutableList()
            updated[index] = updated[index].copy(
                question = question,
                isCustom = false,
                customQuestion = "",
            )
            current.copy(questions = updated, error = null)
        }
    }

    fun selectCustomQuestion(index: Int) {
        if (index !in 0..2) return
        _state.update { current ->
            val updated = current.questions.toMutableList()
            updated[index] = updated[index].copy(
                question = "",
                isCustom = true,
            )
            current.copy(questions = updated, error = null)
        }
    }

    fun setCustomQuestion(index: Int, value: String) {
        if (index !in 0..2) return
        _state.update { current ->
            val updated = current.questions.toMutableList()
            updated[index] = updated[index].copy(isCustom = true, customQuestion = value)
            current.copy(questions = updated, error = null)
        }
    }

    fun setAnswer(index: Int, value: String) {
        if (index !in 0..2) return
        _state.update { current ->
            val updated = current.questions.toMutableList()
            updated[index] = updated[index].copy(answer = value)
            current.copy(questions = updated, error = null)
        }
    }

    fun next() {
        val current = _state.value
        when (current.step) {
            0 -> _state.update { it.copy(step = 1, error = null) }
            1 -> {
                val error = validatePin(current)
                if (error != null) {
                    _state.update { it.copy(error = error) }
                } else {
                    _state.update { it.copy(step = 2, error = null) }
                }
            }
            2 -> {
                val error = validateQuestions(current)
                if (error != null) {
                    _state.update { it.copy(error = error) }
                } else {
                    _state.update { it.copy(step = 3, error = null) }
                }
            }
        }
    }

    fun back() {
        _state.update {
            if (it.step == 0) it else it.copy(step = it.step - 1, error = null)
        }
    }

    fun completeSetup() {
        val current = _state.value
        if (current.isSaving || current.completed) return
        val pinError = validatePin(current)
        if (pinError != null) {
            _state.update { it.copy(error = pinError, step = 1) }
            return
        }
        val questionError = validateQuestions(current)
        if (questionError != null) {
            _state.update { it.copy(error = questionError, step = 2) }
            return
        }

        val answers = current.questions.map {
            SecurityQuestionAnswer(
                question = requireNotNull(it.effectiveQuestion.takeIf(String::isNotBlank)),
                answer = it.answer.trim(),
            )
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                securePrefs.createVault(
                    pin = current.pin,
                    questions = answers,
                    biometricEnabled = current.biometricEnabled,
                )
                settingsRepository.reload()
                sessionManager.unlock()
            }.onSuccess {
                _state.update {
                    it.copy(
                        isSaving = false,
                        completed = true,
                        pin = "",
                        pinConfirm = "",
                        questions = it.questions.map { question ->
                            question.copy(answer = "")
                        },
                    )
                }
            }.onFailure {
                _state.update { it.copy(isSaving = false, error = OnboardingError.SETUP_FAILED) }
            }
        }
    }

    private fun validatePin(state: OnboardingUiState): OnboardingError? {
        if (!Validators.isValidPin(state.pin)) return OnboardingError.PIN_FORMAT
        if (state.pin != state.pinConfirm) return OnboardingError.PIN_MISMATCH
        return null
    }

    private fun validateQuestions(state: OnboardingUiState): OnboardingError? {
        val effective = state.questions.map { it.effectiveQuestion }
        if (effective.any { it.isBlank() } || state.questions.any { it.answer.isBlank() }) {
            return OnboardingError.QUESTIONS_REQUIRED
        }
        if (effective.map { it.lowercase() }.distinct().size != 3) {
            return OnboardingError.QUESTIONS_DUPLICATE
        }
        return null
    }
}
