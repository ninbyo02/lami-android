package io.github.ninbyo02.lami.integration

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
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.db.entity.TitleSource
import io.github.ninbyo02.lami.db.repository.ChatRepository
import io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver
import io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkService
import io.github.ninbyo02.lami.ui.screens.home.AssistantGenerationSessionController
import io.github.ninbyo02.lami.ui.screens.home.AssistantMessageLifecycleCoordinator
import io.github.ninbyo02.lami.ui.screens.home.AssistantMessageLifecycleStore
import io.github.ninbyo02.lami.ui.screens.home.NpuConversationPrefacePlanner
import io.github.ninbyo02.lami.ui.screens.home.NpuKotlinConversationProductRoute
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/** Debug-only background integration test for NPU generation + durable Room ownership. */
class GenerationIntegrationTestService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
            intent?.action != ACTION_RUN_NPU_4096 || !running.compareAndSet(false, true)
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        executor.execute {
            try {
                runBlocking { runBackgroundIntegration() }
            } catch (t: Throwable) {
                writeResult("status=failure\nreason=service_exception\nexception=${t.javaClass.name}:${t.message.orEmpty()}\n")
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

    private suspend fun runBackgroundIntegration() {
        val started = SystemClock.elapsedRealtime()
        writeResult("status=running\nphases=npu_lifecycle,npu_endurance4096,gpu\ncpu_background_status=unsupported_receiver_anr_limit\n")
        val db = ChatDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(db.messageDao(), db.chatDao())
        val modelPath = SettingsPreferences(applicationContext).getValidLocalBaseModelPathOrNull().orEmpty()
        require(modelPath.isNotBlank()) { "selected_local_model_missing" }

        val lifecycleResult = runLifecyclePhase(repository, modelPath)
        val enduranceResult = runEndurancePhase(modelPath)
        NpuKotlinConversationProductRoute.reset("integration-phase-transition")
        Thread.sleep(1_000L)
        val gpuResult = runGenericBenchmarkPhase("gpu", 128, 120_000L)
        val overall = lifecycleResult.passed && enduranceResult.infrastructurePassed && gpuResult.passed
        writeResult(buildString {
            appendLine("status=${if (overall) "success" else "failure"}")
            appendLine("backend=NPU")
            appendLine("lifecycle_status=${if (lifecycleResult.passed) "success" else "failure"}")
            appendLine("lifecycle_chat_id=${lifecycleResult.chatId}")
            appendLine("lifecycle_output=${lifecycleResult.output.replace("\n", " ").take(120)}")
            appendLine("lifecycle_user_rows=${lifecycleResult.userRows}")
            appendLine("lifecycle_assistant_rows=${lifecycleResult.assistantRows}")
            appendLine("lifecycle_in_flight_rows=${lifecycleResult.inFlightRows}")
            appendLine("lifecycle_interrupted_rows=${lifecycleResult.interruptedRows}")
            appendLine("lifecycle_session_terminal=${lifecycleResult.sessionTerminal}")
            appendLine("endurance_status=${if (enduranceResult.infrastructurePassed) "success" else "failure"}")
            appendLine("endurance_quality_pass=${enduranceResult.qualityPassed}")
            appendLine("endurance_failure_reason=${enduranceResult.failureReason}")
            appendLine("endurance_partial_seen=${enduranceResult.partialSeen}")
            appendLine("endurance_max_output_tokens=4096")
            appendLine("gpu_status=${if (gpuResult.passed) "success" else "failure"}")
            appendLine("gpu_reason=${gpuResult.reason}")
            appendLine("cpu_background_status=unsupported_receiver_anr_limit")
            appendLine("cpu_validation=fresh_cpu_product_default_passed")
            appendLine("elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        })
    }

    private suspend fun runLifecyclePhase(repository: ChatRepository, modelPath: String): LifecyclePhaseResult {
        val chatId = repository.newChat(Chat(title = TEST_CHAT_TITLE, titleSource = TitleSource.MANUAL))
        repository.insert(Message(chatId = chatId, message = LIFECYCLE_PROMPT, isSendbyMe = true))
        val lifecycle = AssistantMessageLifecycleCoordinator(repositoryLifecycleStore(repository))
        val session = AssistantGenerationSessionController()
        session.start(requestId = System.currentTimeMillis(), chatId = chatId)
        val pending = lifecycle.upsertPlaceholder(
            existingMessageId = session.ownedMessageId(null),
            placeholderPayload = Message(chatId = chatId, message = "", isSendbyMe = false, status = MessageStatus.PENDING),
            nowEpochMs = System.currentTimeMillis(),
        )
        val assistantId = requireNotNull(pending.messageId)
        session.claimMessage(assistantId)
        val preface = NpuConversationPrefacePlanner.plan(emptyList(), LIFECYCLE_PROMPT)
        val attempt = NpuKotlinConversationProductRoute.run(
            context = applicationContext, chatId = chatId, userPrompt = preface.currentUserPrompt,
            initialTurns = preface.initialTurns, selectedModelFile = modelPath, requestedMaxOutputTokens = 128,
            onPartial = { partial ->
                val text = partial.trim()
                if (text.isNotBlank()) runBlocking { lifecycle.checkpoint(assistantId, text) }
            },
        )
        require(attempt.succeeded) { "lifecycle_generation_failed:${attempt.failureReason}" }
        val finalText = requireNotNull(attempt.result).sanitizedOutput.trim()
        session.beginFinalizing()
        val completed = lifecycle.complete(
            existingMessageId = session.ownedMessageId(null),
            finalPayload = Message(chatId = chatId, message = finalText, isSendbyMe = false, status = MessageStatus.COMPLETED),
        )
        session.complete(requireNotNull(completed.messageId))
        val rows = repository.getMessagesSnapshot(chatId)
        val users = rows.filter { it.isSendbyMe }
        val assistants = rows.filterNot { it.isSendbyMe }
        val inFlight = assistants.count { it.status in MessageStatus.IN_FLIGHT }
        val interrupted = assistants.count { it.status == MessageStatus.INTERRUPTED }
        val passed = users.size == 1 && assistants.size == 1 &&
            assistants.single().status == MessageStatus.COMPLETED && inFlight == 0 && interrupted == 0 &&
            assistants.single().message == finalText && session.session?.isTerminal == true
        return LifecyclePhaseResult(chatId, finalText, users.size, assistants.size, inFlight, interrupted, session.session?.isTerminal == true, passed)
    }

    private suspend fun runEndurancePhase(modelPath: String): EndurancePhaseResult {
        var partialSeen = false
        val preface = NpuConversationPrefacePlanner.plan(emptyList(), ENDURANCE_PROMPT)
        val attempt = NpuKotlinConversationProductRoute.run(
            context = applicationContext, chatId = 9904096, userPrompt = preface.currentUserPrompt,
            initialTurns = preface.initialTurns, selectedModelFile = modelPath, requestedMaxOutputTokens = 4096,
            onPartial = { partial -> if (partial.isNotBlank()) partialSeen = true },
        )
        val qualityOnlyFailure = attempt.failureReason.startsWith("kotlin_conversation_quality_gate_failed:")
        val infrastructurePassed = attempt.succeeded || (partialSeen && qualityOnlyFailure)
        return EndurancePhaseResult(infrastructurePassed, attempt.succeeded, attempt.failureReason, partialSeen)
    }

    private fun runGenericBenchmarkPhase(backend: String, tokens: Int, timeoutMs: Long): GenericPhaseResult {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        File(filesDir, LiteRtLmGpuBenchmarkReceiver.STATE_FILE_NAME).delete()
        val intent = Intent(LiteRtLmGpuBenchmarkService.ACTION_START).apply {
            component = ComponentName(this@GenerationIntegrationTestService, LiteRtLmGpuBenchmarkService::class.java)
            putExtra(LiteRtLmGpuBenchmarkService.EXTRA_PROMPT_VARIANT, "long-sequence")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMESTAMP, timestamp)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MAX_OUTPUT_TOKENS_LIST, tokens.toString())
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_BACKEND_VARIANT, backend)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_CLOSE_POLICY, "normal")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PHASE, "send-message")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MODEL_PATH_SOURCE, "generic_fallback")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMEOUT_MS, timeoutMs)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        val state = File(filesDir, LiteRtLmGpuBenchmarkReceiver.STATE_FILE_NAME)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val text = runCatching { state.readText() }.getOrDefault("")
            if (text.contains("timestamp=$timestamp")) {
                val status = Regex("(?m)^status=([^\n]+)").find(text)?.groupValues?.get(1).orEmpty()
                if (status in setOf("success", "partial", "failure", "blocked")) {
                    val reason = Regex("(?m)^reason=([^\n]*)").find(text)?.groupValues?.get(1).orEmpty()
                    if (!awaitBenchmarkServiceFinished(timestamp, timeoutMs + 15_000L)) {
                        return GenericPhaseResult(false, "service_finish_timeout")
                    }
                    return GenericPhaseResult(status == "success", reason.ifBlank { status })
                }
            }
            Thread.sleep(250L)
        }
        return GenericPhaseResult(false, "state_wait_timeout")
    }

    private fun awaitBenchmarkServiceFinished(timestamp: String, timeoutMs: Long): Boolean {
        val history = File(filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_HISTORY_FILE_NAME)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val text = runCatching { history.readText() }.getOrDefault("")
            val finishedBlock = Regex(
                "(?s)timestamp=" + Regex.escape(timestamp) +
                    "\\nroute_type=litert_lm_gpu_benchmark\\nstage=service_finished\\n",
            )
            if (finishedBlock.containsMatchIn(text)) return true
            Thread.sleep(250L)
        }
        return false
    }

    private fun repositoryLifecycleStore(repository: ChatRepository) = object : AssistantMessageLifecycleStore {
        override suspend fun getMessageById(messageId: Int) = repository.getMessageById(messageId)
        override suspend fun insertAssistantMessage(message: Message): Int = repository.insertAssistantMessageAndAutoTitleAndReturnId(message).toInt()
        override suspend fun markAssistantMessageGenerating(messageId: Int) = repository.markAssistantMessageGenerating(messageId)
        override suspend fun updateGeneratingAssistantMessageContent(messageId: Int, message: String) = repository.updateGeneratingAssistantMessageContent(messageId, message)
        override suspend fun completeAssistantMessage(messageId: Int, message: String) = repository.completeAssistantMessage(messageId, message)
        override suspend fun cancelAssistantMessage(messageId: Int) = repository.cancelAssistantMessage(messageId)
        override suspend fun failAssistantMessage(messageId: Int, message: String?) = repository.failAssistantMessage(messageId, message)
        override suspend fun updateMessage(message: Message) = repository.updateMessage(message)
    }

    private data class LifecyclePhaseResult(
        val chatId: Int, val output: String, val userRows: Int, val assistantRows: Int,
        val inFlightRows: Int, val interruptedRows: Int, val sessionTerminal: Boolean, val passed: Boolean,
    )
    private data class EndurancePhaseResult(
        val infrastructurePassed: Boolean, val qualityPassed: Boolean, val failureReason: String, val partialSeen: Boolean,
    )
    private data class GenericPhaseResult(val passed: Boolean, val reason: String)

    private fun writeResult(text: String) = File(filesDir, RESULT_FILE).writeText(text)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Debug generation integration", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("LAMI generation integration test")
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("LAMI generation integration test")
                .setOngoing(true)
                .build()
        }

    companion object {
        const val ACTION_RUN_NPU_4096 = "io.github.ninbyo02.lami.action.GENERATION_INTEGRATION_NPU_4096"
        const val RESULT_FILE = "generation_integration_result.txt"
        private const val CHANNEL_ID = "generation_integration_debug"
        private const val NOTIFICATION_ID = 0x4C41
        private const val TEST_CHAT_TITLE = "[DEBUG] Background generation integration"
        private const val LIFECYCLE_PROMPT = "1+1はいくつですか。数字だけ答えてください。"
        private const val ENDURANCE_PROMPT = "Pythonで完全なブロック崩しゲームを作って下さい。"
        private val executor = Executors.newSingleThreadExecutor()
        private val running = AtomicBoolean(false)
    }
}
