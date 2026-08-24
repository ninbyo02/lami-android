package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTerminalMetadataArchitectureSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `ChatScreen delegates every post-terminal metadata write to the updater`() {
        val chat = source("ChatScreen.kt")

        assertTrue(chat.contains("PostTerminalAssistantMetadataUpdater("))
        assertTrue(chat.contains("OllamaViewModelPostTerminalAssistantMetadataStore(viewModel)"))
        assertEquals(3, chat.windowed("postTerminalAssistantMetadataUpdater.update(".length)
            .count { it == "postTerminalAssistantMetadataUpdater.update(" })
        assertTrue(chat.contains("expectedMessage = persistedResponse"))
        assertTrue(chat.contains("expectedMessage = assistantTextForPersist"))
        assertTrue(chat.contains("expectedMessage = resolvedAssistantResponse"))
        assertTrue(chat.contains("PostTerminalAssistantMetadataPatch.fromInferenceStats("))
        assertFalse(chat.contains("viewModel.updateMessage("))
        assertFalse(chat.contains("message.copy(localSourceSummary ="))
    }

    @Test
    fun `updater validates identity terminal state and preserves lifecycle fields`() {
        val updater = source("PostTerminalAssistantMetadataUpdater.kt")

        assertTrue(updater.contains("private val updateMutex = Mutex()"))
        assertTrue(updater.contains("updateMutex.withLock"))
        assertTrue(updater.contains("current.isSendbyMe"))
        assertTrue(updater.contains("current.chatId != expectedChatId"))
        assertTrue(updater.contains("current.message != expectedMessage"))
        assertTrue(updater.contains("current.status !in MessageStatus.TERMINAL"))
        assertTrue(updater.contains("checkLifecycleFieldsPreserved("))
        assertTrue(updater.contains("replacement.message == current.message"))
        assertTrue(updater.contains("replacement.status == current.status"))
        assertTrue(updater.contains("replacement.errorCode == current.errorCode"))
        assertTrue(updater.contains("replacement.updatedAtEpochMs == current.updatedAtEpochMs"))
    }

    @Test
    fun `metadata patch cannot assign message or lifecycle fields`() {
        val updater = source("PostTerminalAssistantMetadataUpdater.kt")
        val patchDefinition = updater
            .substringAfter("internal data class PostTerminalAssistantMetadataPatch(")
            .substringBefore(") {\n    fun applyTo")
        val applyBlock = updater
            .substringAfter("fun applyTo(message: Message): Message = message.copy(")
            .substringBefore("\n    )")

        for (forbidden in listOf(
            "messageID",
            "chatId",
            "message:",
            "isSendbyMe",
            "attachmentUriString",
            "attachmentUriStringsJson",
            "createdAtEpochMs",
            "status",
            "errorCode",
            "updatedAtEpochMs",
        )) {
            assertFalse(forbidden, patchDefinition.contains(forbidden))
        }
        for (forbiddenAssignment in listOf(
            "messageID =",
            "chatId =",
            "message =",
            "isSendbyMe =",
            "attachmentUriString =",
            "attachmentUriStringsJson =",
            "createdAtEpochMs =",
            "status =",
            "errorCode =",
            "updatedAtEpochMs =",
        )) {
            assertFalse(forbiddenAssignment, applyBlock.contains(forbiddenAssignment))
        }
    }

    private fun source(fileName: String): String = File(
        root,
        "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/$fileName",
    ).readText()
}
