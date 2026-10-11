package io.github.ninbyo02.lami.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import java.io.FileDescriptor
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import kotlin.math.roundToInt

/** Opt-in debug prototype: owns only the verified HID interface, never USB audio. */
class JabraUsbButtonService : Service() {
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    @Volatile private var running = false
    @Volatile private var requests = emptyList<UsbRequest>()
    @Volatile private var trialEnded = false
    private val trial by lazy { JabraUsbTrialGuard(this) {
        trialEnded = true
        running = false
        requests.forEach { it.cancel() }
    } }
    private var worker: Thread? = null
    private var cpuTrialEnabled = false
    private val diagnostics = ArrayDeque<String>()
    private val policy = JabraAssistKeyPolicy()
    private val centerPolicy = JabraUsbCenterPolicy()
    private var lastCenterLaunchAtMs = -1L
    private val volumePolicy = JabraUsbVolumePolicy()
    private var lastVolumeCommandAtMs = -1L
    private val downTimes = mutableMapOf<Int, Long>()
    private val consumed = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            requests.forEach { it.cancel() }
            if (worker == null) stopSelf()
            return START_NOT_STICKY
        }
        if (!packageName.endsWith(".assistantprobe")) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker != null) return START_NOT_STICKY
        cpuTrialEnabled = intent?.getBooleanExtra(EXTRA_CPU_TRIAL, false) == true
        statusText = "Connecting"
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, "Jabra USB", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2,
            Intent(this, javaClass).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val setup = PendingIntent.getActivity(this, 3,
            Intent(this, JabraUsbSetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(42, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("LAMI Jabra USB")
            .setContentText(if (cpuTrialEnabled) "USB trial (5 min)" else "Connected")
            .setContentIntent(setup).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stop).build()).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        running = true
        worker = Thread({ readButtons() }, "LamiJabraUsb").also { it.start() }
        return START_NOT_STICKY
    }

    private fun readButtons() {
        try {
            val manager = getSystemService(UsbManager::class.java)
            val target = manager.deviceList.values.single {
                isJabraAssistDevice(it.vendorId, it.productId)
            }
            check(manager.hasPermission(target)) { "USB permission required" }
            val intf = (0 until target.interfaceCount).map(target::getInterface)
                .single { it.id == 3 && it.interfaceClass == UsbConstants.USB_CLASS_HID }
            val endpoint = (0 until intf.endpointCount).map(intf::getEndpoint).single {
                it.address == 0x81 && it.type == UsbConstants.USB_ENDPOINT_XFER_INT
            }
            val connection = checkNotNull(manager.openDevice(target)) { "USB open failed" }
            var claimed = false
            val inputs = List(2) { UsbRequest() }
            try {
                claimed = connection.claimInterface(intf, true)
                check(claimed) { "USB claim failed" }
                val descriptor = ByteArray(4096)
                val count = connection.controlTransfer(0x81, 0x06, 0x2200, intf.id,
                    descriptor, descriptor.size, 1500)
                check(count > 0 && matchesJabraDescriptor(descriptor.copyOf(count))) {
                    "Unverified HID descriptor"
                }
                inputs.forEach { check(it.initialize(connection, endpoint)) { "USB request failed" } }
                requests = inputs
                val decoder = JabraUsbReports()
                val buffers = inputs.associateWith { ByteBuffer.allocateDirect(endpoint.maxPacketSize) }
                if (cpuTrialEnabled) trial.start()
                inputs.forEach { check(it.queue(buffers.getValue(it))) { "USB queue failed" } }
                record("Connected HID 3; audio untouched; cpuTrial=$cpuTrialEnabled")
                statusText = if (cpuTrialEnabled) "Connected (5 min)" else "Connected"
                while (running) {
                    check(audio.mode == AudioManager.MODE_NORMAL) { "Call mode: restoring system HID" }
                    val completed = try { connection.requestWait(1000) }
                        catch (_: TimeoutException) { continue }
                    if (!running) break
                    val buffer = checkNotNull(buffers[completed]) { "USB disconnected" }
                    val bytes = ByteArray(buffer.position())
                    buffer.flip()
                    buffer.get(bytes)
                    // Re-arm before any file/media calls; retain the 2-ms release pulse.
                    buffer.clear()
                    check(checkNotNull(completed).queue(buffer)) { "USB requeue failed" }
                    record("report=" + bytes.joinToString("") { "%02x".format(it.toInt() and 255) })
                    // Call controls remain with the system outside this media-only prototype.
                    check(audio.mode == AudioManager.MODE_NORMAL) { "Call mode: restoring system HID" }
                    for (key in decoder.decode(bytes)) dispatch(key)
                }
            } finally {
                requests = emptyList()
                inputs.forEach { it.cancel(); it.close() }
                try {
                    if (claimed) check(connection.releaseInterface(intf)) { "HID release failed" }
                } finally {
                    connection.close()
                }
            }
            record("Disconnected; system HID restored")
            statusText = if (trialEnded) "Trial ended" else "Disconnected"
        } catch (error: Exception) {
            statusText = "Stopped: ${error.message}"
            record(statusText)
        } finally {
            trial.close()
            // Do not leave a media key held if the cable is removed mid-press.
            downTimes.toMap().forEach { (code, time) ->
                if (code !in consumed && code != KeyEvent.KEYCODE_VOLUME_DOWN &&
                    code != KeyEvent.KEYCODE_VOLUME_UP) runCatching { forward(code, false, time) }
            }
            downTimes.clear()
            consumed.clear()
            policy.reset()
            centerPolicy.reset()
            centerMusicReserved = false
            volumePolicy.reset()
            running = false
            android.os.Handler(mainLooper).post { stopSelf() }
        }
    }

    private fun dispatch(key: JabraUsbKey) {
        val now = SystemClock.uptimeMillis()
        val time = if (key.down) now.also { downTimes[key.code] = it }
            else downTimes[key.code] ?: now
        val elapsed = SystemClock.elapsedRealtime()
        val busy = HeadsetVolumeAnnouncements.conversationActive ||
            HeadsetVolumeAnnouncements.assistantWorkActive || AssistantVoiceEvents.phase.value != AssistantVoicePhase.IDLE
        val volumeEligible = HeadsetVolumeAnnouncements.chatForeground &&
            audio.mode == AudioManager.MODE_NORMAL && (busy || acceptsIdleHeadsetInput(
                audio.isMusicActive, busy, HeadsetVolumeAnnouncements.lastVolumeSpeechEndMs, elapsed))
        val volumeResult = volumePolicy.handle(key.code, key.down, volumeEligible,
            busy || (lastVolumeCommandAtMs >= 0 && elapsed - lastVolumeCommandAtMs < 300L),
            audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        if (volumeResult.consume) {
            volumeResult.target?.let { target ->
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val percent = (actual * 100.0 / max).roundToInt().coerceIn(0, 100)
                val lastFeedback = maxOf(lastVolumeCommandAtMs,
                    HeadsetVolumeAnnouncements.lastVolumeSpeechEndMs)
                val followUp = lastVolumeCommandAtMs >= 0 && elapsed - lastFeedback in 0L..4_999L
                lastVolumeCommandAtMs = elapsed
                HeadsetVolumeAnnouncements.publish(HeadsetVolumeAnnouncement(percent, elapsed,
                    if (followUp) HeadsetVolumeFeedbackPhase.SETTLED else HeadsetVolumeFeedbackPhase.NORMAL))
                record("LAMI volume key=${key.code} target=$target actual=$actual percent=$percent followUp=$followUp")
            }
            record("LAMI volume key=${key.code} down=${key.down} blocked=$busy consume=true")
            if (!key.down) downTimes.remove(key.code)
            return
        }
        val assistant = Settings.Secure.getString(contentResolver, "assistant")
            ?.let(ComponentName::unflattenFromString)
        val eligible = assistant == ComponentName(this, AssistantEntryActivity::class.java) &&
            !audio.isMusicActive && audio.mode == AudioManager.MODE_NORMAL
        if (key.code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            val event = KeyEvent(time, now,
                if (key.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key.code, 0)
            val externalMusic = audio.isMusicActive && !busy &&
                elapsed - HeadsetVolumeAnnouncements.lastVolumeSpeechEndMs >= 600L
            val result = centerPolicy.handle(event,
                assistant == ComponentName(this, AssistantEntryActivity::class.java) &&
                    audio.mode == AudioManager.MODE_NORMAL && (!audio.isMusicActive || busy),
                externalMusic,
                !busy && (lastCenterLaunchAtMs < 0 || elapsed - lastCenterLaunchAtMs >= 1500L))
            centerMusicReserved = centerPolicy.musicReserved
            record("Center down=${key.down} eligible=$eligible busy=$busy " +
                "musicReserved=$centerMusicReserved consume=${result.consume} launch=${result.launch}")
            if (key.down && result.consume) consumed.add(key.code)
            if (!result.consume) forward(key.code, key.down, time)
            if (result.launch) {
                lastCenterLaunchAtMs = elapsed
                record("Assist launch; source=Jabra center")
                AssistantLauncher.launchAssist(this)
            }
            if (!key.down) {
                downTimes.remove(key.code)
                consumed.remove(key.code)
            }
            return
        }
        val result = policy.handle(key.code,
            if (key.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            0, 0, time, now, false, eligible)
        if (key.down && result.consume) consumed.add(key.code)
        if (!result.consume) forward(key.code, key.down, time)
        if (result.launch) {
            record("Assist launch; musicActive=${audio.isMusicActive}")
            AssistantLauncher.launchAssist(this)
        }
        if (!key.down) {
            downTimes.remove(key.code)
            consumed.remove(key.code)
        }
    }

    private fun forward(code: Int, down: Boolean, time: Long) {
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP) {
            if (down) audio.adjustSuggestedStreamVolume(
                if (code == KeyEvent.KEYCODE_VOLUME_DOWN) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE,
                AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        } else audio.dispatchMediaKeyEvent(KeyEvent(time, SystemClock.uptimeMillis(),
            if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0))
        record("Forward key=$code down=$down")
    }

    @Synchronized private fun record(value: String) {
        if (diagnostics.size == 64) diagnostics.removeFirst()
        diagnostics.addLast("${SystemClock.elapsedRealtime()} $value")
        openFileOutput("jabra-usb-buttons.txt", MODE_PRIVATE).bufferedWriter().use { writer ->
            diagnostics.forEach { writer.appendLine(it) }
        }
    }

    @Synchronized override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("JabraUsb running=$running cpuTrialHeld=${trial.held}")
        diagnostics.forEach(writer::println)
        worker?.let { thread ->
            writer.println("Reader state=${thread.state}")
            thread.stackTrace.take(12).forEach { writer.println("  $it") }
        }
    }

    override fun onDestroy() {
        running = false
        requests.forEach { it.cancel() }
        trial.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CPU_TRIAL = "io.github.ninbyo02.lami.assistant.CPU_TRIAL"
        @Volatile var statusText = "Disconnected"
            private set
        @Volatile var centerMusicReserved = false
            private set
        const val ACTION_STOP = "io.github.ninbyo02.lami.assistant.JABRA_USB_STOP"
        private const val CHANNEL = "jabra_usb"
    }
}
