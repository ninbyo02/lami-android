package io.github.ninbyo02.lami.tv

import android.content.Context
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Persist a tool turn before dispatch. Screen disposal never replays a device action. */
internal data class BraviaChatReply(val assistantId: Int, val text: String)

internal suspend fun runBraviaChatTurn(
    context: Context,
    viewModel: OllamaViewModel,
    chatId: Int,
    prompt: String,
    command: BraviaCommand,
): BraviaChatReply {
    viewModel.insertMessage(Message(chatId = chatId, message = prompt, isSendbyMe = true))
    val call = BraviaToolCatalog.call(command)
    val assistantId = viewModel.insertAssistantMessageAndReturnId(
        Message(chatId = chatId, message = "テレビの操作を実行しています…", isSendbyMe = false,
            status = MessageStatus.GENERATING, modelName = "BRAVIA",
            localSourceSummary = "tool=" + call.name),
    ).toInt()
    require(assistantId > 0) { "テレビ操作の会話を保存できませんでした。" }
    try {
        val result = BraviaManager.get(context).execute(call)
        withContext(NonCancellable + Dispatchers.IO) {
            viewModel.completeAssistantMessage(assistantId, result.reply)
        }
        return BraviaChatReply(assistantId, result.reply)
    } catch (error: CancellationException) {
        withContext(NonCancellable + Dispatchers.IO) {
            viewModel.completeAssistantMessage(assistantId, "テレビの操作結果を確認できませんでした。")
        }
        throw error
    } catch (_: Exception) {
        val reply = "テレビの操作結果を確認できませんでした。設定から接続を確認してください。"
        withContext(NonCancellable + Dispatchers.IO) {
            viewModel.completeAssistantMessage(assistantId, reply)
        }
        return BraviaChatReply(assistantId, reply)
    }
}
