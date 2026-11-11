package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.LocalInferenceEngineState
import io.github.ninbyo02.lamigeniexprobe.integration.LamiChatTurn
import io.github.ninbyo02.lamigeniexprobe.integration.LamiComputeChoice
import io.github.ninbyo02.lamigeniexprobe.integration.LamiEngineEvent
import io.github.ninbyo02.lamigeniexprobe.integration.LamiInferenceRequest
import io.github.ninbyo02.lamigeniexprobe.integration.LamiTextEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenieXChatBackendTest {
    @Test
    fun forwardsHistoryAndAccumulatesNativeChunksForChat() = runTest {
        lateinit var submitted: LamiInferenceRequest
        val backend = GenieXChatBackend(engine { request ->
            submitted = request
            flowOf(
                LamiEngineEvent.Token("はい、"),
                LamiEngineEvent.Token("覚えています。"),
                LamiEngineEvent.Complete("{}"),
            )
        }, elapsedMs = { 100L })
        val partials = mutableListOf<String>()

        val result = backend.run(
            compute = LamiComputeChoice.NPU,
            prompt = "覚えていますか？",
            history = listOf(user("合言葉はラミィです。"), assistant("了解しました。")),
            onPartial = { partials.add(it) },
        )

        assertEquals(LamiComputeChoice.NPU, submitted.compute)
        assertEquals(
            listOf(
                LamiChatTurn("user", "合言葉はラミィです。"),
                LamiChatTurn("assistant", "了解しました。"),
                LamiChatTurn("user", "覚えていますか？"),
            ),
            submitted.turns,
        )
        assertEquals(listOf("はい、", "はい、覚えています。"), partials)
        assertEquals(LocalInferenceEngineState.READY, result.state)
        assertEquals(partials.last(), result.response)
        assertEquals("geniex", result.trace.selectedAssistantResponseSource)
        assertEquals(2, result.trace.realPartialChunkCount)
    }

    @Test
    fun unfinishedStreamCannotBecomeCompletedChatMessage() = runTest {
        val backend = GenieXChatBackend(
            engine { flowOf(LamiEngineEvent.Token("unfinished")) },
            elapsedMs = { 100L },
        )

        val failure = runCatching {
            backend.run(LamiComputeChoice.CPU, "こんにちは", emptyList()) {}
        }.exceptionOrNull()

        assertTrue(failure is GenieXChatException)
    }

    @Test
    fun stoppingChatCancelsBridgeFlow() = runTest {
        val firstPartial = CompletableDeferred<Unit>()
        var flowStopped = false
        val backend = GenieXChatBackend(engine {
            flow {
                try {
                    emit(LamiEngineEvent.Token("途中"))
                    awaitCancellation()
                } finally {
                    flowStopped = true
                }
            }
        }, elapsedMs = { 100L })

        val generation = launch {
            backend.run(LamiComputeChoice.CPU, "こんにちは", emptyList()) {
                firstPartial.complete(Unit)
            }
        }
        firstPartial.await()
        generation.cancelAndJoin()

        assertTrue(generation.isCancelled)
        assertTrue(flowStopped)
    }

    @Test
    fun historyBudgetKeepsNewestCompleteTurns() {
        val history = listOf(
            user("古".repeat(GENIEX_MAX_PROMPT_CHARS)),
            assistant("古い応答"),
            user("新しい質問"),
            assistant("新しい応答"),
        )
        assertEquals(
            listOf(
                LamiChatTurn("user", "新しい質問"),
                LamiChatTurn("assistant", "新しい応答"),
                LamiChatTurn("user", "続き"),
            ),
            genieXChatTurns("続き", history),
        )
    }

    private fun user(text: String) = LocalConversationTurn(LocalConversationRole.USER, text)
    private fun assistant(text: String) = LocalConversationTurn(LocalConversationRole.MODEL, text)

    private fun engine(events: (LamiInferenceRequest) -> Flow<LamiEngineEvent>): LamiTextEngine =
        object : LamiTextEngine {
            override fun stream(request: LamiInferenceRequest): Flow<LamiEngineEvent> = events(request)
        }
}
