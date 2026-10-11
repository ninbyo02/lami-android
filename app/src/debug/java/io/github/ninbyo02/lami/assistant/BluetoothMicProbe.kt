package io.github.ninbyo02.lami.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal enum class BluetoothMicInput(val label: String, val source: Int) {
    COMMUNICATION("通話用", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    RECOGNITION("音声認識用 (試験)", MediaRecorder.AudioSource.VOICE_RECOGNITION),
}

/** Foreground-only five-second capture; retain speech in RAM, never in diagnostics. */
internal class BluetoothMicProbe(
    private val context: Context,
    private val onState: (String, Boolean, Boolean) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val capturing = AtomicBoolean()
    @Volatile private var cancellationReason = "停止中"
    @Volatile private var recorder: AudioRecord? = null
    private var samples: ShortArray? = null
    private var playback: AudioTrack? = null
    private var playbackFocus: AudioFocusRequest? = null
    private var primed = false
    private var primedModeOwned = false
    private var primeDeadline = 0L
    private var conversationTarget: AudioDeviceInfo? = null
    private var conversationFocus: AudioFocusRequest? = null
    private var conversationDeadline = 0L
    private var conversationHold: AudioTrack? = null
    private var conversationHoldStartedAt = 0L
    val isConversationActive: Boolean get() = conversationTarget != null
    private val lines = ArrayDeque<String>()
    private val captureTimeout = Runnable { cancel("録音が時間内に終了しませんでした") }
    private val playbackEnd = Runnable {
        releasePlayback()
        state(if (isConversationActive) "接続保持中・マイク停止" else "再生終了", false)
    }
    private val conversationTimeout = Runnable { cancel("会話接続終了 (60秒)") }
    private val conversationPoll = object : Runnable {
        override fun run() {
            val target = conversationTarget ?: return
            try {
                checkHeldConnection(AudioManager.MODE_IN_COMMUNICATION, target)
                val hold = checkNotNull(conversationHold) { "通話接続の保持出力がありません" }
                check(hold.playState == AudioTrack.PLAYSTATE_PLAYING) { "通話接続の保持出力が停止しました" }
                if (SystemClock.elapsedRealtime() - conversationHoldStartedAt >= 500) {
                    check(hold.routedDevice?.id == target.id) { "通話接続の保持出力先が変わりました" }
                }
                handler.postDelayed(this, 250)
            } catch (error: Exception) {
                cancel("会話接続を終了しました: ${error.message}")
            }
        }
    }
    private val primeTimeout = Runnable {
        releasePrimedRoute()
        state("接続保持終了", false)
    }
    private val primePoll = object : Runnable {
        override fun run() {
            if (!primed) return
            if (audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                diagnostic("preconnectedOutput=${audio.communicationDevice?.let(::deviceSummary)}")
                if (isConversationActive) {
                    try {
                        startConversationHold(checkNotNull(conversationTarget))
                        state("会話接続保持中 (60秒)・マイク停止", false)
                        handler.post(conversationPoll)
                    } catch (error: Exception) {
                        cancel("会話接続を保持できませんでした: ${error.message}")
                    }
                } else {
                    state("接続保持中 (10秒)", false)
                    handler.postDelayed(primeTimeout, 10_000)
                }
            } else if (SystemClock.elapsedRealtime() >= primeDeadline) {
                releasePrimedRoute()
                state("Bluetooth通話用の接続が開始しませんでした", false)
            } else handler.postDelayed(this, 50)
        }
    }

    fun beginConversation() = preconnect(conversation = true)

    private fun startConversationHold(target: AudioDeviceInfo) {
        // Silent call output keeps Android's communication client active without recording.
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(RATE * 2).build()
        conversationHold = track
        check(track.state == AudioTrack.STATE_NO_STATIC_DATA) { "通話接続の保持出力を準備できませんでした" }
        check(track.setPreferredDevice(target)) { "通話接続の保持出力先を指定できませんでした" }
        check(track.write(ShortArray(RATE), 0, RATE) == RATE) { "無音データを準備できませんでした" }
        check(track.setLoopPoints(0, RATE, -1) == AudioTrack.SUCCESS) { "無音出力を保持できませんでした" }
        track.play()
        conversationHoldStartedAt = SystemClock.elapsedRealtime()
        diagnostic("conversationSilentOutputStarted requestedOutput=${deviceSummary(target)} microphoneOpened=false")
    }

    fun preconnect(reduceSwitching: Boolean = false, conversation: Boolean = false) {
        if (capturing.get() || playback != null) return
        releasePrimedRoute()
        try {
            check(audio.mode == AudioManager.MODE_NORMAL && !audio.isMusicActive) {
                "音楽・通話を停止してください"
            }
            check(pioneerOutput() != null) { "Pioneerが接続されていません" }
            val target = audio.availableCommunicationDevices
                .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }.singleOrNull()
            checkNotNull(target) { "Bluetooth通話用の接続がありません" }
            if (conversation) {
                samples = null
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener({ change ->
                        if (change < 0) cancel("音声の優先権が変わりました")
                    }, handler).build()
                check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    "音声の優先権を取得できませんでした"
                }
                conversationFocus = request
            }
            if (!reduceSwitching) {
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                primedModeOwned = true
            }
            check(audio.setCommunicationDevice(target)) { "Bluetoothへの切り替えが拒否されました" }
            primed = true
            if (conversation) {
                conversationTarget = target
                conversationDeadline = SystemClock.elapsedRealtime() + 60_000
                handler.postDelayed(conversationTimeout, 60_000)
                diagnostic("conversationStarted deadline=$conversationDeadline selectedOutput=${deviceSummary(target)}")
            }
            primeDeadline = SystemClock.elapsedRealtime() + 3500
            diagnostic("preconnectStrategy=${if (reduceSwitching) "routeOnly" else "communicationMode"} " +
                "selectedOutput=${deviceSummary(target)}")
            state("通話接続中", false)
            handler.post(primePoll)
        } catch (error: Exception) {
            releasePrimedRoute()
            state("接続できませんでした: ${error.message}", false)
        }
    }

    private fun releasePrimedRoute() {
        handler.removeCallbacks(primePoll)
        handler.removeCallbacks(primeTimeout)
        handler.removeCallbacks(conversationTimeout)
        handler.removeCallbacks(conversationPoll)
        conversationHold?.let { runCatching { it.stop() }; it.release() }
        conversationHold = null
        if (primed) runCatching { audio.clearCommunicationDevice() }
        if (primedModeOwned) {
            runCatching { audio.mode = AudioManager.MODE_NORMAL }
        }
        if (primed || primedModeOwned) diagnostic("preconnectReleased")
        if (isConversationActive) diagnostic("conversationReleased")
        conversationTarget = null
        conversationDeadline = 0L
        conversationFocus?.let { audio.abandonAudioFocusRequest(it) }
        conversationFocus = null
        primed = false
        primedModeOwned = false
    }

    fun record(reduceSwitching: Boolean = false, inputType: BluetoothMicInput = BluetoothMicInput.COMMUNICATION,
        cutMilliseconds: Int = 0, repetitions: Int = 1) {
        require(cutMilliseconds in 0..BluetoothMicLeadIn.MAX_MS)
        require(repetitions == 1 || repetitions == 3)
        if (playback != null || !capturing.compareAndSet(false, true)) return
        val inheritedMode = if (primed) primedModeOwned else null
        val retainConnection = isConversationActive
        if (primed && !retainConnection) {
            handler.removeCallbacks(primePoll)
            handler.removeCallbacks(primeTimeout)
            primed = false
            primedModeOwned = false
        }
        samples = null
        cancellationReason = "停止中"
        val id = generation.incrementAndGet()
        state("Bluetooth接続中", true)
        handler.postDelayed(captureTimeout, if (repetitions == 1) 15_000 else 40_000)
        Thread({ capture(id, reduceSwitching, inputType, cutMilliseconds, inheritedMode, repetitions, retainConnection) },
            "LamiBluetoothMic").start()
    }

    private fun capture(id: Int, reduceSwitching: Boolean, inputType: BluetoothMicInput,
        cutMilliseconds: Int, inheritedMode: Boolean?, repetitions: Int, retainConnection: Boolean) {
        var modeOwned = inheritedMode == true && !retainConnection
        var routeOwned = inheritedMode != null && !retainConnection
        var focus: AudioFocusRequest? = null
        var input: AudioRecord? = null
        var captured: ShortArray? = null
        var result = "停止中"
        val captureMode = if (inheritedMode ?: !reduceSwitching) AudioManager.MODE_IN_COMMUNICATION
            else AudioManager.MODE_NORMAL
        try {
            diagnostic("captureStrategy=" + if (captureMode == AudioManager.MODE_NORMAL) "routeOnly" else "communicationMode")
            diagnostic("preconnected=${inheritedMode != null}")
            diagnostic("conversationCapture=$retainConnection")
            diagnostic("captureRepetitions=$repetitions gapMs=3000")
            diagnostic("captureSource=${inputType.name}:${inputType.source}")
            diagnostic("leadInCutMs=$cutMilliseconds requestedDiscardSamples=" +
                BluetoothMicLeadIn.sampleCount(cutMilliseconds, RATE))
            check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "マイクが許可されていません"
            }
            check(acceptsCaptureMode(if (inheritedMode == null) AudioManager.MODE_NORMAL else captureMode,
                audio.mode, audio.isMusicActive)) {
                "音楽・通話を停止してください"
            }
            check(!audio.isMicrophoneMute) { "マイクがミュートされています" }
            check(pioneerOutput() != null) { "Pioneerが接続されていません" }
            val devices = audio.availableCommunicationDevices
            diagnostic("communicationCandidates=" + devices.joinToString(transform = ::deviceSummary))
            val target = devices.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }.singleOrNull()
            checkNotNull(target) { "Bluetooth通話用の接続がありません" }
            checkCurrent(id)
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener({ change ->
                    if (change < 0 && generation.get() == id) cancel("音声の優先権が変わりました")
                }, handler).build()
            if (!retainConnection) {
                check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    "音声の優先権を取得できませんでした"
                }
                focus = request
            }
            if (inheritedMode == null && !reduceSwitching) {
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                modeOwned = true
            }
            if (inheritedMode == null) {
                check(audio.setCommunicationDevice(target)) { "Bluetoothへの切り替えが拒否されました" }
                routeOwned = true
            }
            val until = SystemClock.elapsedRealtime() + 3500
            while (audio.communicationDevice?.id != target.id) {
                checkCurrent(id)
                check(SystemClock.elapsedRealtime() < until) { "Bluetooth通話用の接続が開始しませんでした" }
                Thread.sleep(50)
            }
            checkCurrent(id)
            val source = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }.singleOrNull()
            checkNotNull(source) { "Bluetooth入力が見つかりません" }
            diagnostic("selectedOutput=${deviceSummary(target)} selectedInput=${deviceSummary(source)}")
            val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "録音形式を利用できません" }
            input = AudioRecord.Builder().setAudioSource(inputType.source)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minBuffer * 2, RATE / 2)).build()
            check(input.state == AudioRecord.STATE_INITIALIZED) { "録音を準備できませんでした" }
            check(input.setPreferredDevice(source)) { "Bluetooth入力の指定が拒否されました" }
            var lastClip: ShortArray? = null
            repeat(repetitions) { index ->
                if (index > 0) {
                    diagnostic("recordingGapBefore=${index + 1} microphoneStopped=true " +
                        "reportedMode=${audio.mode} music=${audio.isMusicActive}")
                    val deadline = SystemClock.elapsedRealtime() + 3000
                    var displayedSeconds = -1
                    while (SystemClock.elapsedRealtime() < deadline) {
                        checkCurrent(id)
                        checkHeldConnection(captureMode, target)
                        val seconds = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt()
                        if (seconds != displayedSeconds) {
                            displayedSeconds = seconds
                            val message = "接続保持中・マイク停止\n${index + 1}/$repetitions 回目まで $seconds 秒"
                            handler.post { if (generation.get() == id) state(message, true) }
                        }
                        Thread.sleep(50)
                    }
                }
                checkCurrent(id)
                if (repetitions > 1) checkHeldConnection(captureMode, target)
                synchronized(this) {
                    checkCurrent(id)
                    recorder = input
                    input.startRecording()
                }
                check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "録音を開始できませんでした" }
                checkHeldConnection(captureMode, target)
                diagnostic("recordingStart clip=${index + 1}/$repetitions communication=${audio.communicationDevice?.let(::deviceSummary)}")
                val pcm = readClip(id, input, source, target, captureMode, cutMilliseconds, index + 1, repetitions)
                synchronized(this) {
                    checkCurrent(id)
                    input.stop()
                }
                val stats = BluetoothMicSamples.measure(pcm)
                diagnostic("captured clip=${index + 1}/$repetitions samples=${stats.count} " +
                    "nonZero=${stats.nonZero} peak=${stats.peak} rms=${stats.rms}")
                lastClip = pcm
            }
            checkCurrent(id)
            captured = checkNotNull(lastClip)
            result = if (repetitions > 1) "3回の録音完了・${if (retainConnection) "接続保持" else "接続解除"}\n再生対象: 3回目の録音"
                else (if (BluetoothMicSamples.measure(captured).nonZero == 0) "Bluetoothマイクの入力は無音でした"
                    else "Bluetoothマイクの録音完了") +
                    if (retainConnection) "\n接続保持中・マイク停止" else "\n先頭カット: $cutMilliseconds ms"
        } catch (error: Exception) {
            result = if (generation.get() == id) "確認できませんでした: ${error.message}" else "停止中"
            diagnostic("captureStopped=${error.message}")
        } finally {
            synchronized(this) {
                recorder = null
                input?.let { runCatching { it.stop() }; it.release() }
            }
            if (routeOwned) runCatching { audio.clearCommunicationDevice() }
            if (modeOwned) {
                runCatching { audio.mode = AudioManager.MODE_NORMAL }
            }
            focus?.let { audio.abandonAudioFocusRequest(it) }
            diagnostic("cleanup mode=${audio.mode} communication=${audio.communicationDevice?.let(::deviceSummary)}")
            val finalResult = result
            val finalSamples = captured
            handler.post {
                handler.removeCallbacks(captureTimeout)
                capturing.set(false)
                if (generation.get() == id) {
                    if (retainConnection && finalSamples == null) releasePrimedRoute()
                    samples = finalSamples
                    state(finalResult, false)
                } else state(cancellationReason, false)
            }
        }
    }

    private fun checkHeldConnection(captureMode: Int, target: AudioDeviceInfo) {
        // Public mode may remain NORMAL until the recording activity update arrives.
        val mode = audio.mode
        check(acceptsCaptureMode(captureMode, mode, audio.isMusicActive)) {
            "音楽・通話の状態が変わりました"
        }
        check(audio.communicationDevice?.id == target.id) { "通話接続が保持されなかったため試験を停止しました" }
    }

    private fun readClip(id: Int, input: AudioRecord, source: AudioDeviceInfo, target: AudioDeviceInfo, captureMode: Int,
        cutMilliseconds: Int, clip: Int, repetitions: Int): ShortArray {
        val pcm = ShortArray(RATE * 5)
        val block = ShortArray(512)
        val leadIn = BluetoothMicLeadIn(cutMilliseconds, RATE)
        var offset = 0
        var inputConfirmed = false
        val recordingMessage = if (repetitions == 1) "録音中 (5秒)" else "録音中 $clip/$repetitions (5秒)"
        handler.post { if (generation.get() == id) state(
            if (cutMilliseconds == 0) recordingMessage else "先頭カット中 ($cutMilliseconds ms)", true) }
        while (offset < pcm.size) {
            checkCurrent(id)
            checkHeldConnection(captureMode, target)
            val count = input.read(block, 0, minOf(block.size, leadIn.remainingSamples + pcm.size - offset),
                AudioRecord.READ_BLOCKING)
            check(count > 0) { "音声を読み取れませんでした: $count" }
            val actual = input.routedDevice
            if (actual?.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO || actual.id != source.id) {
                diagnostic("rejectedActualInput=${actual?.let(::deviceSummary)}")
                error(if (actual?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC)
                    "内蔵マイクに切り替わったため停止しました" else "Bluetooth入力を確認できませんでした")
            }
            check(input.activeRecordingConfiguration?.isClientSilenced != true) {
                "Androidがマイク入力を無音化しています"
            }
            if (!inputConfirmed) {
                diagnostic("recordingActualInput clip=$clip/$repetitions ${deviceSummary(actual)} " +
                    "silenced=${input.activeRecordingConfiguration?.isClientSilenced}")
                inputConfirmed = true
            }
            val cutting = leadIn.remainingSamples > 0
            offset += leadIn.append(block, count, pcm, offset)
            if (cutting && leadIn.remainingSamples == 0) {
                diagnostic("leadInDiscardedSamples=" + BluetoothMicLeadIn.sampleCount(cutMilliseconds, RATE))
                handler.post { if (generation.get() == id) state(recordingMessage, true) }
            }
        }
        return pcm
    }

    private fun checkCurrent(id: Int) { check(generation.get() == id) { "Cancelled" } }

    fun play(adjustVolume: Boolean = false) {
        val original = samples ?: return
        if (capturing.get() || playback != null) return
        val conversationOutput = conversationTarget
        if (conversationOutput == null) releasePrimedRoute()
        try {
            val gain = if (adjustVolume) BluetoothMicSamples.playbackGain(original) else 1.0
            val pcm = if (adjustVolume) BluetoothMicSamples.amplifiedCopy(original, gain) else original
            diagnostic("playbackGain=$gain originalSamplesUnchanged=true")
            val target = if (conversationOutput != null) {
                checkHeldConnection(AudioManager.MODE_IN_COMMUNICATION, conversationOutput)
                conversationOutput
            } else {
                check(audio.mode == AudioManager.MODE_NORMAL && !audio.isMusicActive) { "音楽・通話を停止してください" }
                checkNotNull(pioneerOutput()) { "Pioneerが接続されていません" }
            }
            diagnostic("conversationPlayback=${conversationOutput != null} requestedOutput=${deviceSummary(target)}")
            val attributes = AudioAttributes.Builder().setUsage(if (conversationOutput == null)
                AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
                    if (change < 0) { releasePlayback(); state("再生を停止しました", false) }
                }, handler).build()
            if (conversationOutput == null) {
                check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "再生を開始できませんでした" }
                playbackFocus = request
            }
            val track = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            playback = track
            check(track.setPreferredDevice(target)) { "Pioneer出力を指定できませんでした" }
            track.setVolume(if (adjustVolume) 1.0f else 0.5f)
            check(track.write(pcm, 0, pcm.size) == pcm.size) { "再生データを準備できませんでした" }
            track.play()
            state("再生中", true)
            handler.postDelayed({
                if (playback === track) {
                    val actual = track.routedDevice
                    diagnostic("playbackActualOutput=${actual?.let(::deviceSummary)}")
                    if (actual?.id != target.id) {
                        cancel("Pioneer出力を確認できませんでした")
                    }
                }
            }, 500)
            handler.postDelayed(playbackEnd, pcm.size * 1000L / RATE + 500)
        } catch (error: Exception) {
            cancel("再生できませんでした: ${error.message}")
        }
    }

    fun cancel(reason: String = "停止中") {
        diagnostic("cancelReason=$reason conversation=$isConversationActive capturing=${capturing.get()}")
        cancellationReason = reason
        generation.incrementAndGet()
        samples = null
        synchronized(this) { recorder?.let { runCatching { it.stop() } } }
        releasePlayback()
        releasePrimedRoute()
        handler.removeCallbacks(captureTimeout)
        state(if (capturing.get()) "停止処理中" else reason, capturing.get())
    }

    private fun releasePlayback() {
        handler.removeCallbacks(playbackEnd)
        playback?.let { runCatching { it.stop() }; it.release() }
        playback = null
        playbackFocus?.let { audio.abandonAudioFocusRequest(it) }
        playbackFocus = null
    }

    private fun state(value: String, busy: Boolean) { onState(value, busy, samples != null) }
    private fun pioneerOutput(): AudioDeviceInfo? = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
            it.productName.toString().contains("SE-MS7BT", ignoreCase = true) }
    private fun deviceSummary(device: AudioDeviceInfo): String =
        "type=${device.type},id=${device.id},name=${device.productName}"

    @Synchronized private fun diagnostic(value: String) {
        if (lines.size == 64) lines.removeFirst()
        lines.addLast("${SystemClock.elapsedRealtime()} $value")
        context.openFileOutput("bluetooth-mic-probe.txt", Context.MODE_PRIVATE).bufferedWriter().use { writer ->
            lines.forEach(writer::appendLine)
        }
    }

    companion object {
        const val RATE = 16_000

        internal fun acceptsCaptureMode(requested: Int, reported: Int, musicActive: Boolean): Boolean =
            !musicActive && (reported == requested || reported == AudioManager.MODE_NORMAL)
    }
}
