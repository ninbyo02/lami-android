package io.github.ninbyo02.lami.ui.screens.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class NpuExperimentProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROBE_DISPATCH && intent.action != ACTION_BACKEND_NPU_ATTACH_PROBE) return
        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                if (intent.action == ACTION_BACKEND_NPU_ATTACH_PROBE) {
                    val runId = intent.getStringExtra("run_id").orEmpty()
                    NpuExperimentProbeLogger.runBackendNpuAttachProbe(
                        context = context.applicationContext,
                        runId = runId,
                        phase = intent.getStringExtra("phase").orEmpty(),
                        modelPath = intent.getStringExtra("model_path"),
                        engineConfigVariant = intent.getStringExtra("engine_config_variant"),
                        engineInitializeOptIn = intent.getBooleanExtra("run_engine_initialize_dry_run", false),
                        engineInitializeDiagnosticFilesClearedBeforeRun =
                            intent.getBooleanExtra("diagnostic_files_cleared_before_run", false),
                    )
                } else {
                    NpuExperimentProbeLogger.logSnapshot(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val ACTION_PROBE_DISPATCH = "io.github.ninbyo02.lami.npu.PROBE_DISPATCH"
        const val ACTION_BACKEND_NPU_ATTACH_PROBE = "io.github.ninbyo02.lami.npu.BACKEND_NPU_ATTACH_PROBE"
        val EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "backend-npu-attach-probe").apply {
                isDaemon = true
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, throwable ->
                    android.util.Log.e("NpuExperimentProbe", "receiver probe failed", throwable)
                }
            }
        }

    }
}
