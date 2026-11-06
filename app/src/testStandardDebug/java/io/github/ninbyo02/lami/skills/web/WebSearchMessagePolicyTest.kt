package io.github.ninbyo02.lami.skills.web

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.viewmodels.buildRemoteChatMessages
import org.junit.Assert.*
import org.junit.Test

class WebSearchMessagePolicyTest {
    private fun message(text: String, user: Boolean, web: Boolean = false) = Message(chatId = 1,
        message = text, isSendbyMe = user, localSourceSummary = if (web) WebSearchMessagePolicy.SOURCE_TAG else "existing_stats=preserved")

    @Test fun ordinaryHistoryAndItsStatisticsRemainUnchanged() {
        val history = listOf(message("hello", true), message("answer", false), message("next", true))
        assertEquals(history, WebSearchMessagePolicy.remoteHistory(history))
        assertTrue(history.all { it.localSourceSummary == "existing_stats=preserved" })
    }

    @Test fun searchUserAndAssistantAndAllLaterDerivedAnswersStayOutOfRemoteHistory() {
        val beforeUser = message("ordinary user", true)
        val beforeAssistant = message("ordinary answer", false)
        val followUp = message("summarize locally", true)
        val explicitUser = message("new remote question", true)
        val history = listOf(beforeUser, beforeAssistant, message("/web private query", true, true),
            message("private search data", false, true), followUp, message("derived summary", false),
            explicitUser, message("later server answer is conservatively excluded", false))
        assertEquals(listOf(beforeUser, beforeAssistant, followUp, explicitUser), WebSearchMessagePolicy.remoteHistory(history))
        assertEquals(8, history.size)
    }

    @Test fun commonRemoteBuilderPreservesCurrentExplicitContentButNeverAddsSearchOrDerivedData() {
        val history = listOf(message("/web private query", true, true), message("private result", false, true),
            message("a question", true), message("private derived answer", false))
        val remote = buildRemoteChatMessages(history, currentContent = "explicit current message", inputTokenBudget = 4000)
        assertEquals(listOf("a question", "explicit current message"), remote.map { it.content })
    }

    @Test fun localContextRetainsDataAndLabelsItAsUntrusted() {
        val web = message("search snippet", false, true)
        assertTrue(WebSearchMessagePolicy.localContextText(web).contains("未検証"))
        assertTrue(WebSearchMessagePolicy.localContextText(web).contains("search snippet"))
        assertEquals("normal", WebSearchMessagePolicy.localContextText(message("normal", false)))
    }
}
