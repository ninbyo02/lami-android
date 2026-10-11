package io.github.ninbyo02.lami.viewmodels

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class OpenAiStreamCollectorTest {
    private fun chunk(content: String? = null, thinking: String? = null, reason: String? = null): String {
        val delta = org.json.JSONObject()
        content?.let { delta.put("content", it) }
        thinking?.let { delta.put("reasoning_content", it) }
        val choice = org.json.JSONObject().put("delta", delta)
        reason?.let { choice.put("finish_reason", it) }
        return "data: " + org.json.JSONObject().put("model", "test-model")
            .put("choices", org.json.JSONArray().put(choice))
    }

    private fun collect(lines: List<String>, events: MutableList<String> = mutableListOf()): OpenAiStreamResult {
        var clock = 0L
        return collectOpenAiStream(
            lines.asSequence(), { clock += 100; clock }, {},
            { _, _ -> events += "thinking" },
            { events += it },
        )
    }

    @Test
    fun thinkingThenAnswerKeepsReasoningOutOfContentAndSeparatesTiming() {
        val events = mutableListOf<String>()
        val result = collect(listOf(
            ": keepalive", "",
            chunk(thinking = "secret"), chunk(content = "OK"),
            chunk(reason = "stop"), "data: [DONE]",
        ), events)
        assertEquals(listOf("thinking", "OK"), events)
        assertEquals("OK", result.text)
        assertEquals(6, result.thinkingCharacterCount)
        assertEquals(1, result.thinkingChunkCount)
        assertEquals(100L, result.timeToFirstThinkingTokenMs)
        assertEquals(200L, result.timeToFirstTokenMs)
        assertEquals("test-model", result.model)
        assertEquals("stop", result.finishReason)
    }

    @Test
    fun normalResponseHasNoThinkingMetrics() {
        val events = mutableListOf<String>()
        val result = collect(listOf(chunk(content = "OK"), "data: [DONE]"), events)
        assertEquals(listOf("OK"), events)
        assertNull(result.timeToFirstThinkingTokenMs)
        assertEquals(0, result.thinkingCharacterCount)
        assertEquals(0, result.thinkingChunkCount)
    }

    @Test
    fun finalChunkPayloadIsConsumedBeforeFinish() {
        val result = collect(listOf(chunk(content = "answer", thinking = "secret", reason = "stop")))
        assertEquals("answer", result.text)
        assertEquals(6, result.thinkingCharacterCount)
        assertEquals(100L, result.timeToFirstTokenMs)
    }

    @Test
    fun thinkingOnlyLengthPreservesMetricsForBudgetDiagnostic() {
        val result = collect(listOf(chunk(thinking = "secret", reason = "length")))
        assertEquals("", result.text)
        assertEquals("length", result.finishReason)
        assertEquals(6, result.thinkingCharacterCount)
        assertNull(result.timeToFirstTokenMs)
        assertTrue(describeThinkingOnlyCompletion(
            result.thinkingCharacterCount, result.finishReason, null, 8192,
        ).contains("output token budget"))
    }

    @Test
    fun thinkingOnlyStopIsDifferentFromEmptyResponse() {
        val result = collect(listOf(chunk(thinking = "secret"), "data: [DONE]"))
        assertTrue(describeThinkingOnlyCompletion(
            result.thinkingCharacterCount, result.finishReason, null, 8192,
        ).contains("without assistant content"))
        val empty = collect(listOf("data: [DONE]"))
        assertEquals("Empty response", describeThinkingOnlyCompletion(
            empty.thinkingCharacterCount, empty.finishReason, null, 8192,
        ))
    }

    @Test
    fun lateThinkingDoesNotRevertAnswerState() {
        val events = mutableListOf<String>()
        val result = collect(listOf(
            chunk(content = "A"), chunk(thinking = "secret"),
            chunk(content = "B", reason = "stop"),
        ), events)
        assertEquals(listOf("A", "AB"), events)
        assertEquals("AB", result.text)
        assertEquals(1, result.thinkingChunkCount)
    }

    @Test
    fun incompleteStreamDoesNotReportSuccess() {
        val failure = runCatching { collect(listOf(chunk(thinking = "secret"))) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("during thinking"))
        val partial = runCatching { collect(listOf(chunk(content = "partial"))) }.exceptionOrNull()
        assertTrue(partial is IOException)
    }

    @Test
    fun cancellationDuringThinkingPreventsLaterContent() {
        var cancelled = false
        val events = mutableListOf<String>()
        val failure = runCatching {
            collectOpenAiStream(
                sequenceOf(chunk(thinking = "secret"), chunk(content = "late"), "data: [DONE]"),
                { 100L },
                { if (cancelled) throw CancellationException("stopped") },
                { _, _ -> cancelled = true },
                { events += it },
            )
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(events.isEmpty())
    }

    @Test
    fun finalFlushRetainsWhitespaceAndDoesNotDuplicateUpdates() {
        val events = mutableListOf<String>()
        val result = collectOpenAiStream(
            sequenceOf(chunk(content = "a"), chunk(content = " "), chunk(content = "b"), "data: [DONE]"),
            { 0L }, {}, { _, _ -> fail("unexpected thinking") }, { events += it },
        )
        assertEquals("a b", result.text)
        assertEquals(listOf("a", "a b"), events)
        assertEquals(2, result.assistantUpdateCount)
    }
}
