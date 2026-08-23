package io.github.ninbyo02.lami.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun `normalizeUrlInput trims whitespace and normalizes unicode`() {
        val input = "  http://Example.com：11434/　"

        val normalized = normalizeUrlInput(input)

        assertEquals("http://Example.com:11434/", normalized)
    }

    @Test
    fun `validateUrlFormat accepts private http and public https`() {
        val privateHttpResult = validateUrlFormat("http://192.168.52.99:11434")
        val httpsResult = validateUrlFormat("https://example.com")

        assertTrue(privateHttpResult.isValid)
        assertNull(privateHttpResult.errorMessage)
        assertTrue(httpsResult.isValid)
        assertNull(httpsResult.errorMessage)
    }

    @Test
    fun `validateUrlFormat accepts common local endpoint forms`() {
        assertTrue(validateUrlFormat("http://localhost:11434").isValid)
        assertTrue(validateUrlFormat("http://server.local:11434").isValid)
        assertTrue(validateUrlFormat("http://nas:11434").isValid)
        assertTrue(validateUrlFormat("http://100.100.1.2:11434").isValid)
        assertTrue(validateUrlFormat("http://[::1]:11434").isValid)
        assertTrue(validateUrlFormat("http://[fd00::2]:11434").isValid)
    }

    @Test
    fun `validateUrlFormat rejects public cleartext endpoints`() {
        val publicDomain = validateUrlFormat("http://example.com:11434")
        val publicIp = validateUrlFormat("http://203.0.113.10:11434")
        val domainThatStartsLikeIpv6Ula = validateUrlFormat("http://fd.example.com:11434")

        assertFalse(publicDomain.isValid)
        assertEquals(PUBLIC_CLEARTEXT_ERROR_MESSAGE, publicDomain.errorMessage)
        assertFalse(publicIp.isValid)
        assertEquals(PUBLIC_CLEARTEXT_ERROR_MESSAGE, publicIp.errorMessage)
        assertFalse(domainThatStartsLikeIpv6Ula.isValid)
        assertEquals(PUBLIC_CLEARTEXT_ERROR_MESSAGE, domainThatStartsLikeIpv6Ula.errorMessage)
    }

    @Test
    fun `validateUrlFormat rejects blank or incomplete urls`() {
        val blankResult = validateUrlFormat("   ")
        val missingSchemeResult = validateUrlFormat("example.com:11434")

        assertFalse(blankResult.isValid)
        assertEquals(PORT_ERROR_MESSAGE, blankResult.errorMessage)
        assertFalse(missingSchemeResult.isValid)
        assertEquals(PORT_ERROR_MESSAGE, missingSchemeResult.errorMessage)
    }

    @Test
    fun `validateUrlFormat normalizes mixed width port numbers`() {
        val result = validateUrlFormat("http://localhost:1３434")

        assertTrue(result.isValid)
        assertNull(result.errorMessage)
        assertEquals("http://localhost:13434", result.normalizedUrl)
    }
}
