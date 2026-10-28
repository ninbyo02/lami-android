package io.github.ninbyo02.lami.assistant

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantModelOverlapContractTest {
    @Test fun productionStartsOnlyModelPreparationBeforeWaitingForUnchangedChatSplash() {
        val main = File("src/main/java/io/github/ninbyo02/lami/MainActivity.kt").readText()
        val body = main.substringAfter("private fun startAssistantRecognition(").substringBefore("private fun pulseAssistantReady")
        assertTrue(body.indexOf("assistantRecognizer.prepareModel()") < body.indexOf("lifecycle.withResumed"))
        assertTrue(body.indexOf("snapshotFlow { chatEntryReadyVersion") < body.indexOf("assistantRecognizer.listenOnce"))
        assertTrue(body.indexOf("AssistantVoiceEvents.prepareCapture()") < body.indexOf("assistantRecognizer.listenOnce"))
        assertTrue(body.contains("preparation = preparation"))
        assertTrue(main.contains("StartupBackendSplash("))
        assertTrue(main.contains("onFinished = { showStartupSplash = false }"))
    }
    @Test fun modelFactoryHasNoInputRouteRecorderOrRecognitionAndReleasesItsProbeSession() {
        val source = File("src/main/java/io/github/ninbyo02/lami/asr/LamiAssistantRecognizer.kt").readText()
        val owner = source.substringAfter("private val models =").substringBefore("fun prepareModel()")
        assertTrue(owner.contains("loadOrInstall()"))
        assertTrue(owner.contains("engine.open(model).close()"))
        assertFalse(owner.contains("route.start()")); assertFalse(owner.contains("AudioRecord("))
        assertFalse(owner.contains("recordUtterance()"))
        assertTrue(source.indexOf("beforeCapture()") < source.indexOf("recognize(controller, bluetooth)"))
    }
    @Test fun activityMemoryAndDestructionInvalidateModelsWithoutChangingStartupOrCue() {
        val main = File("src/main/java/io/github/ninbyo02/lami/MainActivity.kt").readText()
        assertTrue(main.contains("assistantRecognizer.releasePreparedModel()"))
        assertTrue(main.contains("assistantRecognizer.close()"))
        assertTrue(main.contains("AssistantListeningCue(voice,"))
        val cue = File("src/main/java/io/github/ninbyo02/lami/assistant/AssistantListeningCue.kt").readText()
        assertTrue(cue.contains("どうしましたか。")); assertTrue(cue.contains("finished.await()"))
    }
    @Test fun readyModelCannotStartCaptureBeforeSplashCleanupAndCueComplete() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var loaded = false; var captures = 0
        val owner = AssistantPreparedModelOwner(scope, { loaded = true; "model" }, { it }, { it }, { })
        val model = owner.prepare()
        val splash = CompletableDeferred<Unit>(); val cleanup = CompletableDeferred<Unit>(); val cue = CompletableDeferred<Unit>()
        val invocation = launch {
            splash.await(); cleanup.await()
            val lease = owner.acquire(model.await())
            try { cue.await(); captures++ } finally { lease.close() }
        }
        runCurrent(); assertTrue(loaded); assertEquals(0, captures)
        splash.complete(Unit); runCurrent(); assertEquals(0, captures)
        cleanup.complete(Unit); runCurrent(); assertEquals(0, captures)
        cue.complete(Unit); runCurrent(); invocation.join(); assertEquals(1, captures)
        owner.close(); runCurrent(); scope.cancel()
    }
    @Test fun finishedSplashCannotStartCaptureBeforeModelReadyOrAfterCancellation() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val load = CompletableDeferred<Unit>(); var captures = 0
        val owner = AssistantPreparedModelOwner(scope, { load.await(); "model" }, { it }, { it }, { })
        val model = owner.prepare()
        val old = launch { val lease = owner.acquire(model.await()); try { captures++ } finally { lease.close() } }
        runCurrent(); assertEquals(0, captures); old.cancelAndJoin()
        load.complete(Unit); runCurrent(); assertEquals(0, captures)
        owner.acquire(model.await()).close(); owner.close(); runCurrent(); scope.cancel()
    }
}
