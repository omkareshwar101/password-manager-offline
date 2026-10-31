package com.offlinevault.app.di

import android.content.Context
import com.offlinevault.app.data.AccountRepository
import com.offlinevault.app.data.BackupManager
import com.offlinevault.app.data.ImageStore
import com.offlinevault.app.data.SettingsRepository
import com.offlinevault.app.data.local.VaultDatabase
import com.offlinevault.app.security.CryptoManager
import com.offlinevault.app.security.SecureClipboard
import com.offlinevault.app.security.SecurePrefs
import com.offlinevault.app.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val cryptoManager = CryptoManager()
    val securePrefs = SecurePrefs(appContext)
    val settingsRepository = SettingsRepository(securePrefs)
    val imageStore = ImageStore(appContext)

    private val databasePassphrase = securePrefs.getOrCreateDatabasePassphrase()
    private val database = VaultDatabase.build(appContext, databasePassphrase)
    private val accountDao = database.accountDao()

    val accountRepository = AccountRepository(
        database = database,
        dao = accountDao,
        crypto = cryptoManager,
        imageStore = imageStore,
    )

    val sessionManager = SessionManager(settingsRepository, applicationScope)
    val secureClipboard = SecureClipboard(appContext, settingsRepository, applicationScope)
    val backupManager = BackupManager(appContext, accountRepository, imageStore)

    /**
     * Records and images are destroyed and the encrypted database is vacuumed.
     * Auth material and settings are wiped while the random database passphrase
     * is retained so the fresh onboarding can safely create a new vault.
     */
    suspend fun wipeEverything() {
        accountRepository.secureErase()
        imageStore.deleteAllImages()
        securePrefs.clearUserDataKeepingDatabaseKey()
        settingsRepository.reload()
        sessionManager.lock()
    }
}
