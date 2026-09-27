package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeferredTokenizerInputTest {
    @Test fun countsOriginalPromptAndOutputBeforeDisplaySanitization() {
        val original = DeferredTokenizerInput("effective prompt\n", "**宇宙旅行**\n\n原文。\n")
        val snapshot = LocalInferenceMeasuredTokenSnapshot(deferredTokenizerInput = original)
        assertSame(original, resolveDeferredTokenizerInput(snapshot, "user prompt", "宇宙旅行\n原文。"))
    }

    @Test fun legacyTraceWithoutOriginalStringsUsesProvidedText() {
        assertEquals(DeferredTokenizerInput("質問", "答え"), resolveDeferredTokenizerInput(null, "質問", "答え"))
        assertEquals(DeferredTokenizerInput("質問", "答え"), resolveDeferredTokenizerInput(LocalInferenceMeasuredTokenSnapshot(), "質問", "答え"))
    }
}
