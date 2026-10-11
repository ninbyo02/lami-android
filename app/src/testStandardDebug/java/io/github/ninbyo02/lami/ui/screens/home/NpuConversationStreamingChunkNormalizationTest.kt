package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NpuConversationStreamingChunkNormalizationTest {
    @Test fun deltaChunkPassesThrough() = assertEquals(" world", normalizeNpuConversationStreamingChunk("Hello", " world"))
    @Test fun cumulativeCallbackReturnsOnlySuffix() = assertEquals(" world", normalizeNpuConversationStreamingChunk("Hello", "Hello world"))
    @Test fun repeatedCumulativeCallbackIsDropped() = assertEquals("", normalizeNpuConversationStreamingChunk("Hello", "Hello"))
    @Test fun firstCallbackPassesThrough() = assertEquals("Hello", normalizeNpuConversationStreamingChunk("", "Hello"))
}
