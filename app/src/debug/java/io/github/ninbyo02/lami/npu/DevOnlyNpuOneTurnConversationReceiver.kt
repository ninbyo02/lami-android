package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class DevOnlyNpuOneTurnConversationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val resultFile = File(
            appContext.filesDir,
            DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_FILE_NAME,
        )
        if (intent.action != DevOnlyNpuOneTurnConversationContract.RECEIVER_ACTION) {
            writeFailure(
                resultFile = resultFile,
                reason = "unexpected_action",
                message = intent.action.orEmpty(),
            )
            return
        }
        writeProgress(
            resultFile = resultFile,
            status = "received",
        )
        if (!running.compareAndSet(false, true)) {
            writeFailure(
                resultFile = resultFile,
                reason = "already_running",
                message = "dev-only one-turn conversation receiver is already running",
            )
            return
        }

        val pendingResult = goAsync()
        Thread({
            try {
                writeProgress(
                    resultFile = resultFile,
                    status = "running",
                )
                val request = DevOnlyNpuOneTurnConversationRequest(
                    userPrompt = intent.getStringExtra(
                        DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT,
                    ).orEmpty().ifBlank {
                        DevOnlyNpuOneTurnConversationContract.DEFAULT_USER_PROMPT
                    },
                    contextText = intent.getStringExtra(
                        DevOnlyNpuOneTurnConversationContract.EXTRA_CONTEXT,
                    ).orEmpty(),
                    unsafeDevBypassPromptLengthGate = intent.getBooleanExtra(
                        DevOnlyNpuOneTurnConversationContract.EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
                        true,
                    ),
                )
                val display = runBlocking {
                    DevOnlyNpuOneTurnConversationEntry(appContext).run(request)
                }
                resultFile.writeText(
                    DevOnlyNpuOneTurnConversationContract.receiverResultText(
                        display = display,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
            } catch (throwable: Throwable) {
                writeFailure(
                    resultFile = resultFile,
                    reason = "receiver_failure:${throwable.javaClass.simpleName}",
                    message = throwable.message.orEmpty(),
                )
            } finally {
                running.set(false)
                pendingResult.finish()
            }
        }, "DevOnlyNpuOneTurnConversationReceiver").start()
    }

    private fun writeProgress(
        resultFile: File,
        status: String,
    ) {
        resultFile.writeText(
            DevOnlyNpuOneTurnConversationContract.receiverProgressText(
                status = status,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun writeFailure(
        resultFile: File,
        reason: String,
        message: String,
    ) {
        resultFile.writeText(
            DevOnlyNpuOneTurnConversationContract.receiverFailureText(
                reason = reason,
                message = message,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        private val running = AtomicBoolean(false)
    }
}
