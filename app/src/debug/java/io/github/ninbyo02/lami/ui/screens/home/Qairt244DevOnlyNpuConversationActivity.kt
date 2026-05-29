package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
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
                if (runStarted) return@setOnClickListener
                runStarted = true
                outputView.text = "DEV ONLY NPU ONE TURN\nstatus=starting"
                startDevOnlyRun(outputView)
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(runButton)
                addView(outputView)
            },
        )
    }

    private fun startDevOnlyRun(outputView: TextView) {
        Thread({
            val request = DevOnlyNpuOneTurnConversationRequest(
                userPrompt = intent?.getStringExtra(EXTRA_USER_PROMPT).orEmpty().ifBlank { DEFAULT_PROMPT },
                contextText = intent?.getStringExtra(EXTRA_CONTEXT).orEmpty(),
                unsafeDevBypassPromptLengthGate = intent?.getBooleanExtra(
                    EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
                    true,
                ) ?: true,
            )
            val text = try {
                runBlocking {
                    DevOnlyNpuOneTurnConversationEntry(this@Qairt244DevOnlyNpuConversationActivity)
                        .run(request)
                        .text
                }
            } catch (throwable: Throwable) {
                "DEV ONLY NPU ONE TURN\n" +
                    "status=failure\n" +
                    "reason=activity_failure:${throwable.javaClass.simpleName}\n" +
                    "requested_max_output_tokens=${DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS}\n" +
                    "effective_max_output_tokens=${DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS}\n" +
                    "max_output_tokens=${DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS}\n" +
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

    private companion object {
        private const val EXTRA_USER_PROMPT = "user_prompt"
        private const val EXTRA_CONTEXT = "context"
        private const val EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE =
            "unsafe_dev_bypass_prompt_length_gate"
        private const val DEFAULT_PROMPT = "こんにちは。"
    }
}
