package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.toInferenceStats
import io.github.ninbyo02.lami.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AssistantMessageFactoryTest {
    @Test
    fun `createAssistantMessage keeps inference stats for newly generated response`() {
        val latestStats = InferenceStats(
            modelName = "qwen3-vl:30b",
            finishReason = "stop",
            imageInputCount = 1,
            inputTokens = 80,
            outputTokens = 120,
            totalTokens = 200,
            tokensPerSecond = 24.0,
            inferenceTimeSec = 5.0,
            generationTimeMs = 5_000L,
            modelLoadDurationNs = 2_300_000_000L,
            promptEvalDurationNs = 1_100_000_000L,
            generationDurationNs = 4_500_000_000L,
            evalDurationNs = 4_500_000_000L,
            timeToFirstTokenMs = 320L,
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals("qwen3-vl:30b", message.modelName)
        assertEquals(80, message.inputTokens)
        assertEquals(120, message.completionTokens)
        assertEquals(200, message.totalTokens)
        assertEquals(24.0, message.tokensPerSecond)
        assertEquals(5.0, message.inferenceTimeSec)
        assertEquals(5_000L, message.generationTimeMs)
        assertEquals(4_500_000_000L, message.evalDurationNs)
        assertEquals(2_300_000_000L, message.loadDurationNs)
        assertEquals(1_100_000_000L, message.promptEvalDurationNs)
        assertEquals("stop", message.finishReason)
        assertEquals(320L, message.timeToFirstTokenMs)
        assertEquals(1, message.imageInputCount)
    }

    @Test
    fun `createAssistantMessage calculates totalTokens when field is missing`() {
        val latestStats = InferenceStats(
            inputTokens = 7,
            outputTokens = 9,
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals(16, message.totalTokens)
    }

    @Test
    fun `createAssistantMessage does not fabricate stats without latestInferenceStats`() {
        val message = createAssistantMessage(
            chatId = 7,
            response = "error",
        )

        assertNull(message.modelName)
        assertNull(message.inputTokens)
        assertNull(message.completionTokens)
        assertNull(message.totalTokens)
        assertNull(message.tokensPerSecond)
        assertNull(message.inferenceTimeSec)
        assertNull(message.generationTimeMs)
        assertNull(message.evalDurationNs)
        assertNull(message.loadDurationNs)
        assertNull(message.promptEvalDurationNs)
        assertNull(message.finishReason)
        assertNull(message.timeToFirstTokenMs)
        assertNull(message.imageInputCount)
    }
    @Test
    fun `createAssistantMessage prefers canonical modelName over legacy model`() {
        val latestStats = InferenceStats(
            modelName = "canonical",
            model = "legacy",
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals("canonical", message.modelName)
    }



    @Test
    fun `createAssistantMessage stores explicit imageInputCount parameter`() {
        val latestStats = InferenceStats(imageInputCount = 1)

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
            imageInputCount = 3,
        )

        assertEquals(3, message.imageInputCount)
    }

    @Test
    fun `createAssistantMessage keeps local failure compact stats copyable`() {
        val compact = """
            [DEV診断: Local inference failure compact]
            status=failure
            reason=local_inference_failure
            selected_backend=GPU
            route_family=local_gpu
            gallery_stack_probe_enabled=true
        """.trimIndent()
        val latestStats = InferenceStats(
            modelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            generationTimeMs = 12_345L,
            finishReason = "local_inference_failure",
            localSourceSummary = compact,
            responseCharCount = 18,
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ローカル推論の応答取得に失敗しました",
            latestInferenceStats = latestStats,
            localSourceSummary = compact,
            generationTimeMs = 12_345L,
        )
        val restoredStats = message.toInferenceStats()

        assertNotNull(restoredStats)
        assertEquals("gemma-4-E2B-it-edge-gallery.litertlm", message.modelName)
        assertEquals("local_inference_failure", message.finishReason)
        assertEquals(12_345L, message.generationTimeMs)
        assertEquals(compact, message.localSourceSummary)
        assertEquals(compact, restoredStats?.localSourceSummary)
        assertEquals("local_inference_failure", restoredStats?.finishReason)
    }
}
