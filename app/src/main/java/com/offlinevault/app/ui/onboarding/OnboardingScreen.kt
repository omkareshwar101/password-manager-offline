package com.offlinevault.app.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WifiOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.app.R
import com.offlinevault.app.security.BiometricHelper
import com.offlinevault.app.security.findFragmentActivity
import com.offlinevault.app.ui.components.OfflineBadge
import com.offlinevault.app.ui.components.OfflineVaultLogo
import com.offlinevault.app.ui.components.SecurityQuestionFields
import com.offlinevault.app.viewmodel.OnboardingError
import com.offlinevault.app.viewmodel.OnboardingViewModel
import com.offlinevault.app.ui.viewmodel.appViewModel

private const val TOTAL_STEPS = 4

@Composable
fun OnboardingScreen(
    onSetupComplete: () -> Unit,
) {
    val viewModel = appViewModel { OnboardingViewModel(it.securePrefs, it.settingsRepository, it.sessionManager) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var biometricFailed by remember { mutableStateOf(false) }

    LaunchedEffect(state.completed) {
        if (state.completed) onSetupComplete()
    }

    BackHandler {
        if (state.step > 0) {
            viewModel.back()
        } else {
            activity?.finish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step > 0) {
                    IconButton(onClick = viewModel::back) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                } else {
                    OfflineVaultLogo(size = 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.step_of, state.step + 1, TOTAL_STEPS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OfflineBadge()
            }

            StepIndicator(currentStep = state.step)

            AnimatedContent(
                targetState = state.step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding-step",
                modifier = Modifier.weight(1f),
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (step) {
                        0 -> PrivacyStep()
                        1 -> PinStep(
                            pin = state.pin,
                            pinConfirm = state.pinConfirm,
                            onPinChange = viewModel::setPin,
                            onConfirmChange = viewModel::setPinConfirm,
                        )

                        2 -> QuestionsStep(
                            state = state,
                            onSelectQuestion = viewModel::selectQuestion,
                            onSelectCustom = viewModel::selectCustomQuestion,
                            onCustomQuestionChange = viewModel::setCustomQuestion,
                            onAnswerChange = viewModel::setAnswer,
                        )

                        3 -> BiometricStep(
                            enabled = state.biometricEnabled,
                            onEnable = {
                                biometricFailed = false
                                if (activity != null) {
                                    BiometricHelper.authenticate(
                                        activity = activity,
                                        title = context.getString(R.string.biometric_confirm_prompt),
                                        subtitle = context.getString(R.string.onboarding_biometric_subtitle),
                                        negativeButtonText = context.getString(R.string.cancel),
                                        onSuccess = {
                                            viewModel.setBiometricEnabled(true)
                                        },
                                        onFailure = {
                                            biometricFailed = true
                                            viewModel.setBiometricEnabled(false)
                                        },
                                    )
                                } else {
                                    biometricFailed = true
                                }
                            },
                            onDisable = { viewModel.setBiometricEnabled(false) },
                            biometricFailed = biometricFailed,
                            onOpenSettings = {
                                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Settings.ACTION_BIOMETRIC_ENROLL
                                } else {
                                    Settings.ACTION_SECURITY_SETTINGS
                                }
                                runCatching {
                                    context.startActivity(Intent(action))
                                }
                            },
                        )
                    }

                    ErrorText(
                        step = state.step,
                        error = state.error,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.step > 0) {
                    OutlinedButton(
                        onClick = viewModel::back,
                        enabled = !state.isSaving,
                    ) {
                        Text(stringResource(R.string.back))
                    }
                }
                Button(
                    onClick = {
                        if (state.step == 3) viewModel.completeSetup() else viewModel.next()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.creating_vault))
                    } else {
                        Text(
                            when (state.step) {
                                0 -> stringResource(R.string.get_started)
                                3 -> stringResource(R.string.create_vault)
                                else -> stringResource(R.string.continue_label)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(TOTAL_STEPS) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index <= currentStep) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PrivacyStep() {
    Text(
        text = stringResource(R.string.privacy_title),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.privacy_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    PrivacyPoint(
        icon = Icons.Outlined.WifiOff,
        title = stringResource(R.string.privacy_no_internet_title),
        body = stringResource(R.string.privacy_no_internet_body),
    )
    PrivacyPoint(
        icon = Icons.Outlined.Shield,
        title = stringResource(R.string.privacy_encrypted_title),
        body = stringResource(R.string.privacy_encrypted_body),
    )
    PrivacyPoint(
        icon = Icons.Outlined.Key,
        title = stringResource(R.string.privacy_keystore_title),
        body = stringResource(R.string.privacy_keystore_body),
    )
    PrivacyPoint(
        icon = Icons.Outlined.Storage,
        title = stringResource(R.string.privacy_storage_title),
        body = stringResource(R.string.privacy_storage_body),
    )
    PrivacyPoint(
        icon = Icons.Outlined.Lock,
        title = stringResource(R.string.privacy_recovery_title),
        body = stringResource(R.string.privacy_recovery_body),
    )
}

@Composable
private fun PrivacyPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PinStep(
    pin: String,
    pinConfirm: String,
    onPinChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_pin_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.onboarding_pin_subtitle),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = pin,
        onValueChange = onPinChange,
        label = { Text(stringResource(R.string.pin)) },
        supportingText = { Text(stringResource(R.string.pin_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = pinConfirm,
        onValueChange = onConfirmChange,
        label = { Text(stringResource(R.string.confirm_pin)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = stringResource(R.string.pin_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun QuestionsStep(
    state: com.offlinevault.app.viewmodel.OnboardingUiState,
    onSelectQuestion: (Int, String) -> Unit,
    onSelectCustom: (Int) -> Unit,
    onCustomQuestionChange: (Int, String) -> Unit,
    onAnswerChange: (Int, String) -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_questions_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.onboarding_questions_subtitle),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SecurityQuestionFields(
        questions = state.questions,
        onSelectQuestion = onSelectQuestion,
        onSelectCustom = onSelectCustom,
        onCustomQuestionChange = onCustomQuestionChange,
        onAnswerChange = onAnswerChange,
        enabled = !state.isSaving,
    )
    Text(
        text = stringResource(R.string.security_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun BiometricStep(
    enabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    biometricFailed: Boolean,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val available = remember { BiometricHelper.isAvailable(context) }
    Text(
        text = stringResource(R.string.onboarding_biometric_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.onboarding_biometric_subtitle),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            if (enabled) {
                Text(
                    text = stringResource(R.string.biometrics_enabled),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.disable_biometric))
                }
            } else if (available) {
                Button(onClick = onEnable) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.enable_biometrics))
                }
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.skip_for_now))
                }
            } else {
                Text(
                    text = stringResource(R.string.no_biometrics_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.open_device_settings))
                }
                Text(
                    text = stringResource(R.string.biometric_setup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            if (biometricFailed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.authentication_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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

@Composable
private fun ErrorText(
    step: Int,
    error: OnboardingError?,
) {
    if (error == null) return
    val message = when (error) {
        OnboardingError.PIN_FORMAT,
        OnboardingError.PIN_MISMATCH,
        -> if (error == OnboardingError.PIN_FORMAT) {
            stringResource(R.string.pin_error_length)
        } else {
            stringResource(R.string.pin_error_mismatch)
        }

        OnboardingError.QUESTIONS_REQUIRED -> stringResource(R.string.answer_required)
        OnboardingError.QUESTIONS_DUPLICATE -> stringResource(R.string.questions_must_differ)
        OnboardingError.SETUP_FAILED -> stringResource(R.string.onboarding_error)
    }
    if (
        (step == 1 && (error == OnboardingError.PIN_FORMAT || error == OnboardingError.PIN_MISMATCH)) ||
        (step == 2 && (error == OnboardingError.QUESTIONS_REQUIRED || error == OnboardingError.QUESTIONS_DUPLICATE)) ||
        error == OnboardingError.SETUP_FAILED
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
