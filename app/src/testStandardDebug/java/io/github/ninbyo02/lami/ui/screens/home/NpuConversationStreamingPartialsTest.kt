package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class NpuConversationStreamingPartialsTest {
    @Test
    fun `EOS flushes the final greeting suppressed inside the UI throttle interval`() {
        val updates = mutableListOf<String>()
        val dispatcher = NpuConversationStreamingPartials("こんにちは", 1000L, updates::add)
        val first = "こんにちは。"
        val final = "こんにちは。何かお手伝いできることはありますか？"
        dispatcher.offer(first, 1390L)
        dispatcher.offer(final, 1430L)
        assertEquals(listOf(first), updates)

        dispatcher.finish(final, 1431L)

        assertEquals(listOf(first, final), updates)
        assertEquals(2, dispatcher.chunkCount)
        assertEquals(390L, dispatcher.timeToFirstChunkMs)
    }

    @Test
    fun `EOS does not duplicate an already delivered final chunk or change TTFT`() {
        val updates = mutableListOf<String>()
        val dispatcher = NpuConversationStreamingPartials("1+1はいくつですか。数字だけ答えてください。", 1000L, updates::add)
        dispatcher.offer("2", 1161L)
        dispatcher.finish("2", 1170L)
        dispatcher.finish("2", 1171L)

        assertEquals(listOf("2"), updates)
        assertEquals(1, dispatcher.chunkCount)
        assertEquals(161L, dispatcher.timeToFirstChunkMs)
    }

    @Test
    fun `unsafe final output cannot flush an earlier safe throttled prefix`() {
        val updates = mutableListOf<String>()
        val dispatcher = NpuConversationStreamingPartials("こんにちは", 1000L, updates::add)
        val first = "こんにちは。"
        val pending = "こんにちは。何かお手伝いできることはありますか？"
        dispatcher.offer(first, 1390L)
        dispatcher.offer(pending, 1430L)
        dispatcher.finish("$pending<start_of_turn>user\n次の質問", 1431L)

        assertEquals(listOf(first), updates)
        assertEquals(1, dispatcher.chunkCount)
        assertEquals(390L, dispatcher.timeToFirstChunkMs)
    }

    @Test
    fun `empty and rejected final output do not create visible chunks or TTFT`() {
        for (text in listOf("", "   ", "<start_of_turn>user\n質問")) {
            val updates = mutableListOf<String>()
            val dispatcher = NpuConversationStreamingPartials("こんにちは", 1000L, updates::add)
            dispatcher.finish(text, 1400L)
            assertTrue(updates.isEmpty())
            assertEquals(0, dispatcher.chunkCount)
            assertNull(dispatcher.timeToFirstChunkMs)
        }
    }

    @Test
    fun `safe text appearing only at EOS records its actual first visible dispatch time`() {
        val updates = mutableListOf<String>()
        val dispatcher = NpuConversationStreamingPartials("こんにちは", 1000L, updates::add)
        dispatcher.offer("こん", 1380L)
        assertTrue(updates.isEmpty())
        dispatcher.finish("こんにちは。何かお手伝いできることはありますか？", 1430L)

        assertEquals(1, updates.size)
        assertEquals(1, dispatcher.chunkCount)
        assertEquals(430L, dispatcher.timeToFirstChunkMs)
    }
}
