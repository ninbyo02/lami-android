package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantNumberDisplayTest {
    @Test fun joinsSpacedDigitsOnlyBeforeJapaneseCounters() {
        assertEquals("一年は12ヶ月、一日は24時間です。",
            normalizeAssistantCounterNumbers("一年は1 2ヶ月、一日は2 4時間です。"))
        assertEquals("参加者は100人です。", normalizeAssistantCounterNumbers("参加者は1 0 0人です。"))
    }

    @Test fun doesNotJoinListsOrAcrossNewlines() {
        val text = "候補：1 2 3\n1\n2時間\n1, 2人"
        assertEquals(text, normalizeAssistantCounterNumbers(text))
    }

    @Test fun preservesInlineAndFencedCode() {
        val code = "`1 2ヶ月`\n```text\n2 4時間\n```\n~~~text\n1 0人\n~~~"
        assertEquals("24時間です。\n$code", normalizeAssistantCounterNumbers("2 4時間です。\n$code"))
    }

    @Test fun preservesIncompleteCodeDuringStreaming() {
        val text = "24時間です。\n```text\n2 4時間"
        assertEquals(text, normalizeAssistantCounterNumbers("2 4時間です。\n```text\n2 4時間"))
    }

    @Test fun displaySanitizerUsesTheSameNumberCorrection() {
        assertEquals("一日は24時間です。", sanitizeAssistantMessageForDisplay("一日は2 4時間です。"))
    }
}
