package io.github.ninbyo02.lami

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

internal object StandardDebugLogcatProbe {
    private val logged = AtomicBoolean(false)

    @JvmStatic
    fun logStarted() {
        if (!logged.compareAndSet(false, true)) return
        Log.i("LamiNpuEngine", "event=standard_debug_probe_started")
    }
}
