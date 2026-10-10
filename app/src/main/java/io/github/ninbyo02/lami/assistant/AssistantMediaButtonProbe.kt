package io.github.ninbyo02.lami.assistant

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/** Foreground-only opt-in diagnostic. Does not request playback or background media ownership. */
class AssistantMediaButtonProbe(private val context: Context) {
    private var session: MediaSession? = null
    private var downAt: Long? = null

    fun observeActivityKey(event: KeyEvent) {
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            Log.i("LamiAssistantButton", "activityKey=${event.keyCode} action=${event.action} repeat=${event.repeatCount}")
        }
    }

    fun start() {
        if (session != null) return
        session = MediaSession(context, "LAMI-Assistant-Button-Probe").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return false
                    if (event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                        event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK) return false
                    Log.i("LamiAssistantButton", "key=${event.keyCode} action=${event.action} repeat=${event.repeatCount}")
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        downAt = SystemClock.elapsedRealtime()
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        val duration = downAt?.let { SystemClock.elapsedRealtime() - it } ?: 0L
                        downAt = null
                        Log.i("LamiAssistantButton", "durationMs=$duration (diagnostic only)")
                    }
                    // Preserve regular playback and headset controls; never consume a key.
                    return false
                }
            })
            isActive = true
        }
    }

    fun stop() {
        session?.isActive = false
        session?.release()
        session = null
        downAt = null
    }
}
