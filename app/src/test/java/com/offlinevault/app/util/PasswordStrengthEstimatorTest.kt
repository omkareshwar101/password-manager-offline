package com.offlinevault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthEstimatorTest {
    @Test
    fun `empty and very short passwords are weak`() {
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEstimator.estimate(""))
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEstimator.estimate("a1!"))
        assertTrue(PasswordStrengthEstimator.isWeak("a1!"))
    }

    @Test
    fun `short passwords are always weak regardless of characters`() {
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEstimator.estimate("Ab1!xyz"))
    }

    @Test
    fun `long mixed passwords rate strong or better`() {
        val password = "CorrectHorseBatteryStaple9!"
        assertTrue(PasswordStrengthEstimator.estimate(password).score >= PasswordStrength.STRONG.score)
        assertFalse(PasswordStrengthEstimator.isWeak(password))
    }

    @Test
    fun `segments map to four meter levels`() {
        assertEquals(1, PasswordStrengthEstimator.segments(""))
        assertEquals(4, PasswordStrengthEstimator.segments("CorrectHorseBatteryStaple9!"))
        assertEquals(
            PasswordStrengthEstimator.segments("password123"),
            PasswordStrengthEstimator.scoreOutOfFour("password123"),
        )
    }
}
