package io.github.ninbyo02.lami.debug

import android.app.Service
import android.content.Intent
import android.os.IBinder

class QairtNpuProbeService : Service() {
    private lateinit var runner: QairtNpuProbeRunner

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        runner = QairtNpuProbeRunner(this)
        runner.marker(stage = "onCreate-first-line", intent = null, append = false)
        try {
            super.onCreate()
            runner.stage("onCreate-after-super")
        } catch (throwable: Throwable) {
            runner.logFailure(stage = "onCreate", throwable = throwable)
            throw throwable
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runner.marker(stage = "onStartCommand-first-line", intent = intent, append = true)
        try {
            runner.appendResultLine("startCommand flags=$flags startId=$startId")
            runner.startProbeThread(intent = intent, completionStage = "service-stop") {
                stopSelf(startId)
            }
        } catch (throwable: Throwable) {
            runner.logFailure(stage = "onStartCommand", throwable = throwable)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
