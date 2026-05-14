package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingTtsBreakIndexTest {
    @Test
    fun findStreamingTtsBreakIndex_prefersSentenceBreak() {
        assertEquals(
            8,
            findStreamingTtsBreakIndex("はい、確認します。続けます"),
        )
    }

    @Test
    fun findStreamingTtsBreakIndex_usesJapaneseCommaForEarlySpeech() {
        assertEquals(
            2,
            findStreamingTtsBreakIndex("はい、確認します"),
        )
    }

    @Test
    fun findStreamingTtsBreakIndex_returnsMinusOneWithoutBreak() {
        assertEquals(
            -1,
            findStreamingTtsBreakIndex("確認しています"),
        )
    }
}
