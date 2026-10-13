package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import android.os.Debug
import android.os.PowerManager
import android.system.Os
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.MainActivity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Manual native benchmark. Run only in the isolated imageBenchmark APK. */
@RunWith(AndroidJUnit4::class)
class LocalImageInferenceBenchmarkInstrumentedTest {
    @Test
    fun inferImagesAndRecordTimings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Manual benchmark requires an explicit imageBackend argument", args.containsKey("imageBackend"))
        check(context.packageName.endsWith(".imagebench")) { "Refusing to operate the main/TTS APK" }
        val backend = PreferredBackendDryRunSetting.valueOf(args.getString("imageBackend", "CPU"))
        val count = args.getString("imageCount", "1").toInt()
        val capacityProbe = args.getString("imageCapacityProbe", "false").toBoolean()
        val endpoints = args.getString("imagePrompt", "counts") == "endpoints"
        val warmFollowup = args.getString("imageWarmFollowup", "false").toBoolean()
        require(!warmFollowup || (backend == PreferredBackendDryRunSetting.NPU && count == 1 && !capacityProbe))
        require(count in 1..if (capacityProbe) 16 else NpuImageInputPolicy.MAX_IMAGES)
        val model = args.getString("imageModel") ?: if (backend == PreferredBackendDryRunSetting.NPU) {
            "/data/local/tmp/lami_npu_durability_model.litertlm"
        } else {
            "/data/local/tmp/lami_gpu_model.litertlm"
        }
        val stem = "image_benchmark_${backend}_${count}" + if (capacityProbe) "_capacity" else ""
        val resultFile = File(context.filesDir, "$stem.json")
        val traceFile = File(context.filesDir, "$stem.trace")
        val result = JSONObject()
            .put("backend", backend.name)
            .put("image_count", count)
            .put("model", model)
            .put("model_bytes", File(model).length())
            .put("apk_commit", BuildConfig.GIT_SHA)
            .put("api_version", BuildConfig.LITERTLM_ANDROID_VERSION)
            .put("package", context.packageName)
            .put("context_tokens", NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT)
            .put("capacity_probe", capacityProbe)
            .put("prompt_profile", if (endpoints) "endpoints" else "counts")
            .put("content_order", if (capacityProbe) "images_then_text" else "text_then_images")
            .put("protocol_version", 3)
            .put("status", "preparing")
        fun save() { resultFile.writeText(result.toString(2)) }
        save()
        traceFile.writeText("")
        var started = 0L
        var initStarted: Long? = null
        var initEnded: Long? = null
        var firstPartial: Long? = null
        var response = ""
        var activity: ActivityScenario<MainActivity>? = null
        var wakeLock: PowerManager.WakeLock? = null
        val memorySamples = JSONArray()
        fun memory(stage: String) {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            memorySamples.put(JSONObject().put("stage", stage).put("total_pss_kb", info.totalPss)
                .put("native_pss_kb", info.nativePss).put("dalvik_pss_kb", info.dalvikPss))
            result.put("memory_samples", memorySamples)
            if (stage == "first_partial") {
                result.put("loaded_runtime_libraries", JSONArray(File("/proc/self/maps").readLines()
                    .filter { line -> listOf("libQnnHtp", "libLiteRtDispatch_Qualcomm", "libLiteRtOpenClAccelerator", "liblitertlm_jni").any(line::contains) }
                    .map { it.substringAfterLast('/').substringBefore(' ') }.distinct()))
                result.put("accelerator_device_fds", JSONArray(File("/proc/self/fd").listFiles().orEmpty()
                    .mapNotNull { runCatching { Os.readlink(it.absolutePath) }.getOrNull() }
                    .filter { it.contains("fastrpc") || it.contains("adsprpc") || it.contains("kgsl") }.distinct()))
            }
        }
        fun trace(line: String) {
            val elapsed = if (started == 0L) 0L else SystemClock.elapsedRealtime() - started
            traceFile.appendText("${elapsed}ms $line\n")
            if (line.contains("engine-initialize-start") || line.contains("engine_initialize_start")) initStarted = elapsed
            if (line.contains("engine-initialize-success") || line.contains("engine_initialize_success")) initEnded = elapsed
            Regex("engine_created_ms=(\\d+)").find(line)?.let {
                result.put("engine_create_and_initialize_ms", it.groupValues[1].toLong())
            }
            result.put("last_stage", line.take(1800)).put("elapsed_ms", elapsed)
            save()
        }
        try {
            wakeLock = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lami:image-benchmark")
                .apply { acquire(240_000L) }
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val capability = resolveLocalImageInputCapability(
                selectedModelPath = model,
                selectedModelDisplayName = File(model).name,
                allowUnverifiedGenericVision = true,
                preferredBackend = backend,
            ) as? LocalImageInputCapability.Supported ?: error("Model fingerprint/backend rejected")
            result.put("model_sha256", capability.sha256)
            result.put("vision_backend", capability.visionBackend.name)
            result.put("vision_adapter_backend", "CPU")
            val uris = (0 until count).map { index ->
                val source = File(context.filesDir, "${stem}_source_$index.png")
                val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = if (index == count - 1) Color.BLUE else Color.RED
                if (index == count - 1) canvas.drawRect(80f, 80f, 304f, 304f, paint)
                else canvas.drawCircle(192f, 192f, 112f, paint)
                source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                Uri.fromFile(source)
            }
            val inputs = uris.chunked(NpuImageInputPolicy.MAX_IMAGES).flatMap {
                prepareLocalImageInputs(context, it, persistent = false)
            }
            result.put("image_paths", JSONArray(inputs.map { it.absolutePath }))
            result.put("expected_answer", if (count == 1) "blue square" else if (endpoints) "first red circle, last blue square" else "${count - 1} red circles, 1 blue square")
            val prompt = if (endpoints && count > 1) {
                "Describe the first and last images: their shapes and colors. Answer in English in one short sentence."
            } else if (count == 1) {
                "Describe the shape and its color in the image. Answer in English in at most 6 words."
            } else {
                "Look at all $count images. Count red circles and blue squares separately. Answer in English in one short sentence."
            }
            result.put("prompt", prompt).put("status", "running")
            save()
            started = SystemClock.elapsedRealtime()
            memory("before_engine")
            val partial: (String) -> Unit = { text ->
                if (firstPartial == null) {
                    firstPartial = SystemClock.elapsedRealtime() - started
                    memory("first_partial")
                }
                response = text
            }
            withTimeout(180_000L) {
                if (capacityProbe) {
                    val languageBackend = when (backend) {
                        PreferredBackendDryRunSetting.CPU -> Backend.CPU()
                        PreferredBackendDryRunSetting.GPU -> Backend.GPU()
                        PreferredBackendDryRunSetting.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
                        else -> error("Capacity probe needs an explicit backend")
                    }
                    val cache = File(context.cacheDir, "image_capacity_$backend").apply { mkdirs() }
                    Engine(EngineConfig(
                        modelPath = model, backend = languageBackend,
                        visionBackend = if (backend == PreferredBackendDryRunSetting.GPU) Backend.GPU() else Backend.CPU(),
                        audioBackend = null, maxNumTokens = 4096, maxNumImages = count, cacheDir = cache.absolutePath,
                    )).use { engine ->
                        trace("capacity engine-initialize-start")
                        engine.initialize()
                        trace("capacity engine-initialize-success")
                        engine.createConversation(LocalConversationPolicy.conversationConfig()).use { conversation ->
                            val contents = Contents.of(buildList {
                                inputs.forEach { add(Content.ImageFile(it.absolutePath)) }
                                add(Content.Text(prompt))
                            })
                            val builder = StringBuilder()
                            try {
                                conversation.sendMessageAsync(contents, LocalConversationPolicy.generationExtraContext).collect {
                                    builder.append(it.contents.toString())
                                    partial(builder.toString())
                                }
                            } finally {
                                if (builder.isEmpty()) runCatching { conversation.cancelProcess() }
                            }
                            response = builder.toString().trim()
                            result.put("route_success", response.isNotBlank())
                            trace("capacity generation-complete")
                        }
                    }
                } else if (backend == PreferredBackendDryRunSetting.NPU) {
                    NpuKotlinConversationProductRoute.reset("image_benchmark_cold", ::trace)
                    val attempt = NpuKotlinConversationProductRoute.run(
                        context = context, chatId = -100 - count, userPrompt = prompt,
                        initialTurns = emptyList(), selectedModelFile = model, requestedMaxOutputTokens = 128,
                        imageFilePaths = inputs.map { it.absolutePath },
                        markdownStreamingMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
                        onPartial = partial, trace = ::trace,
                    )
                    result.put("route_success", attempt.succeeded).put("failure_reason", attempt.failureReason)
                    response = attempt.result?.sanitizedOutput ?: response
                    if (warmFollowup && attempt.succeeded) {
                        val coldTotal = SystemClock.elapsedRealtime() - started
                        result.put("cold_total_ms", coldTotal).put("cold_first_partial_ms", firstPartial)
                        val warmStarted = SystemClock.elapsedRealtime()
                        var warmFirstPartial: Long? = null
                        val warm = NpuKotlinConversationProductRoute.run(
                            context = context, chatId = -100 - count,
                            userPrompt = "What color was the square in my previous image? Answer in English in at most 4 words.",
                            initialTurns = listOf(
                                LocalConversationTurn(LocalConversationRole.USER, prompt, inputs.map { it.absolutePath }),
                                LocalConversationTurn(LocalConversationRole.MODEL, response),
                            ), selectedModelFile = model, requestedMaxOutputTokens = 64,
                            imageFilePaths = emptyList(), markdownStreamingMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
                            onPartial = { if (warmFirstPartial == null) warmFirstPartial = SystemClock.elapsedRealtime() - warmStarted },
                            trace = ::trace,
                        )
                        result.put("warm_total_ms", SystemClock.elapsedRealtime() - warmStarted)
                            .put("warm_first_partial_ms", warmFirstPartial ?: JSONObject.NULL)
                            .put("warm_engine_reused", warm.engineReused)
                            .put("warm_conversation_reused", warm.conversationReused)
                            .put("warm_response", warm.result?.sanitizedOutput)
                            .put("warm_semantic_pass", warm.succeeded && warm.result?.sanitizedOutput.orEmpty().lowercase().contains("blue"))
                    }
                } else {
                    val attempt = runLocalImageConversation(
                        context = context, capability = capability, prompt = prompt,
                        imageFilePaths = inputs.map { it.absolutePath }, history = emptyList(),
                        markdownStreamingMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
                        onPartial = partial, appendTrace = ::trace,
                    )
                    response = attempt.response
                    result.put("route_success", true)
                }
            }
            result.put("total_ms", SystemClock.elapsedRealtime() - started)
            result.put("first_partial_ms", firstPartial ?: JSONObject.NULL)
            result.put("initialize_ms", if (initStarted != null && initEnded != null) initEnded!! - initStarted!! else JSONObject.NULL)
            result.put("response", response)
            val lower = response.lowercase()
            val semanticPass = if (count == 1) {
                lower.contains("blue") && lower.contains("square")
            } else if (endpoints) {
                lower.contains("red") && lower.contains("circle") && lower.contains("blue") && lower.contains("square")
            } else {
                val numberWords = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
                lower.contains("red") && lower.contains("blue") &&
                    (lower.contains((count - 1).toString()) || numberWords.getOrNull(count - 1)?.let(lower::contains) == true) &&
                    (lower.contains("1") || lower.contains("one"))
            }
            result.put("semantic_pass", semanticPass)
            memory("after_generation")
            result.put("status", if (result.optBoolean("route_success") && semanticPass &&
                (!warmFollowup || result.optBoolean("warm_semantic_pass"))) "passed" else "failed")
        } catch (failure: Throwable) {
            result.put("status", "failed").put("error", "${failure.javaClass.simpleName}: ${failure.message}")
            result.put("response", response)
            result.put("total_ms", if (started == 0L) JSONObject.NULL else SystemClock.elapsedRealtime() - started)
            result.put("first_partial_ms", firstPartial ?: JSONObject.NULL)
            traceFile.appendText(failure.stackTraceToString())
        } finally {
            save()
            if (backend == PreferredBackendDryRunSetting.NPU) {
                NpuKotlinConversationProductRoute.reset("image_benchmark_done", ::trace)
            }
            activity?.close()
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        val expectedCapacityRejection = capacityProbe && count == 16 &&
            result.optString("error").contains("Input token ids are too long") &&
            result.optString("error").contains("4096")
        assertTrue(result.toString(), result.optString("status") == "passed" || expectedCapacityRejection)
    }
}
