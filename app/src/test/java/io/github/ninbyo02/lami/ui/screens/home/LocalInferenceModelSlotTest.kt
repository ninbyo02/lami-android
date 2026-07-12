package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceModelSlotTest {
    @Test
    fun `NPU uses NPU preview missing message`() {
        assertEquals(
            LocalInferenceModelSlot.NPU_PREVIEW,
            localModelSlotForBackend(PreferredBackendDryRunSetting.NPU),
        )
        assertEquals(
            "NPUプレビューモデルが未設定です",
            missingLocalModelMessageForBackend(PreferredBackendDryRunSetting.NPU),
        )
    }

    @Test
    fun `Qualcomm QNN NPU uses NPU preview missing message`() {
        assertEquals(
            LocalInferenceModelSlot.NPU_PREVIEW,
            localModelSlotForBackend(PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU),
        )
        assertEquals(
            "NPUプレビューモデルが未設定です",
            missingLocalModelMessageForBackend(PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU),
        )
    }

    @Test
    fun `GPU uses Generic fallback missing message`() {
        assertEquals(
            LocalInferenceModelSlot.GENERIC_FALLBACK,
            localModelSlotForBackend(PreferredBackendDryRunSetting.GPU),
        )
        assertEquals(
            "汎用フォールバックモデルが未設定です",
            missingLocalModelMessageForBackend(PreferredBackendDryRunSetting.GPU),
        )
    }

    @Test
    fun `CPU uses Generic fallback missing message`() {
        assertEquals(
            LocalInferenceModelSlot.GENERIC_FALLBACK,
            localModelSlotForBackend(PreferredBackendDryRunSetting.CPU),
        )
        assertEquals(
            "汎用フォールバックモデルが未設定です",
            missingLocalModelMessageForBackend(PreferredBackendDryRunSetting.CPU),
        )
    }

    @Test
    fun `DEFAULT selects NPU preview when only NPU model exists`() {
        assertEquals(
            LocalInferenceModelSlot.NPU_PREVIEW,
            resolveLocalModelSlotForAvailableModels(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuModelPath = "/models/gemma-npu.litertlm",
                genericModelPath = null,
            ),
        )
    }

    @Test
    fun `DEFAULT selects Generic fallback when only Generic model exists`() {
        assertEquals(
            LocalInferenceModelSlot.GENERIC_FALLBACK,
            resolveLocalModelSlotForAvailableModels(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuModelPath = null,
                genericModelPath = "/models/gemma-generic.litertlm",
            ),
        )
    }

    @Test
    fun `DEFAULT preserves Generic preference when both models exist`() {
        assertEquals(
            LocalInferenceModelSlot.GENERIC_FALLBACK,
            resolveLocalModelSlotForAvailableModels(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuModelPath = "/models/gemma-npu.litertlm",
                genericModelPath = "/models/gemma-generic.litertlm",
            ),
        )
    }

    @Test
    fun `DEFAULT remains Generic when no local model exists`() {
        assertEquals(
            LocalInferenceModelSlot.GENERIC_FALLBACK,
            resolveLocalModelSlotForAvailableModels(
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuModelPath = null,
                genericModelPath = null,
            ),
        )
    }

    @Test
    fun `DEFAULT uses Generic fallback missing message`() {
        assertEquals(
            LocalInferenceModelSlot.GENERIC_FALLBACK,
            localModelSlotForBackend(PreferredBackendDryRunSetting.DEFAULT),
        )
        assertEquals(
            "汎用フォールバックモデルが未設定です",
            missingLocalModelMessageForBackend(PreferredBackendDryRunSetting.DEFAULT),
        )
    }
}
