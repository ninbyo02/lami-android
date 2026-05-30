package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
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
    override fun invoke(userPrompt: String): NpuStandardRouteS1RawResult =
        runCatching {
            RealNpuStandardRouteS1ResultMapper.fromDisplay(requestRunner(request(userPrompt)))
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
