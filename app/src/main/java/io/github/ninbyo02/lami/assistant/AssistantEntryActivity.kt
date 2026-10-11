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

/** Receives explicit Android assist launches without modifying the voice engine. */
class AssistantEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(
                EXTRA_ASSISTANT_SHOW_WHEN_LOCKED,
                getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
            )
            putExtra(EXTRA_ASSISTANT_START_LISTENING, true)
            putExtra(
                EXTRA_ASSISTANT_AUTO_SUBMIT,
                intent.getBooleanExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, false),
            )
        }
        startActivity(launch)
        finish()
    }
}
