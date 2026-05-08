package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelPathHeldEngineClearTest {
    @Test
    fun `initial null model path does not update held engine`() {
        assertFalse(
            shouldApplyHeldEngineModelPath(
                localBaseModelFilePath = null,
            ),
        )
    }

    @Test
    fun `blank model path does not update held engine`() {
        assertFalse(
            shouldApplyHeldEngineModelPath(
                localBaseModelFilePath = "",
            ),
        )
    }

    @Test
    fun `valid model path updates held engine key`() {
        assertTrue(
            shouldApplyHeldEngineModelPath(
                localBaseModelFilePath = "/models/gemma.litertlm",
            ),
        )
    }
}
