package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationEntry
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import kotlinx.coroutines.runBlocking

class Qairt244DevOnlyNpuConversationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outputView = TextView(this).apply {
            text = "DEV ONLY NPU ONE TURN\nstatus=starting"
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        setContentView(outputView)

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
