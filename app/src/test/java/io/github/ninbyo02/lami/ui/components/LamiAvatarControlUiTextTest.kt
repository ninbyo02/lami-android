package io.github.ninbyo02.lami.ui.components

import io.github.ninbyo02.lami.viewmodels.LamiStatus
import io.github.ninbyo02.lami.viewmodels.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LamiAvatarControlUiTextTest {
    @Test
    fun `local mode with no server shows local labels and no server model failure`() {
        val text = resolveLamiControlUiText(
            selectedInferenceTarget = InferenceTarget.LOCAL,
            baseUrl = "",
            lamiStatus = LamiStatus.READY,
            availableModels = emptyList(),
        )

        assertEquals("ローカルモード", text.connectionLabel)
        assertEquals("ローカル推論", text.destinationLabel)
        assertEquals("ローカルモードではサーバーモデルを使用しません", text.modelListMessage)
        assertFalse(text.modelListMessage.orEmpty().contains("モデルを取得できませんでした"))
        assertFalse(text.showModelSearch)
        assertFalse(text.showSettingsButton)
    }

    @Test
    fun `server mode with no server shows unconfigured labels`() {
        val text = resolveLamiControlUiText(
            selectedInferenceTarget = InferenceTarget.SERVER,
            baseUrl = "",
            lamiStatus = LamiStatus.READY,
            availableModels = emptyList(),
        )

        assertEquals("サーバー未設定", text.connectionLabel)
        assertEquals("なし", text.destinationLabel)
        assertEquals("サーバーが登録されていません", text.modelListMessage)
        assertFalse(text.showModelSearch)
        assertTrue(text.showSettingsButton)
    }

    @Test
    fun `server mode connection failure shows failed label and model fetch failure`() {
        val text = resolveLamiControlUiText(
            selectedInferenceTarget = InferenceTarget.SERVER,
            baseUrl = "http://server.local:11434",
            lamiStatus = LamiStatus.ERROR,
            availableModels = emptyList(),
        )

        assertEquals("接続失敗", text.connectionLabel)
        assertEquals("http://server.local:11434", text.destinationLabel)
        assertEquals("モデルを取得できませんでした", text.modelListMessage)
        assertFalse(text.showModelSearch)
        assertTrue(text.showSettingsButton)
    }

    @Test
    fun `server mode connection success keeps ok label and model list`() {
        val text = resolveLamiControlUiText(
            selectedInferenceTarget = InferenceTarget.SERVER,
            baseUrl = "http://server.local:11434",
            lamiStatus = LamiStatus.READY,
            availableModels = listOf(ModelInfo("gemma")),
        )

        assertEquals("接続OK", text.connectionLabel)
        assertEquals("http://server.local:11434", text.destinationLabel)
        assertNull(text.modelListMessage)
        assertTrue(text.showModelSearch)
        assertFalse(text.showSettingsButton)
    }
}
