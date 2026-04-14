package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalInferenceStatsUiModelBuilderTest {

    @Test
    fun `assistantUpdateCount と generationTimeMs から token per sec を算出できる`() {
        val actual = buildLocalAssistantUpdateBasedTokensPerSecondOrNull(
            assistantUpdateCount = 67,
            generationTimeMs = 3_600L,
        )

        requireNotNull(actual)
        assertEquals(18.6, actual, 0.01)
    }

    @Test
    fun `assistantUpdateCount が 0 以下なら null を返す`() {
        val actual = buildLocalAssistantUpdateBasedTokensPerSecondOrNull(
            assistantUpdateCount = 0,
            generationTimeMs = 3_600L,
        )

        assertNull(actual)
    }
}
