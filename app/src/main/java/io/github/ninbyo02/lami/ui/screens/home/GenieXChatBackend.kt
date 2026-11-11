package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import io.github.ninbyo02.lami.ui.components.LocalInferenceEngineState
import io.github.ninbyo02.lamigeniexprobe.integration.LamiChatTurn
import io.github.ninbyo02.lamigeniexprobe.integration.LamiComputeChoice
import io.github.ninbyo02.lamigeniexprobe.integration.LamiEngineEvent
import io.github.ninbyo02.lamigeniexprobe.integration.LamiInferenceRequest
import io.github.ninbyo02.lamigeniexprobe.integration.LamiTextEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

internal const val GENIEX_MODEL_PATH_MARKER = "geniex-lfm3-1.7b"
internal const val GENIEX_MAX_PROMPT_CHARS = 4_000

internal fun genieXChatModelName(compute: LamiComputeChoice): String =
    "GenieX ${compute.name} · LFM3-1.7B"

internal class GenieXChatException(val userMessage: String) : IllegalStateException(userMessage)

internal class GenieXChatBackend(
    private val engine: LamiTextEngine,
    private val elapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    suspend fun run(
        compute: LamiComputeChoice,
        prompt: String,
        history: List<LocalConversationTurn>,
        onPartial: suspend (String) -> Unit,
    ): LocalInferenceRunResult {
        val request = LamiInferenceRequest(
            id = nextRequestId.getAndUpdate { (it + 1) and Int.MAX_VALUE },
            compute = compute,
            turns = genieXChatTurns(prompt, history),
        )
        val start = elapsedMs()
        val response = StringBuilder()
        var firstResponse: Long? = null
        var chunkCount = 0
        var completed = false
        val finished = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
            engine.stream(request).collect { event ->
                when (event) {
                    is LamiEngineEvent.Token -> if (event.text.isNotEmpty()) {
                        if (firstResponse == null) firstResponse = elapsedMs()
                        chunkCount++
                        response.append(event.text)
                        onPartial(response.toString())
                    }
                    is LamiEngineEvent.Complete -> completed = true
                    is LamiEngineEvent.Failure -> throw GenieXChatException(
                        when (event.code) {
                            "BIND_FAILED", "DISCONNECTED" -> "GenieXエンジンに接続できませんでした。設定でエンジンを確認してください。"
                            "BUSY" -> "GenieXは前の処理を終了しています。少し待ってから送信してください。"
                            "MODEL_MISSING" -> "設定からGenieX用のLFM3-1.7Bモデルを取り込んでください。"
                            else -> "GenieXの応答取得に失敗しました。"
                        },
                    )
                }
            }
            completed
        }
        if (finished == null) throw GenieXChatException("GenieXの応答がタイムアウトしました。")
        if (!finished || response.isBlank()) {
            throw GenieXChatException("GenieXの応答を最後まで取得できませんでした。")
        }
        val end = elapsedMs()
        return LocalInferenceRunResult(
            state = LocalInferenceEngineState.READY,
            response = response.toString(),
            trace = LocalInferenceTrace(
                localModelDisplayName = genieXChatModelName(compute),
                selectedAssistantResponseSource = "geniex",
                requestedPreferredBackend = "GENIEX_${compute.name}",
                preferredBackendApplyResult = "geniex_compute_requested",
                localTraceStartElapsedRealtimeMs = start,
                localTraceFirstResponseElapsedRealtimeMs = firstResponse,
                localTraceCompletedElapsedRealtimeMs = end,
                wallClockTotalInferenceDurationNs = (end - start) * 1_000_000,
                realPartialReceived = chunkCount > 0,
                realPartialChunkCount = chunkCount,
            ),
        )
    }

    private companion object {
        const val GENERATION_TIMEOUT_MS = 120_000L
        val nextRequestId = AtomicInteger()
    }
}

internal fun genieXChatTurns(
    prompt: String,
    history: List<LocalConversationTurn>,
): List<LamiChatTurn> {
    require(prompt.isNotBlank() && prompt.length <= GENIEX_MAX_PROMPT_CHARS)
    require(history.none { it.imageFilePaths.isNotEmpty() })
    // Keep the newest contiguous text within the IPC and model context budget.
    var remaining = GENIEX_MAX_PROMPT_CHARS - prompt.length
    val newestFirst = mutableListOf<LocalConversationTurn>()
    for (turn in LocalConversationHistoryPolicy.bounded(history).asReversed()) {
        if (turn.text.length > remaining) break
        newestFirst.add(turn)
        remaining -= turn.text.length
    }
    val earlierTurns = newestFirst.asReversed().dropWhile { it.role != LocalConversationRole.USER }
    return earlierTurns.map { turn ->
        LamiChatTurn(
            role = if (turn.role == LocalConversationRole.USER) "user" else "assistant",
            content = turn.text,
        )
    } + LamiChatTurn(role = "user", content = prompt)
}
