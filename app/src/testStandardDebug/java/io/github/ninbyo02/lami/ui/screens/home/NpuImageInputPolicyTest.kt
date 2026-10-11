package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Content
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuImageInputPolicyTest {
    @Test
    fun `images are sent as content in selection order and image only gets default prompt`() {
        val first = File.createTempFile("image_first", ".jpg").apply { writeBytes(byteArrayOf(1)) }
        val second = File.createTempFile("image_second", ".jpg").apply { writeBytes(byteArrayOf(2)) }
        try {
            val contents = NpuImageInputPolicy.contents("", listOf(first.absolutePath, second.absolutePath)).contents
            assertEquals(NpuImageInputPolicy.DEFAULT_PROMPT, (contents[0] as Content.Text).text)
            assertEquals(first.absolutePath, (contents[1] as Content.ImageFile).absolutePath)
            assertEquals(second.absolutePath, (contents[2] as Content.ImageFile).absolutePath)
            assertEquals(
                "look",
                (NpuImageInputPolicy.contents(" look ", emptyList()).contents.single() as Content.Text).text,
            )
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unreadable image never turns into a text only request`() {
        NpuImageInputPolicy.contents("look", listOf("/missing/image.jpg"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `too many images are rejected rather than truncated`() {
        NpuImageInputPolicy.contents(
            "look",
            List(NpuImageInputPolicy.MAX_IMAGES + 1) { "/missing/image.jpg" },
        )
    }

    @Test
    fun `restored conversation history contains image only turns`() {
        val image = File.createTempFile("history_image", ".jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            val turns = listOf(
                LocalConversationTurn(LocalConversationRole.USER, "", listOf(image.absolutePath)),
                LocalConversationTurn(LocalConversationRole.MODEL, "description"),
            )
            val config = LocalConversationPolicy.conversationConfig(turns)
            val restored = config.initialMessages.first().contents.contents
            assertEquals(NpuImageInputPolicy.DEFAULT_PROMPT, (restored[0] as Content.Text).text)
            assertEquals(image.absolutePath, (restored[1] as Content.ImageFile).absolutePath)
            assertEquals(2, NpuConversationPrefacePlanner.plan(turns, "follow up").initialTurns.size)
        } finally {
            image.delete()
        }
    }

    @Test
    fun `image route requires both normal NPU gate and image capability`() {
        assertTrue(shouldEnterNpuStandardRouteS1(true, InferenceTarget.LOCAL, true, "look", imageInputSupported = true))
        assertFalse(shouldEnterNpuStandardRouteS1(false, InferenceTarget.LOCAL, true, "look", imageInputSupported = true))
        assertFalse(shouldEnterNpuStandardRouteS1(true, InferenceTarget.SERVER, true, "look", imageInputSupported = true))
        assertFalse(shouldEnterNpuStandardRouteS1(true, InferenceTarget.LOCAL, true, "look"))
        assertFalse(shouldEnterLocalLiteRtRouteAfterNpuS1Decision(false, InferenceTarget.LOCAL, true, "look"))
    }
}
