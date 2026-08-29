package io.github.ninbyo02.lami.ui.screens.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver

internal class StandardNpuReleasePreflightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        Thread({
            val appContext = context.applicationContext
            val outputDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val statusFile = outputDir.resolve(STATUS_FILE)
            val requestedMode = intent.getStringExtra(EXTRA_MODE).orEmpty()
            val mode = requestedMode.ifBlank { MODE_DISPATCH_API_PREFLIGHT }
            statusFile.writeText("status=started\nmode=$mode\nprocess=npu_preflight\n")
            try {
                require(mode in ALLOWED_MODES) { "unsupported preflight mode: $mode" }
                if (mode == MODE_STANDARD_ROUTE_TURN) {
                    val userPrompt = decodeExtra(intent, EXTRA_USER_PROMPT_BASE64)
                    val contextText = decodeExtra(intent, EXTRA_CONTEXT_BASE64)
                    require(userPrompt.isNotBlank()) { "user_prompt_base64 is required" }
                    val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(
                        userPrompt = userPrompt,
                        contextText = contextText,
                    )
                    val request = RealNpuStandardRouteS1Provider.request(
                        userPrompt = promptRewrite.rewrittenPromptText,
                        contextText = if (promptRewrite.contextualFactEmbedded) "" else contextText,
                        maxOutputTokens = intent.getIntExtra(EXTRA_MAX_OUTPUT_TOKENS, 32),
                    )
                    val display = NpuStandardRoutePersistentProbeRunner.run(
                        context = appContext,
                        request = request,
                    )
                    val outputBase64 = Base64.encodeToString(
                        display.output.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    )
                    statusFile.writeText(
                        "status=returned\nmode=$mode\nroute_status=${display.status}\n" +
                            "reason=${display.reason}\noutput_base64=$outputBase64\n" +
                            "decode_reached=${display.decodeReached}\n" +
                            "npu_evidence=${display.npuEvidence}\n" +
                            "fallback=${display.fallback}\ntimeout=${display.timeout}\n",
                    )
                } else {
                    val model = Qairt244ModelPathResolver.resolve(appContext)
                    require(model.resolved) { "model_resolution_failed:${model.reasonCode}" }
                    val result = Qairt244ShortMultitokenSmoke.runPersistentProbe(
                        context = appContext,
                        modelPath = requireNotNull(model.path),
                        runId = "standard_npu_release_preflight_${System.currentTimeMillis()}",
                        prompt = "東京は日本の首都です。",
                        maxOutputTokens = 1,
                        runCount = 1,
                        holderKey = "standard_npu_release_preflight_v1",
                        nativeProbeMode = mode,
                        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
                    )
                    statusFile.writeText(
                        "status=returned\nmode=$mode\nnative_return=${result.nativeReturn}\n" +
                            "throwable_class=${result.throwableClass}\n" +
                            "throwable_message=${result.throwableMessage}\n",
                    )
                }
            } catch (throwable: Throwable) {
                statusFile.writeText(
                    "status=failed\nmode=$mode\nthrowable_class=${throwable.javaClass.name}\n" +
                        "throwable_message=${throwable.message.orEmpty()}\n",
                )
            } finally {
                pending.finish()
            }
        }, "StandardNpuReleasePreflight").start()
    }

    private fun decodeExtra(intent: Intent, name: String): String {
        val encoded = intent.getStringExtra(name).orEmpty()
        if (encoded.isBlank()) return ""
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }

    private companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.STANDARD_NPU_RELEASE_PREFLIGHT"
        const val EXTRA_MODE = "native_probe_mode"
        const val EXTRA_USER_PROMPT_BASE64 = "user_prompt_base64"
        const val EXTRA_CONTEXT_BASE64 = "context_base64"
        const val EXTRA_MAX_OUTPUT_TOKENS = "max_output_tokens"
        const val STATUS_FILE = "standard_npu_release_preflight_status.txt"
        const val MODE_DISPATCH_API_PREFLIGHT = "dispatch_api_preflight"
        const val MODE_STANDARD_ROUTE_TURN = "standard_route_turn"
        val ALLOWED_MODES = setOf(
            MODE_DISPATCH_API_PREFLIGHT,
            MODE_STANDARD_ROUTE_TURN,
            "dispatch_initialize_preflight",
            "before_engine_create",
            "engine_create_only",
        )
    }
}
