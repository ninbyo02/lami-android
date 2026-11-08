package io.github.ninbyo02.lami.assistant

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AssistantSubmissionWiringTest {
    private val main = File("src/main/java/io/github/ninbyo02/lami/MainActivity.kt").readText()
    private val chat = File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
    @Test fun assistTrampolineCannotShareTheChatTaskAffinity() {
        val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val ns = "http://schemas.android.com/apk/res/android"
        val activities = xml.getElementsByTagName("activity")
        val entry = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
            .single { it.getAttributeNS(ns, "name") == ".assistant.AssistantEntryActivity" }
        assertTrue(entry.hasAttributeNS(ns, "taskAffinity"))
        assertEquals("", entry.getAttributeNS(ns, "taskAffinity"))
        assertEquals("true", entry.getAttributeNS(ns, "excludeFromRecents"))
    }
    @Test fun eachCaptureOwnsAPersistentSubmissionIdAndRefreshesSavedSetting() {
        val entry = main.substringAfter("private fun maybeStartAssistantRecognition").substringBefore("private fun startAssistantRecognition")
        assertTrue(entry.contains("AssistantVoiceEvents.beginInvocation()"))
        val capture = main.substringAfter("private fun startAssistantRecognition").substringBefore("private fun pulseAssistantReady")
        assertTrue(capture.contains(".voiceInputAutoSendFlow.first()"))
        assertTrue(capture.contains("AssistantVoiceEvents.publishTranscript("))
        assertTrue(capture.contains("automaticSending && !manuallyStopped && result.automaticEligible"))
    }
    @Test fun bufferedEventsAndDelayedSubmissionBothCheckOwnership() {
        val collector = chat.substringAfter("AssistantVoiceEvents.events.collect").substringBefore("val lifecycleOwner")
        assertTrue(collector.indexOf("consumeTranscript(event)") < collector.indexOf("userPrompt = transcript"))
        assertTrue(chat.contains("AssistantVoiceEvents.claimSubmission(invocationId, userPrompt, voiceInputAutoSend == true)"))
        assertTrue(chat.contains("pendingAssistantInvocationId == invocationId"))
        assertTrue(chat.contains("pendingFirstChatAssistantInvocationId = assistantInvocationId"))
    }
}
