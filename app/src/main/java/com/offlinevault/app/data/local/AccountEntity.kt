package com.offlinevault.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sensitive textual fields are individually AES-GCM encrypted before they reach
 * Room. The entire database file is additionally encrypted by SQLCipher.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name_enc")
    val nameEncrypted: String,

    @ColumnInfo(name = "image_ref")
    val imageRef: String?,

    @ColumnInfo(name = "url_enc")
    val urlEncrypted: String?,

    @ColumnInfo(name = "username_enc")
    val usernameEncrypted: String?,

    @ColumnInfo(name = "password_enc")
    val passwordEncrypted: ByteArray,

    @ColumnInfo(name = "notes_enc")
    val notesEncrypted: String?,

    @ColumnInfo(name = "category_enc")
    val categoryEncrypted: String,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "password_changed_at")
    val passwordChangedAt: Long,
)
