package com.offlinevault.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.offlinevault.app.R
import com.offlinevault.app.data.model.ThemeMode
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.security.BiometricHelper
import com.offlinevault.app.security.findFragmentActivity
import com.offlinevault.app.ui.components.AppBottomBar
import com.offlinevault.app.ui.components.DividerLabel
import com.offlinevault.app.ui.components.rememberSensitiveActionGate
import com.offlinevault.app.ui.navigation.Routes
import com.offlinevault.app.viewmodel.PinChangeResult
import com.offlinevault.app.viewmodel.SettingsViewModel
import com.offlinevault.app.viewmodel.WipeResult
import com.offlinevault.app.ui.viewmodel.appViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    navController: NavHostController,
    onVaultWiped: () -> Unit,
) {
    val viewModel = appViewModel {
        SettingsViewModel(it.securePrefs, it.settingsRepository, it)
    }
    val settings by container.settingsRepository.state.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authFailed = stringResource(R.string.authentication_failed)
    val biometricUnavailable = stringResource(R.string.biometric_unavailable)
    val biometricNotEnrolled = stringResource(R.string.biometric_not_enrolled)
    val biometricEnabled = stringResource(R.string.biometrics_enabled)
    val wipeFailed = stringResource(R.string.wipe_failed)
    val wipeSuccess = stringResource(R.string.wipe_success)

    var showChangePin by remember { mutableStateOf(false) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showClipboard by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var wipeStage by remember { mutableIntStateOf(0) }
    var wipePin by remember { mutableStateOf("") }
    var wipeError by remember { mutableStateOf(false) }

    val requireAuth = rememberSensitiveActionGate(container) {
        scope.launch { snackbarHostState.showSnackbar(authFailed) }
    }

    fun enableBiometrics() {
        if (!BiometricHelper.isAvailable(context)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (activity == null) biometricUnavailable else biometricNotEnrolled,
                )
            }
            return
        }
        if (activity == null) {
            scope.launch { snackbarHostState.showSnackbar(biometricUnavailable) }
            return
        }
        BiometricHelper.authenticate(
            activity = activity,
            title = context.getString(R.string.biometric_enabled_prompt),
            subtitle = context.getString(R.string.biometric_unlock_desc),
            negativeButtonText = context.getString(R.string.cancel),
            onSuccess = {
                viewModel.setBiometricEnabled(true)
                Toast.makeText(context, biometricEnabled, Toast.LENGTH_SHORT).show()
            },
            onFailure = {
                scope.launch { snackbarHostState.showSnackbar(authFailed) }
            },
        )
    }

    if (showChangePin) {
        ChangePinDialog(
            viewModel = viewModel,
            onDismiss = { showChangePin = false },
        )
    }

    if (showAutoLock) {
        ChoiceDialog(
            title = stringResource(R.string.auto_lock),
            options = listOf(
                0 to stringResource(R.string.auto_lock_immediate),
                15 to durationLabel(15),
                30 to durationLabel(30),
                60 to durationLabel(60),
                300 to durationLabel(300),
            ),
            selected = settings.autoLockSeconds,
            onDismiss = { showAutoLock = false },
            onSelect = {
                viewModel.setAutoLockSeconds(it)
                showAutoLock = false
            },
        )
    }

    if (showClipboard) {
        ChoiceDialog(
            title = stringResource(R.string.clipboard_clear),
            options = listOf(
                0 to stringResource(R.string.clipboard_off),
                15 to durationLabel(15),
                30 to durationLabel(30),
                60 to durationLabel(60),
            ),
            selected = settings.clipboardSeconds,
            onDismiss = { showClipboard = false },
            onSelect = {
                viewModel.setClipboardSeconds(it)
                showClipboard = false
            },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = settings.themeMode,
            onDismiss = { showTheme = false },
            onSelect = {
                viewModel.setThemeMode(it)
                showTheme = false
            },
        )
    }

    if (wipeStage == 1) {
        AlertDialog(
            onDismissRequest = { wipeStage = 0 },
            title = { Text(stringResource(R.string.wipe_double_title)) },
            text = { Text(stringResource(R.string.wipe_double_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        wipePin = ""
                        wipeError = false
                        wipeStage = 2
                    },
                ) {
                    Text(stringResource(R.string.wipe_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeStage = 0 }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (wipeStage == 2) {
        AlertDialog(
            onDismissRequest = { if (!isWorking) wipeStage = 0 },
            title = { Text(stringResource(R.string.wipe_pin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.wipe_pin_body))
                    OutlinedTextField(
                        value = wipePin,
                        onValueChange = {
                            if (it.length <= 12 && it.all(Char::isDigit)) {
                                wipePin = it
                                wipeError = false
                            }
                        },
                        label = { Text(stringResource(R.string.current_pin)) },
                        singleLine = true,
                        isError = wipeError,
                        supportingText = if (wipeError) {
                            { Text(stringResource(R.string.invalid_current_pin)) }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !isWorking,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllData(wipePin) { result ->
                            when (result) {
                                WipeResult.SUCCESS -> {
                                    wipeStage = 0
                                    Toast.makeText(context, wipeSuccess, Toast.LENGTH_SHORT).show()
                                    onVaultWiped()
                                }

                                WipeResult.INVALID_PIN -> wipeError = true
                                WipeResult.FAILED -> {
                                    wipeStage = 0
                                    scope.launch { snackbarHostState.showSnackbar(wipeFailed) }
                                }
                            }
                        }
                    },
                    enabled = wipePin.isNotBlank() && !isWorking,
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (isWorking) R.string.deleting else R.string.wipe_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeStage = 0 }, enabled = !isWorking) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                actions = {
                    IconButtonLock(container)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = Routes.SETTINGS,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.VAULT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
        ) {
            DividerLabel(text = stringResource(R.string.section_security))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.change_pin),
                    subtitle = stringResource(R.string.change_pin_desc),
                    onClick = { showChangePin = true },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.ManageAccounts,
                    title = stringResource(R.string.change_security_questions),
                    subtitle = stringResource(R.string.change_security_questions_desc),
                    onClick = {
                        requireAuth(
                            context.getString(R.string.auth_manage_questions_title),
                            context.getString(R.string.auth_manage_questions_subtitle),
                        ) {
                            navController.navigate(Routes.SECURITY_QUESTIONS)
                        }
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = stringResource(R.string.biometric_unlock),
                    subtitle = stringResource(R.string.biometric_unlock_desc),
                    checked = settings.biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) enableBiometrics() else viewModel.setBiometricEnabled(false)
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.auto_lock),
                    subtitle = autoLockLabel(settings.autoLockSeconds),
                    onClick = { showAutoLock = true },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = stringResource(R.string.clipboard_clear),
                    subtitle = clipboardLabel(settings.clipboardSeconds),
                    onClick = { showClipboard = true },
                )
            }

            Text(
                text = stringResource(R.string.clipboard_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )

            DividerLabel(text = stringResource(R.string.section_appearance))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.theme),
                    subtitle = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    },
                    onClick = { showTheme = true },
                )
            }

            DividerLabel(text = stringResource(R.string.section_data))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Backup,
                    title = stringResource(R.string.backup_restore),
                    subtitle = stringResource(R.string.backup_restore_desc),
                    onClick = { navController.navigate(Routes.BACKUP) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = stringResource(R.string.delete_all_data),
                    subtitle = stringResource(R.string.delete_all_data_desc),
                    onClick = {
                        requireAuth(
                            context.getString(R.string.wipe_double_title),
                            context.getString(R.string.wipe_pin_body),
                        ) {
                            wipeStage = 1
                        }
                    },
                    destructive = true,
                )
            }

            DividerLabel(text = stringResource(R.string.section_about))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_privacy),
                    subtitle = stringResource(R.string.about_privacy_desc),
                    onClick = { navController.navigate(Routes.ABOUT) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.offline_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconButtonLock(container: AppContainer) {
    androidx.compose.material3.IconButton(onClick = { container.sessionManager.lock() }) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = stringResource(R.string.content_desc_lock),
        )
    }
}

