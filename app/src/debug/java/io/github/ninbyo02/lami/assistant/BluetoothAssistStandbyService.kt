package io.github.ninbyo02.lami.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Explicit bounded standby; no silent playback, microphone, or automatic restart. */
class BluetoothAssistStandbyService : Service() {
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val handler by lazy { Handler(mainLooper) }
    private var session: MediaSession? = null
    private var tone: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var armed = false
    private var window: BluetoothStandbyWindow? = null
    private var targetId = -1
    private val policy = BluetoothAssistKeyPolicy()
    private val diagnostics = ArrayDeque<String>()
    private var playbackRegistered = false
    private val playbackMonitor = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            if (standbyRunning && armed) audioInterruption()?.let(::end)
        }
    }
    private val monitor = object : Runnable {
        override fun run() {
            if (session == null) return
            val reason = when {
                window?.remaining(SystemClock.elapsedRealtime()) == 0L -> "待機時間が終了しました"
                targetDevice()?.id != targetId -> "Pioneerの接続が切れました"
                !isDefaultAssistant() -> "既定アシスタントが変更されました"
                else -> audioInterruption()
            }
            if (reason != null) end(reason)
            else {
                remainingMillis = window?.remaining(SystemClock.elapsedRealtime()) ?: 0
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            end("停止中")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || !packageName.endsWith(".assistantprobe")) {
            stopSelf()
            return START_NOT_STICKY
        }
        showNotification()
        if (session != null) return START_NOT_STICKY
        val target = targetDevice()
        record("Outputs=" + audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .joinToString { "${it.type}:${it.productName}:${it.id}" })
        if (target == null || audio.isMusicActive || audio.mode != AudioManager.MODE_NORMAL ||
            !isDefaultAssistant()) {
            end(when {
                target == null -> "Pioneerが接続されていません"
                audio.mode != AudioManager.MODE_NORMAL -> "通話中は待機を開始できません"
                audio.isMusicActive -> "音楽・音声再生中は待機を開始できません"
                else -> "LAMIを既定アシスタントに設定してください"
            })
            return START_NOT_STICKY
        }
        targetId = target.id
        window = BluetoothStandbyWindow(SystemClock.elapsedRealtime(),
            intent.getIntExtra(EXTRA_MINUTES, BluetoothStandbyWindow.DEFAULT_MINUTES))
        remainingMillis = window!!.remaining(SystemClock.elapsedRealtime())
        standbyRunning = true
        AssistantMediaButtonProbe.bluetoothStandbyActive = true
        session = MediaSession(this, "LAMI-Bluetooth-Standby").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        ?: return false
                    val eligible = eligible()
                    val result = policy.handle(event, ownsButton())
                    record("key=${event.keyCode} action=${event.action} repeat=${event.repeatCount} " +
                        "device=${event.deviceId}:${event.device?.name} eligible=$eligible " +
                        "consume=${result.consume} launch=${result.launch}")
                    if (result.launch && eligible) launch("Short media press")
                    return result.consume
                }

                override fun onPlay() {
                    record("Transport onPlay eligible=${eligible()}")
                    if (eligible()) launch("Transport play")
                }

                override fun onPause() { record("Transport pause; not an assist trigger") }
            }, handler)
            setPlaybackState(PlaybackState.Builder().setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
            isActive = true
        }
        try {
            playConfirmation(target)
            audio.registerAudioPlaybackCallback(playbackMonitor, handler)
            playbackRegistered = true
            handler.post(monitor)
        } catch (error: Exception) {
            end("確認音を再生できませんでした: ${error.message}")
        }
        return START_NOT_STICKY
    }

    private fun targetDevice(): AudioDeviceInfo? = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
            it.productName.toString().contains("SE-MS7BT", ignoreCase = true) }

    private fun isDefaultAssistant(): Boolean = Settings.Secure.getString(contentResolver, "assistant")
        ?.let(ComponentName::unflattenFromString) == ComponentName(this, AssistantEntryActivity::class.java)

    private fun assistantActive(): Boolean = HeadsetVolumeAnnouncements.conversationActive ||
        HeadsetVolumeAnnouncements.assistantWorkActive || AssistantVoiceEvents.phase.value != AssistantVoicePhase.IDLE

    private fun audioInterruption(): String? = bluetoothStandbyAudioInterruption(
        audio.isMusicActive, audio.mode, assistantActive(), armed)

    private fun ownsButton(): Boolean = standbyRunning && armed &&
        (window?.remaining(SystemClock.elapsedRealtime()) ?: 0L) > 0L &&
        targetDevice()?.id == targetId && isDefaultAssistant() &&
        audioInterruption() == null

    private fun eligible(): Boolean = ownsButton() && !assistantActive() &&
        window?.canLaunch(SystemClock.elapsedRealtime()) == true

    private fun playConfirmation(target: AudioDeviceInfo) {
        statusText = "接続確認中"
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0) end("Audio focus lost")
            }, handler).build()
        check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Audio focus denied"
        }
        focus = request
        val rate = 24_000
        val samples = ShortArray(rate / 4) { i ->
            val envelope = min(1.0, min(i, rate / 4 - 1 - i) / 240.0)
            (sin(2 * PI * 660 * i / rate) * 4000 * envelope).toInt().toShort()
        }
        val track = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        tone = track
        check(track.setPreferredDevice(target)) { "Pioneer routing rejected" }
        track.setVolume(0.25f)
        check(track.write(samples, 0, samples.size) == samples.size) { "Tone write failed" }
        track.play()
        session?.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        record("Real confirmation started; preferred=${target.productName}")
        // Only the actual 250-ms sound is PLAYING; standby does not produce audio.
        handler.postDelayed({
            record("Confirmation route=${track.routedDevice?.productName}")
            releaseTone()
            if (session != null) {
                session?.setPlaybackState(PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PAUSED, 250, 0f).build())
                handler.postDelayed({
                    if (session != null && standbyRunning) {
                        armed = true
                        statusText = "待機中"
                        record("Armed; no audio playback or CPU wake lock")
                        showNotification()
                    }
                }, 500)
            }
        }, 500)
    }

    private fun releaseTone() {
        tone?.let { runCatching { it.stop() }; it.release() }
        tone = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun launch(source: String) {
        window?.launched(SystemClock.elapsedRealtime())
        record("Assist requested: $source")
        try {
            AssistantLauncher.launchAssist(this)
            record("Standby retained after assist request")
        } catch (error: Exception) {
            end("LAMIを開けませんでした: ${error.message}")
        }
    }

    private fun end(reason: String) {
        armed = false
        standbyRunning = false
        remainingMillis = 0
        statusText = reason
        record(reason)
        stopSelf()
    }

    private fun showNotification() {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, "LAMI Bluetooth待機", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 51,
            Intent(this, javaClass).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val setup = PendingIntent.getActivity(this, 52,
            Intent(this, BluetoothAssistSetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(51, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("LAMI Bluetooth待機")
            .setContentText("Pioneer / ${window?.minutes ?: BluetoothStandbyWindow.DEFAULT_MINUTES}分")
            .setContentIntent(setup).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun record(value: String) {
        if (diagnostics.size == 64) diagnostics.removeFirst()
        diagnostics.addLast("${SystemClock.elapsedRealtime()} $value")
        openFileOutput("bluetooth-assist-buttons.txt", MODE_PRIVATE).bufferedWriter().use { writer ->
            diagnostics.forEach(writer::appendLine)
        }
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("BluetoothStandby armed=$armed remainingMs=${window?.remaining(SystemClock.elapsedRealtime())}")
        diagnostics.forEach(writer::println)
    }

    override fun onDestroy() {
        armed = false
        standbyRunning = false
        remainingMillis = 0
        AssistantMediaButtonProbe.bluetoothStandbyActive = false
        if (playbackRegistered) audio.unregisterAudioPlaybackCallback(playbackMonitor)
        handler.removeCallbacksAndMessages(null)
        releaseTone()
        session?.release()
        session = null
        if (statusText == "待機中" || statusText == "接続確認中") {
            statusText = "停止中"
            record("Stopped")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.github.ninbyo02.lami.assistant.BLUETOOTH_STANDBY_START"
        const val ACTION_STOP = "io.github.ninbyo02.lami.assistant.BLUETOOTH_STANDBY_STOP"
        const val EXTRA_MINUTES = "standby_minutes"
        private const val CHANNEL = "lami_bluetooth_standby"
        @Volatile var standbyRunning = false
            private set
        @Volatile var remainingMillis = 0L
            private set
        @Volatile var statusText = "停止中"
            private set
    }
}
