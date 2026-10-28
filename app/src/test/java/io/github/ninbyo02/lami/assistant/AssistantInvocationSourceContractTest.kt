package io.github.ninbyo02.lami.assistant

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Integration wiring checks only; not proof of real Android recognition or vibration. */
class AssistantInvocationSourceContractTest {
    private fun main() = File("src/main/java/io/github/ninbyo02/lami/MainActivity.kt").readText()
    @Test fun replacementWaitsForOldCaptureBeforeStartingTheNext() {
        val body = main().substringAfter("private fun startAssistantRecognition(").substringBefore("private fun pulseAssistantReady")
        val owners = File("src/main/java/io/github/ninbyo02/lami/assistant/AssistantCaptureSerialJobs.kt").readText()
        assertTrue(owners.indexOf("previous.forEach { it.cancel() }") < owners.indexOf("previous.forEach { it.join() }"))
        assertTrue(owners.indexOf("previous.forEach { it.join() }") < owners.indexOf("block()"))
        assertTrue(body.contains("assistantCaptureJobs.replace(lifecycleScope)"))
        assertTrue(body.contains("isCurrentSession = { assistantCaptureRequests.isCurrent(requestId) }"))
        val events = body.substringAfter("onCaptureEvent = { event ->").substringBefore("publishEvent =")
        assertTrue(events.indexOf("assistantCaptureRequests.observe") < events.indexOf("runOnUiThread"))
    }
    @Test fun readyPulseUsesOneStandardHapticWithoutPermissionOrSettingOverride() {
        val body = main().substringAfter("private fun pulseAssistantReady").substringBefore("override fun onWindowFocusChanged")
        assertTrue(body.contains("claimPulse(requestId, foreground)"))
        assertTrue(body.contains("hasWindowFocus()"))
        assertTrue(body.contains("HapticFeedbackConstants.CLOCK_TICK"))
        assertEquals(1, Regex("performHapticFeedback").findAll(main()).count())
        assertFalse(main().contains("FLAG_IGNORE_GLOBAL_SETTING"))
        assertFalse(File("src/main/AndroidManifest.xml").readText().contains("android.permission.VIBRATE"))
    }
    @Test fun microphoneWaitsForChatPlaybackAndManualRecordingCleanup() {
        val source = File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
        val preparation = source.substringAfter("AssistantVoiceEvents.registerCapturePreparation").substringBefore("onDispose { registration.close() }")
        assertTrue(preparation.contains("cancelVoiceInput()"))
        assertTrue(preparation.contains("manualCapture?.join()"))
        assertTrue(preparation.contains("ttsController.stopForVoiceInput()"))
        val launch = main().substringAfter("private fun startAssistantRecognition")
        assertTrue(launch.indexOf("AssistantVoiceEvents.prepareCapture()") < launch.indexOf("assistantRecognizer.listenOnce"))
    }
    @Test fun ordinaryLauncherDoesNotStartRecognitionAndAutoSendUsesExistingPreference() {
        val body = main().substringAfter("private fun maybeStartAssistantRecognition").substringBefore("private fun startAssistantRecognition")
        assertTrue(body.indexOf("if (!launchIntent.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false)) return") < body.indexOf("assistantCaptureRequests.begin()"))
        assertTrue(main().contains(".voiceInputAutoSendFlow.first()"))
        assertTrue(main().contains("automaticSending && !manuallyStopped && result.automaticEligible"))
    }
}
