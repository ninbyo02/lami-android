package io.github.ninbyo02.lami.gpu

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Base64
import android.view.Gravity
import android.widget.TextView
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.benchmark.ConversationAbBenchmarkContract
import io.github.ninbyo02.lami.ui.screens.home.LocalConversationPolicy
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class GpuIdlePrewarmDiagnosticActivity : Activity() {
    private var diagnosticRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            !BuildConfig.DEBUG ||
            !BuildConfig.GPU_IDLE_PREWARM_DIAGNOSTIC ||
            !BuildConfig.APPLICATION_ID.endsWith(".gpuidleprewarm") ||
            intent.action != GpuIdlePrewarmDiagnosticReceiver.ACTION_START
        ) {
            finishAndRemoveTask()
            return
        }
        setContentView(
            TextView(this).apply {
                gravity = Gravity.CENTER
                text = "GPU idle-prewarm diagnostic\nIsolated debug package"
            },
        )
        diagnosticRunning = true
        GpuIdlePrewarmController.start(applicationContext, intent) {
            runOnUiThread {
                diagnosticRunning = false
                finishAndRemoveTask()
            }
        }
    }

    override fun onStop() {
        if (diagnosticRunning && !isChangingConfigurations && !isFinishing) {
            GpuIdlePrewarmController.cancel("background_confirmed")
        }
        super.onStop()
    }
}

class GpuIdlePrewarmDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || !BuildConfig.GPU_IDLE_PREWARM_DIAGNOSTIC) return
        if (intent.action != ACTION_CANCEL) return
        GpuIdlePrewarmController.cancel(
            intent.getStringExtra(EXTRA_CANCEL_REASON) ?: "user_stop",
        )
    }

    companion object {
        const val ACTION_START =
            "io.github.ninbyo02.lami.action.GPU_IDLE_PREWARM_DIAGNOSTIC"
        const val ACTION_CANCEL =
            "io.github.ninbyo02.lami.action.CANCEL_GPU_IDLE_PREWARM_DIAGNOSTIC"
        const val EXTRA_MODE = "mode"
        const val EXTRA_MODEL_PATH_BASE64 = "model_path_base64"
        const val EXTRA_PROMPT_BASE64 = "prompt_base64"
        const val EXTRA_SCENARIO_ID = "scenario_id"
        const val EXTRA_IDLE_DELAY_MS = "idle_delay_ms"
        const val EXTRA_HOLD_AFTER_PREWARM_MS = "hold_after_prewarm_ms"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val EXTRA_CANCEL_REASON = "cancel_reason"
    }
}

internal object GpuIdlePrewarmController : ComponentCallbacks2 {
    private const val GPU_CONTEXT_MAX_TOKENS = 512
    private const val MODE_ON_DEMAND = "on_demand"
    private const val MODE_PREWARM = "idle_prewarm"
    private val worker = Executors.newSingleThreadExecutor()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val active = AtomicReference<Future<*>?>()
    private val activeEngine = AtomicReference<Engine?>()
    private val activeConversation = AtomicReference<Conversation?>()
    private val cancelRequested = AtomicBoolean(false)
    private val cancelReason = AtomicReference<String?>(null)
    private val completion = AtomicReference<(() -> Unit)?>(null)

