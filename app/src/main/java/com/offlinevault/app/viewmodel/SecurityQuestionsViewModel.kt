package com.offlinevault.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinevault.app.data.model.SecurityQuestionAnswer
import com.offlinevault.app.data.model.SecurityQuestionDraft
import com.offlinevault.app.data.model.SecurityQuestionPresets
import com.offlinevault.app.security.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SecurityQuestionsError {
    MISSING,
    DUPLICATE,
    SAVE_FAILED,
}

data class SecurityQuestionsUiState(
    val questions: List<SecurityQuestionDraft>,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: SecurityQuestionsError? = null,
)

class SecurityQuestionsViewModel(
    private val securePrefs: SecurePrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SecurityQuestionsUiState(questions = buildInitialDrafts()),
    )
    val state: StateFlow<SecurityQuestionsUiState> = _state.asStateFlow()

    private fun buildInitialDrafts(): List<SecurityQuestionDraft> {
        val existing = securePrefs.getSecurityQuestions()
        return (0..2).map { index ->
            val label = existing.getOrNull(index).orEmpty()
            if (label.isBlank()) {
                SecurityQuestionDraft(
                    question = SecurityQuestionPresets.questions.getOrElse(index) {
                        SecurityQuestionPresets.questions.first()
                    },
                )
            } else if (label in SecurityQuestionPresets.questions) {
                SecurityQuestionDraft(question = label)
            } else {
                SecurityQuestionDraft(
                    isCustom = true,
                    customQuestion = label,
                )
            }
        }
    }

    fun selectQuestion(index: Int, question: String) {
        updateDraft(index) { it.copy(question = question, isCustom = false, customQuestion = "") }
    }

    fun selectCustom(index: Int) {
        updateDraft(index) { it.copy(question = "", isCustom = true) }
    }

    fun setCustomQuestion(index: Int, value: String) {
        updateDraft(index) { it.copy(isCustom = true, customQuestion = value) }
    }

    fun setAnswer(index: Int, value: String) {
        updateDraft(index) { it.copy(answer = value) }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        val effective = current.questions.map { it.effectiveQuestion }
        if (effective.any { it.isBlank() } || current.questions.any { it.answer.isBlank() }) {
            _state.update { it.copy(error = SecurityQuestionsError.MISSING) }
            return
        }
        if (effective.map { it.lowercase() }.distinct().size != 3) {
            _state.update { it.copy(error = SecurityQuestionsError.DUPLICATE) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                securePrefs.updateSecurityQuestions(
                    current.questions.map { draft ->
                        SecurityQuestionAnswer(
                            question = draft.effectiveQuestion,
                            answer = draft.answer.trim(),
                        )
                    },
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        questions = it.questions.map { question -> question.copy(answer = "") },
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(isSaving = false, error = SecurityQuestionsError.SAVE_FAILED)
                }
            }
        }
    }

    private fun updateDraft(index: Int, transform: (SecurityQuestionDraft) -> SecurityQuestionDraft) {
        if (index !in 0..2) return
        _state.update { state ->
            val updated = state.questions.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(questions = updated, error = null)
        }
    }
}
