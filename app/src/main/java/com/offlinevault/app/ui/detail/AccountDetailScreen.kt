package com.offlinevault.app.ui.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.offlinevault.app.R
import com.offlinevault.app.data.model.Account
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.ui.components.AccountAvatar
import com.offlinevault.app.ui.components.rememberSensitiveActionGate
import com.offlinevault.app.ui.navigation.Routes
import com.offlinevault.app.ui.util.relativeAge
import com.offlinevault.app.ui.util.rememberCopyMessage
import com.offlinevault.app.viewmodel.AccountDetailViewModel
import com.offlinevault.app.viewmodel.DetailMessage
import com.offlinevault.app.ui.viewmodel.appViewModel
import kotlinx.coroutines.launch

@Composable
fun AccountDetailScreen(
    accountId: Long,
    container: AppContainer,
    navController: NavHostController,
) {
    val viewModel = appViewModel(key = "detail-$accountId") {
        AccountDetailViewModel(accountId, it.accountRepository, it.sessionManager)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account = state.account
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copy = rememberCopyMessage(container)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var firstStart by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val authTitle = stringResource(R.string.auth_reveal_title)
    val authFailed = stringResource(R.string.password_not_revealed)
    val showWarnings = state.isWeak || state.isOld || state.isDuplicate

    val requireAuth = rememberSensitiveActionGate(container) {
        scope.launch { snackbarHostState.showSnackbar(authFailed) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.hidePassword()
                Lifecycle.Event.ON_START -> {
                    if (firstStart) {
                        firstStart = false
                    } else {
                        // Returning from the editor should show fresh values.
                        viewModel.loadAccount()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                DetailMessage.LOAD_FAILED -> context.getString(R.string.error_generic)
                DetailMessage.REVEAL_FAILED -> context.getString(R.string.error_generic)
                DetailMessage.FAVORITE_FAILED -> context.getString(R.string.error_generic)
                DetailMessage.DELETE_FAILED -> context.getString(R.string.error_generic)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            Toast.makeText(context, context.getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    if (showDeleteDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = {
                Text(stringResource(R.string.delete_account_body, account.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_details)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    if (account != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (account.isFavorite) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarBorder
                                },
                                contentDescription = stringResource(
                                    if (account.isFavorite) R.string.unfavorite else R.string.favorite,
                                ),
                                tint = if (account.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
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
        bottomBar = {
            if (account != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { navController.navigate(Routes.edit(account.id)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.edit))
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.share_blocked_title),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            account == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.account_not_found))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::loadAccount) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AccountAvatar(
                        imageRef = account.imageRef,
                        accountName = account.name,
                        size = 96.dp,
                        shape = CircleShape,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(account.category.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.last_changed,
                            relativeAge(account.passwordChangedAt),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    if (showWarnings) {
                        WarningsCard(
                            isWeak = state.isWeak,
                            isOld = state.isOld,
                            isDuplicate = state.isDuplicate,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    DetailFieldCard(
                        label = stringResource(R.string.url_label),
                        value = account.url?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.no_url),
                        copyValue = account.url,
                        copyContentDescription = stringResource(R.string.copy_url),
                        onCopy = copy,
                        enabled = !account.url.isNullOrBlank(),
                        valueDecoration = TextDecoration.Underline,
                    )
                    Spacer(Modifier.height(10.dp))
                    DetailFieldCard(
                        label = stringResource(R.string.username_label),
                        value = account.username?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.no_username),
                        copyValue = account.username,
                        copyContentDescription = stringResource(R.string.copy_username),
                        onCopy = copy,
                        enabled = !account.username.isNullOrBlank(),
                    )
                    Spacer(Modifier.height(10.dp))

                    PasswordCard(
                        password = account.password,
                        visible = state.passwordVisible,
                        onReveal = {
                            if (state.passwordVisible) {
                                viewModel.hidePassword()
                            } else {
                                val subtitle = context.getString(
                                    R.string.auth_reveal_subtitle,
                                    account.name,
                                )
                                requireAuth(authTitle, subtitle) { viewModel.showPassword() }
                            }
                        },
                        onCopy = {
                            val subtitle = context.getString(
                                R.string.auth_reveal_subtitle,
                                account.name,
                            )
                            if (state.passwordVisible) {
                                copy(account.password)
                            } else {
                                requireAuth(authTitle, subtitle) { copy(account.password) }
                            }
                        },
                    )

                    Spacer(Modifier.height(10.dp))
                    DetailFieldCard(
                        label = stringResource(R.string.notes_label),
                        value = account.notes?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.no_notes),
                        copyValue = account.notes,
                        copyContentDescription = stringResource(R.string.copy_notes),
                        onCopy = copy,
                        enabled = !account.notes.isNullOrBlank(),
                        multiline = true,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.open_in_browser_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.share_blocked_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.detail_security_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun WarningsCard(
    isWeak: Boolean,
    isOld: Boolean,
    isDuplicate: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isDuplicate) {
                WarningLine(stringResource(R.string.duplicate_warning))
            }
            if (isWeak) {
                WarningLine(stringResource(R.string.weak_password_warning))
            }
            if (isOld) {
                WarningLine(stringResource(R.string.old_password_warning))
            }
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailFieldCard(
    label: String,
    value: String,
    copyValue: String?,
    copyContentDescription: String,
    onCopy: (String) -> Unit,
    enabled: Boolean,
    multiline: Boolean = false,
    valueDecoration: TextDecoration? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = valueDecoration,
                    maxLines = if (multiline) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { copyValue?.let(onCopy) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = copyContentDescription,
                )
            }
        }
    }
}

@Composable
private fun PasswordCard(
    password: String,
    visible: Boolean,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.password_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = password,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!visible) {
                    Text(
                        text = "â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢",
                        style = MaterialTheme.typography.bodyLarge,
                        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                        modifier = Modifier.weight(1f),
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy_password),
                    )
                }
                IconButton(onClick = onReveal) {
                    Icon(
                        imageVector = if (visible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (visible) R.string.hide_password else R.string.reveal_password,
                        ),
                    )
                }
            }
            if (visible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.password_auto_hide_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TextButton(onClick = onReveal, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.reveal_password))
                }
            }
        }
    }
}
