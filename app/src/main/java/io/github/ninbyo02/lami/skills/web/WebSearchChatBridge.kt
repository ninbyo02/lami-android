package io.github.ninbyo02.lami.skills.web

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal interface WebSearchChatStore {
    suspend fun insertUser(message: Message)
    suspend fun insertAssistant(message: Message): Long
    suspend fun complete(id: Int, reply: String)
    suspend fun cancel(id: Int)
    suspend fun fail(id: Int)
}

internal class WebSearchViewModelStore(private val viewModel: OllamaViewModel) : WebSearchChatStore {
    override suspend fun insertUser(message: Message) = viewModel.insertMessage(message)
    override suspend fun insertAssistant(message: Message) = viewModel.insertAssistantMessageAndReturnId(message)
    override suspend fun complete(id: Int, reply: String) { viewModel.completeAssistantMessage(id, reply) }
    override suspend fun cancel(id: Int) { viewModel.cancelAssistantMessage(id) }
    override suspend fun fail(id: Int) { viewModel.failAssistantMessage(id, "Web検索の結果を保存できませんでした。") }
}

/** Plain durable messages only: no speech session, TTS, remote provider or inference state. */
internal suspend fun runWebSearchChatTurn(
    manager: WebSearchManager,
    store: WebSearchChatStore,
    chatId: Int,
    prompt: String,
    intent: WebSearchIntent,
) = withContext(Dispatchers.IO) {
    var assistantId: Int? = null
    try {
        store.insertUser(Message(chatId = chatId, message = prompt, isSendbyMe = true,
            localSourceSummary = WebSearchMessagePolicy.SOURCE_TAG))
        // Repository title work can suspend after INSERT. Obtain the persisted ID
        // even if cancellation arrives there, then terminate that exact placeholder.
        withContext(NonCancellable) {
            val id = store.insertAssistant(Message(chatId = chatId, message = "Web検索の依頼を確認しています…", isSendbyMe = false,
                status = MessageStatus.GENERATING, modelName = "Web検索（DuckDuckGo）", localSourceSummary = WebSearchMessagePolicy.SOURCE_TAG))
            check(id in 1L..Int.MAX_VALUE.toLong())
            assistantId = id.toInt()
        }
        currentCoroutineContext().ensureActive()
        val id = requireNotNull(assistantId)
        val result = when (intent) {
            is WebSearchIntent.Search -> manager.search(intent.query)
            WebSearchIntent.Rejected -> manager.rejected()
        }
        if (!manager.commitIfCurrent(result) { store.complete(id, result.reply) }) store.cancel(id)
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) { assistantId?.let { store.cancel(it) } }
        throw cancelled
    } catch (_: Exception) {
        val id = assistantId ?: throw IllegalStateException("Web検索の会話を保存できませんでした。")
        withContext(NonCancellable) { store.fail(id) }
    }
}
