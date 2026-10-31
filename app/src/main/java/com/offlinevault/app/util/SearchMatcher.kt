package com.offlinevault.app.util

object SearchMatcher {
    /**
     * Case-insensitive fuzzy match. First checks a direct substring, then a
     * simple ordered-subsequence match (e.g. "gml" matches "Gmail").
     */
    fun matches(query: String, vararg candidates: String?): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val haystacks = candidates.filterNotNull().map { it.lowercase() }
        if (haystacks.any { it.contains(needle) }) return true
        if (needle.length < 2) return false
        return haystacks.any { candidate ->
            var queryIndex = 0
            for (char in candidate) {
                if (queryIndex < needle.length && char == needle[queryIndex]) {
                    queryIndex++
                }
                if (queryIndex == needle.length) return@any true
            }
            false
        }
    }
}
