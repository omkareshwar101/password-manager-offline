package com.offlinevault.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.app.R
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.security.BiometricHelper
import com.offlinevault.app.security.findFragmentActivity
import com.offlinevault.app.ui.components.OfflineBadge
import com.offlinevault.app.ui.components.OfflineVaultLogo
import com.offlinevault.app.ui.util.formatLockout
import com.offlinevault.app.ui.util.localizedSecurityQuestion
import com.offlinevault.app.viewmodel.AuthError
import com.offlinevault.app.viewmodel.AuthViewModel
import com.offlinevault.app.ui.viewmodel.appViewModel
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(container: AppContainer) {
    val viewModel = appViewModel { AuthViewModel(it.securePrefs, it.sessionManager) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember(context) { BiometricHelper.isAvailable(context) }
    val canUseBiometric = settings.biometricEnabled && biometricAvailable
    val lockedOut = state.lockoutRemainingMillis > 0L
    var biometricInProgress by remember { mutableStateOf(false) }
    var autoPrompted by remember { mutableStateOf(false) }

    val promptBiometric: () -> Unit = {
        if (!biometricInProgress && !lockedOut && !state.forceQuestions && activity != null) {
            biometricInProgress = true
            BiometricHelper.authenticate(
                activity = activity,
                title = context.getString(R.string.biometric_prompt_title),
                subtitle = context.getString(R.string.biometric_prompt_subtitle),
                negativeButtonText = context.getString(R.string.use_pin),
                onSuccess = {
                    biometricInProgress = false
                    viewModel.onBiometricSucceeded()
                },
                onFailure = {
                    biometricInProgress = false
                    viewModel.onBiometricFailed()
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(350)
        if (!autoPrompted && canUseBiometric && !state.forceQuestions && !lockedOut) {
            autoPrompted = true
            promptBiometric()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OfflineVaultLogo(size = 48.dp)
                    OfflineBadge()
                }

                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lock_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(26.dp))

                if (lockedOut) {
                    LockoutCard(remaining = state.lockoutRemainingMillis)
                } else {
                    if (state.forceQuestions) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.auth_force_questions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (state.showQuestions) {
                        SecurityQuestionsPanel(
                            questions = state.securityQuestions,
                            isBusy = state.isBusy,
                            onSubmit = viewModel::submitSecurityAnswers,
                        )
                        if (!state.forceQuestions) {
                            TextButton(onClick = viewModel::showPin) {
                                Text(stringResource(R.string.use_pin))
                            }
                        }
                    } else {
                        PinPanel(
                            pin = state.pin,
                            isBusy = state.isBusy,
                            onPinChange = viewModel::setPin,
                            onUnlock = viewModel::submitPin,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (canUseBiometric) {
                            OutlinedButton(
                                onClick = promptBiometric,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isBusy,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.use_biometrics))
                            }
                            Spacer(Modifier.height(6.dp))
                        } else if (settings.biometricEnabled && !biometricAvailable) {
                            Text(
                                text = stringResource(R.string.biometric_not_enrolled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        TextButton(onClick = viewModel::showQuestions) {
                            Text(stringResource(R.string.use_security_questions))
                        }
                    }
                }

                AuthErrorText(error = state.error, lockedOut = lockedOut)
                Spacer(Modifier.height(30.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.auth_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PinPanel(
    pin: String,
    isBusy: Boolean,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    OutlinedTextField(
        value = pin,
        onValueChange = onPinChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isBusy,
        label = { Text(stringResource(R.string.enter_pin)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onUnlock,
        modifier = Modifier.fillMaxWidth(),
        enabled = pin.isNotBlank() && !isBusy,
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(R.string.unlock_vault))
    }
}

@Composable
private fun SecurityQuestionsPanel(
    questions: List<String>,
    isBusy: Boolean,
    onSubmit: (List<String>) -> Unit,
) {
    val answers = remember(questions) {
        mutableStateListOf(*Array(questions.size.coerceAtLeast(3)) { "" })
    }
    val visible = remember(questions) {
        mutableStateListOf(*Array(questions.size.coerceAtLeast(3)) { false })
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.auth_questions_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.auth_questions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        questions.take(3).forEachIndexed { index, question ->
            OutlinedTextField(
                value = answers.getOrNull(index).orEmpty(),
                onValueChange = { answers[index] = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                label = { Text(localizedSecurityQuestion(question)) },
                singleLine = true,
                visualTransformation = if (visible.getOrNull(index) == true) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { visible[index] = !visible[index] }) {
                        Icon(
                            imageVector = if (visible.getOrNull(index) == true) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (visible.getOrNull(index) == true) R.string.hide else R.string.show,
                            ),
                        )
                    }
                },
            )
        }
        Button(
            onClick = { onSubmit(answers.toList()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy && answers.count { it.isNotBlank() } >= 2,
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.submit_answers))
        }
    }
}

@Composable
private fun LockoutCard(remaining: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.auth_locked_out,
                    formatLockout(remaining),
                ),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.security_question_locked_note),
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AuthErrorText(error: AuthError?, lockedOut: Boolean) {
    if (error == null || (lockedOut && error == AuthError.LOCKED_OUT)) return
    val message = when (error) {
        AuthError.INVALID_PIN -> stringResource(R.string.auth_invalid_pin)
        AuthError.INVALID_ANSWERS -> stringResource(R.string.auth_invalid_answers)
        AuthError.LOCKED_OUT -> stringResource(R.string.auth_locked_out_short)
        AuthError.BIOMETRIC_FAILED -> stringResource(R.string.auth_biometric_failed)
        AuthError.FORCE_QUESTIONS -> stringResource(R.string.auth_force_questions)
        AuthError.GENERIC -> stringResource(R.string.error_generic)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
