package com.offlinevault.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    @Test
    fun `empty query matches everything`() {
        assertTrue(SearchMatcher.matches("", "anything"))
        assertTrue(SearchMatcher.matches("   "))
        assertTrue(SearchMatcher.matches("", null))
    }

    @Test
    fun `substring matching is case insensitive`() {
        assertTrue(SearchMatcher.matches("GMAIL", "user@gmail.com", "Google Mail"))
        assertTrue(SearchMatcher.matches("bank", "My Bank", "alice"))
    }

    @Test
    fun `fuzzy subsequence matching works across candidates`() {
        assertTrue(SearchMatcher.matches("gml", "Gmail"))
        assertTrue(SearchMatcher.matches("gthb", "GitHub"))
        assertFalse(SearchMatcher.matches("gml", "Bank"))
    }

    @Test
    fun `single character requires a real substring match`() {
        assertTrue(SearchMatcher.matches("g", "Gmail"))
        assertFalse(SearchMatcher.matches("z", "Gmail"))
    }

    @Test
    fun `null candidates are ignored safely`() {
        assertTrue(SearchMatcher.matches("mail", null, "Gmail", null))
        assertFalse(SearchMatcher.matches("missing", null, null))
    }
}
