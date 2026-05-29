package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import java.io.File
import kotlinx.coroutines.runBlocking

class Qairt244DevOnlyNpuConversationActivity : Activity() {
    private var runStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outputView = TextView(this).apply {
            text = DevOnlyNpuOneTurnConversationContract.INITIAL_DISPLAY_TEXT
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        val runButton = Button(this).apply {
            text = "Run dev-only NPU one turn"
            setOnClickListener {
                triggerDevOnlyRun(
                    outputView = outputView,
                    trigger = "activity_manual_button",
                )
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(runButton)
                addView(outputView)
            },
        )
        if (intent?.getBooleanExtra(DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN, false) == true) {
            triggerDevOnlyRun(
                outputView = outputView,
                trigger = "activity_auto_run",
            )
        }
    }

    private fun triggerDevOnlyRun(
        outputView: TextView,
        trigger: String,
    ) {
        if (runStarted) return
        runStarted = true
        val request = currentRequest()
        outputView.text = "DEV ONLY NPU ONE TURN\nstatus=starting"
        startDevOnlyRun(
            outputView = outputView,
            trigger = trigger,
            request = request,
        )
    }

    private fun startDevOnlyRun(
        outputView: TextView,
        trigger: String,
        request: DevOnlyNpuOneTurnConversationRequest,
    ) {
        Thread({
            runOnUiThread {
                outputView.text = "DEV ONLY NPU ONE TURN\nstatus=running"
            }
            val resultFile = File(
                applicationContext.filesDir,
                DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_FILE_NAME,
            )
            val text = try {
                writeProgressResultFile(
                    resultFile = resultFile,
                    trigger = trigger,
                    status = "running",
                    maxOutputTokens = request.maxOutputTokens,
                )
                val display = runBlocking {
                    DevOnlyNpuOneTurnConversationEntry(this@Qairt244DevOnlyNpuConversationActivity)
                        .run(request)
                }
                resultFile.writeText(
                    DevOnlyNpuOneTurnConversationContract.receiverResultText(
                        display = display,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
                display.text
            } catch (throwable: Throwable) {
                writeFailureResultFile(
                    resultFile = resultFile,
                    throwable = throwable,
                    maxOutputTokens = request.maxOutputTokens,
                )
                val maxOutputTokens = request.maxOutputTokens
                "DEV ONLY NPU ONE TURN\n" +
                    "status=failure\n" +
                    "reason=activity_failure:${throwable.javaClass.simpleName}\n" +
                    "requested_max_output_tokens=$maxOutputTokens\n" +
                    "effective_max_output_tokens=$maxOutputTokens\n" +
                    "max_output_tokens=$maxOutputTokens\n" +
                    "native_max_output_tokens_limit=-\n" +
                    "run_decode_reached=false\n" +
                    "npu_backend_evidence=-\n" +
                    "fallback_used=false\n" +
                    "timeout=false\n" +
                    "fresh_crash=false\n" +
                    "message=${throwable.message.orEmpty()}\n" +
                    "standard_route_connected=false\n" +
                    "backend_npu_persisted=false\n" +
                    "db=false\n" +
                    "tts=false\n" +
                    "markdown=false\n" +
                    "streaming=false"
            }
            runOnUiThread {
                outputView.text = text
            }
        }, "Qairt244DevOnlyNpuConversation").start()
    }

    private fun writeFailureResultFile(
        resultFile: File,
        throwable: Throwable,
        maxOutputTokens: Int,
    ) {
        runCatching {
            resultFile.writeText(
                DevOnlyNpuOneTurnConversationContract.receiverFailureText(
                    reason = "activity_failure:${throwable.javaClass.simpleName}",
                    message = throwable.message.orEmpty(),
                    timestampMs = System.currentTimeMillis(),
                    maxOutputTokens = maxOutputTokens,
                ),
            )
        }
    }

    private fun currentRequest(): DevOnlyNpuOneTurnConversationRequest =
        DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = intent?.getStringExtra(DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT),
            contextText = intent?.getStringExtra(DevOnlyNpuOneTurnConversationContract.EXTRA_CONTEXT).orEmpty(),
            unsafeDevBypassPromptLengthGate = intent?.getBooleanExtra(
                DevOnlyNpuOneTurnConversationContract.EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
                true,
            ) ?: true,
            requestedMaxOutputTokens = intent?.getIntExtra(
                DevOnlyNpuOneTurnConversationContract.EXTRA_MAX_OUTPUT_TOKENS,
                DevOnlyNpuOneTurnConversationContract.DEFAULT_MAX_OUTPUT_TOKENS,
            ) ?: DevOnlyNpuOneTurnConversationContract.DEFAULT_MAX_OUTPUT_TOKENS,
        )

    private fun writeProgressResultFile(
        resultFile: File,
        trigger: String,
        status: String,
        maxOutputTokens: Int,
    ) {
        resultFile.writeText(
            DevOnlyNpuOneTurnConversationContract.receiverProgressText(
                status = status,
                action = trigger,
                packageName = packageName,
                className = javaClass.name,
                userPromptPresent = intent?.hasExtra(
                    DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT,
                ) == true,
                timestampMs = System.currentTimeMillis(),
                maxOutputTokens = maxOutputTokens,
            ),
        )
    }
}
