package com.offlinevault.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.offlinevault.app.R
import com.offlinevault.app.ui.navigation.Routes

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.VAULT,
            onClick = { if (currentRoute != Routes.VAULT) onNavigate(Routes.VAULT) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.content_desc_vault),
                )
            },
            label = { Text(stringResource(R.string.vault)) },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { if (currentRoute != Routes.SETTINGS) onNavigate(Routes.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.content_desc_settings),
                )
            },
            label = { Text(stringResource(R.string.settings)) },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ABOUT,
            onClick = { if (currentRoute != Routes.ABOUT) onNavigate(Routes.ABOUT) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.content_desc_about),
                )
            },
            label = { Text(stringResource(R.string.about)) },
        )
    }
}
