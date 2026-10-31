package com.offlinevault.app.util

object Constants {
    const val MIN_PIN_LENGTH = 6
    const val MAX_PIN_LENGTH = 12
    const val PASSWORD_REVEAL_MILLIS = 15_000L
    const val PASSWORD_MAX_AGE_MILLIS = 180L * 24L * 60L * 60L * 1000L
    const val MAX_IMAGE_DIMENSION = 768
    const val MAX_BACKUP_BYTES = 50L * 1024L * 1024L
    const val BUILTIN_ICON_PREFIX = "builtin:"
    const val PENDING_IMAGE_PREFIX = "pending:"
    const val EXPORT_MIME_TYPE = "application/octet-stream"
    const val VAULT_FILE_EXTENSION = ".vault"

    const val DATABASE_NAME = "offlinevault.db"
    const val DB_PASSPHRASE_PREF = "database_passphrase_v1"
}