    @Synchronized
    fun start(context: Context, intent: Intent, onComplete: () -> Unit) {
        if (active.get() != null) {
            writeState(context, "blocked", "already_running", null)
            onComplete()
            return
        }
        check(BuildConfig.APPLICATION_ID.endsWith(".gpuidleprewarm")) {
            "Diagnostic must use the isolated application id"
        }
        completion.set(onComplete)
        cancelRequested.set(false)
        cancelReason.set(null)
        context.registerComponentCallbacks(this)
        val timeoutMs = intent.getLongExtra(
            GpuIdlePrewarmDiagnosticReceiver.EXTRA_TIMEOUT_MS,
            180_000L,
        ).coerceIn(5_000L, 300_000L)
        lateinit var future: FutureTask<Unit>
        future = FutureTask {
            try {
                writeState(context, "running", "started", null)
                val result = runDiagnostic(context, intent)
                writeResult(context, result)
                writeState(context, result.getString("status"), result.getString("reason"), result)
            } catch (failure: Throwable) {
                val reason = cancelReason.get() ?: "diagnostic_failure"
                writeState(
                    context,
                    if (cancelRequested.get()) "cancelled" else "failure",
                    reason,
                    failure,
                )
            } finally {
                releaseResources()
                runCatching { context.unregisterComponentCallbacks(this) }
                active.compareAndSet(future, null)
                completion.getAndSet(null)?.invoke()
            }
            Unit
        }
        active.set(future)
        worker.execute(future)
        watchdog.schedule({
            if (active.get() === future && !future.isDone) cancel("timeout")
        }, timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun cancel(reason: String) {
        cancelReason.compareAndSet(null, normalizeCancelReason(reason))
        cancelRequested.set(true)
        active.get()?.cancel(true)
    }

    override fun onLowMemory() {
        cancel("low_memory")
    }
    override fun onTrimMemory(level: Int) {
        when {
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                cancel("background_confirmed")
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                cancel("low_memory")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @OptIn(ExperimentalApi::class)
    private fun runDiagnostic(context: Context, intent: Intent): JSONObject {
        val mode = intent.getStringExtra(GpuIdlePrewarmDiagnosticReceiver.EXTRA_MODE)
            ?.trim()?.lowercase().orEmpty()
        require(mode == MODE_ON_DEMAND || mode == MODE_PREWARM) { "invalid mode" }
        val modelPath = decodeRequired(
            intent.getStringExtra(
                GpuIdlePrewarmDiagnosticReceiver.EXTRA_MODEL_PATH_BASE64,
            ),
        )
        val prompt = decodeRequired(
            intent.getStringExtra(GpuIdlePrewarmDiagnosticReceiver.EXTRA_PROMPT_BASE64),
        )
        val model = File(modelPath)
        require(model.isFile && model.length() > 0L) { "model missing" }
        val scenarioId = intent.getStringExtra(
            GpuIdlePrewarmDiagnosticReceiver.EXTRA_SCENARIO_ID,
        )?.takeIf { it.isNotBlank() } ?: mode
        val idleDelayMs = intent.getLongExtra(
            GpuIdlePrewarmDiagnosticReceiver.EXTRA_IDLE_DELAY_MS,
            if (mode == MODE_PREWARM) 1_000L else 0L,
        ).coerceIn(0L, 30_000L)
        val holdMs = intent.getLongExtra(
            GpuIdlePrewarmDiagnosticReceiver.EXTRA_HOLD_AFTER_PREWARM_MS,
            500L,
        ).coerceIn(0L, 60_000L)
        val startedMs = SystemClock.elapsedRealtime()
        val before = ResourceSnapshot.capture(context)
        val modelSha256 = sha256(model)
        val responsiveness = MainThreadLatencyProbe()
        responsiveness.start()

        var engineCreateMs: Long? = null
        var conversationCreateMs: Long? = null
        var sendMs: Long? = null
        var output = ""
        var ttftMs: Double? = null
        var prefillTokens: Double? = null
        var prefillTokensPerSecond: Double? = null
        var decodeTokens: Double? = null
        var decodeTokensPerSecond: Double? = null
        var streamingChunkCount = 0
        var benchmarkInfoError: String? = null
        var afterPrewarm: ResourceSnapshot? = null
        var userVisibleStartedMs: Long? = null
        var userVisibleCompletedMs: Long? = null
        var status = "success"
        var reason = "completed"
        try {
            cancellableDelay(idleDelayMs)
            if (mode == MODE_ON_DEMAND) {
                userVisibleStartedMs = SystemClock.elapsedRealtime()
            }
            val createStarted = SystemClock.elapsedRealtime()
            val engine = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = GPU_CONTEXT_MAX_TOKENS,
                    cacheDir = null,
                ),
            )
            activeEngine.set(engine)
            engine.initialize()
            engineCreateMs = SystemClock.elapsedRealtime() - createStarted
            ensureNotCancelled()
            afterPrewarm = ResourceSnapshot.capture(context)
            if (mode == MODE_PREWARM) {
                cancellableDelay(holdMs)
                userVisibleStartedMs = SystemClock.elapsedRealtime()
            }

            val conversationStarted = SystemClock.elapsedRealtime()
            val conversation = engine.createConversation(
                LocalConversationPolicy.conversationConfig(),
            )
            activeConversation.set(conversation)
            conversationCreateMs = SystemClock.elapsedRealtime() - conversationStarted

            ensureNotCancelled()
            val sendStarted = SystemClock.elapsedRealtime()
            val streamedOutput = StringBuilder()
            runBlocking {
                conversation.sendMessageAsync(
                    prompt,
                    LocalConversationPolicy.generationExtraContext,
                ).collect { message ->
                    ensureNotCancelled()
                    val chunk = renderMessage(message)
                    if (chunk.isNotEmpty()) {
                        if (ttftMs == null) {
                            ttftMs =
                                (SystemClock.elapsedRealtime() - sendStarted).toDouble()
                        }
                        streamedOutput.append(chunk)
                        streamingChunkCount += 1
                    }
                }
            }
            sendMs = SystemClock.elapsedRealtime() - sendStarted
            output = ConversationAbBenchmarkContract.sanitize(
                prompt = prompt,
                rawOutput = streamedOutput.toString(),
            )
            runCatching { conversation.getBenchmarkInfo() }
                .onSuccess { info ->
                    prefillTokens = info.lastPrefillTokenCount.toDouble()
                    prefillTokensPerSecond = info.lastPrefillTokensPerSecond
                    decodeTokens = info.lastDecodeTokenCount.toDouble()
                    decodeTokensPerSecond = info.lastDecodeTokensPerSecond
                    if (ttftMs == null) {
                        ttftMs = info.timeToFirstTokenInSecond * 1_000.0
                    }
                }
                .onFailure { failure ->
                    benchmarkInfoError =
                        failure.javaClass.name + ":" + failure.message.orEmpty()
                }
            userVisibleCompletedMs = SystemClock.elapsedRealtime()
            if (output.isBlank()) {
                status = "failure"
                reason = "blank_output"
            }
        } catch (interrupted: InterruptedException) {
            status = "cancelled"
            reason = cancelReason.get() ?: "interrupted"
            Thread.currentThread().interrupt()
        } finally {
            responsiveness.stop()
            releaseResources()
        }

        runCatching {
            System.gc()
            Thread.sleep(500L)
        }
        val afterRelease = ResourceSnapshot.capture(context)
        return JSONObject()
            .put("schema_version", 1)
            .put("scenario_id", scenarioId)
            .put("mode", mode)
            .put("status", status)
            .put("reason", reason)
            .put("model_file_name", model.name)
            .put("model_bytes", model.length())
            .put("model_sha256", modelSha256)
            .put("api_surface", "LiteRT-LM Kotlin Conversation.sendMessageAsync")
            .put("conversation_api_used", true)
            .put("app_template_used", false)
            .put("prompt_template_owner", "model_metadata")
            .put("sampler_profile", "lami_stable_v1")
            .put("sampler_top_k", 40)
            .put("sampler_top_p", 0.9)
            .put("sampler_temperature", 0.3)
            .put("sampler_seed", 42)
            .put("gpu_context_max_tokens", GPU_CONTEXT_MAX_TOKENS)
            .put("text_backend", "GPU")
            .put("vision_backend", "GPU")
            .put("audio_backend", "CPU")
            .putNullable("engine_create_ms", engineCreateMs)
            .putNullable("conversation_create_ms", conversationCreateMs)
            .putNullable("send_ms", sendMs)
            .put("total_ms", SystemClock.elapsedRealtime() - startedMs)
            .putNullable(
                "user_visible_latency_ms",
                userVisibleStartedMs?.let { started ->
                    userVisibleCompletedMs?.minus(started)
                },
            )
            .putNullable(
                "prewarm_lead_ms",
                if (mode == MODE_PREWARM) {
                    userVisibleStartedMs?.let { it - startedMs }
                } else {
                    0L
                },
            )
            .put("idle_delay_ms", idleDelayMs)
            .put("hold_after_prewarm_ms", holdMs)
            .put("generation_extra_context", "enable_thinking=false")
            .put("prompt", prompt)
            .put("output", output)
            .putNullable("ttft_ms", ttftMs)
            .putNullable("prefill_tokens", prefillTokens)
            .putNullable("prefill_tokens_per_second", prefillTokensPerSecond)
            .putNullable("decode_tokens", decodeTokens)
            .putNullable("decode_tokens_per_second", decodeTokensPerSecond)
            .put("streaming_chunk_count", streamingChunkCount)
            .putNullable("benchmark_info_error", benchmarkInfoError)
            .put("main_thread_max_latency_ms", responsiveness.maxLatencyMs())
            .put("resource_before", before.toJson())
            .putNullable("resource_after_prewarm", afterPrewarm?.toJson())
            .put("resource_after_release", afterRelease.toJson())
            .put("cancel_requested", cancelRequested.get())
            .putNullable("cancel_reason", cancelReason.get())
            .put(
                "resources_closed",
                activeEngine.get() == null && activeConversation.get() == null,
            )
            .put("build_type", "debug")
            .put("application_id", BuildConfig.APPLICATION_ID)
    }

    private fun releaseResources() {
        runCatching { activeConversation.getAndSet(null)?.close() }
        runCatching { activeEngine.getAndSet(null)?.close() }
    }

    private fun ensureNotCancelled() {
        if (cancelRequested.get() || Thread.currentThread().isInterrupted) {
            throw InterruptedException(cancelReason.get() ?: "cancelled")
        }
    }

    private fun cancellableDelay(durationMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + durationMs
        while (SystemClock.elapsedRealtime() < deadline) {
            ensureNotCancelled()
            Thread.sleep(minOf(25L, deadline - SystemClock.elapsedRealtime()))
        }
    }

    private fun decodeRequired(value: String?): String {
        require(!value.isNullOrBlank()) { "required base64 extra missing" }
        return String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun normalizeCancelReason(reason: String): String {
        val normalized = reason.trim().lowercase().replace('-', '_')
        return if (normalized in setOf(
                "navigation",
                "model_changed",
                "backend_changed",
                "low_memory",
                "background_confirmed",
                "user_stop",
                "timeout",
            )
        ) normalized else "user_stop"
    }

    private fun renderMessage(message: Message): String =
        message.contents.contents.joinToString("") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> ""
            }
        }.ifBlank { message.contents.toString() }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                ensureNotCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    private fun writeResult(context: Context, result: JSONObject) {
        val directory = File(context.filesDir, "gpu_idle_prewarm").apply {
            mkdirs()
        }
        val name = result.getString("scenario_id")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        File(directory, name + ".json").writeText(result.toString(2))
    }

    private fun writeState(
        context: Context,
        status: String,
        reason: String,
        detail: Any?,
    ) {
        val detailValue = when (detail) {
            is JSONObject -> detail
            is Throwable -> detail.javaClass.name + ":" + detail.message
            else -> detail
        }
        val state = JSONObject()
            .put("status", status)
            .put("reason", reason)
            .put("updated_elapsed_ms", SystemClock.elapsedRealtime())
            .putNullable("detail", detailValue)
        File(
            context.filesDir,
            "gpu_idle_prewarm_state.json",
        ).writeText(state.toString(2))
    }
}

private data class ResourceSnapshot(
    val elapsedMs: Long,
    val rssKb: Long?,
    val pssKb: Int,
    val javaHeapUsedBytes: Long,
    val batteryTempTenthsC: Int?,
    val energyCounterNwh: Long?,
    val chargeCounterUah: Int?,
    val thermalStatus: Int?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("elapsed_ms", elapsedMs)
        .putNullable("rss_kb", rssKb)
        .put("pss_kb", pssKb)
        .put("java_heap_used_bytes", javaHeapUsedBytes)
        .putNullable("battery_temp_tenths_c", batteryTempTenthsC)
        .putNullable("energy_counter_nwh", energyCounterNwh)
        .putNullable("charge_counter_uah", chargeCounterUah)
        .putNullable("thermal_status", thermalStatus)

    companion object {
        fun capture(context: Context): ResourceSnapshot {
            val memoryInfo = Debug.MemoryInfo().also {
                Debug.getMemoryInfo(it)
            }
            val runtime = Runtime.getRuntime()
            val battery = context.getSystemService(BatteryManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            return ResourceSnapshot(
                elapsedMs = SystemClock.elapsedRealtime(),
                rssKb = File("/proc/self/status").useLines { lines ->
                    lines.firstOrNull { it.startsWith("VmRSS:") }
                        ?.split(Regex("\\s+"))
                        ?.getOrNull(1)
                        ?.toLongOrNull()
                },
                pssKb = memoryInfo.totalPss,
                javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                batteryTempTenthsC = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Int.MIN_VALUE,
                )?.takeUnless { it == Int.MIN_VALUE },
                energyCounterNwh = battery?.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER,
                )?.takeUnless { it == Long.MIN_VALUE },
                chargeCounterUah = battery?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,
                )?.takeUnless { it == Int.MIN_VALUE },
                thermalStatus = power?.currentThermalStatus,
            )
        }
    }
}

private class MainThreadLatencyProbe {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val maxLatencyMs = AtomicLong(0L)
    private var task: ScheduledFuture<*>? = null

    fun start() {
        task = scheduler.scheduleAtFixedRate({
            val postedAt = SystemClock.elapsedRealtime()
            handler.post {
                val latency = SystemClock.elapsedRealtime() - postedAt
                while (true) {
                    val current = maxLatencyMs.get()
                    if (
                        latency <= current ||
                        maxLatencyMs.compareAndSet(current, latency)
                    ) {
                        break
                    }
                }
            }
        }, 0L, 50L, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        task?.cancel(true)
        scheduler.shutdownNow()
    }

    fun maxLatencyMs(): Long = maxLatencyMs.get()
}

private fun JSONObject.putNullable(
    name: String,
    value: Any?,
): JSONObject = put(name, value ?: JSONObject.NULL)
