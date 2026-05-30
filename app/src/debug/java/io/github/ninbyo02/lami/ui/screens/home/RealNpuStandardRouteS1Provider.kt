package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import io.github.ninbyo02.lami.npu.Qairt244DevOnlyNpuRouteAdapter
import kotlinx.coroutines.runBlocking

internal class RealNpuStandardRouteS1Provider(
    private val requestRunner: (DevOnlyNpuOneTurnConversationRequest) -> DevOnlyNpuOneTurnConversationDisplay = { request ->
        val appContext = resolveApplicationContext()
            ?: error(REASON_DEV_ONLY_ENTRY_UNAVAILABLE)
        runBlocking {
            DevOnlyNpuOneTurnConversationEntry(appContext).run(request)
        }
    },
) : NpuStandardRouteS1Provider {
    override fun invoke(
        userPrompt: String,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult =
        runCatching {
            trace(buildNpuRealPromptHandoffTrace(stage = "provider", userPrompt = userPrompt))
            val request = request(userPrompt)
            trace(buildNpuRealPromptRequestTrace(request))
            val rawResult = RealNpuStandardRouteS1ResultMapper.fromDisplay(requestRunner(request))
            trace(
                buildNpuRealPromptResultTrace(
                    sanitizedOutput = rawResult.sanitizedOutput,
                    qualityClassification = rawResult.qualityClassification,
                ),
            )
            rawResult
        }.getOrElse { throwable ->
            RealNpuStandardRouteS1ResultMapper.failure(
                reason = throwable.message
                    ?.takeIf { it.isNotBlank() }
                    ?: REASON_DEV_ONLY_REQUEST_FAILED,
            )
        }

    companion object {
        const val REASON_DEV_ONLY_ENTRY_UNAVAILABLE = "dev_only_entry_unavailable"
        const val REASON_DEV_ONLY_REQUEST_FAILED = "dev_only_request_failed"

        fun request(userPrompt: String): DevOnlyNpuOneTurnConversationRequest =
            DevOnlyNpuOneTurnConversationRequest(
                userPrompt = userPrompt,
                contextText = "",
                unsafeDevBypassPromptLengthGate = true,
                maxOutputTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
                promptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
                timeoutMs = DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS,
            )

        fun buildNpuRealPromptRequestTrace(
            request: DevOnlyNpuOneTurnConversationRequest,
        ): String {
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
                append(" final_input_tokens=unavailable")
                append(" final_input_code_points=")
                append(finalInput.codePointCount(0, finalInput.length))
                append(" prompt_tail_variant=")
                append(request.promptTailVariant)
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
