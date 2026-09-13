package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

internal object GpuIdlePrewarmDiagnostic {
    private const val TAG = "GpuIdlePrewarm"
    private const val STATE_FILE = "gpu_idle_prewarm_state.txt"
    private const val DEFAULT_IDLE_DELAY_MS = 2_000L
    private const val BACKEND_KEY = "text=GPU/vision=GPU/audio=CPU/requested=GPU/text=GPU"

    suspend fun run(
        context: Context,
        holder: LocalInferenceEngineHolder,
        request: GpuIdlePrewarmRequest,
    ) {
        val appContext = context.applicationContext
        val generation = GpuIdlePrewarmCancellationSignal.snapshot()
        val modelFile = File(request.modelPath)
        val engineKey = HeldEngineKey(
            modelPath = modelFile.absolutePath,
            backendKey = BACKEND_KEY,
            cacheDirPath = request.cacheDirPath,
        )
        val holderSnapshot = holder.getDevDiagnosticSnapshot()
        val eligibility = resolveGpuIdlePrewarmEligibility(
            GpuIdlePrewarmEligibilityInput(
                debugBuild = BuildConfig.DEBUG,
                featureEnabled = readBooleanProperty("debug.lami.gpu_idle_prewarm"),
                localTargetSelected = request.localTargetSelected,
                preferredBackend = request.preferredBackend,
                inferenceActive = request.inferenceActive,
                appInForeground = holderSnapshot.appInForeground,
                verifiedGpuRuntime = BuildConfig.STANDARD_GPU_OPENCL_RUNTIME,
                modelExists = modelFile.isFile,
                modelSizeBytes = modelFile.takeIf { it.isFile }?.length() ?: -1L,
            ),
        )
        if (!eligibility.eligible) {
            writeState(appContext, "status=skipped\nreason=${eligibility.reason}\n")
            return
        }

        val idleDelayMs = readLongProperty("debug.lami.gpu_idle_prewarm_delay_ms")
            ?.coerceIn(250L, 30_000L)
            ?: DEFAULT_IDLE_DELAY_MS
        writeState(
            appContext,
            "status=waiting\nreason=idle_delay\nidle_delay_ms=$idleDelayMs\n" +
                "model_path=${modelFile.absolutePath}\nmodel_bytes=${modelFile.length()}\n",
        )
        try {
            delay(idleDelayMs)
            currentCoroutineContext().ensureActive()
            if (!GpuIdlePrewarmCancellationSignal.isCurrent(generation)) {
                writeCancelled(appContext, "cancelled_before_create")
                return
            }
            val beforePssKb = Debug.getPss()
            val beforeNativeBytes = Debug.getNativeHeapAllocatedSize()
            val beforeJavaBytes = usedJavaHeapBytes()
            val startedAtMs = SystemClock.elapsedRealtime()
            writeState(
                appContext,
                "status=initializing\nstarted_at_elapsed_ms=$startedAtMs\n" +
                    "pss_before_kb=$beforePssKb\nnative_before_bytes=$beforeNativeBytes\n" +
                    "java_before_bytes=$beforeJavaBytes\n",
            )
            val result = withContext(Dispatchers.IO) {
                holder.prewarmForDebug(
                    engineKey = engineKey,
                    preferredBackend = request.preferredBackend,
                    generationIsCurrent = {
                        GpuIdlePrewarmCancellationSignal.isCurrent(generation)
                    },
                    appendTrace = { message -> Log.i(TAG, message) },
                )
            }
            if (!GpuIdlePrewarmCancellationSignal.isCurrent(generation)) {
                releaseUnusedPrewarm(holder, engineKey)
                writeCancelled(appContext, "cancelled_after_create")
                return
            }
            currentCoroutineContext().ensureActive()
            val finishedAtMs = SystemClock.elapsedRealtime()
            val afterPssKb = Debug.getPss()
            val afterNativeBytes = Debug.getNativeHeapAllocatedSize()
            val afterJavaBytes = usedJavaHeapBytes()
            writeState(
                appContext,
                buildString {
                    appendLine("status=${result.status}")
                    appendLine("engine_create_ms=${result.engineCreateMs ?: -1L}")
                    appendLine("total_ms=${finishedAtMs - startedAtMs}")
                    appendLine("pss_before_kb=$beforePssKb")
                    appendLine("pss_after_kb=$afterPssKb")
                    appendLine("pss_delta_kb=${afterPssKb - beforePssKb}")
                    appendLine("native_before_bytes=$beforeNativeBytes")
                    appendLine("native_after_bytes=$afterNativeBytes")
                    appendLine("native_delta_bytes=${afterNativeBytes - beforeNativeBytes}")
                    appendLine("java_before_bytes=$beforeJavaBytes")
                    appendLine("java_after_bytes=$afterJavaBytes")
                    appendLine("java_delta_bytes=${afterJavaBytes - beforeJavaBytes}")
                    appendLine("failure_stage=${result.failureStage ?: "none"}")
                    appendLine("failure_class=${result.failureClassName ?: "none"}")
                    appendLine("failure_message=${result.failureMessage ?: "none"}")
                    appendLine("generation=$generation")
                },
            )
            Log.i(TAG, "status=${result.status} engine_create_ms=${result.engineCreateMs}")
        } catch (cancelled: CancellationException) {
            releaseUnusedPrewarm(holder, engineKey)
            writeCancelled(appContext, "coroutine_cancelled")
            throw cancelled
        }
    }

    private suspend fun releaseUnusedPrewarm(
        holder: LocalInferenceEngineHolder,
        engineKey: HeldEngineKey,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            holder.releaseUnusedDebugPrewarm(
                engineKey = engineKey,
                reason = "debug-idle-prewarm-${GpuIdlePrewarmCancellationSignal.reason()}",
                appendTrace = { message -> Log.i(TAG, message) },
            )
        }
    }

    private fun writeCancelled(context: Context, fallback: String) {
        val reason = GpuIdlePrewarmCancellationSignal.reason().takeIf { it != "none" } ?: fallback
        writeState(context, "status=cancelled\nreason=$reason\n")
        Log.i(TAG, "status=cancelled reason=$reason")
    }
    private fun writeState(context: Context, text: String) {
        runCatching { File(context.filesDir, STATE_FILE).writeText(text) }
            .onFailure { Log.w(TAG, "state write failed", it) }
    }

    private fun usedJavaHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun readBooleanProperty(key: String): Boolean {
        val value = readProperty(key) ?: return false
        return value.equals("true", ignoreCase = true) || value == "1"
    }

    private fun readLongProperty(key: String): Long? = readProperty(key)?.toLongOrNull()

    private fun readProperty(key: String): String? {
        System.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
