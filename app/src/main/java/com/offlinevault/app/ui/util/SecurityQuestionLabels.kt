package com.offlinevault.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.offlinevault.app.R
import com.offlinevault.app.data.model.SecurityQuestionPresets

/**
 * Preset questions are persisted as canonical English identifiers so that a
 * language change never breaks an existing answer. The UI renders their
 * localized labels while always storing the canonical text.
 */
@StringRes
fun securityQuestionLabelRes(question: String): Int? {
    val index = SecurityQuestionPresets.questions.indexOf(question)
    return presetLabelResources.getOrNull(index)
}

@Composable
fun localizedSecurityQuestion(question: String): String {
    val labelRes = securityQuestionLabelRes(question)
    return if (labelRes != null) stringResource(labelRes) else question
}

val presetLabelResources: List<Int> = listOf(
    R.string.preset_pet,
    R.string.preset_friend,
    R.string.preset_teacher,
    R.string.preset_city,
    R.string.preset_school,
    R.string.preset_book,
)
