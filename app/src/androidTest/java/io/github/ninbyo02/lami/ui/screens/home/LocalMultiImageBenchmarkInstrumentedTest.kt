package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMultiImageBenchmarkInstrumentedTest {
    @Test fun identifyDistinctImagesAndFollowUp() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit manual matrix only", args.getString("imageMatrix", "false").toBoolean())
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".imagebench"))
        val backend = PreferredBackendDryRunSetting.valueOf(args.getString("imageBackend", "GPU"))
        val count = args.getString("imageCount", "8").toInt()
        require(count == 8 || count == 10)
        val historyMode = args.getString("imageHistoryMode", "product")
        require(historyMode == "product" || historyMode == "fixed")
        val trial = args.getString("imageTrial", "baseline")
        require(trial.matches(Regex("[A-Za-z0-9_-]{1,32}")))
        val model = if (backend == PreferredBackendDryRunSetting.NPU) "/data/local/tmp/lami_npu_durability_model.litertlm" else "/data/local/tmp/lami_gpu_model.litertlm"
        val stem = "multi_image_${backend}_$count" + if (trial == "baseline") "" else "_$trial"
        val jsonFile = File(context.filesDir, "$stem.json")
        val traceFile = File(context.filesDir, "$stem.trace").apply { writeText("") }
        val result = JSONObject().put("backend", backend.name).put("image_count", count)
            .put("context_tokens", 4096).put("model", model).put("api_version", BuildConfig.LITERTLM_ANDROID_VERSION)
            .put("commit", BuildConfig.GIT_SHA).put("protocol_version", 3).put("fixture_protocol_version", 2).put("status", "preparing")
            .put("trial", trial).put("history_mode", historyMode)
            .put("sampler", JSONObject().put("profile", LocalConversationPolicy.SAMPLER_PROFILE)
                .put("top_k", LocalConversationPolicy.SAMPLER_TOP_K).put("top_p", LocalConversationPolicy.SAMPLER_TOP_P)
                .put("temperature", LocalConversationPolicy.SAMPLER_TEMPERATURE).put("seed", LocalConversationPolicy.SAMPLER_SEED)
                .put("thinking_enabled", LocalConversationPolicy.THINKING_ENABLED))
            .put("requested_output_tokens", 128).put("native_per_generation_output_cap_enforced", false)
            .put("system_instruction", LocalConversationPolicy.SYSTEM_INSTRUCTION)
            .put("content_order", "text_then_images")
        fun save() { jsonFile.writeText(result.toString(2)) }
        fun temperature() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", -1) ?: -1
        fun memory(): JSONObject {
            val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            return JSONObject().put("total_pss_kb", info.totalPss).put("native_pss_kb", info.nativePss)
        }
        save()
        val lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lami:multi-image-test").apply { acquire(600_000L) }
        var activity: ActivityScenario<MainActivity>? = null
        try {
            check(temperature() in 0 until 400) { "Thermal guard: temperature unavailable or battery at least 40C" }
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val capability = resolveLocalImageInputCapability(model, File(model).name, true, backend)
                as? LocalImageInputCapability.Supported ?: error("Model/backend fingerprint rejected")
            result.put("model_sha256", capability.sha256).put("vision_backend", capability.visionBackend.name)
                .put("vision_adapter_backend", "CPU")
            result.put("native_jni_sha256", sha256(File(context.applicationInfo.nativeLibraryDir, "liblitertlm_jni.so")))
            val fixtures = fixtureOrder.take(count)
            val inputs = prepareLocalImageInputs(context, fixtures.map { fixture ->
                val source = File(context.filesDir, "${stem}_${fixture.label}.png")
                drawFixture(fixture).let { bitmap ->
                    source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                Uri.fromFile(source)
            }, persistent = true)
            val paths = inputs.map { it.absolutePath }
            result.put("fixtures", JSONArray(fixtures.map { JSONObject().put("label", it.label).put("color", it.color).put("shape", it.shape).put("dot_count", it.dotCount) }))
                .put("image_paths", JSONArray(paths))
                .put("image_sha256", JSONArray(paths.map { path -> MessageDigest.getInstance("SHA-256").digest(File(path).readBytes()).joinToString("") { "%02x".format(it) } }))
            val coldPrompt = "Per image: read its letter and the fill color and type of the large shape below it. One line <letter>:<color>:<shape>, in input order. English only."
            val cold = executePhase(context, backend, capability, "cold", coldPrompt, paths, emptyList(), result, traceFile, ::temperature, ::memory, ::save)
            val historyReply = if (historyMode == "fixed") "The images are available for the next question." else cold.first
            result.put("restored_history_model_text", historyReply)
            val restoredHistory = listOf(
                LocalConversationTurn(LocalConversationRole.USER, coldPrompt, paths),
                LocalConversationTurn(LocalConversationRole.MODEL, historyReply),
            )
            val alive = inputs.all { File(it.absolutePath).isFile && Uri.parse(it.savedUriString).path == it.absolutePath }
            result.put("image_history_uris_valid", alive)
            check(alive) { "Saved image URI did not survive the first turn" }
            check(temperature() in 0 until 400) { "Thermal guard before history turn" }
            val middle = count / 2
            val targets = listOf(fixtures.first(), fixtures[middle], fixtures.last())
            val followup = "For shapes marked ${targets.joinToString(", ") { it.label }}, count the black dots inside each. One line <letter>:<number>, in that order. English only."
            val warm = executePhase(context, backend, capability, "warm", followup, emptyList(), restoredHistory, result, traceFile, ::temperature, ::memory, ::save)
            result.put("cold_assessment", assessmentJson(LocalMultiImageAssessmentPolicy.assess(cold.first, fixtures)))
                .put("warm_assessment", dotAssessmentJson(LocalMultiImageAssessmentPolicy.assessDots(warm.first, targets)))
                .put("all_inputs_accepted", cold.second && warm.second)
                .put("status", if (cold.second && warm.second) "completed" else "native_failure")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("error", "${failure.javaClass.simpleName}: ${failure.message}")
            traceFile.appendText(failure.stackTraceToString())
        } finally {
            result.put("temperature_end_tenths_c", temperature())
            save()
            if (backend == PreferredBackendDryRunSetting.NPU) NpuKotlinConversationProductRoute.reset("multi_image_test_done")
            activity?.close()
            if (lock.isHeld) lock.release()
        }
        assertTrue(result.toString(), result.optBoolean("all_inputs_accepted"))
    }

    private suspend fun executePhase(
        context: Context, backend: PreferredBackendDryRunSetting, capability: LocalImageInputCapability.Supported,
        phase: String, prompt: String, images: List<String>, history: List<LocalConversationTurn>,
        result: JSONObject, traceFile: File, temperature: () -> Int, memory: () -> JSONObject, save: () -> Unit,
    ): Pair<String, Boolean> {
        val available = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val minimumMemory = if (phase == "warm" && backend == PreferredBackendDryRunSetting.NPU) 768L * 1024 * 1024 else 2L * 1024 * 1024 * 1024
        check(!available.lowMemory && available.availMem >= minimumMemory) { "Memory guard: available=${available.availMem}, required=$minimumMemory" }
        val metrics = JSONObject().put("prompt", prompt).put("new_image_count", images.size)
            .put("history_image_count", localImageCapacity(emptyList(), history))
            .put("temperature_start_tenths_c", temperature()).put("memory_before", memory())
            .put("available_memory_before_bytes", available.availMem)
            .put("warm_kind", if (phase == "cold") "new_engine_disk_caches_not_cleared" else if (backend == PreferredBackendDryRunSetting.NPU) "reused_engine_recreated_conversation" else "new_engine_with_warm_disk_cache")
        result.put(phase, metrics).put("status", "$phase-running")
        save()
        if (phase == "cold" && backend == PreferredBackendDryRunSetting.NPU) NpuKotlinConversationProductRoute.reset("multi_image_cold")
        val started = SystemClock.elapsedRealtime()
        var firstPartial: Long? = null
        var response = ""
        var initializedAt: Long? = null
        val partial: (String) -> Unit = { text ->
            if (firstPartial == null) {
                firstPartial = SystemClock.elapsedRealtime() - started
                metrics.put("first_partial_ms", firstPartial).put("memory_first_partial", memory())
                metrics.put("runtime_libraries", JSONArray(File("/proc/self/maps").readLines()
                    .filter { line -> listOf("libQnnHtp", "libLiteRtDispatch_Qualcomm", "libLiteRtOpenClAccelerator", "liblitertlm_jni").any(line::contains) }
                    .map { it.substringAfterLast('/').substringBefore(' ') }.distinct()))
                metrics.put("accelerator_fds", JSONArray(File("/proc/self/fd").listFiles().orEmpty()
                    .mapNotNull { runCatching { Os.readlink(it.absolutePath) }.getOrNull() }
                    .filter { it.contains("fastrpc") || it.contains("kgsl") }.distinct()))
            }
            response = text
        }
        val trace: (String) -> Unit = { line ->
            val elapsed = SystemClock.elapsedRealtime() - started
            traceFile.appendText("$phase ${elapsed}ms $line\n")
            metrics.put("last_stage", line.take(1200)).put("elapsed_ms", elapsed)
            if (line.contains("engine-initialize-success")) initializedAt = elapsed
            Regex("engine_created_ms=(\\d+)").find(line)?.let { initializedAt = it.groupValues[1].toLong() }
            save()
        }
        var accepted = false
        try {
            withTimeout(180_000L) {
                if (backend == PreferredBackendDryRunSetting.NPU) {
                    val attempt = NpuKotlinConversationProductRoute.run(
                        context, -200, prompt, history, capability.modelPath, 128,
                        images, MarkdownStreamingMode.EDGE_GALLERY_COMPAT, partial, trace,
                    )
                    response = attempt.result?.sanitizedOutput ?: response
                    accepted = attempt.succeeded
                    metrics.put("engine_reused", attempt.engineReused).put("conversation_reused", attempt.conversationReused)
                        .put("failure_reason", attempt.failureReason)
                } else {
                    val output = runLocalImageConversation(context, capability, prompt, images, history,
                        MarkdownStreamingMode.EDGE_GALLERY_COMPAT, partial, trace)
                    response = output.response
                    accepted = true
                    metrics.put("engine_reused", false).put("conversation_reused", false)
                }
            }
            return response to accepted
        } catch (failure: Throwable) {
            metrics.put("error", "${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        } finally {
            metrics.put("total_ms", SystemClock.elapsedRealtime() - started).put("first_partial_ms", firstPartial ?: JSONObject.NULL)
                .put("engine_initialize_elapsed_ms", initializedAt ?: JSONObject.NULL).put("response", response)
                .put("native_accepted", accepted).put("temperature_end_tenths_c", temperature()).put("memory_after", memory())
            save()
        }
    }

    private fun assessmentJson(score: LocalMultiImageAssessment): JSONObject = JSONObject()
        .put("labels", JSONArray(score.labels)).put("missing", JSONArray(score.missing))
        .put("duplicates", JSONArray(score.duplicates)).put("unexpected", JSONArray(score.unexpected))
        .put("ordered", score.ordered).put("distinct_ordered", score.distinctOrdered)
        .put("all_recognized", score.allRecognized).put("endpoints_recognized", score.endpointsRecognized)
        .put("individual", JSONArray(score.recognitions.map { item -> JSONObject().put("label", item.fixture.label)
            .put("expected_color", item.fixture.color).put("expected_shape", item.fixture.shape).put("body", item.body)
            .put("color_correct", item.colorCorrect).put("shape_correct", item.shapeCorrect) }))

    private fun dotAssessmentJson(score: LocalMultiImageDotAssessment): JSONObject = JSONObject()
        .put("labels", JSONArray(score.labels)).put("missing", JSONArray(score.missing)).put("ordered", score.ordered)
        .put("all_correct", score.allCorrect).put("reported", JSONObject(score.reported)).put("correct", JSONObject(score.correct))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun drawFixture(fixture: LocalMultiImageFixture): Bitmap {
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 86f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        canvas.drawText(fixture.label, 192f, 99f, paint)
        val shape = Path()
        when (fixture.shape) {
            "circle" -> shape.addCircle(192f, 245f, 90f, Path.Direction.CW)
            "square" -> shape.addRect(102f, 155f, 282f, 335f, Path.Direction.CW)
            "triangle" -> { shape.moveTo(192f, 145f); shape.lineTo(92f, 335f); shape.lineTo(292f, 335f); shape.close() }
            "star" -> {
                for (index in 0..9) {
                    val angle = Math.toRadians(-90.0 + index * 36.0)
                    val radius = if (index % 2 == 0) 100.0 else 45.0
                    val x = (192 + radius * kotlin.math.cos(angle)).toFloat()
                    val y = (245 + radius * kotlin.math.sin(angle)).toFloat()
                    if (index == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
                }
                shape.close()
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = when (fixture.color) {
            "red" -> Color.RED; "blue" -> Color.BLUE; "green" -> Color.rgb(0, 180, 0)
            "yellow" -> Color.YELLOW; "cyan" -> Color.CYAN; "magenta" -> Color.MAGENTA; else -> Color.BLACK
        }
        canvas.drawPath(shape, paint)
        paint.color = Color.BLACK; paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f
        canvas.drawPath(shape, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (fixture.color == "black") Color.WHITE else Color.BLACK
        for (dot in 0 until fixture.dotCount) {
            val x = 192f + (dot - (fixture.dotCount - 1) / 2f) * 35f
            canvas.drawCircle(x, 245f, 10f, paint)
        }
        return bitmap
    }

    private val fixtureOrder = listOf(
        LocalMultiImageFixture("F", "cyan", "square", 3), LocalMultiImageFixture("B", "blue", "square", 2),
        LocalMultiImageFixture("H", "red", "star", 2), LocalMultiImageFixture("A", "red", "circle", 1),
        LocalMultiImageFixture("J", "green", "square", 1), LocalMultiImageFixture("D", "yellow", "star", 1),
        LocalMultiImageFixture("G", "black", "triangle", 1), LocalMultiImageFixture("C", "green", "triangle", 3),
        LocalMultiImageFixture("I", "blue", "circle", 3), LocalMultiImageFixture("E", "magenta", "circle", 2),
    )
}
