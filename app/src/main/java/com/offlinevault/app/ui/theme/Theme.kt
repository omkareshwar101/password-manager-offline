package com.offlinevault.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DeepIndigo,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = TealAccent,
    onSecondary = Color(0xFF00332D),
    secondaryContainer = Color(0xFFB2F5EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF5C6BC0),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1B1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF3F0F4),
)

private val DarkColorScheme = darkColorScheme(
    primary = DeepIndigoDark,
    onPrimary = Color(0xFF0D1B60),
    primaryContainer = Color(0xFF26347E),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = TealDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB2F5EA),
    tertiary = Color(0xFFBBC2FF),
    onTertiary = Color(0xFF292F5F),
    background = DarkBackground,
    onBackground = Color(0xFFE6E1E6),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E1E6),
    inverseOnSurface = Color(0xFF303033),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun OfflineVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
