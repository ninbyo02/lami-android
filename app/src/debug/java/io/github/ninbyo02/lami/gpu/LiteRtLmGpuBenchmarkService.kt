package io.github.ninbyo02.lami.gpu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Debug-only, fixed-input cold-start bridge for the standard token benchmark. */
class LiteRtLmGpuBenchmarkService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.let(::validate) ?: return reject("missing_intent", startId)
        if (!running.compareAndSet(false, true)) return reject("already_running", startId)
        writeServiceMarker(request, "service_started", "validated_fixed_debug_request")

        executor.execute {
            try {
                val receiverIntent = Intent(LiteRtLmGpuBenchmarkReceiver.ACTION).apply {
                    component = ComponentName(
                        this@LiteRtLmGpuBenchmarkService,
                        LiteRtLmGpuBenchmarkReceiver::class.java,
                    )
                    setPackage(packageName)
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMESTAMP, request.timestamp)
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PROMPTS, LONG_SEQUENCE_PROMPT)
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MAX_OUTPUT_TOKENS_LIST, request.tokens.toString())
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_BACKEND_VARIANT, request.backend)
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_CLOSE_POLICY, "normal")
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PHASE, "send-message")
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MODEL_PATH_SOURCE, "generic_fallback")
                    putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMEOUT_MS, request.timeoutMs)
                }
                writeServiceMarker(request, "service_dispatch", "internal_explicit_receiver")
                sendBroadcast(receiverIntent)
                val terminal = awaitTerminalState(request.timestamp, request.timeoutMs + 5_000L)
                writeServiceMarker(
                    request,
                    "service_finished",
                    if (terminal) "terminal_state_observed stopSelf" else "state_wait_timeout stopSelf",
                )
            } finally {
                running.set(false)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun reject(reason: String, startId: Int): Int {
        writeRawMarker("service_rejected", reason)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun validate(intent: Intent): Request? {
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT) return null
        if (packageName != STANDARD_APP_ID || intent.action != ACTION_START) return null
        if (intent.getStringExtra(EXTRA_PROMPT_VARIANT) != "long-sequence") return null
        if (intent.getStringExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MODEL_PATH_SOURCE) != "generic_fallback") return null
        if (intent.getStringExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PHASE) != "send-message") return null
        if (intent.getStringExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_CLOSE_POLICY) != "normal") return null
        val backend = intent.getStringExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_BACKEND_VARIANT)
            ?.takeIf { it == "gpu" || it == "cpu" } ?: return null
        val tokens = intent.getStringExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MAX_OUTPUT_TOKENS_LIST)
            ?.toIntOrNull() ?: return null
        if (tokens !in allowedTokens(backend)) return null
        val timestamp = intent.getStringExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMESTAMP)
            ?.takeIf { TIMESTAMP.matches(it) } ?: return null
        val timeoutMs = intent.getLongExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMEOUT_MS, 60_000L)
            .coerceIn(1_000L, 300_000L)
        return Request(timestamp, backend, tokens, timeoutMs)
    }

    private fun allowedTokens(backend: String): Set<Int> =
        if (backend == "gpu") setOf(32, 128, 512) else setOf(32)

    private fun awaitTerminalState(timestamp: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val state = File(filesDir, LiteRtLmGpuBenchmarkReceiver.STATE_FILE_NAME)
        while (SystemClock.elapsedRealtime() < deadline) {
            val text = runCatching { state.readText() }.getOrDefault("")
            if (text.contains("timestamp=$timestamp") &&
                TERMINAL_STATUS.any { text.contains("status=$it") }) return true
            Thread.sleep(250L)
        }
        return false
    }

    private fun writeServiceMarker(request: Request, stage: String, detail: String) {
        writeRawMarker(
            stage,
            "timestamp=${request.timestamp} backend=${request.backend} tokens=${request.tokens} $detail",
        )
    }

    private fun writeRawMarker(stage: String, detail: String) {
        runCatching {
            val text = listOf(
                "timestamp=${intentTimestamp(detail)}",
                "route_type=litert_lm_gpu_benchmark",
                "stage=$stage",
                "detail=${detail.replace('\n', ' ').take(500)}",
                "elapsed_realtime_ms=${SystemClock.elapsedRealtime()}",
                "wall_time_ms=${System.currentTimeMillis()}",
            ).joinToString("\n", postfix = "\n")
            File(filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_FILE_NAME).writeText(text)
            File(filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_HISTORY_FILE_NAME).appendText(text + "\n")
        }
    }

    private fun intentTimestamp(detail: String): String =
        TIMESTAMP.find(detail)?.value ?: "unknown"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Debug token benchmark", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Debug token benchmark")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Debug token benchmark")
                .setOngoing(true)
                .build()
        }

    private data class Request(val timestamp: String, val backend: String, val tokens: Int, val timeoutMs: Long)

    companion object {
        const val ACTION_START = "io.github.ninbyo02.lami.action.START_LITERT_LM_TOKEN_BENCHMARK"
        const val EXTRA_PROMPT_VARIANT = "prompt_variant"
        private const val STANDARD_APP_ID = "io.github.ninbyo02.lami"
        private const val CHANNEL_ID = "debug_token_benchmark"
        private const val NOTIFICATION_ID = 7331
        private val TIMESTAMP = Regex("[0-9]{8}_[0-9]{6}")
        private val TERMINAL_STATUS = setOf("success", "partial", "failure", "blocked")
        private const val LONG_SEQUENCE_PROMPT = "Write only the decimal integers from 1 through 4000, in ascending order, separated by single spaces. Do not summarize, skip, explain, or add any other text."
        private val running = AtomicBoolean(false)
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkService")
        }
    }
}
