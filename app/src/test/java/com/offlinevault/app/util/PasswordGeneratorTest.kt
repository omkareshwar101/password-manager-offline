package com.offlinevault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    private val generator = PasswordGenerator()

    @Test
    fun `generates the requested length within supported bounds`() {
        assertEquals(8, generator.generate(PasswordOptions(length = 4)).length)
        assertEquals(16, generator.generate(PasswordOptions(length = 16)).length)
        assertEquals(32, generator.generate(PasswordOptions(length = 99)).length)
    }

    @Test
    fun `includes at least one character from every selected group`() {
        repeat(100) {
            val password = generator.generate(PasswordOptions(length = 12))
            assertTrue("missing uppercase in $password", password.any(Char::isUpperCase))
            assertTrue("missing lowercase in $password", password.any(Char::isLowerCase))
            assertTrue("missing digit in $password", password.any(Char::isDigit))
            assertTrue(
                "missing symbol in $password",
                password.any { !it.isLetterOrDigit() },
            )
        }
    }

    @Test
    fun `supports a single selected character group`() {
        repeat(20) {
            val password = generator.generate(
                PasswordOptions(
                    length = 10,
                    uppercase = false,
                    lowercase = false,
                    digits = true,
                    symbols = false,
                ),
            )
            assertTrue(password.all(Char::isDigit))
        }
    }

    @Test
    fun `removes ambiguous characters when requested`() {
        val ambiguous = "O0Il1".toSet()
        repeat(100) {
            val password = generator.generate(
                PasswordOptions(length = 24, excludeAmbiguous = true),
            )
            assertFalse(password.any { it in ambiguous })
        }
    }

    @Test
    fun `falls back to lowercase when no groups are selected`() {
        val password = generator.generate(
            PasswordOptions(
                length = 12,
                uppercase = false,
                lowercase = false,
                digits = false,
                symbols = false,
            ),
        )
        assertEquals(12, password.length)
        assertTrue(password.all(Char::isLowerCase))
    }
}
