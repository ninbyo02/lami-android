package io.github.ninbyo02.lami.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

internal object AssistantLauncher {
    fun launchAssist(context: Context) {
        val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
            `package` = context.packageName
            putExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, true)
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        try {
            context.startActivity(assistIntent)
        } catch (e: ActivityNotFoundException) {
            Log.w("LamiAssistantButton", "ACTION_ASSIST unavailable; falling back to AssistantEntryActivity", e)
            context.startActivity(
                Intent(context, AssistantEntryActivity::class.java).apply {
                    putExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, true)
                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )
        }
    }
}
