package com.offlinevault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test
    fun `pin accepts six to twelve digits`() {
        assertTrue(Validators.isValidPin("123456"))
        assertTrue(Validators.isValidPin("123456789012"))
    }

    @Test
    fun `pin rejects wrong length or non-digits`() {
        assertFalse(Validators.isValidPin("12345"))
        assertFalse(Validators.isValidPin("1234567890123"))
        assertFalse(Validators.isValidPin("12345a"))
        assertFalse(Validators.isValidPin(""))
    }

    @Test
    fun `empty url is allowed because the field is optional`() {
        assertTrue(Validators.isValidUrl(""))
        assertTrue(Validators.isValidUrl("   "))
    }

    @Test
    fun `valid http and https urls pass`() {
        assertTrue(Validators.isValidUrl("https://example.com"))
        assertTrue(Validators.isValidUrl("http://sub.example.co.in/path?q=1"))
        assertTrue(Validators.isValidUrl("HTTPS://Example.COM"))
    }

    @Test
    fun `invalid urls fail`() {
        assertFalse(Validators.isValidUrl("example.com"))
        assertFalse(Validators.isValidUrl("ftp://example.com"))
        assertFalse(Validators.isValidUrl("https://example"))
        assertFalse(Validators.isValidUrl("https://exa mple.com"))
        assertFalse(Validators.isValidUrl("https://.com"))
    }

    @Test
    fun `security answers are trimmed, lowercased and space-collapsed`() {
        assertEquals("first pet", Validators.normalizeAnswer("  First   Pet  "))
        assertEquals("delhi", Validators.normalizeAnswer("DELHI"))
    }
}
