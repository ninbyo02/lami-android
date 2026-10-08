package io.github.ninbyo02.lami.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Explicit debug test with a visible stop action and a ten-minute bound. */
class LamiNeuralVoiceDiagnosticService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        running = true
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("lami_voice_probe", "LAMI voice test", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "lami_voice_probe")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("LAMI voice test")
            .setContentText("Generating speech on this phone")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        startForeground(2708, notification)
        val text = intent?.getStringExtra("lami_neural_tts_text_probe")
        val pipelineTexts = intent?.getStringArrayListExtra("lami_neural_tts_pipeline_texts")
        val serial = intent?.getBooleanExtra("lami_neural_tts_serial_baseline", false) ?: false
        scope.launch {
            try {
                if (pipelineTexts != null && intent.getBooleanExtra("lami_neural_tts_streaming_probe", false)) LamiNeuralVoiceDiagnostics.runStreamingProbe(applicationContext, pipelineTexts)
                else if (pipelineTexts != null) LamiNeuralVoiceDiagnostics.runPipelineProbe(applicationContext, pipelineTexts, serial, !(intent?.getBooleanExtra("lami_neural_tts_cp_allocation_baseline", false) ?: false), intent?.getBooleanExtra("lami_neural_tts_prefix_decode_probe", false) ?: false, intent?.getBooleanExtra("lami_neural_tts_pcm_thread_probe", false) ?: false, intent?.getBooleanExtra("lami_neural_tts_gpu_decoder_probe", false) ?: false)
                else LamiNeuralVoiceDiagnostics.runProbe(applicationContext, text)
            }
            finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
