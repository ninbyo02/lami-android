package com.sonusid.ollama.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutVersionLabelTest {
    @Test
    fun buildVersionLabel_withSha_formatsWithParen() {
        val actual = buildVersionLabel(version = "1.0.0", sha = "61034f0")

        assertEquals("v1.0.0 (61034f0)", actual)
    }

    @Test
    fun buildVersionLabel_withBlankSha_fallsBack() {
        val actual = buildVersionLabel(version = "1.0.0", sha = "   ")

        assertEquals("v1.0.0", actual)
    }

    @Test
    fun buildVersionLabel_withLongSha_trimsTo7Chars() {
        val actual = buildVersionLabel(version = "1.0.0", sha = "61034f0abcdef")

        assertEquals("v1.0.0 (61034f0)", actual)
    }

    @Test
    fun buildPrLabel_withPrNumber_formatsBuildLabel() {
        val actual = buildPrLabel(buildPrNumber = "1240")

        assertEquals("Build: 1240", actual)
    }

    @Test
    fun buildPrLabel_withBlankPrNumber_fallsBackToVersionCode() {
        val actual = buildPrLabel(buildPrNumber = "   ")

        assertEquals("Build: 1", actual)
    }

    @Test
    fun buildPrLabel_withNonNumericPrNumber_fallsBackToVersionCode() {
        val actual = buildPrLabel(buildPrNumber = "PR-1240")

        assertEquals("Build: 1", actual)
    }
}
