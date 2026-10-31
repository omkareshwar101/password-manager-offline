package com.offlinevault.app.data.model

data class SecurityQuestionDraft(
    val question: String = "",
    val customQuestion: String = "",
    val answer: String = "",
    val isCustom: Boolean = false,
) {
    val effectiveQuestion: String
        get() = if (isCustom) customQuestion.trim() else question.trim()
}

object SecurityQuestionPresets {
    /**
     * Canonical English labels are persisted so that switching the UI language
     * does not change which question was configured. Custom questions are
     * stored exactly as the user wrote them.
     */
    val questions: List<String> = listOf(
        "What was your first pet's name?",
        "What is your childhood best friend's name?",
        "What was your favourite teacher's name?",
        "What city were you born in?",
        "What was the name of your first school?",
        "What is your favourite book?",
    )
}
