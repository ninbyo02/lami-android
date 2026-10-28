package io.github.ninbyo02.lami.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import io.github.ninbyo02.lami.MainActivity

internal const val EXTRA_ASSISTANT_SHOW_WHEN_LOCKED =
    "io.github.ninbyo02.lami.assistant.SHOW_WHEN_LOCKED"
internal const val EXTRA_ASSISTANT_START_LISTENING =
    "io.github.ninbyo02.lami.assistant.START_LISTENING"
internal const val EXTRA_ASSISTANT_AUTO_SUBMIT =
    "io.github.ninbyo02.lami.assistant.AUTO_SUBMIT"
private const val EXTRA_ENTRY_CONSUMED = "io.github.ninbyo02.lami.assistant.ENTRY_CONSUMED"

/** Receives explicit Android assist launches without modifying the voice engine. */
class AssistantEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        traceEntry("create", intent, savedInstanceState != null)
        if (intent.getBooleanExtra(EXTRA_ENTRY_CONSUMED, false) ||
            savedInstanceState?.getBoolean(EXTRA_ENTRY_CONSUMED, false) == true ||
            (savedInstanceState != null && intent.action != Intent.ACTION_ASSIST)) {
            finish(); return
        }
        openVoiceInput(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_ENTRY_CONSUMED, intent.getBooleanExtra(EXTRA_ENTRY_CONSUMED, false))
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        traceEntry("new_intent", intent, false)
        if (intent.getBooleanExtra(EXTRA_ENTRY_CONSUMED, false)) { finish(); return }
        openVoiceInput(intent)
    }

    private fun openVoiceInput(request: Intent) {
        // Consume before dispatch: a restored Activity must not replay an old request.
        request.putExtra(EXTRA_ENTRY_CONSUMED, true)
        val launch = Intent(this, MainActivity::class.java).apply {
            // The assist trampoline has no affinity; return to the existing chat task.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(
                EXTRA_ASSISTANT_SHOW_WHEN_LOCKED,
                getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
            )
            putExtra(EXTRA_ASSISTANT_START_LISTENING, true)
            putExtra(
                EXTRA_ASSISTANT_AUTO_SUBMIT,
                request.getBooleanExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, false),
            )
        }
        startActivity(launch)
        finish()
    }

    private fun traceEntry(stage: String, request: Intent, restored: Boolean) {
        io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext,
            "[LAMI_ASR] entry stage=$stage assist=${request.action == Intent.ACTION_ASSIST} restored=$restored consumed=${request.getBooleanExtra(EXTRA_ENTRY_CONSUMED, false)}")
    }
}
