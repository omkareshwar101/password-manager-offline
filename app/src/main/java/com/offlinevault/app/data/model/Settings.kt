package com.offlinevault.app.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 30,
    val clipboardSeconds: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
