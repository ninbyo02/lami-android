package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

/** Release builds never expose the development backend. */
internal object EmulatorMainChatBackend {
    fun isEnabled(context: Context): Boolean = false
    fun modelName(context: Context): String = ""
    suspend fun run(context: Context, prompt: String, initialTurns: List<LocalConversationTurn>, onPartial: (String) -> Unit): LocalInferenceRunResult? = null
}
