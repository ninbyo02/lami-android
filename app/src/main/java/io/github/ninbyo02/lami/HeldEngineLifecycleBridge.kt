package io.github.ninbyo02.lami

import android.content.ComponentCallbacks2
import android.os.SystemClock
import io.github.ninbyo02.lami.ui.screens.home.LocalInferenceEngineHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class HeldEngineLifecycleBridge(
    private val holder: LocalInferenceEngineHolder,
) {
    fun onStart(scope: CoroutineScope) {
        scope.launch {
            holder.notifyAppForegrounded(nowElapsedMs = SystemClock.elapsedRealtime())
        }
    }

    fun onStop(scope: CoroutineScope) {
        scope.launch {
            holder.notifyAppBackgrounded(nowElapsedMs = SystemClock.elapsedRealtime())
        }
    }

    fun onTrimMemory(scope: CoroutineScope, level: Int) {
        if (!isCriticalTrimLevel(level)) return
        notifyLowMemory(scope)
    }

    fun onLowMemory(scope: CoroutineScope) {
        notifyLowMemory(scope)
    }

    private fun notifyLowMemory(scope: CoroutineScope) {
        scope.launch {
            holder.notifyLifecycleEvent(reason = LOW_MEMORY_REASON)
        }
    }

    @Suppress("DEPRECATION")
    private fun isCriticalTrimLevel(level: Int): Boolean {
        return level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
    }

    private companion object {
        const val LOW_MEMORY_REASON = "low-memory"
    }
}
