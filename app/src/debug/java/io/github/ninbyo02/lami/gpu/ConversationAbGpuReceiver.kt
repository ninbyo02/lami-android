package io.github.ninbyo02.lami.gpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.benchmark.ConversationAbBenchmarkContract
import io.github.ninbyo02.lami.benchmark.ConversationAbRunResult
import io.github.ninbyo02.lami.benchmark.ConversationAbTurnResult
import io.github.ninbyo02.lami.ui.screens.home.LocalConversationPolicy
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalApi::class)
class ConversationAbGpuReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        receiverDispatcher.execute {
            var timedOut = false
            try {
                if (!running.compareAndSet(false, true)) {
                    ConversationAbBenchmarkContract.write(
                        appContext,
                        failureResult(reason = "already_running"),
                    )
                    return@execute
                }
                val timeoutMs = intent.getLongExtra(
                    ConversationAbBenchmarkContract.EXTRA_TIMEOUT_MS,
                    ConversationAbBenchmarkContract.DEFAULT_TIMEOUT_MS,
                ).coerceIn(1_000L, 300_000L)
                val future = workerDispatcher.submit<ConversationAbRunResult> {
                    runBenchmark(appContext, intent)
                }
                val result = try {
                    future.get(timeoutMs, TimeUnit.MILLISECONDS)
                } catch (timeout: TimeoutException) {
                    timedOut = true
                    future.cancel(true)
                    failureResult(
                        reason = "timeout",
                        exception = timeout,
                    )
                }
                ConversationAbBenchmarkContract.write(appContext, result)
            } catch (throwable: Throwable) {
                ConversationAbBenchmarkContract.write(
                    appContext,
                    failureResult(
                        reason = "receiver_failure",
                        exception = throwable,
                    ),
                )
            } finally {
                running.set(false)
                pendingResult.finish()
                if (timedOut) {
                    Thread({
                        Thread.sleep(PROCESS_KILL_DELAY_MS)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }, "ConversationAbGpuTimeoutCleanup").start()
                }
            }
        }
    }

    private fun runBenchmark(
        appContext: Context,
        intent: Intent,
    ): ConversationAbRunResult {
        val startedMs = SystemClock.elapsedRealtime()
        val scenarioId = intent.getStringExtra(ConversationAbBenchmarkContract.EXTRA_SCENARIO_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: ConversationAbBenchmarkContract.DEFAULT_SCENARIO_ID
        val prompts = ConversationAbBenchmarkContract.decodePrompts(
            intent.getStringExtra(ConversationAbBenchmarkContract.EXTRA_PROMPTS_BASE64),
        )
        val requestedMaxOutputTokens = intent.getIntExtra(
            ConversationAbBenchmarkContract.EXTRA_MAX_OUTPUT_TOKENS,
            16,
        ).coerceIn(1, 128)
        val modelPath = resolveModelPath(appContext, intent)
            ?: return failureResult(
                scenarioId = scenarioId,
                requestedMaxOutputTokens = requestedMaxOutputTokens,
                reason = "gpu_generic_model_missing",
            )
        val modelFile = File(modelPath)
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            return failureResult(
                scenarioId = scenarioId,
                requestedMaxOutputTokens = requestedMaxOutputTokens,
                reason = "gpu_model_invalid",
                modelFile = modelFile,
            )
        }

        var engine: Engine? = null
        var conversation: Conversation? = null
        var engineCreateMs: Long? = null
        var conversationCreateMs: Long? = null
        val turns = mutableListOf<ConversationAbTurnResult>()
        return try {
            check(BuildConfig.DEBUG) { "Conversation A/B receiver is debug-only" }
            val engineStartedMs = SystemClock.elapsedRealtime()
            engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    visionBackend = null,
                    audioBackend = null,
                    maxNumTokens = ConversationAbBenchmarkContract.GPU_CONTEXT_MAX_TOKENS,
                    cacheDir = null,
                ),
            )
            engine.initialize()
            engineCreateMs = SystemClock.elapsedRealtime() - engineStartedMs

            val conversationStartedMs = SystemClock.elapsedRealtime()
            conversation = engine.createConversation(
                LocalConversationPolicy.conversationConfig(),
            )
            conversationCreateMs = SystemClock.elapsedRealtime() - conversationStartedMs

            prompts.forEachIndexed { promptIndex, prompt ->
                val sendStartedMs = SystemClock.elapsedRealtime()
                val message = conversation.sendMessage(prompt)
                val sendMs = SystemClock.elapsedRealtime() - sendStartedMs
                val rawOutput = renderMessage(message)
                val sanitizedOutput = ConversationAbBenchmarkContract.sanitize(
                    prompt = prompt,
                    rawOutput = rawOutput,
                )
                val benchmarkInfo = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
                val success = sanitizedOutput.isNotBlank()
                turns += ConversationAbTurnResult(
                    index = promptIndex + 1,
                    prompt = prompt,
                    rawOutput = rawOutput,
                    sanitizedOutput = sanitizedOutput,
                    status = if (success) "success" else "failure",
                    reason = if (success) "completed" else "blank_output_after_sanitize",
                    sendMs = sendMs,
                    ttftMs = benchmarkInfo
                        ?.timeToFirstTokenInSecond
                        ?.times(1_000.0)
                        ?.toLong(),
                    outputTokens = benchmarkInfo?.lastDecodeTokenCount,
                    tokensPerSecond = benchmarkInfo?.lastDecodeTokensPerSecond,
                )
            }
            val success = turns.size == prompts.size && turns.all { it.status == "success" }
            ConversationAbRunResult(
                scenarioId = scenarioId,
                backend = "GPU",
                apiSurface = "LiteRT-LM Kotlin Conversation.sendMessage",
                modelFileName = modelFile.name,
                modelBytes = modelFile.length(),
                requestedMaxOutputTokens = requestedMaxOutputTokens,
                effectiveMaxOutputTokens = null,
                outputLimitSource = "kotlin_conversation_api_not_exposed",
                engineCreateMs = engineCreateMs,
                conversationCreateMs = conversationCreateMs,
                totalMs = SystemClock.elapsedRealtime() - startedMs,
                status = if (success) "success" else "failure",
                reason = if (success) "completed" else "turn_failure",
                turns = turns,
            )
        } catch (throwable: Throwable) {
            ConversationAbRunResult(
                scenarioId = scenarioId,
                backend = "GPU",
                apiSurface = "LiteRT-LM Kotlin Conversation.sendMessage",
                modelFileName = modelFile.name,
                modelBytes = modelFile.length(),
                requestedMaxOutputTokens = requestedMaxOutputTokens,
                effectiveMaxOutputTokens = null,
                outputLimitSource = "kotlin_conversation_api_not_exposed",
                engineCreateMs = engineCreateMs,
                conversationCreateMs = conversationCreateMs,
                totalMs = SystemClock.elapsedRealtime() - startedMs,
                status = "failure",
                reason = "gpu_conversation_failure",
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message,
                turns = turns,
            )
        } finally {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
        }
    }

    private fun resolveModelPath(
        appContext: Context,
        intent: Intent,
    ): String? {
        ConversationAbBenchmarkContract.decodeOptionalBase64(
            intent.getStringExtra(ConversationAbBenchmarkContract.EXTRA_MODEL_PATH_BASE64),
        )?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            runBlocking {
                SettingsPreferences(appContext).getValidLocalGenericModelPathOrNull()
            }
        }.getOrNull()
    }

    private fun renderMessage(message: Message): String {
        val text = message.contents.contents.joinToString(separator = "") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> ""
            }
        }
        return text.takeIf { it.isNotBlank() }
            ?: message.contents.toString().takeIf { it.isNotBlank() }
            ?: message.toString()
    }

    private fun failureResult(
        scenarioId: String = ConversationAbBenchmarkContract.DEFAULT_SCENARIO_ID,
        requestedMaxOutputTokens: Int? = null,
        reason: String,
        exception: Throwable? = null,
        modelFile: File? = null,
    ): ConversationAbRunResult = ConversationAbRunResult(
        scenarioId = scenarioId,
        backend = "GPU",
        apiSurface = "LiteRT-LM Kotlin Conversation.sendMessage",
        modelFileName = modelFile?.name.orEmpty(),
        modelBytes = modelFile?.takeIf { it.isFile }?.length() ?: 0L,
        requestedMaxOutputTokens = requestedMaxOutputTokens,
        effectiveMaxOutputTokens = null,
        outputLimitSource = "kotlin_conversation_api_not_exposed",
        status = "failure",
        reason = reason,
        exceptionClass = exception?.javaClass?.name,
        exceptionMessage = exception?.message,
        turns = emptyList(),
    )

    companion object {
        private const val PROCESS_KILL_DELAY_MS = 250L
        private val running = AtomicBoolean(false)
        private val receiverDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ConversationAbGpuReceiver")
        }
        private val workerDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ConversationAbGpuWorker")
        }
    }
}
