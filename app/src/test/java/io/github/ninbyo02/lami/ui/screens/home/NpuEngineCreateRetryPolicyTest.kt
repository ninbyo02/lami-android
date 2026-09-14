package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuEngineCreateRetryPolicyTest {
    @Test
    fun firstObservedEngineCreateFailureIsRetried() {
        assertTrue(
            shouldRetryNpuEngineCreateFailure(
                attemptNumber = 1,
                throwable = LiteRtLmJniException(
                    "Failed to create engine: INTERNAL: llm_litert_npu_compiled_model_executor.cc:895",
                ),
            ),
        )
    }

    @Test
    fun secondEngineCreateFailureIsNotRetried() {
        assertFalse(
            shouldRetryNpuEngineCreateFailure(
                attemptNumber = 2,
                throwable = LiteRtLmJniException("Failed to create engine: INTERNAL"),
            ),
        )
    }

    @Test
    fun unrelatedFailuresAreNotRetried() {
        assertFalse(shouldRetryNpuEngineCreateFailure(1, IllegalStateException("blank output")))
        assertFalse(shouldRetryNpuEngineCreateFailure(1, LiteRtLmJniException("send failed")))
    }

    private class LiteRtLmJniException(message: String) : RuntimeException(message)
}
