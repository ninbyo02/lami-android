package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import io.github.ninbyo02.lami.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Keeps the probe's USB speech on a native 48-kHz stereo PCM path. */
internal class UsbTtsPlayback(
    private val context: Context,
    private val completed: (Boolean) -> Unit,
    private val trace: (String) -> Unit,
) {
    private data class Request(val id: String, val file: File, val device: AudioDeviceInfo)
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    @Volatile private var request: Request? = null
    private var player: AudioTrack? = null

    fun isActive(): Boolean = request != null

    fun target(): AudioDeviceInfo? {
        if (!BuildConfig.APPLICATION_ID.endsWith(".assistantprobe")) return null
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).singleOrNull {
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE) &&
                it.productName.toString().contains("Jabra Evolve2 40 SE", ignoreCase = true)
        }
    }

    fun begin(id: String, device: AudioDeviceInfo): File {
        stop()
        val file = File.createTempFile("lami-usb-speech-", ".wav", context.cacheDir)
        val pending = Request(id, file, device)
        request = pending
        handler.postDelayed({
            if (request === pending) finish(pending, "synthesis_timeout")
        }, 60_000)
        return file
    }

    fun synthesized(id: String?): Boolean {
        val pending = request?.takeIf { it.id == id } ?: return false
        handler.post {
            if (request !== pending) return@post
            if (!audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.id == pending.device.id }) {
                finish(pending, "disconnected")
                return@post
            }
            Thread({
                val decoded = runCatching { readSpeech(pending.file) }
                handler.post decoded@{
                    if (request !== pending) return@decoded
                    decoded.fold(onSuccess = { start(pending, it) },
                        onFailure = { finish(pending, "decode_failed=${it.message}") })
                }
            }, "lami-usb-speech").start()
        }
        return true
    }

    private fun readSpeech(file: File): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val format = extractor.getTrackFormat(0)
            check(format.getString(MediaFormat.KEY_MIME) == "audio/raw") { "Non-PCM synthesis" }
            check(!format.containsKey(MediaFormat.KEY_PCM_ENCODING) ||
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT) { "Non-16-bit synthesis" }
            extractor.selectTrack(0)
            val buffer = ByteBuffer.allocate(262_144)
            val bytes = ByteArrayOutputStream()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                check(bytes.size() + size <= 6_000_000) { "Speech too long" }
                bytes.write(buffer.array(), 0, size)
                if (!extractor.advance()) break
            }
            val raw = bytes.toByteArray()
            check(raw.size % 2 == 0 && raw.isNotEmpty()) { "Empty PCM synthesis" }
            val shorts = ShortArray(raw.size / 2)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return UsbSpeechPcm.stereo48k(shorts,
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        } finally {
            extractor.release()
        }
    }

    private fun start(pending: Request, pcm: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            player = track
            check(track.setPreferredDevice(pending.device)) { "USB output rejected" }
            track.addOnRoutingChangedListener({ routing ->
                if (request === pending && routing.routedDevice?.id != pending.device.id) {
                    finish(pending, "route_changed")
                }
            }, handler)
            check(track.write(pcm, 0, pcm.size) == pcm.size) { "Incomplete PCM write" }
            val frames = pcm.size / 2
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) { finish(pending, "done") }
                override fun onPeriodicNotification(track: AudioTrack) = Unit
            }, handler)
            check(track.setNotificationMarkerPosition(frames) == AudioTrack.SUCCESS) { "Completion marker rejected" }
            handler.removeCallbacksAndMessages(null)
            track.play()
            trace("usb_pcm_started device=${pending.device.id} frames=$frames rate=48000 channels=2")
            handler.postDelayed({
                if (request === pending) {
                    val actual = track.routedDevice
                    trace("usb_pcm_route device=${actual?.id} type=${actual?.type}")
                    if (actual?.id != pending.device.id) finish(pending, "route_changed")
                }
            }, 300)
            handler.postDelayed({
                if (request === pending) finish(pending, "playback_timeout head=${track.playbackHeadPosition}")
            }, frames * 1000L / 48_000 + 2000)
        }.onFailure { finish(pending, "failed=${it.javaClass.simpleName}:${it.message}") }
    }

    fun failed(id: String?): Boolean {
        val pending = request?.takeIf { it.id == id } ?: return false
        handler.post { if (request === pending) finish(pending, "synthesis_failed") }
        return true
    }

    private fun finish(pending: Request, reason: String) {
        if (request !== pending) return
        trace("usb_pcm_end reason=$reason")
        stop()
        completed(reason == "done")
    }

    fun stop() {
        val previous = request
        request = null
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        previous?.file?.delete()
    }
}
