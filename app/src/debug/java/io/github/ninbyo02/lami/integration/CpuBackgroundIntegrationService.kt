package io.github.ninbyo02.lami.integration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.LocalConversationPolicy
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalApi::class)
class CpuBackgroundIntegrationService : Service() {
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NOTIFICATION_ID, notification()) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT || intent?.action != ACTION_RUN || !running.compareAndSet(false, true)) { stopSelf(startId); return START_NOT_STICKY }
        executor.execute {
            try { runBlocking { runCpu() } }
            catch (t: Throwable) { writeResult("status=failure\nreason=exception\nexception=${t.javaClass.name}:${t.message.orEmpty()}\n") }
            finally { running.set(false); stopSelf(startId) }
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }

    private suspend fun runCpu() {
        val started = SystemClock.elapsedRealtime()
        writeResult("status=running\nbackend=CPU\nconfig=cpu_product_default\n")
        val modelPath = SettingsPreferences(applicationContext).getValidLocalGenericModelPathOrNull().orEmpty()
        val model = File(modelPath)
        require(model.isFile && model.canRead() && model.length() > 0L) { "generic_model_missing" }
        var engine: Engine? = null
        var conversation: Conversation? = null
        try {
            val engineStarted = SystemClock.elapsedRealtime()
            engine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.GPU(), audioBackend = Backend.CPU(), maxNumTokens = null, cacheDir = File(cacheDir, "cpu_integration").absolutePath))
            engine.initialize()
            val engineMs = SystemClock.elapsedRealtime() - engineStarted
            val conversationStarted = SystemClock.elapsedRealtime()
            conversation = engine.createConversation(LocalConversationPolicy.conversationConfig())
            val conversationMs = SystemClock.elapsedRealtime() - conversationStarted
            val sendStarted = SystemClock.elapsedRealtime()
            val message = conversation.sendMessage(PROMPT)
            val sendMs = SystemClock.elapsedRealtime() - sendStarted
            val output = message.toString().trim()
            val benchmark = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
            val passed = output.isNotBlank()
            writeResult(buildString {
                appendLine("status=${if (passed) "success" else "failure"}")
                appendLine("backend=CPU")
                appendLine("config=cpu_product_default")
                appendLine("reason=${if (passed) "completed" else "blank_output"}")
                appendLine("engine_create_ms=$engineMs")
                appendLine("conversation_create_ms=$conversationMs")
                appendLine("send_ms=$sendMs")
                appendLine("output_chars=${output.length}")
                appendLine("output_tokens=${benchmark?.lastDecodeTokenCount ?: -1}")
                appendLine("tokens_per_second=${benchmark?.lastDecodeTokensPerSecond ?: -1.0}")
                appendLine("elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            })
        } finally { runCatching { conversation?.close() }; runCatching { engine?.close() } }
    }
    private fun writeResult(text: String) = File(filesDir, RESULT_FILE).writeText(text)
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Debug CPU integration", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("LAMI CPU integration test").setOngoing(true).build() else { @Suppress("DEPRECATION") Notification.Builder(this).setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("LAMI CPU integration test").setOngoing(true).build() }
    companion object {
        const val ACTION_RUN = "io.github.ninbyo02.lami.action.CPU_BACKGROUND_INTEGRATION"
        const val RESULT_FILE = "cpu_background_integration_result.txt"
        private const val CHANNEL_ID = "cpu_background_integration_debug"
        private const val NOTIFICATION_ID = 0x435055
        private const val PROMPT = "1+1はいくつですか。数字だけ答えてください。"
        private val running = AtomicBoolean(false)
        private val executor = Executors.newSingleThreadExecutor()
    }
}
