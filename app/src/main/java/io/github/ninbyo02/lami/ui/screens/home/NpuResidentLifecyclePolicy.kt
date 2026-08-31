package io.github.ninbyo02.lami.ui.screens.home

internal const val NPU_RESIDENT_BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
internal const val NPU_RESIDENT_IDLE_TIMEOUT_MS = 10 * 60 * 1000L

internal data class NpuResidentLifecycleState(
    val appInForeground: Boolean = true,
    val backgroundedAtElapsedMs: Long? = null,
    val lastUsedAtElapsedMs: Long? = null,
)

internal fun isNpuBackgroundReleaseDue(
    state: NpuResidentLifecycleState,
    nowElapsedMs: Long,
): Boolean = !state.appInForeground &&
    state.backgroundedAtElapsedMs?.let { nowElapsedMs - it >= NPU_RESIDENT_BACKGROUND_TIMEOUT_MS } == true

internal fun isNpuIdleReleaseDue(
    state: NpuResidentLifecycleState,
    nowElapsedMs: Long,
): Boolean = !state.appInForeground &&
    state.lastUsedAtElapsedMs?.let { nowElapsedMs - it >= NPU_RESIDENT_IDLE_TIMEOUT_MS } == true

internal interface NpuConversationLifecycle {
    suspend fun notifyAppForegrounded(nowElapsedMs: Long)
    suspend fun notifyAppBackgrounded(nowElapsedMs: Long)
    suspend fun notifyLowMemory()
}
