package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceStatsSheetContentTest {
    @Test
    fun `buildInferenceDetailSections returns token and supplement sections in expected order`() {
        val stats = InferenceStats(
            inputTokens = 100,
            outputTokens = 240,
            totalTokens = 340,
            imageInputCount = 2,
        )

        val sections = buildInferenceDetailSections(stats)

        assertEquals(listOf("トークン", "補足"), sections.map { it.title })
        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("100", "240", "340"), sections[0].items.map { it.value })
        assertEquals(listOf("画像入力"), sections[1].items.map { it.label })
        assertEquals(listOf("2枚"), sections[1].items.map { it.value })
    }

    @Test
    fun `buildInferenceDetailSections keeps placeholder when values are missing`() {
        val sections = buildInferenceDetailSections(InferenceStats())

        assertEquals(listOf("—", "—", "—"), sections[0].items.map { it.value })
        assertEquals("—", sections[1].items.first().value)
    }
}
