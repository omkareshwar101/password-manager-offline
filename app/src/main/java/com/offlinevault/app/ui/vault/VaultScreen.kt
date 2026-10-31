package com.offlinevault.app.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.offlinevault.app.R
import com.offlinevault.app.data.model.AccountCategory
import com.offlinevault.app.data.model.AccountSummary
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.ui.components.AccountAvatar
import com.offlinevault.app.ui.components.AppBottomBar
import com.offlinevault.app.ui.components.EmptyState
import com.offlinevault.app.ui.components.OfflineVaultLogo
import com.offlinevault.app.ui.components.rememberSensitiveActionGate
import com.offlinevault.app.ui.navigation.Routes
import com.offlinevault.app.ui.util.rememberCopyMessage
import com.offlinevault.app.viewmodel.RevealedPassword
import com.offlinevault.app.viewmodel.VaultMessage
import com.offlinevault.app.viewmodel.VaultViewModel
import com.offlinevault.app.ui.viewmodel.appViewModel
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(
    container: AppContainer,
    navController: NavHostController,
) {
    val viewModel = appViewModel { VaultViewModel(it.accountRepository, it.sessionManager) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val revealed by viewModel.revealed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copy = rememberCopyMessage(container)
    val lifecycleOwner = LocalLifecycleOwner.current
    val authFailed = stringResource(R.string.password_not_revealed)
    val authTitle = stringResource(R.string.auth_reveal_title)
    val authSubtitleTemplate = stringResource(R.string.auth_reveal_subtitle, "%s")
    val requireAuth = rememberSensitiveActionGate(container) {
        scope.launch { snackbarHostState.showSnackbar(authFailed) }
    }
    val loadFailed = stringResource(R.string.error_generic)
    val revealFailed = stringResource(R.string.error_generic)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.hideReveal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                when (message) {
                    VaultMessage.LOAD_FAILED -> loadFailed
                    VaultMessage.REVEAL_FAILED -> revealFailed
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OfflineVaultLogo(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.vault_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::lockVault) {
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
            AppBottomBar(
                currentRoute = Routes.VAULT,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.VAULT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.ADD) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_account),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text(stringResource(R.string.search_accounts)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.content_desc_search),
                    )
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.content_desc_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text(stringResource(R.string.all_categories)) },
                    )
                }
                items(AccountCategory.entries) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(stringResource(category.labelRes)) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.accounts_count,
                        state.totalCount,
                        state.totalCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.offline_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.totalCount == 0 -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = stringResource(R.string.no_accounts_title),
                            body = stringResource(R.string.no_accounts_body),
                        )
                    }
                }

                state.accounts.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = stringResource(R.string.no_search_results),
                            body = stringResource(R.string.search_accounts),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 2.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.accounts, key = AccountSummary::id) { account ->
                            AccountCard(
                                account = account,
                                revealed = revealed?.takeIf { it.accountId == account.id },
                                onOpen = { navController.navigate(Routes.account(account.id)) },
                                onReveal = {
                                    val subtitle = authSubtitleTemplate.format(account.name)
                                    requireAuth(authTitle, subtitle) {
                                        viewModel.reveal(account.id)
                                    }
                                },
                                onHide = viewModel::hideReveal,
                                onCopyPassword = { password -> copy(password) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountSummary,
    revealed: RevealedPassword?,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onCopyPassword: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(
                    imageRef = account.imageRef,
                    accountName = account.name,
                    size = 56.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (account.isFavorite) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.favorite),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Text(
                        text = account.username?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.username_not_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountWarningBadges(account)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            CircleShape,
                        )
                        .clickable(onClick = if (revealed != null) onHide else onReveal)
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                ) {
                    Icon(
                        imageVector = if (revealed != null) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (revealed != null) R.string.hide_password else R.string.reveal_password,
                        ),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(
                            if (revealed != null) R.string.hide else R.string.reveal,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            AnimatedVisibility(
                visible = revealed != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (revealed != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = revealed.password,
                                modifier = Modifier.weight(1f),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { onCopyPassword(revealed.password) }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_password),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.reveal_auto_hide, 15),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountWarningBadges(account: AccountSummary) {
    if (account.isDuplicate) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = stringResource(R.string.badge_reused),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
    }
    if (account.isWeak) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = stringResource(R.string.badge_weak),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(14.dp),
        )
    }
    if (account.isOld) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.badge_old),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
