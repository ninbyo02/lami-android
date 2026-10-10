package io.github.ninbyo02.lami.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.ninbyo02.lami.MainActivity

/** Receives explicit Android assist launches without modifying the voice engine. */
class AssistantEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launch)
        finish()
    }
}
