package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPhotoBenchmarkInstrumentedTest {
    @Test fun inferNonpersonalKnownPhoto() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("imagePhoto", "false").toBoolean())
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".imagebench"))
        val backend = PreferredBackendDryRunSetting.valueOf(args.getString("imageBackend", "GPU"))
        val model = if (backend == PreferredBackendDryRunSetting.NPU) "/data/local/tmp/lami_npu_durability_model.litertlm" else "/data/local/tmp/lami_gpu_model.litertlm"
        val stem = "photo_${backend}_earth"
        val file = File(context.filesDir, "$stem.json")
        val trace = File(context.filesDir, "$stem.trace").apply { writeText("") }
        val result = JSONObject().put("backend", backend.name).put("commit", BuildConfig.GIT_SHA)
            .put("status", "preparing").put("seed", 42).put("top_k", 40).put("top_p", 0.9).put("temperature", 0.3)
        fun save() = file.writeText(result.toString(2))
        fun temp() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", -1) ?: -1
        val lock = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lami:photo-test")
        var activity: ActivityScenario<MainActivity>? = null
        var accepted = false
        var started = 0L
        var first: Long? = null
        var response = ""
        save()
        try {
            check(temp() in 0 until 400)
            lock.acquire(240_000L)
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val source = File("/data/local/tmp/lami_nasa_AS17-148-22727.jpg")
            val capability = resolveLocalImageInputCapability(model, File(model).name, true, backend)
                as? LocalImageInputCapability.Supported ?: error("Model fingerprint rejected")
            val prepared = prepareLocalImageInputs(context, listOf(Uri.fromFile(source)), persistent = true).single()
            fun sha(path: File) = MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }
            result.put("source_sha256", sha(source)).put("prepared_sha256", sha(File(prepared.absolutePath)))
                .put("prepared_image", prepared.absolutePath).put("model_sha256", capability.sha256)
                .put("vision_backend", capability.visionBackend.name).put("temperature_start_tenths_c", temp())
            val prompt = "Name the main object and two visible features. English only."
            result.put("prompt", prompt).put("status", "running")
            save()
            if (backend == PreferredBackendDryRunSetting.NPU) NpuKotlinConversationProductRoute.reset("photo_start")
            started = SystemClock.elapsedRealtime()
            val partial: (String) -> Unit = { text -> if (first == null) first = SystemClock.elapsedRealtime() - started; response = text }
            val log: (String) -> Unit = { line -> trace.appendText("${SystemClock.elapsedRealtime() - started}ms $line\n") }
            withTimeout(180_000L) {
                if (backend == PreferredBackendDryRunSetting.NPU) {
                    val attempt = NpuKotlinConversationProductRoute.run(context, -201, prompt, emptyList(), model, 128,
                        listOf(prepared.absolutePath), MarkdownStreamingMode.EDGE_GALLERY_COMPAT, partial, log)
                    accepted = attempt.succeeded
                    response = attempt.result?.sanitizedOutput ?: response
                    result.put("failure_reason", attempt.failureReason)
                } else {
                    response = runLocalImageConversation(context, capability, prompt, listOf(prepared.absolutePath), emptyList(),
                        MarkdownStreamingMode.EDGE_GALLERY_COMPAT, partial, log).response
                    accepted = true
                }
            }
            result.put("total_ms", SystemClock.elapsedRealtime() - started).put("first_partial_ms", first)
            val lower = response.lowercase()
            result.put("earth_correct", lower.contains("earth"))
                .put("visible_feature_count", listOf("cloud", "ocean", "land", "continent", "ice").count(lower::contains))
                .put("status", "completed")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("error", failure.stackTraceToString())
        } finally {
            result.put("native_accepted", accepted).put("response", response).put("temperature_end_tenths_c", temp())
            save()
            if (backend == PreferredBackendDryRunSetting.NPU) NpuKotlinConversationProductRoute.reset("photo_done")
            activity?.close()
            if (lock.isHeld) lock.release()
        }
        assertTrue(result.toString(), accepted)
    }
}
