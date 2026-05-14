package io.github.ninbyo02.lami.debug

import android.app.Activity
import android.os.Bundle

class QairtNpuProbeActivity : Activity() {
    private lateinit var runner: QairtNpuProbeRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        runner = QairtNpuProbeRunner(this)
        val previousRun = runner.readPreviousRunSnapshot()
        runner.marker(stage = "activity-onCreate", intent = intent, append = false)
        runner.reportPreviousRunIfIncomplete(previousRun)
        try {
            super.onCreate(savedInstanceState)
            runner.startProbeThread(intent = intent, completionStage = "activity-finish") {
                runOnUiThread {
                    finish()
                }
            }
        } catch (throwable: Throwable) {
            runner.logFailure(stage = "activity-onCreate", throwable = throwable)
            finish()
        }
    }
}
