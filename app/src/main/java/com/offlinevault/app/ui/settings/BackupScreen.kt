package com.offlinevault.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.offlinevault.app.R
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.viewmodel.BackupMessage
import com.offlinevault.app.viewmodel.BackupViewModel
import com.offlinevault.app.ui.viewmodel.appViewModel
import com.offlinevault.app.util.Constants
import kotlinx.coroutines.launch

private enum class BackupOperation {
    EXPORT,
    IMPORT,
}

@Composable
fun BackupScreen(
    container: AppContainer,
    navController: NavHostController,
) {
    val viewModel = appViewModel { BackupViewModel(it.securePrefs, it.backupManager) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExportPin by remember { mutableStateOf(false) }
    var exportPin by remember { mutableStateOf("") }
    var exportPinError by remember { mutableStateOf(false) }
    var verifyingExportPin by remember { mutableStateOf(false) }
    var pendingExportPin by remember { mutableStateOf("") }

    var showImportWarning by remember { mutableStateOf(false) }
    var showImportPin by remember { mutableStateOf(false) }
    var importPin by remember { mutableStateOf("") }
    var importPinError by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var operation by remember { mutableStateOf<BackupOperation?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(Constants.EXPORT_MIME_TYPE),
    ) { uri ->
        if (uri != null && pendingExportPin.isNotBlank()) {
            operation = BackupOperation.EXPORT
            viewModel.export(uri, pendingExportPin)
        }
        pendingExportPin = ""
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportWarning = true
        }
    }

    fun startExport(pin: String) {
        verifyingExportPin = true
        scope.launch {
            val valid = runCatching { container.securePrefs.verifyPin(pin) }.getOrDefault(false)
            verifyingExportPin = false
            if (!valid) {
                exportPinError = true
            } else {
                pendingExportPin = pin
                exportPin = ""
                showExportPin = false
                // The file is created only after the PIN has been verified.
                exportLauncher.launch(context.getString(R.string.backup_file_name))
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            BackupMessage.Exported -> context.getString(R.string.export_success)
            BackupMessage.ExportFailed -> context.getString(R.string.export_failed)
            BackupMessage.InvalidCurrentPin -> context.getString(R.string.invalid_current_pin)
            is BackupMessage.Imported -> context.resources.getQuantityString(
                R.plurals.import_success,
                message.count,
                message.count,
            )
            BackupMessage.ImportFailed -> context.getString(R.string.import_failed)
        }
        operation = null
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    if (showExportPin) {
        BackupPinDialog(
            title = stringResource(R.string.export_pin_title),
            body = stringResource(R.string.export_pin_body),
            pin = exportPin,
            onPinChange = {
                if (it.length <= 12 && it.all(Char::isDigit)) {
                    exportPin = it
                    exportPinError = false
                }
            },
            isError = exportPinError,
            isWorking = verifyingExportPin,
            confirmLabel = stringResource(R.string.export_backup),
            onDismiss = {
                if (!verifyingExportPin) {
                    showExportPin = false
                    exportPin = ""
                    exportPinError = false
                }
            },
            onConfirm = { startExport(exportPin) },
        )
    }

    if (showImportWarning) {
        AlertDialog(
            onDismissRequest = {
                showImportWarning = false
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.import_warning_title)) },
            text = { Text(stringResource(R.string.import_warning_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showImportWarning = false
                        importPin = ""
                        importPinError = false
                        showImportPin = true
                    },
                ) {
                    Text(stringResource(R.string.continue_label))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportWarning = false
                        pendingImportUri = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showImportPin) {
        BackupPinDialog(
            title = stringResource(R.string.import_backup),
            body = stringResource(R.string.backup_pin_hint),
            pin = importPin,
            onPinChange = {
                if (it.length <= 12 && it.all(Char::isDigit)) {
                    importPin = it
                    importPinError = false
                }
            },
            isError = importPinError,
            isWorking = state.isWorking && operation == BackupOperation.IMPORT,
            confirmLabel = stringResource(R.string.restore_action),
            onDismiss = {
                if (!state.isWorking) {
                    showImportPin = false
                    importPin = ""
                    pendingImportUri = null
                }
            },
            onConfirm = {
                val uri = pendingImportUri
                if (uri != null && importPin.isNotBlank()) {
                    showImportPin = false
                    operation = BackupOperation.IMPORT
                    viewModel.import(uri, importPin)
                    importPin = ""
                    pendingImportUri = null
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { container.sessionManager.lock() }) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.content_desc_lock),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.isWorking) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(
                            if (operation == BackupOperation.IMPORT) {
                                R.string.reading_backup
                            } else {
                                R.string.writing_backup
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        text = stringResource(R.string.backup_never_network),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.export_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.export_backup_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            exportPin = ""
                            exportPinError = false
                            showExportPin = true
                        },
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_backup))
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = stringResource(R.string.import_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.import_backup_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.choose_backup))
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.backup_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = stringResource(R.string.about_clipboard_body),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BackupPinDialog(
    title: String,
    body: String,
    pin: String,
    onPinChange: (String) -> Unit,
    isError: Boolean,
    isWorking: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text(stringResource(R.string.pin)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.invalid_current_pin)) }
                    } else {
                        { Text(stringResource(R.string.pin_hint)) }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !isWorking,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = pin.isNotBlank() && !isWorking,
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(confirmLabel)
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
