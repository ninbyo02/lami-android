package io.github.ninbyo02.lamigeniexprobe

import android.content.Context
import android.os.SystemClock
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.ChatMessage
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.ProfilingData
import com.geniex.sdk.bean.SamplerConfig
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reuses weights across turns and resets KV state before sending a full chat history.
 */
internal class GenieXResidentSession(private val context: Context) {
    private val mutex = Mutex()
    private var engine: LlmWrapper? = null
    private var engineKey: EngineKey? = null

    internal data class Turn(val role: String, val text: String) {
        init {
            require(role in listOf("system", "user", "assistant")) { "Invalid chat role" }
            require(text.isNotBlank() && text.length <= 10000) { "Invalid chat text" }
        }
    }

    internal data class Outcome(
        val text: String,
        val compute: ComputeTarget,
        val modelReused: Boolean,
        val loadMs: Long,
        val generationMs: Long,
        val ttftMs: Double?,
        val callbackFirstMs: Long?,
        val generatedTokens: Long?,
        val decodeTps: Double?,
        val promptTokens: Long?,
        val chunkCount: Int,
        val stopReason: String?,
    ) {
        fun toMetricsJson() = JSONObject().apply {
            put("requested_compute", compute.value)
            put("model_reused", modelReused)
            put("model_load_ms", loadMs)
            put("generation_ms", generationMs)
            put("ttft_ms", ttftMs)
            put("callback_first_ms", callbackFirstMs)
            put("generated_tokens", generatedTokens)
            put("decode_tok_s", decodeTps)
            put("prompt_tokens", promptTokens)
            put("chunk_count", chunkCount)
            put("stop_reason", stopReason)
            put("output_chars", text.length)
            put("npu_hardware_offload_verified", false)
        }
    }

    private data class EngineKey(
        val modelPath: String,
        val modelLength: Long,
        val modelModifiedMs: Long,
        val compute: ComputeTarget,
    )

    private fun initializeNative() {
        val sdk = GenieXSdk.getInstance()
        val error = AtomicReference<String?>(null)
        sdk.init(context.applicationContext, object : GenieXSdk.InitCallback {
            override fun onSuccess() = Unit
            override fun onFailure(reason: String) { error.set(reason) }
        })
        check(error.get() == null) { "GenieX init failure: " + error.get() }
    }

    suspend fun generate(
        model: File,
        compute: ComputeTarget,
        turns: List<Turn>,
        maxTokens: Int,
        stop: Deferred<Unit>,
        onChunk: (String) -> Unit,
    ): Outcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            fun checkStopped() {
                if (stop.isCompleted) throw CancellationException("GenieX request stopped")
            }

            checkStopped()
            val key = EngineKey(model.canonicalPath, model.length(), model.lastModified(), compute)
            val reused = engine != null && key == engineKey
            val loadMs = if (reused) 0L else {
                val started = SystemClock.elapsedRealtime()
                Lfm3ModelPolicy.verify(model, ::checkStopped)
                checkStopped()
                engine?.close()
                engine = null
                engineKey = null
                initializeNative()
                engine = LlmWrapper.builder().llmCreateInput(
                    LlmCreateInput(
                        model_path = key.modelPath,
                        config = ModelConfig(
                            nCtx = 4096,
                            nThreads = 4,
                            nThreadsBatch = 4,
                            nBatch = 512,
                            nUBatch = 256,
                        ),
                        runtime_id = "llama_cpp",
                        compute_unit = compute.value,
                    )
                ).build().getOrThrow()
                engineKey = key
                SystemClock.elapsedRealtime() - started
            }
            val wrapper = checkNotNull(engine)
            try {
                checkStopped()
                if (reused) {
                    val resetStatus = wrapper.reset()
                    check(resetStatus == 0) { "GenieX session reset failed: " + resetStatus }
                }
                val template = wrapper.applyChatTemplate(
                    turns.map { ChatMessage(role = it.role, content = it.text) }.toTypedArray(),
                    tools = null,
                    enableThinking = false,
                ).getOrThrow()
                checkStopped()
                val generation = GenerationConfig(
                    maxTokens = maxTokens,
                    samplerConfig = SamplerConfig(
                        temperature = 0.1f,
                        topK = 50,
                        repetitionPenalty = 1.1f,
                        chatTemplateGrammar = template.chatTemplateGrammar,
                    ),
                )
                val start = SystemClock.elapsedRealtime()
                val text = StringBuilder()
                var first: Long? = null
                var chunkCount = 0
                var profile: ProfilingData? = null
                var finished = false
                coroutineScope {
                    val generate = async {
                        ensureActive()
                        wrapper.generateStreamFlow(template.formattedText, config = generation).collect { item ->
                            when (item) {
                                is LlmStreamResult.Token -> {
                                    if (first == null) first = SystemClock.elapsedRealtime() - start
                                    text.append(item.text)
                                    chunkCount++
                                    onChunk(item.text)
                                }
                                is LlmStreamResult.Completed -> {
                                    finished = true
                                    profile = item.profile
                                }
                                is LlmStreamResult.Error -> throw IllegalStateException(
                                    "GenieX streaming error: " + item.throwable
                                )
                            }
                        }
                    }
                    val cancellation = launch {
                        stop.await()
                        generate.cancel()
                        withContext(NonCancellable) {
                            runCatching { wrapper.stopStream() }
                        }
                    }
                    try {
                        generate.await()
                    } finally {
                        cancellation.cancelAndJoin()
                    }
                }
                checkStopped()
                check(finished) { "The native stream stopped without a completion event" }
                Outcome(
                    text = text.toString(),
                    compute = compute,
                    modelReused = reused,
                    loadMs = loadMs,
                    generationMs = SystemClock.elapsedRealtime() - start,
                    ttftMs = profile?.ttftMs,
                    callbackFirstMs = first,
                    generatedTokens = profile?.generatedTokens,
                    decodeTps = profile?.decodingSpeed,
                    promptTokens = profile?.promptTokens,
                    chunkCount = chunkCount,
                    stopReason = profile?.stopReason,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                wrapper.close()
                engine = null
                engineKey = null
                throw error
            }
        }
    }

    suspend fun close() = mutex.withLock {
        withContext(Dispatchers.IO) {
            engine?.close()
            engine = null
            engineKey = null
        }
    }
}
