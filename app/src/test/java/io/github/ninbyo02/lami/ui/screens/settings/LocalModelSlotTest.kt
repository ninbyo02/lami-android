package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelSlotTest {
    @Test
    fun `local model slots expose separate labels and routes`() {
        assertEquals("NPUプレビューモデル", LocalModelSlot.NpuPreview.title)
        assertEquals("汎用フォールバックモデル", LocalModelSlot.GenericFallback.title)
        assertEquals(Routes.LOCAL_BASE_MODEL, SettingsRoute.LocalBaseModel.route)
        assertEquals(Routes.LOCAL_GENERIC_FALLBACK_MODEL, SettingsRoute.LocalGenericFallbackModel.route)
    }

    @Test
    fun `generic fallback model copy explains GPU CPU fallback`() {
        assertEquals(
            "NPUが使えない場合のGPU/CPU推論に使用します。",
            LocalModelSlot.GenericFallback.description,
        )
    }
}
