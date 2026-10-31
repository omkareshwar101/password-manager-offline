package com.offlinevault.app.util

import java.net.URI

object Validators {
    fun isValidPin(pin: String): Boolean =
        pin.length in Constants.MIN_PIN_LENGTH..Constants.MAX_PIN_LENGTH &&
            pin.all(Char::isDigit)

    fun isValidUrl(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.any(Char::isWhitespace)) return false
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        return runCatching {
            val uri = URI(trimmed)
            val host = uri.host
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                !host.isNullOrBlank() &&
                host.contains('.') &&
                !host.startsWith(".") &&
                !host.endsWith(".")
        }.getOrDefault(false)
    }

    fun normalizeAnswer(answer: String): String =
        answer.trim().lowercase().replace(Regex("\\s+"), " ")
}
