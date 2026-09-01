package io.github.ninbyo02.lami.ui.screens.home

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenericCpuStreamingCancellationInstrumentedTest {
    @Test
    fun cpuConversationFlowStopsPromptlyAfterCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val model = File("/data/local/tmp/lami_gpu_model.litertlm")
        assumeTrue("generic LiteRT model is required for this manual device test", model.isFile)

        val firstPartial = CompletableDeferred<Unit>()
        val partialCount = AtomicInteger(0)
        val job = launch(Dispatchers.Default) {
            tryRunOfficialLiteRtFlowStreaming(
                prompt = "Pythonで短いピンボールゲームの例を作ってください。コードを含めてください。",
                modelPath = model.absolutePath,
                cacheDirPath = context.cacheDir.absolutePath,
                mediaPipeProbeContext = context,
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.CPU,
                markdownStreamingMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
                onPartial = {
                    partialCount.incrementAndGet()
                    if (!firstPartial.isCompleted) firstPartial.complete(Unit)
                },
            )
        }

        withTimeout(120_000L) { firstPartial.await() }
        val cancelStartedAt = SystemClock.elapsedRealtime()
        job.cancelAndJoin()
        val cancelDurationMs = SystemClock.elapsedRealtime() - cancelStartedAt
        val countAfterJoin = partialCount.get()
        delay(1_500L)

        assertTrue("cancel should close Conversation/Engine promptly, took ${cancelDurationMs}ms", cancelDurationMs < 5_000L)
        assertEquals("no native chunks may arrive after cancelAndJoin", countAfterJoin, partialCount.get())
    }
}
