package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Backend
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalImageBackendPolicyTest {
    @Test
    fun `CPU selects generic language and vision on CPU`() {
        assertEquals(
            LocalImageBackendPlan(LocalImageLanguageBackend.CPU, LocalVisionBackend.CPU),
            localImageBackendPlan(LocalImageModelKind.GENERIC, PreferredBackendDryRunSetting.CPU),
        )
    }

    @Test
    fun `GPU and automatic generic select GPU language and vision`() {
        listOf(PreferredBackendDryRunSetting.GPU, PreferredBackendDryRunSetting.DEFAULT).forEach { backend ->
            assertEquals(
                LocalImageBackendPlan(LocalImageLanguageBackend.GPU, LocalVisionBackend.GPU),
                localImageBackendPlan(LocalImageModelKind.GENERIC, backend),
            )
        }
    }

    @Test
    fun `SM8750 NPU choices explicitly retain CPU vision`() {
        listOf(PreferredBackendDryRunSetting.NPU, PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
            PreferredBackendDryRunSetting.DEFAULT).forEach { backend ->
            assertEquals(
                LocalImageBackendPlan(LocalImageLanguageBackend.NPU, LocalVisionBackend.CPU),
                localImageBackendPlan(LocalImageModelKind.QUALCOMM_SM8750, backend),
            )
        }
    }

    @Test
    fun `generic model never silently handles explicit NPU selection`() {
        listOf(PreferredBackendDryRunSetting.NPU, PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU).forEach {
            assertNull(localImageBackendPlan(LocalImageModelKind.GENERIC, it))
        }
    }

    @Test
    fun `NPU compiled model never silently handles CPU or GPU selection`() {
        listOf(PreferredBackendDryRunSetting.CPU, PreferredBackendDryRunSetting.GPU).forEach {
            assertNull(localImageBackendPlan(LocalImageModelKind.QUALCOMM_SM8750, it))
        }
    }

    @Test
    fun `follow up without new attachment still reserves images in restored history`() {
        val history = listOf(LocalConversationTurn(LocalConversationRole.USER, "look", listOf("first", "second")))
        assertEquals(2, localImageCapacity(emptyList(), history))
        assertEquals(5, localImageCapacity(listOf("one", "two", "three"), history))
        assertEquals(0, localImageCapacity(emptyList(), emptyList()))
    }

    @Test
    fun `CPU image EngineConfig enables CPU vision and excludes audio`() {
        val config = buildLiteRtEngineConfig(
            modelPath = "/models/gemma.litertlm",
            cacheDirPath = "/cache",
            imageInputCount = 2,
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.CPU,
            appendTrace = {},
        )
        assertEquals(Backend.CPU().name, config.backend.name)
        assertEquals(Backend.CPU().name, config.visionBackend?.name)
        assertEquals(2, config.maxNumImages)
        assertNull(config.audioBackend)
    }

    @Test
    fun `GPU image EngineConfig enables GPU vision and excludes audio`() {
        val config = buildLiteRtEngineConfig(
            modelPath = "/models/gemma.litertlm",
            cacheDirPath = "/cache",
            imageInputCount = 1,
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
            appendTrace = {},
        )
        assertEquals(Backend.GPU().name, config.backend.name)
        assertEquals(Backend.GPU().name, config.visionBackend?.name)
        assertEquals(1, config.maxNumImages)
        assertNull(config.audioBackend)
    }

    @Test
    fun `diagnostics distinguish NPU language from CPU image processing`() {
        val plan = requireNotNull(localImageBackendPlan(LocalImageModelKind.QUALCOMM_SM8750, PreferredBackendDryRunSetting.NPU))
        assertEquals("image_input_count=1\nlanguage_backend=NPU\nvision_backend=CPU\nvision_adapter_backend=CPU", plan.diagnostics(1))
    }
}
