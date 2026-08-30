package io.github.ninbyo02.lami.benchmark

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAbBenchmarkContractTest {
    @Test
    fun promptTransportPreservesOrderedJapaneseTurns() {
        val json = """["好きな色は赤です。","何色ですか。"]"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())

        assertEquals(
            listOf("好きな色は赤です。", "何色ですか。"),
            ConversationAbBenchmarkContract.decodePrompts(encoded),
        )
    }

    @Test
    fun nativeRepeatedKeyOutputMapsToCommonTurnSchema() {
        val nativeText = """
            status=success
            turn_index=1
            user_prompt=好きな色は赤です。
            turn_status=success
            turn_reason=completed
            raw_output=: 赤
            send_ms=81
            turn_index=2
            user_prompt=2足す3は。
            turn_status=success
            turn_reason=completed
            raw_output=5
            send_ms=92
        """.trimIndent()

        val turns = ConversationAbBenchmarkContract.parseNativeTurns(
            nativeText = nativeText,
            prompts = listOf("好きな色は赤です。", "2足す3は。"),
        )

        assertEquals(2, turns.size)
        assertEquals("赤", turns[0].sanitizedOutput)
        assertEquals("5", turns[1].sanitizedOutput)
        assertEquals(81L, turns[0].sendMs)
        assertTrue(turns.all { it.status == "success" })
    }

    @Test
    fun commonJsonRecordsTemplateSamplerAndOutputLimitEvidence() {
        val result = ConversationAbRunResult(
            scenarioId = ConversationAbBenchmarkContract.DEFAULT_SCENARIO_ID,
            backend = "NPU",
            apiSurface = "LiteRT-LM C++ Conversation::SendMessage",
            modelFileName = "model.litertlm",
            modelBytes = 123L,
            requestedMaxOutputTokens = 16,
            effectiveMaxOutputTokens = 16,
            outputLimitSource = "C++ OptionalArgs.max_output_tokens",
            status = "success",
            reason = "completed",
            turns = emptyList(),
        )

        val json = ConversationAbBenchmarkContract.toJson(result)

        assertTrue(json.contains(""""conversationApiUsed": true"""))
        assertTrue(json.contains(""""directSessionApiUsed": false"""))
        assertTrue(json.contains(""""modelTemplateSource": "model_metadata""""))
        assertTrue(json.contains(""""appTemplateUsed": false"""))
        assertTrue(json.contains(""""samplerProfile": "lami_stable_v1""""))
        assertTrue(json.contains(""""samplerSeed": 42"""))
        assertFalse(json.contains("modelPath"))
    }
}
