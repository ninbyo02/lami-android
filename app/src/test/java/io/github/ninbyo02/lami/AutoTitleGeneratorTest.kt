package io.github.ninbyo02.lami

import io.github.ninbyo02.lami.utils.AutoTitleGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AutoTitleGeneratorTest {

    @Test
    fun meaninglessInputFallsBackToDate() {
        val title = AutoTitleGenerator.generateTitle("あああああ", LocalDate.of(2026, 2, 21))

        assertEquals("2026-02-21", title)
    }
}
