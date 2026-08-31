package io.github.ninbyo02.lami

import android.content.ComponentCallbacks2
import android.os.SystemClock
import io.github.ninbyo02.lami.ui.screens.home.LocalInferenceEngineHolder
import io.github.ninbyo02.lami.ui.screens.home.NpuConversationLifecycle
import io.github.ninbyo02.lami.ui.screens.home.NpuKotlinConversationProductRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class HeldEngineLifecycleBridge(
    private val holder: LocalInferenceEngineHolder,
    private val npuLifecycle: NpuConversationLifecycle = NpuKotlinConversationProductRoute,
) {
    fun onStart(scope: CoroutineScope) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        scope.launch { holder.notifyAppForegrounded(nowElapsedMs = nowElapsedMs) }
        scope.launch { npuLifecycle.notifyAppForegrounded(nowElapsedMs = nowElapsedMs) }
    }

    fun onStop(scope: CoroutineScope) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        scope.launch { holder.notifyAppBackgrounded(nowElapsedMs = nowElapsedMs) }
        scope.launch { npuLifecycle.notifyAppBackgrounded(nowElapsedMs = nowElapsedMs) }
    }

    fun onTrimMemory(scope: CoroutineScope, level: Int) {
        if (!isCriticalTrimLevel(level)) return
        notifyLowMemory(scope)
    }

    fun onLowMemory(scope: CoroutineScope) {
        notifyLowMemory(scope)
    }

    private fun notifyLowMemory(scope: CoroutineScope) {
        scope.launch { holder.notifyLifecycleEvent(reason = LOW_MEMORY_REASON) }
        scope.launch { npuLifecycle.notifyLowMemory() }
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
