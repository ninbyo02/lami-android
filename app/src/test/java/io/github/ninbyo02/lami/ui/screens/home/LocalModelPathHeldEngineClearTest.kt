package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelPathHeldEngineClearTest {
    @Test
    fun `initial null model path does not clear held engine`() {
        assertFalse(
            shouldClearHeldEngineForLocalModelPath(
                localBaseModelFilePath = null,
                hasObservedValidPath = false,
            ),
        )
    }

    @Test
    fun `blank model path clears after a valid path was observed`() {
        assertTrue(
            shouldClearHeldEngineForLocalModelPath(
                localBaseModelFilePath = "",
                hasObservedValidPath = true,
            ),
        )
    }

    @Test
    fun `valid model path does not clear held engine`() {
        assertFalse(
            shouldClearHeldEngineForLocalModelPath(
                localBaseModelFilePath = "/models/gemma.litertlm",
                hasObservedValidPath = true,
            ),
        )
    }
}
