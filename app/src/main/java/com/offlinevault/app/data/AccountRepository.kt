package com.offlinevault.app.data

import androidx.room.withTransaction
import com.offlinevault.app.data.local.AccountDao
import com.offlinevault.app.data.local.AccountEntity
import com.offlinevault.app.data.local.VaultDatabase
import com.offlinevault.app.data.model.Account
import com.offlinevault.app.data.model.AccountCategory
import com.offlinevault.app.data.model.AccountDraft
import com.offlinevault.app.data.model.RestoredAccount
import com.offlinevault.app.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountRepository(
    private val database: VaultDatabase,
    private val dao: AccountDao,
    private val crypto: CryptoManager,
    private val imageStore: ImageStore,
) {
    fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll()
            .map { entities -> entities.map(::decrypt) }
            .flowOn(Dispatchers.IO)

    suspend fun getAccount(id: Long): Account? = withContext(Dispatchers.IO) {
        dao.getById(id)?.let(::decrypt)
    }

    suspend fun getAllAccounts(): List<Account> = withContext(Dispatchers.IO) {
        dao.getAll().map(::decrypt)
    }

    suspend fun saveAccount(draft: AccountDraft, accountId: Long? = null): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = accountId?.let { dao.getById(it) }
            if (accountId != null && existing == null) {
                throw IllegalStateException("Account not found")
            }
            val passwordChangedAt = if (existing == null) {
                now
            } else {
                val oldPassword = String(crypto.decryptBytes(existing.passwordEncrypted), Charsets.UTF_8)
                if (oldPassword == draft.password) existing.passwordChangedAt else now
            }

            val entity = AccountEntity(
                id = existing?.id ?: 0L,
                nameEncrypted = requireNotNull(crypto.encryptString(draft.name.trim())),
                imageRef = draft.imageRef,
                urlEncrypted = crypto.encryptString(draft.url?.trim()?.takeIf { it.isNotEmpty() }),
                usernameEncrypted = crypto.encryptString(draft.username?.trim()?.takeIf { it.isNotEmpty() }),
                passwordEncrypted = crypto.encryptBytes(draft.password.toByteArray(Charsets.UTF_8)),
                notesEncrypted = crypto.encryptString(draft.notes?.trim()?.takeIf { it.isNotEmpty() }),
                categoryEncrypted = requireNotNull(
                    crypto.encryptString(draft.category.storageValue),
                ),
                isFavorite = existing?.isFavorite ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                passwordChangedAt = passwordChangedAt,
            )

            val id = if (existing == null) {
                dao.insert(entity)
            } else {
                dao.update(entity)
                existing.id
            }
            if (existing != null && existing.imageRef != draft.imageRef) {
                imageStore.delete(existing.imageRef)
            }
            id
        }

    suspend fun deleteAccount(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        dao.delete(entity)
        imageStore.delete(entity.imageRef)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        dao.update(
            entity.copy(
                isFavorite = favorite,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun replaceAllWithBackup(restored: List<RestoredAccount>) = withContext(Dispatchers.IO) {
        val oldImages = dao.getAll().mapNotNull { it.imageRef }
        val now = System.currentTimeMillis()
        val entities = restored.map { account ->
            AccountEntity(
                id = 0L,
                nameEncrypted = requireNotNull(crypto.encryptString(account.name)),
                imageRef = account.imageRef,
                urlEncrypted = crypto.encryptString(account.url),
                usernameEncrypted = crypto.encryptString(account.username),
                passwordEncrypted = crypto.encryptBytes(account.password.toByteArray(Charsets.UTF_8)),
                notesEncrypted = crypto.encryptString(account.notes),
                categoryEncrypted = requireNotNull(
                    crypto.encryptString(account.category.storageValue),
                ),
                isFavorite = account.isFavorite,
                createdAt = account.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = account.updatedAt.takeIf { it > 0 } ?: now,
                passwordChangedAt = account.passwordChangedAt.takeIf { it > 0 } ?: now,
            )
        }
        database.withTransaction {
            dao.deleteAll()
            dao.insertAll(entities)
        }
        oldImages.forEach(imageStore::delete)
    }

    /**
     * Deletes all rows, removes their images, and rebuilds the SQLCipher file
     * with VACUUM so old encrypted pages are not left in free space.
     */
    suspend fun secureErase() = withContext(Dispatchers.IO) {
        val images = dao.getAll().mapNotNull { it.imageRef }
        val writable = database.openHelper.writableDatabase
        runCatching { writable.execSQL("PRAGMA secure_delete = ON") }
        database.withTransaction { dao.deleteAll() }
        images.forEach(imageStore::delete)
        runCatching { writable.execSQL("VACUUM") }
    }

    private fun decrypt(entity: AccountEntity): Account = Account(
        id = entity.id,
        name = crypto.decryptString(entity.nameEncrypted).orEmpty(),
        imageRef = entity.imageRef,
        url = crypto.decryptString(entity.urlEncrypted),
        username = crypto.decryptString(entity.usernameEncrypted),
        password = String(crypto.decryptBytes(entity.passwordEncrypted), Charsets.UTF_8),
        notes = crypto.decryptString(entity.notesEncrypted),
        category = AccountCategory.fromStorage(crypto.decryptString(entity.categoryEncrypted)),
        isFavorite = entity.isFavorite,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        passwordChangedAt = entity.passwordChangedAt,
    )
}
