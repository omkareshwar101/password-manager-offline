package com.offlinevault.app.ui.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.offlinevault.app.R
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.util.PasswordStrength
import java.util.Locale
import java.util.concurrent.TimeUnit

fun copiedMessage(context: Context, container: AppContainer, text: String) {
    if (text.isBlank()) return
    val seconds = container.secureClipboard.copySensitive(text)
    val message = if (seconds > 0) {
        context.resources.getQuantityString(R.plurals.copied_clears, seconds, seconds)
    } else {
        context.getString(R.string.copied_no_clear)
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberCopyMessage(container: AppContainer): (String) -> Unit {
    val context = LocalContext.current
    return { value -> copiedMessage(context, container, value) }
}

fun formatLockout(remainingMillis: Long): String {
    val safe = remainingMillis.coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

fun strengthLabelRes(strength: PasswordStrength): Int = when (strength) {
    PasswordStrength.WEAK -> R.string.strength_weak
    PasswordStrength.FAIR -> R.string.strength_fair
    PasswordStrength.STRONG -> R.string.strength_strong
    PasswordStrength.VERY_STRONG -> R.string.strength_very_strong
}

fun passwordStrengthLabelRes(password: String): Int {
    val strength = com.offlinevault.app.util.PasswordStrengthEstimator.estimate(password)
    return when (strength) {
        com.offlinevault.app.util.PasswordStrength.FAIR -> R.string.strength_medium
        else -> strengthLabelRes(strength)
    }
}

/** e.g. "3 days ago", "2 months ago" using localized string resources. */
@Composable
fun relativeAge(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val elapsed = (now - timestampMillis).coerceAtLeast(0L)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    return when {
        days <= 0L -> androidx.compose.ui.res.stringResource(R.string.changed_today)
        days == 1L -> androidx.compose.ui.res.stringResource(R.string.changed_one_day)
        days < 30L -> androidx.compose.ui.res.stringResource(R.string.changed_days, days.toInt())
        days < 365L -> {
            val months = (days / 30).toInt()
            if (months <= 1) {
                androidx.compose.ui.res.stringResource(R.string.changed_one_month)
            } else {
                androidx.compose.ui.res.stringResource(R.string.changed_months, months)
            }
        }
        else -> {
            val years = (days / 365).toInt()
            if (years <= 1) {
                androidx.compose.ui.res.stringResource(R.string.changed_one_year)
            } else {
                androidx.compose.ui.res.stringResource(R.string.changed_years, years)
            }
        }
    }
}
