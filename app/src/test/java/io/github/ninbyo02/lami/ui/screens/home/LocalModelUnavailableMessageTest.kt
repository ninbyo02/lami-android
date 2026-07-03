package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelUnavailableMessageTest {
    @Test
    fun `generic fallback reports unset only when no saved model info exists`() {
        assertEquals(
            "汎用フォールバックモデルが未設定です",
            missingLocalModelMessageForBackend(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
                displayName = null,
                filePath = null,
            ),
        )
    }

    @Test
    fun `generic fallback asks user to reselect when saved model file is missing`() {
        assertEquals(
            "汎用フォールバックモデルのファイルが見つかりません。設定で選び直してください",
            missingLocalModelMessageForBackend(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
                displayName = "gemma-4-E2B-it.litertlm",
                filePath = null,
            ),
        )
    }

    @Test
    fun `npu preview asks user to reselect when saved model file is missing`() {
        assertEquals(
            "NPUプレビューモデルのファイルが見つかりません。設定で選び直してください",
            missingLocalModelMessageForBackend(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.NPU,
                displayName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
                filePath = "/data/user/0/io.github.ninbyo02.lami/files/local_models/missing.litertlm",
            ),
        )
    }
}
