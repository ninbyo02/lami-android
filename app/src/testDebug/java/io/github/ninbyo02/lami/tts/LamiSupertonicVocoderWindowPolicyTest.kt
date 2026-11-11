package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class LamiSupertonicVocoderWindowPolicyTest {
    @Test fun `diagnostics retain the configured window when adaptive policy is not requested`() {
        for (text in listOf("こんにちは。", "あ".repeat(110))) {
            assertEquals(8, LamiSupertonicVocoderWindowPolicy.select(text, 8, false))
            assertEquals(24, LamiSupertonicVocoderWindowPolicy.select(text, 24, false))
        }
    }

    @Test fun `adaptive playback preserves short onset and uses measured bulk window from forty code points`() {
        assertEquals(8, LamiSupertonicVocoderWindowPolicy.select("あ".repeat(39), 8, true))
        assertEquals(16, LamiSupertonicVocoderWindowPolicy.select("あ".repeat(40), 8, true))
        assertEquals(16, LamiSupertonicVocoderWindowPolicy.select("あ".repeat(110), 8, true))
    }

    @Test fun `supplementary characters count once when selecting the short utterance window`() {
        // Twenty supplementary characters occupy forty UTF-16 code units.
        assertEquals(8, LamiSupertonicVocoderWindowPolicy.select("😀".repeat(20), 8, true))
        assertEquals(8, LamiSupertonicVocoderWindowPolicy.select("😀".repeat(39), 8, true))
        assertEquals(16, LamiSupertonicVocoderWindowPolicy.select("あ".repeat(39) + "😀", 8, true))
    }
}
