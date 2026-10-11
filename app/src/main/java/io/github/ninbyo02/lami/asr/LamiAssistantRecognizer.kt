package io.github.ninbyo02.lami.asr

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.github.ninbyo02.lami.asr.platform.audio.AudioInputSource
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent
import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureObserver
import io.github.ninbyo02.lami.assistant.AssistantVoiceEvent
import io.github.ninbyo02.lami.assistant.AssistantVoiceEvents
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface AssistantRecognitionResult {
    data class Success(
        val transcript: String,
        val usedBluetoothMicrophone: Boolean,
    ) : AssistantRecognitionResult

    data class ModelRequired(val stagingPath: String) : AssistantRecognitionResult

    data class Failure(val detail: String) : AssistantRecognitionResult
}

/** One-shot local recognizer used by Android assist launches inside the LAMI process. */
internal class LamiAssistantRecognizer(
    context: Context,
    private val beforeCapture: suspend () -> Unit = {},
) : Closeable {
    private val appContext = context.applicationContext
    private var activeController: PrivacyLocalAsrController? = null

    suspend fun listenOnce(): AssistantRecognitionResult {
        check(activeController == null) { "assistant recognition is already active" }
        val bluetooth = canUseBluetoothConversationMicrophone()
        trace("listen_once bluetooth=$bluetooth")
        val route = if (bluetooth) BluetoothConversationAudio(appContext) else ConversationAudioRoute.NONE
        val controller = PrivacyLocalAsrController(
            context = appContext,
            conversationAudio = route,
            audioInputSource = if (bluetooth) {
                AudioInputSource.VOICE_COMMUNICATION
            } else {
                AudioInputSource.MIC
            },
            audioInputGain = if (bluetooth) BLUETOOTH_INPUT_GAIN else 1.0,
        )
        var peakDbFs = -120f
        controller.setAudioCaptureObserver(AudioCaptureObserver { event ->
            when (event) {
                AudioCaptureEvent.Started -> AssistantVoiceEvents.publish(AssistantVoiceEvent.Listening)
                is AudioCaptureEvent.Level -> peakDbFs = maxOf(peakDbFs, event.sample.peakDbFs)
                is AudioCaptureEvent.InputInterrupted -> trace("capture_interrupted reason=${event.reason}")
                else -> Unit
            }
        })
        activeController = controller
        return try {
            when (val prepared = controller.prepare()) {
                is PrivacyAsrScreenState -> {
                    trace("prepared phase=${prepared.phase} detail=${prepared.detail}")
                    when (prepared.phase) {
                    PrivacyAsrPhase.READY -> {
                        withContext(Dispatchers.IO) { route.start() }
                        beforeCapture()
                        recognize(controller, bluetooth)
                    }
                    PrivacyAsrPhase.MODEL_REQUIRED ->
                        AssistantRecognitionResult.ModelRequired(controller.stagingPath)
                    else -> AssistantRecognitionResult.Failure(prepared.detail)
                    }
                }
            }
        } catch (failure: Throwable) {
            trace("recognition_failed type=${failure.javaClass.simpleName}")
            AssistantRecognitionResult.Failure(
                "音声認識に失敗しました (${failure.javaClass.simpleName})",
            )
        } finally {
            trace("capture_peak_dbfs=$peakDbFs")
            activeController = null
            controller.close()
        }
    }

    private suspend fun recognize(
        controller: PrivacyLocalAsrController,
        bluetooth: Boolean,
    ): AssistantRecognitionResult {
        val state = controller.recordUtterance()
        trace("record_ended phase=${state.phase} detail=${state.detail} textLength=${state.transcript?.text?.length ?: 0}")
        val transcript = state.transcript?.text?.trim().orEmpty()
        return if (state.phase == PrivacyAsrPhase.FINAL && transcript.isNotEmpty()) {
            AssistantRecognitionResult.Success(transcript, bluetooth)
        } else {
            AssistantRecognitionResult.Failure(state.detail)
        }
    }

    private fun canUseBluetoothConversationMicrophone(): Boolean {
        val audio = appContext.getSystemService(AudioManager::class.java)
        return audio.mode == AudioManager.MODE_NORMAL &&
            !audio.isMusicActive &&
            audio.availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
    }

    override fun close() {
        activeController?.close()
        activeController = null
    }

    private fun trace(message: String) {
        io.github.ninbyo02.lami.util.DebugTraceFile.append(appContext, "[LAMI_ASR] $message")
    }

    private companion object {
        // Real-device Pioneer tests showed useful speech headroom at +12 dB.
        const val BLUETOOTH_INPUT_GAIN = 4.0
    }
}