@Composable
private fun autoLockLabel(seconds: Int): String =
    durationLabel(seconds, R.string.auto_lock_immediate)

@Composable
private fun clipboardLabel(seconds: Int): String =
    durationLabel(seconds, R.string.clipboard_off)

@Composable
private fun durationLabel(
    seconds: Int,
    zeroLabel: Int = R.string.auto_lock_immediate,
): String = when {
    seconds <= 0 -> stringResource(zeroLabel)
    seconds >= 60 -> pluralStringResource(
        R.plurals.auto_lock_minutes,
        seconds / 60,
        seconds / 60,
    )
    else -> pluralStringResource(R.plurals.auto_lock_seconds, seconds, seconds)
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ChangePinDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(stringResource(R.string.change_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinField(
                    value = currentPin,
                    onValueChange = { currentPin = it; errorRes = null },
                    label = stringResource(R.string.current_pin),
                    enabled = !isWorking,
                )
                PinField(
                    value = newPin,
                    onValueChange = { newPin = it; errorRes = null },
                    label = stringResource(R.string.new_pin),
                    enabled = !isWorking,
                )
                PinField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it; errorRes = null },
                    label = stringResource(R.string.confirm_new_pin),
                    enabled = !isWorking,
                )
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes!!),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.pin_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.changePin(currentPin, newPin, confirmPin) { result ->
                        when (result) {
                            PinChangeResult.Success -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pin_changed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onDismiss()
                            }

                            PinChangeResult.WrongCurrentPin ->
                                errorRes = R.string.invalid_current_pin

                            PinChangeResult.InvalidNewPin ->
                                errorRes = R.string.pin_error_length

                            PinChangeResult.Mismatch ->
                                errorRes = R.string.pins_dont_match

                            PinChangeResult.Failed ->
                                errorRes = R.string.error_generic
                        }
                    }
                },
                enabled = currentPin.isNotBlank() &&
                    newPin.isNotBlank() &&
                    confirmPin.isNotBlank() &&
                    !isWorking,
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.change_pin))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (it.length <= 12 && it.all(Char::isDigit)) onValueChange(it)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        enabled = enabled,
    )
}
