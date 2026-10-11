package io.github.ninbyo02.lami.assistant

internal class BluetoothStandbyWindow(startedAt: Long, requestedMinutes: Int) {
    val minutes = requestedMinutes.takeIf { it in durations } ?: DEFAULT_MINUTES
    val deadline = startedAt + minutes * 60_000L
    private var lastLaunchAt: Long? = null

    fun remaining(now: Long): Long = (deadline - now).coerceAtLeast(0)
    fun canLaunch(now: Long): Boolean = remaining(now) > 0 &&
        (lastLaunchAt?.let { now - it >= 1500 } ?: true)
    fun launched(now: Long) { lastLaunchAt = now }

    companion object {
        const val DEFAULT_MINUTES = 30
        val durations = listOf(5, 30, 60)
    }
}
