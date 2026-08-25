package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.Build
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import io.github.ninbyo02.lami.npu.Qairt244DevOnlyNpuRouteAdapter
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import kotlinx.coroutines.runBlocking

internal class RealNpuStandardRouteS1Provider(
    private val requestRunner: (DevOnlyNpuOneTurnConversationRequest) -> DevOnlyNpuOneTurnConversationDisplay = { request ->
        val appContext = resolveApplicationContext()
            ?: error(REASON_DEV_ONLY_ENTRY_UNAVAILABLE)
        if (BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
            NpuStandardRoutePersistentProbeRunner.run(
                context = appContext,
                request = request,
            )
        } else {
            runBlocking {
                DevOnlyNpuOneTurnConversationEntry(appContext).run(request)
            }
        }
    },
) : NpuStandardRouteS1Provider {
    override fun invoke(
        userPrompt: String,
        maxOutputTokens: Int,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult = invokeWithContext(
        userPrompt = userPrompt,
        contextText = "",
        maxOutputTokens = maxOutputTokens,
        trace = trace,
    )

    override fun invokeWithContext(
        userPrompt: String,
        contextText: String,
        maxOutputTokens: Int,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult {
        val maxOutputTokensResolution = NpuStandardRoutePreferences.resolveNativeMaxOutputTokens(maxOutputTokens)
        val effectiveMaxOutputTokens = NpuStandardRouteS1Contract.maxOutputTokensForPrompt(
            userPrompt = userPrompt,
            requestedMaxOutputTokens = maxOutputTokensResolution.effectiveMaxOutputTokens,
        )
        val maxOutputTokensClamped =
            maxOutputTokensResolution.clamped ||
                effectiveMaxOutputTokens != maxOutputTokensResolution.requestedMaxOutputTokens
        NpuEngineLogcatDiagnostics.i(
            event = "s1_engine_request_start",
            route = "RealNpuStandardRouteS1Provider.invoke",
            probeName = "npu_s1_provider",
            backendRequested = "NPU",
            maxOutputTokens = effectiveMaxOutputTokens,
            memorySnapshot = resolveApplicationContext()?.let { appContext ->
                captureLocalMemorySnapshot(appContext, "s1_engine_request_start")
            },
            detail = "prompt_length=${userPrompt.length} " +
                "requested_max_output_tokens=${maxOutputTokensResolution.requestedMaxOutputTokens} " +
                "effective_max_output_tokens=${effectiveMaxOutputTokens} " +
                "max_output_tokens_clamped=${maxOutputTokensClamped}",
        )
        return runCatching {
            trace(buildNpuRealPromptHandoffTrace(stage = "provider", userPrompt = userPrompt))
            val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(userPrompt)
            val nativeRequest = request(
                userPrompt = promptRewrite.rewrittenPromptText,
                contextText = contextText,
                maxOutputTokens = effectiveMaxOutputTokens,
            )
            trace(buildNpuRealPromptRequestTrace(nativeRequest))
            val mappedRawResult = RealNpuStandardRouteS1ResultMapper.fromDisplay(
                display = requestRunner(nativeRequest),
                userPrompt = userPrompt,
            )
            val providerStage = if (mappedRawResult.status == NpuStandardRouteS1Contract.STATUS_SUCCESS) {
                NPU_S1_NATIVE_STAGE_PROVIDER_SUCCESS
            } else {
                NPU_S1_NATIVE_STAGE_PROVIDER_FAILURE
            }
            val resolvedModel = resolveApplicationContext()
                ?.let(Qairt244ModelPathResolver::resolve)
                ?.takeIf { it.resolved }
            val resolvedModelInfo = resolvedModel?.modelInfo
            val rawResult = mappedRawResult.copy(
                requestedMaxOutputTokens = maxOutputTokensResolution.requestedMaxOutputTokens,
                effectiveMaxOutputTokens = effectiveMaxOutputTokens,
                selectedModelName = resolvedModelInfo?.canonicalModelBasename.orEmpty(),
                selectedModelFile = resolvedModel?.path.orEmpty(),
                npuModelEligible = resolvedModelInfo?.required,
                nativeDiagnostics = mappedRawResult.nativeDiagnostics.copy(
                    nativeStageHistory = listOf(
                        NPU_S1_NATIVE_STAGE_PROVIDER_START,
                        mappedRawResult.nativeDiagnostics.nativeStageHistory,
                        providerStage,
                    ).filter { it.isNotBlank() && it != "unavailable" }.joinToString(">"),
                ),
            )
            trace(
                buildNpuRealPromptResultTrace(
                    status = rawResult.status,
                    reason = rawResult.reason,
                    maxOutputTokens = rawResult.effectiveMaxOutputTokens,
                    rawOutput = rawResult.rawOutput,
                    sanitizedOutput = rawResult.sanitizedOutput,
                    qualityClassification = rawResult.qualityClassification,
                    runDecodeReached = rawResult.runDecodeReached,
                    fallbackUsed = rawResult.fallbackUsed,
                    timeout = rawResult.timeout,
                    freshCrash = rawResult.freshCrash,
                ),
            )
            NpuEngineLogcatDiagnostics.i(
                event = if (rawResult.reason.startsWith("adapter_failure")) "s1_adapter_failure" else "s1_decode_success",
                route = "RealNpuStandardRouteS1Provider.invoke",
                probeName = "npu_s1_provider",
                backendRequested = "NPU",
                maxOutputTokens = rawResult.effectiveMaxOutputTokens,
                detail = "status=${rawResult.status} reason=${rawResult.reason} " +
                    "requested_max_output_tokens=${rawResult.requestedMaxOutputTokens} " +
                    "effective_max_output_tokens=${rawResult.effectiveMaxOutputTokens} " +
                    "max_output_tokens_clamped=${maxOutputTokensClamped} " +
                    "run_decode_reached=${rawResult.runDecodeReached} fallback_used=${rawResult.fallbackUsed} " +
                    "timeout=${rawResult.timeout} fresh_crash=${rawResult.freshCrash}",
            )
            rawResult
        }.getOrElse { throwable ->
            val nativeLinkDiagnostics = buildNpuNativeLinkFailureDiagnostics(
                throwable = throwable,
                javaLibraryPath = System.getProperty("java.library.path"),
                supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            )
            val reason = if (nativeLinkDiagnostics.detected) {
                NPU_STANDARD_ROUTE_NATIVE_LINK_FAILURE_REASON
            } else {
                throwable.message
                    ?.takeIf { it.isNotBlank() }
                    ?: REASON_DEV_ONLY_REQUEST_FAILED
            }
            NpuS1LogcatDiagnostics.logAdapterFailure(
                reason = reason,
                throwable = throwable,
                memorySnapshot = resolveApplicationContext()?.let { appContext ->
                    captureLocalMemorySnapshot(
                        context = appContext,
                        stage = "npu_s1_provider_failure",
                    )
                },
                promptLength = userPrompt.length,
                effectiveMaxOutputTokens = effectiveMaxOutputTokens,
            )
            NpuEngineLogcatDiagnostics.e(
                event = "s1_adapter_failure",
                route = "RealNpuStandardRouteS1Provider.invoke",
                throwable = throwable,
                probeName = "npu_s1_provider",
                backendRequested = "NPU",
                maxOutputTokens = effectiveMaxOutputTokens,
                memorySnapshot = resolveApplicationContext()?.let { appContext ->
                    captureLocalMemorySnapshot(appContext, "s1_provider_failure")
                },
                detail = "prompt_length=${userPrompt.length} reason=$reason " +
                    "requested_max_output_tokens=${maxOutputTokensResolution.requestedMaxOutputTokens} " +
                    "effective_max_output_tokens=${effectiveMaxOutputTokens} " +
                    "max_output_tokens_clamped=${maxOutputTokensClamped} " +
                    npuNativeLinkFailureDiagnosticsLines(nativeLinkDiagnostics).joinToString(" "),
            )
            RealNpuStandardRouteS1ResultMapper.failure(
                reason = reason,
                maxOutputTokens = effectiveMaxOutputTokens,
            ).copy(
                requestedMaxOutputTokens = maxOutputTokensResolution.requestedMaxOutputTokens,
                effectiveMaxOutputTokens = effectiveMaxOutputTokens,
                inputPrompt = userPrompt,
                nativeDiagnostics = NpuS1NativeStageDiagnostics(
                    nativeStage = NPU_S1_NATIVE_STAGE_PROVIDER_FAILURE,
                    nativeStageHistory = "$NPU_S1_NATIVE_STAGE_PROVIDER_START>$NPU_S1_NATIVE_STAGE_PROVIDER_FAILURE",
                    nativeErrorClass = throwable.javaClass.simpleName,
                    nativeErrorMessage = throwable.message ?: reason,
                    nativeErrorStage = NPU_S1_NATIVE_STAGE_PROVIDER_FAILURE,
                    nativeErrorSource = "throwable",
                    nativeLinkFailureDetected = nativeLinkDiagnostics.detected.toString(),
                    nativeLinkFailureLibrary = nativeLinkDiagnostics.failedLibraryName,
                    nativeLoadOrder = nativeLinkDiagnostics.loadOrder,
                    javaLibraryPath = nativeLinkDiagnostics.javaLibraryPath,
                    supportedAbis = nativeLinkDiagnostics.supportedAbis,
                ),
            )
        }
    }

    companion object {
        const val REASON_DEV_ONLY_ENTRY_UNAVAILABLE = "dev_only_entry_unavailable"
        const val REASON_DEV_ONLY_REQUEST_FAILED = "dev_only_request_failed"

        fun request(
            userPrompt: String,
            contextText: String = "",
            maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
        ): DevOnlyNpuOneTurnConversationRequest {
            val sanitizedMaxOutputTokens = NpuStandardRoutePreferences.sanitizeMaxOutputTokens(maxOutputTokens)
            return DevOnlyNpuOneTurnConversationRequest(
                userPrompt = userPrompt,
                contextText = contextText,
                unsafeDevBypassPromptLengthGate = true,
                maxOutputTokens = NpuStandardRouteS1Contract.maxOutputTokensForPrompt(
                    userPrompt = userPrompt,
                    requestedMaxOutputTokens = sanitizedMaxOutputTokens,
                ),
                promptTailVariant = NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT,
                timeoutMs = DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS,
            )
        }

        fun buildNpuRealPromptRequestTrace(
            request: DevOnlyNpuOneTurnConversationRequest,
        ): String {
            val promptRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(request.userPrompt)
            val finalInput = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
                contextText = request.contextText,
                userPrompt = request.userPrompt,
                promptTailVariant = request.promptTailVariant,
            )
            return buildString {
                append("NPU_REAL_PROMPT request_prompt_hash=")
                append(npuRealPromptHash(request.userPrompt))
                append(" request_prompt_length=")
                append(request.userPrompt.length)
                append(" request_prompt_code_points=")
                append(request.userPrompt.codePointCount(0, request.userPrompt.length))
                append(" request_prompt_preview=")
                append(npuRealPromptPreview(request.userPrompt))
                append(" prompt_source=")
                append(Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_DEV_ONLY_CONVERSATION)
                append(" context_code_points=")
                append(request.contextText.codePointCount(0, request.contextText.length))
                append(" final_input_tokens=unavailable")
                append(" final_input_hash=")
                append(npuRealPromptHash(finalInput))
                append(" final_input_code_points=")
                append(finalInput.codePointCount(0, finalInput.length))
                append(" prompt_tail_variant=")
                append(request.promptTailVariant)
                append(" prompt_wrapper_used=")
                append(NpuStandardRouteS1Contract.PROMPT_WRAPPER_USED)
                append(" arithmetic_prompt_detected=")
                append(promptRewrite.arithmeticPromptDetected)
                append(" short_prompt_rewrite_applied=")
                append(promptRewrite.shortPromptRewriteApplied)
                append(" rewritten_prompt_tail=")
                append(npuRealPromptPreview(promptRewrite.rewrittenPromptText.takeLast(120)))
                append(" max_output_tokens=")
                append(request.maxOutputTokens)
            }
        }

        private fun resolveApplicationContext(): Context? {
            val currentApplication = runCatching {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication")
                currentApplicationMethod.invoke(null)
            }.getOrNull() as? Context

            if (currentApplication != null) return currentApplication.applicationContext

            return runCatching {
                val appGlobalsClass = Class.forName("android.app.AppGlobals")
                val initialApplicationMethod = appGlobalsClass.getDeclaredMethod("getInitialApplication")
                initialApplicationMethod.invoke(null)
            }.getOrNull() as? Context
        }
    }
}
