package com.offlinevault.app.data.model

data class Account(
    val id: Long,
    val name: String,
    val imageRef: String?,
    val url: String?,
    val username: String?,
    val password: String,
    val notes: String?,
    val category: AccountCategory,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long,
)

data class AccountDraft(
    val name: String,
    val imageRef: String?,
    val url: String?,
    val username: String?,
    val password: String,
    val notes: String?,
    val category: AccountCategory,
)

data class RestoredAccount(
    val name: String,
    val imageRef: String?,
    val url: String?,
    val username: String?,
    val password: String,
    val notes: String?,
    val category: AccountCategory,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long,
)

data class AccountSummary(
    val id: Long,
    val name: String,
    val username: String?,
    val imageRef: String?,
    val category: AccountCategory,
    val isFavorite: Boolean,
    val passwordChangedAt: Long,
    val isWeak: Boolean,
    val isDuplicate: Boolean,
    val isOld: Boolean,
)

data class SecurityQuestionAnswer(
    val question: String,
    val answer: String,
)

data class AuthAttemptState(
    val pinFailures: Int,
    val totalFailures: Int,
    val lockoutUntil: Long,
    val forceQuestions: Boolean,
    val lockedOut: Boolean,
)
