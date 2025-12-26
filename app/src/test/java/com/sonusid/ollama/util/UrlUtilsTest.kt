package com.sonusid.ollama.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun `normalizeUrlInput converts full width characters`() {
        val input = "　HTTP：//ｌｏｃａｌｈｏｓｔ：１１４３４　"

        val normalized = normalizeUrlInput(input)

        assertEquals("HTTP://localhost:11434", normalized)
    }

    @Test
    fun `validateUrlFormat passes normalized http url`() {
        val result = validateUrlFormat("ｈｔｔｐ：／／ｅｘａｍｐｌｅ．ｃｏｍ：５０００")

        assertTrue(result.isValid)
        assertEquals("http://example.com:5000", result.normalizedUrl)
    }

    @Test
    fun `validateUrlFormat rejects invalid url`() {
        val result = validateUrlFormat("：：：")

        assertFalse(result.isValid)
        assertEquals(PORT_ERROR_MESSAGE, result.errorMessage)
    }
}
