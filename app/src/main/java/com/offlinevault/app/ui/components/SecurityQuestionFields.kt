package com.offlinevault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.offlinevault.app.R
import com.offlinevault.app.data.model.SecurityQuestionDraft
import com.offlinevault.app.data.model.SecurityQuestionPresets
import com.offlinevault.app.ui.util.localizedSecurityQuestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuestionFields(
    questions: List<SecurityQuestionDraft>,
    onSelectQuestion: (Int, String) -> Unit,
    onSelectCustom: (Int) -> Unit,
    onCustomQuestionChange: (Int, String) -> Unit,
    onAnswerChange: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        questions.take(3).forEachIndexed { index, draft ->
            var expanded by remember { mutableStateOf(false) }
            var answerVisible by remember { mutableStateOf(false) }
            val customLabel = stringResource(R.string.preset_custom)
            val display = if (draft.isCustom) {
                customLabel
            } else {
                localizedSecurityQuestion(draft.question)
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.question_n, index + 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (enabled) expanded = it },
                    ) {
                        OutlinedTextField(
                            value = display,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.choose_question)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            enabled = enabled,
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            SecurityQuestionPresets.questions.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(localizedSecurityQuestion(preset)) },
                                    onClick = {
                                        onSelectQuestion(index, preset)
                                        expanded = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(customLabel) },
                                onClick = {
                                    onSelectCustom(index)
                                    expanded = false
                                },
                            )
                        }
                    }

                    if (draft.isCustom) {
                        OutlinedTextField(
                            value = draft.customQuestion,
                            onValueChange = { onCustomQuestionChange(index, it) },
                            label = { Text(stringResource(R.string.custom_question_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = draft.answer,
                        onValueChange = { onAnswerChange(index, it) },
                        label = { Text(stringResource(R.string.answer_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = if (answerVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        trailingIcon = {
                            IconButton(onClick = { answerVisible = !answerVisible }) {
                                Icon(
                                    imageVector = if (answerVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (answerVisible) R.string.hide else R.string.show,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.security_answers_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
