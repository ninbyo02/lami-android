package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS4PseudoStreamingMapperTest {
    @Test
    fun `successful S1 result creates pseudo streaming candidate`() {
        val mapping = NpuStandardRouteS4PseudoStreamingMapper.map(
            s1Result = successResult(),
            finalText = LONG_TEXT,
        )
        val candidate = requireNotNull(mapping.pseudoStreamingCandidate)

        assertTrue(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.failureReason)
        assertEquals(LONG_TEXT, candidate.finalText)
        assertEquals(LONG_TEXT, candidate.dbPersistedText)
        assertEquals(LONG_TEXT, candidate.chunks.last())
        assertTrue(candidate.chunks.size in 3..5)
        assertTrue(candidate.chunks.all { it.isNotBlank() })
        assertFalse(candidate.sideEffects.realTokenStreaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
    }

    @Test
    fun `short text uses a single chunk`() {
        val mapping = NpuStandardRouteS4PseudoStreamingMapper.map(
            s1Result = successResult(),
            finalText = "こんにちは。",
        )
        val candidate = requireNotNull(mapping.pseudoStreamingCandidate)

        assertEquals(listOf("こんにちは。"), candidate.chunks)
        assertEquals("こんにちは。", candidate.dbPersistedText)
    }

    @Test
    fun `chunks are cumulative and final chunk equals original text`() {
        val candidate = requireNotNull(
            NpuStandardRouteS4PseudoStreamingMapper.map(
                s1Result = successResult(),
                finalText = LONG_TEXT,
            ).pseudoStreamingCandidate,
        )

        candidate.chunks.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.startsWith(previous))
        }
        assertEquals(LONG_TEXT, candidate.chunks.last())
    }

    @Test
    fun `code fence text is not mutated by chunking`() {
        val markdown = """
            # 見出し

            ```kotlin
            println("hello")
            ```

            説明文です。さらに続きます。最後の文です。
        """.trimIndent()
        val candidate = requireNotNull(
            NpuStandardRouteS4PseudoStreamingMapper.map(
                s1Result = successResult(),
                finalText = markdown,
            ).pseudoStreamingCandidate,
        )

        assertEquals(markdown, candidate.chunks.last())
        assertTrue(candidate.chunks.last().contains("```kotlin"))
        assertTrue(candidate.chunks.last().contains("println(\"hello\")"))
    }

    @Test
    fun `failure result creates no pseudo streaming candidate`() {
        val mapping = NpuStandardRouteS4PseudoStreamingMapper.map(
            s1Result = successResult(fallbackUsed = true),
            finalText = LONG_TEXT,
        )

        assertFalse(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.pseudoStreamingCandidate)
        assertEquals(NpuStandardRouteS4PseudoStreamingContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `empty text creates no pseudo streaming candidate`() {
        val mapping = NpuStandardRouteS4PseudoStreamingMapper.map(
            s1Result = successResult(),
            finalText = "   ",
        )

        assertFalse(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.pseudoStreamingCandidate)
        assertEquals(NpuStandardRouteS4PseudoStreamingContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `markdown processed text can be passed as final text`() {
        val finalizedMarkdown = "# 見出し\n\n- 項目1\n- 項目2\n\n本文です。続きます。終わります。"
        val candidate = requireNotNull(
            NpuStandardRouteS4PseudoStreamingMapper.map(
                s1Result = successResult(),
                finalText = finalizedMarkdown,
            ).pseudoStreamingCandidate,
        )

        assertEquals(finalizedMarkdown, candidate.finalText)
        assertEquals(finalizedMarkdown, candidate.dbPersistedText)
        assertEquals(finalizedMarkdown, candidate.chunks.last())
    }

    private fun successResult(
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = "こんにちは。",
        sanitizedOutput = "こんにちは。",
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = fallbackUsed,
        timeout = false,
        freshCrash = false,
        displayText = "こんにちは。",
    )

    private companion object {
        val LONG_TEXT = listOf(
            "最初の文です。",
            "次の文です。",
            "さらに説明します。",
            "最後の文です。",
        ).joinToString("")
    }
}
