package com.offlinevault.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.ui.about.AboutScreen
import com.offlinevault.app.ui.addedit.AddEditAccountScreen
import com.offlinevault.app.ui.auth.AuthScreen
import com.offlinevault.app.ui.detail.AccountDetailScreen
import com.offlinevault.app.ui.navigation.Routes
import com.offlinevault.app.ui.settings.BackupScreen
import com.offlinevault.app.ui.settings.SecurityQuestionsScreen
import com.offlinevault.app.ui.settings.SettingsScreen
import com.offlinevault.app.ui.vault.VaultScreen

@Composable
fun OfflineVaultRoot(
    container: AppContainer,
    onVaultWiped: () -> Unit,
) {
    val unlocked by container.sessionManager.isUnlocked.collectAsStateWithLifecycle()
    if (unlocked) {
        VaultNavHost(container = container, onVaultWiped = onVaultWiped)
    } else {
        AuthScreen(container = container)
    }
}

@Composable
private fun VaultNavHost(
    container: AppContainer,
    onVaultWiped: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.VAULT,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.VAULT) {
            VaultScreen(container = container, navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                navController = navController,
                onVaultWiped = onVaultWiped,
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(navController = navController)
        }
        composable(Routes.ADD) {
            AddEditAccountScreen(
                accountId = null,
                container = container,
                navController = navController,
            )
        }
        composable(
            route = Routes.ACCOUNT,
            arguments = listOf(navArgument(Routes.ACCOUNT_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            AccountDetailScreen(
                accountId = entry.arguments?.getLong(Routes.ACCOUNT_ID_ARG) ?: -1L,
                container = container,
                navController = navController,
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument(Routes.ACCOUNT_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            AddEditAccountScreen(
                accountId = entry.arguments?.getLong(Routes.ACCOUNT_ID_ARG) ?: -1L,
                container = container,
                navController = navController,
            )
        }
        composable(Routes.SECURITY_QUESTIONS) {
            SecurityQuestionsScreen(container = container, navController = navController)
        }
        composable(Routes.BACKUP) {
            BackupScreen(container = container, navController = navController)
        }
    }
}
