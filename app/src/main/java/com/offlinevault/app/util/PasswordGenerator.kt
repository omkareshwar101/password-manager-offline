package com.offlinevault.app.util

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.min

data class PasswordOptions(
    val length: Int = 16,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
)

/**
 * Local-only password generation. SecureRandom is deliberately injected so the
 * generator is unit-testable, while production always uses a SecureRandom.
 */
class PasswordGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    private val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val lowercase = "abcdefghijklmnopqrstuvwxyz"
    private val digits = "0123456789"
    private val symbols = "!@#$%^&*()-_=+[]{};:,.?/"

    fun generate(options: PasswordOptions): String {
        val requestedLength = options.length.coerceIn(8, 32)
        val ambiguous = "O0Il1"
        fun filtered(value: String): String =
            if (options.excludeAmbiguous) value.filterNot { it in ambiguous } else value

        val groups = buildList {
            if (options.uppercase) filtered(uppercase).takeIf { it.isNotEmpty() }?.let(::add)
            if (options.lowercase) filtered(lowercase).takeIf { it.isNotEmpty() }?.let(::add)
            if (options.digits) filtered(digits).takeIf { it.isNotEmpty() }?.let(::add)
            if (options.symbols) filtered(symbols).takeIf { it.isNotEmpty() }?.let(::add)
        }

        // The UI disables generation when no group is selected. Falling back to
        // lowercase here keeps the utility total and avoids returning empty data.
        val actualGroups = groups.ifEmpty { listOf(lowercase) }
        val all = actualGroups.joinToString(separator = "")
        val result = CharArray(requestedLength)
        var index = 0

        // Guarantee at least one character from every selected group.
        for (group in actualGroups) {
            if (index < requestedLength) {
                result[index++] = group[random.nextInt(group.length)]
            }
        }
        while (index < requestedLength) {
            result[index++] = all[random.nextInt(all.length)]
        }

        // Fisher-Yates shuffle using SecureRandom, not java.util.Random.
        for (i in result.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return String(result)
    }
}

enum class PasswordStrength(val score: Int) {
    WEAK(0),
    FAIR(1),
    STRONG(2),
    VERY_STRONG(3),
}

object PasswordStrengthEstimator {
    fun estimate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.WEAK

        val pool = when {
            password.any { !it.isLetterOrDigit() } -> 94
            password.any { it.isDigit() } && password.any { it.isLetter() } -> 62
            password.any { it.isUpperCase() } && password.any { it.isLowerCase() } -> 52
            else -> 26
        }
        val entropy = password.length * ln(pool.toDouble()) / ln(2.0)
        val uniqueFactor = password.toSet().size.toDouble().div(password.length)
        val adjusted = entropy * (0.65 + (uniqueFactor * 0.35))
        return when {
            password.length < 8 || adjusted < 35.0 -> PasswordStrength.WEAK
            adjusted < 55.0 -> PasswordStrength.FAIR
            adjusted < 75.0 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
    }

    /** 0..4 segments for the visual meter. */
    fun segments(password: String): Int = when (estimate(password)) {
        PasswordStrength.WEAK -> 1
        PasswordStrength.FAIR -> 2
        PasswordStrength.STRONG -> 3
        PasswordStrength.VERY_STRONG -> 4
    }

    fun isWeak(password: String): Boolean =
        estimate(password).score <= PasswordStrength.FAIR.score ||
            password.length < 12

    fun scoreOutOfFour(password: String): Int = min(4, segments(password))
}
