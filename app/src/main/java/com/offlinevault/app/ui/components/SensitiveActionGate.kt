package com.offlinevault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.app.R
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.security.BiometricHelper
import com.offlinevault.app.security.findFragmentActivity
import kotlinx.coroutines.launch

/**
 * A reusable re-authentication gate for revealing a secret when biometrics are
 * unavailable or disabled. When biometrics are available, a BiometricPrompt is
 * shown immediately; a failed/cancelled prompt never runs the protected action.
 */
@Composable
fun rememberSensitiveActionGate(
    container: AppContainer,
    onFailure: () -> Unit,
): (title: String, subtitle: String, action: () -> Unit) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val settings by container.settingsRepository.state.collectAsStateWithLifecycle()
    val currentOnFailure by rememberUpdatedState(onFailure)
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTitle by remember { mutableStateOf("") }
    var pendingSubtitle by remember { mutableStateOf("") }
    var authInProgress by remember { mutableStateOf(false) }

    val requireAuth: (String, String, () -> Unit) -> Unit = { title, subtitle, action ->
        if (!authInProgress) {
            val canUseBiometric = settings.biometricEnabled &&
                BiometricHelper.isAvailable(context) &&
                activity != null
            if (canUseBiometric) {
                authInProgress = true
                BiometricHelper.authenticate(
                    activity = activity!!,
                    title = title,
                    subtitle = subtitle,
                    negativeButtonText = context.getString(R.string.cancel),
                    onSuccess = {
                        authInProgress = false
                        action()
                    },
                    onFailure = {
                        authInProgress = false
                        currentOnFailure()
                    },
                )
            } else {
                pendingAction = action
                pendingTitle = title
                pendingSubtitle = subtitle
                showPinDialog = true
            }
        }
    }

    if (showPinDialog) {
        MasterPinDialog(
            container = container,
            title = pendingTitle,
            description = pendingSubtitle,
            onVerified = {
                val action = pendingAction
                showPinDialog = false
                pendingAction = null
                action?.invoke()
            },
            onDismiss = {
                showPinDialog = false
                pendingAction = null
            },
        )
    }

    return requireAuth
}

@Composable
fun MasterPinDialog(
    container: AppContainer,
    title: String,
    description: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= 12 && value.all(Char::isDigit)) {
                            pin = value
                            error = false
                        }
                    },
                    label = { Text(stringResource(R.string.pin)) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.invalid_current_pin)) }
                    } else {
                        { Text(stringResource(R.string.pin_hint)) }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !verifying,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.isNotBlank() && !verifying,
                onClick = {
                    scope.launch {
                        verifying = true
                        val success = runCatching {
                            container.securePrefs.verifyPin(pin)
                        }.getOrDefault(false)
                        verifying = false
                        if (success) onVerified() else error = true
                    }
                },
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.verify))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !verifying) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
