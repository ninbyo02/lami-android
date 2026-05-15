package io.github.ninbyo02.lami.ui.screens.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NpuExperimentProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROBE_DISPATCH) return
        NpuExperimentProbeLogger.logSnapshot(context.applicationContext)
    }

    private companion object {
        const val ACTION_PROBE_DISPATCH = "io.github.ninbyo02.lami.npu.PROBE_DISPATCH"
    }
}
