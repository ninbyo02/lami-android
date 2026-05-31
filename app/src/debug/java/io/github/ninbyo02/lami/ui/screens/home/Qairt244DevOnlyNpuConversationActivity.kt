package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationMatrix
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import io.github.ninbyo02.lami.npu.DevOnlyNpuPromptTemplateMatrix
import io.github.ninbyo02.lami.npu.DevOnlyNpuPromptTemplateMatrixEntry
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
        val promptTemplateMatrixButton = Button(this).apply {
            text = "Run dev-only NPU prompt template matrix"
            setOnClickListener {
                triggerDevOnlyPromptTemplateMatrixRun(outputView)
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(runButton)
                addView(promptTemplateMatrixButton)
                addView(outputView)
            },
        )
        if (
            intent?.getBooleanExtra(
                DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN_PROMPT_TEMPLATE_MATRIX,
                false,
            ) == true
        ) {
            triggerDevOnlyPromptTemplateMatrixRun(outputView)
        } else if (intent?.getBooleanExtra(DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN_MATRIX, false) == true) {
            triggerDevOnlyMatrixRun(outputView)
        } else if (intent?.getBooleanExtra(DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN, false) == true) {
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
                    promptTailVariant = request.promptTailVariant,
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
                    promptTailVariant = request.promptTailVariant,
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

    private fun triggerDevOnlyMatrixRun(
        outputView: TextView,
    ) {
        if (runStarted) return
        runStarted = true
        val request = currentRequest()
        outputView.text = "DEV ONLY NPU ONE TURN MATRIX\nstatus=starting"
        Thread({
            runOnUiThread {
                outputView.text = "DEV ONLY NPU ONE TURN MATRIX\nstatus=running"
            }
            val resultFile = File(
                applicationContext.filesDir,
                DevOnlyNpuOneTurnConversationContract.MATRIX_RESULT_FILE_NAME,
            )
            val text = try {
                resultFile.writeText(
                    DevOnlyNpuOneTurnConversationMatrix.buildHeader(
                        baseRequest = request,
                        status = "running",
                    ).joinToString(separator = "\n", postfix = "\n"),
                )
                runBlocking {
                    DevOnlyNpuOneTurnConversationMatrix.run(
                        entry = DevOnlyNpuOneTurnConversationEntry(this@Qairt244DevOnlyNpuConversationActivity),
                        baseRequest = request,
                    )
                }
            } catch (throwable: Throwable) {
                DevOnlyNpuOneTurnConversationMatrix.failureText(
                    reason = "activity_matrix_failure",
                    throwable = throwable,
                    baseRequest = request,
                )
            }
            resultFile.writeText(text)
            runOnUiThread {
                outputView.text = text
            }
        }, "Qairt244DevOnlyNpuConversationMatrix").start()
    }

    private fun triggerDevOnlyPromptTemplateMatrixRun(
        outputView: TextView,
    ) {
        if (runStarted) return
        runStarted = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        outputView.text = "DEV ONLY NPU PROMPT TEMPLATE MATRIX\nstatus=starting"
        Log.i(TAG, "prompt_template_matrix status=starting")
        Thread({
            runOnUiThread {
                outputView.text = "DEV ONLY NPU PROMPT TEMPLATE MATRIX\nstatus=running"
            }
            Log.i(TAG, "prompt_template_matrix status=running")
            val appContext = applicationContext
            val resultFile = File(
                appContext.filesDir,
                DevOnlyNpuPromptTemplateMatrix.RESULT_FILE_NAME,
            )
            val text = try {
                runBlocking {
                    DevOnlyNpuPromptTemplateMatrixEntry(appContext).run()
                }
            } catch (throwable: Throwable) {
                Log.i(
                    TAG,
                    "prompt_template_matrix status=failed exception=${throwable.javaClass.simpleName}",
                )
                DevOnlyNpuPromptTemplateMatrix.buildHeader(status = "failure")
                    .plus(
                        listOf(
                            "reason=activity_prompt_template_matrix_failure:${throwable.javaClass.simpleName}",
                            "message_preview=${throwable.message.orEmpty().take(32)}",
                        ),
                    )
                    .joinToString(separator = "\n", postfix = "\n")
            }
            resultFile.writeText(text)
            Log.i(TAG, "prompt_template_matrix status=finished result_file=${resultFile.name}")
            runOnUiThread {
                outputView.text = text
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }, "Qairt244DevOnlyNpuPromptTemplateMatrix").start()
    }

    private fun writeFailureResultFile(
        resultFile: File,
        throwable: Throwable,
        maxOutputTokens: Int,
        promptTailVariant: String,
    ) {
        runCatching {
            resultFile.writeText(
                DevOnlyNpuOneTurnConversationContract.receiverFailureText(
                    reason = "activity_failure:${throwable.javaClass.simpleName}",
                    message = throwable.message.orEmpty(),
                    timestampMs = System.currentTimeMillis(),
                    maxOutputTokens = maxOutputTokens,
                    safety = DevOnlyNpuOneTurnConversationContract.safety(
                        promptTailVariant = promptTailVariant,
                    ),
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
            requestedPromptTailVariant = intent?.getStringExtra(
                DevOnlyNpuOneTurnConversationContract.EXTRA_PROMPT_TAIL_VARIANT,
            ),
        )

    private fun writeProgressResultFile(
        resultFile: File,
        trigger: String,
        status: String,
        maxOutputTokens: Int,
        promptTailVariant: String,
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
                safety = DevOnlyNpuOneTurnConversationContract.safety(
                    promptTailVariant = promptTailVariant,
                ),
            ),
        )
    }

    private companion object {
        private const val TAG = "DevOnlyNpuConversation"
    }
}
