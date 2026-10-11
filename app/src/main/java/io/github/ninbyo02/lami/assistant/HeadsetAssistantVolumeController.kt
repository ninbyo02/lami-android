package io.github.ninbyo02.lami.assistant

import android.app.Activity
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import java.io.File

/** Keeps headset volume keys scoped to LAMI while the assistant activity is visible. */
class HeadsetAssistantVolumeController(private val activity: Activity) {
    private val audio = activity.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val keys = AssistantVolumeKeyPolicy()
    private val idleVoice = IdleHeadsetVolumePolicy()
    private val settlement = HeadsetVolumeSettlementPolicy()
    private var previous: IdleHeadsetVolumeSnapshot? = null
    private var active = false
    private val monitor = object : Runnable {
        override fun run() {
            if (!active) return
            try {
                val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                val pioneer = outputs.singleOrNull()
                    ?.takeIf { it.productName.toString().contains("SE-MS7BT", ignoreCase = true) }
                val now = SystemClock.elapsedRealtime()
                val assistantBusy = HeadsetVolumeAnnouncements.conversationActive ||
                    HeadsetVolumeAnnouncements.assistantWorkActive
                val snapshot = IdleHeadsetVolumeSnapshot(
                    routeId = pioneer?.id,
                    eligible = pioneer != null && audio.mode == AudioManager.MODE_NORMAL &&
                        acceptsIdleHeadsetInput(
                            audio.isMusicActive,
                            assistantBusy,
                            HeadsetVolumeAnnouncements.lastVolumeSpeechEndMs,
                            now,
                        ),
                    voiceVolume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                    mediaVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                )
                val old = previous
                if (old != null && old.routeId == snapshot.routeId && pioneer != null &&
                    (old.mediaVolume != snapshot.mediaVolume || old.voiceVolume != snapshot.voiceVolume)
                ) {
                    record("native_volume_observed voice=${snapshot.voiceVolume} " +
                        "media=${snapshot.mediaVolume} correction=false")
                }
                // HFP and AVRCP can alternate. They share one burst latch and one logical
                // level; repeats and echoes must not produce another step or continuous writes.
                if (snapshot.routeId == null && !settlement.isActive) settlement.reset()
                val pendingBefore = settlement.isActive
                val target = idleVoice.update(
                    snapshot,
                    now,
                    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    HeadsetVolumeAnnouncements.conversationActive || pendingBefore,
                )
                if (pendingBefore) {
                    when (val action = settlement.update(
                        snapshot,
                        HeadsetVolumeAnnouncements.conversationActive,
                        audio.mode != AudioManager.MODE_NORMAL || HeadsetVolumeAnnouncements.assistantWorkActive,
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        now,
                    )) {
                        is HeadsetVolumeSettlementAction.Apply -> {
                            val actual = applyVolume(action.target, "settle", announce = false)
                            settlement.applied(actual, now, action.repair)
                            record("settle_write repair=${action.repair}")
                        }
                        is HeadsetVolumeSettlementAction.Confirm -> {
                            record("settle_confirmed target=${action.target} actual=${action.actual}")
                            announceVolume(action.target, HeadsetVolumeFeedbackPhase.SETTLED)
                        }
                        is HeadsetVolumeSettlementAction.Cancel -> {
                            record("settle_cancelled reason=${action.reason}")
                            if (action.reason == "volume_did_not_settle" || action.reason == "timeout") {
                                announceVolume(snapshot.mediaVolume, HeadsetVolumeFeedbackPhase.FAILED)
                            }
                        }
                        null -> Unit
                    }
                } else if (target != null && settlement.begin(snapshot, target, now)) {
                    record("settle_started target=$target cue=${settlement.releaseCueRequired}")
                    if (settlement.releaseCueRequired) {
                        announceVolume(target, HeadsetVolumeFeedbackPhase.RELEASE_CUE)
                    }
                }
                previous = snapshot
            } catch (error: RuntimeException) {
                record("idle_monitor_disabled error=${error.javaClass.simpleName}")
                return
            }
            handler.postDelayed(this, if (settlement.isActive) 100L else 200L)
        }
    }

    fun activate() {
        if (active) return
        active = true
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
        keys.reset()
        idleVoice.reset()
        settlement.reset()
        previous = null
        handler.post(monitor)
        record("activated stream=music bounded_settlement=true anchored_headset_steps=true key_steps=true")
    }

    fun deactivate() {
        active = false
        handler.removeCallbacks(monitor)
        keys.reset()
        idleVoice.reset()
        settlement.reset()
        previous = null
        activity.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
    }

    fun observeKey(event: KeyEvent): Boolean {
        if (!active || event.keyCode !in VOLUME_KEYS) return false
        try {
            if (event.action == KeyEvent.ACTION_UP && keys.isPressed(event.keyCode)) {
                keys.release(event.keyCode)
                record("key=${event.keyCode} action=up consumed=true")
                return true
            }
            val pioneer = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                    it.productName.toString().contains("SE-MS7BT", ignoreCase = true)
            }
            val busy = HeadsetVolumeAnnouncements.conversationActive || settlement.isActive
            if (!pioneer || (!busy && !keys.isPressed(event.keyCode) &&
                    (audio.mode != AudioManager.MODE_NORMAL || audio.isMusicActive))) return false
            if (event.action != KeyEvent.ACTION_DOWN) return keys.isPressed(event.keyCode)
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = keys.press(
                event.keyCode,
                event.repeatCount > 0,
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1,
                idleVoice.commandBaseline(audio.getStreamVolume(AudioManager.STREAM_MUSIC)),
                maxVolume,
                busy,
            )
            record("key=${event.keyCode} action=down repeat=${event.repeatCount} " +
                "device=${event.deviceId}:${event.device?.name} " +
                "blocked=$busy target=$target consumed=true")
            if (target != null) {
                applyVolume(target, "key")
            }
            return true
        } catch (error: RuntimeException) {
            record("key_volume_failed error=${error.javaClass.simpleName}")
            return true
        }
    }

    private fun applyVolume(target: Int, source: String, announce: Boolean = true): Int {
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (before != target) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        }
        val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        idleVoice.acknowledgeMediaAdjustment(actual)
        if (announce) announceVolume(actual, HeadsetVolumeFeedbackPhase.NORMAL)
        record("${source}_volume_applied media=$before->$actual")
        return actual
    }

    private fun announceVolume(volume: Int, phase: HeadsetVolumeFeedbackPhase) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val percent = (volume * 100.0 / max).toInt().coerceIn(0, 100)
        HeadsetVolumeAnnouncements.publish(
            HeadsetVolumeAnnouncement(percent, SystemClock.elapsedRealtime(), phase),
        )
        record("feedback_request phase=$phase percent=$percent")
    }

    private fun record(message: String) {
        Log.i(LOG_TAG, message)
        runCatching {
            val file = File(activity.filesDir, "headset-assistant-volume.txt")
            if (file.length() > 32_768) file.writeText("")
            file.appendText("${SystemClock.elapsedRealtime()} $message\n")
        }
    }

    private companion object {
        const val LOG_TAG = "LamiAssistantVolume"
        val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
    }
}
