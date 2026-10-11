package io.github.ninbyo02.lami.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** Five-minute, explicitly approved experiment; never keeps the screen on. */
internal class JabraUsbTrialGuard(context: Context, onExpired: () -> Unit) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:JabraUsbTrial")
        .apply { setReferenceCounted(false) }
    private val expire = Runnable {
        close()
        onExpired()
    }
    val held get() = wakeLock.isHeld

    @Synchronized fun start() {
        check(!held) { "Trial already started" }
        wakeLock.acquire(DURATION_MS)
        handler.postDelayed(expire, DURATION_MS)
    }

    @Synchronized override fun close() {
        handler.removeCallbacks(expire)
        if (held) wakeLock.release()
    }

    companion object {
        const val DURATION_MS = 300_000L
    }
}
