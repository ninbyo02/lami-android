package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScreenStreamingRenderTest {

    @Test
    fun composerExpandAffordance_isVisibleFromFiveLinesAndUsesFullScreenLayout() {
        assertFalse(shouldShowComposerExpandAffordance(measuredLines = 4))
        assertTrue(shouldShowComposerExpandAffordance(measuredLines = 5))
        assertTrue(shouldShowComposerExpandAffordance(measuredLines = 8))
        assertTrue(shouldUseFullScreenComposerEditor())
        assertTrue(composerExpandButtonEndPaddingDp() <= 8)
    }


    @Test
    fun scrollToBottomFab_isLayeredAboveComposerAndLandscapeGradientIsDisabled() {
        assertTrue(scrollToBottomFabZIndex() > composerInputLayerZIndex())
        assertTrue(scrollToBottomFabBottomPaddingExtraDp() >= 48)
        assertFalse(shouldEnableComposerBottomGradient(isLandscape = true))
        assertTrue(shouldEnableComposerBottomGradient(isLandscape = false))
    }

    @Test
    fun shouldRefreshRender_shortDeltaWithoutNewline_returnsFalse() {
        assertFalse(
            shouldRefreshRender(
                prev = "hello",
                next = "hello world",
                isStreaming = true,
            )
        )
    }

    @Test
    fun shouldRefreshRender_shortJapaneseConversationDelta_returnsFalse() {
        assertFalse(
            shouldRefreshRender(
                prev = "こんにちは！",
                next = "こんにちは！何かお手伝いできますか？",
                isStreaming = true,
            )
        )
    }

    @Test
    fun shouldRefreshRender_newlineOrLongDelta_returnsTrue() {
        assertTrue(
            shouldRefreshRender(
                prev = "hello",
                next = "hello\nworld",
                isStreaming = true,
            )
        )
        assertTrue(
            shouldRefreshRender(
                prev = "hello",
                next = "hello 12345678901234567890123456789012",
                isStreaming = true,
            )
        )
    }

    @Test
    fun shouldRefreshRender_fenceOrPythonFusion_returnsTrue() {
        assertTrue(
            shouldRefreshRender(
                prev = "説明",
                next = "説明```python",
                isStreaming = true,
            )
        )
        assertTrue(
            shouldRefreshRender(
                prev = "説明\n",
                next = "説明\npythonimport random",
                isStreaming = true,
            )
        )
    }

    @Test
    fun shouldRefreshRender_pythonLanguageTagWithCode_returnsTrue() {
        assertTrue(
            shouldRefreshRender(
                prev = "説明\n",
                next = "説明\npython\nimport random",
                isStreaming = true,
            )
        )
    }
}
