package com.offlinevault.app.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinevault.app.OfflineVaultApplication
import com.offlinevault.app.di.AppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline creator: (AppContainer) -> VM,
): VM {
    val application = LocalContext.current.applicationContext as OfflineVaultApplication
    val container = application.container
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return creator(container) as T
            }
        },
    )
}
