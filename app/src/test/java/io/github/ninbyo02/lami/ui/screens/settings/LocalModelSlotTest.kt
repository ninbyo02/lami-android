package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelSlotTest {
    @Test
    fun `local model slots expose separate labels and routes`() {
        assertEquals("Snapdragon NPU向けのLiteRTモデル", LocalModelSlot.NpuPreview.title)
        assertEquals("GPU／CPU向けのLiteRTモデル", LocalModelSlot.GenericFallback.title)
        assertEquals(Routes.LOCAL_BASE_MODEL, SettingsRoute.LocalBaseModel.route)
        assertEquals(Routes.LOCAL_GENERIC_FALLBACK_MODEL, SettingsRoute.LocalGenericFallbackModel.route)
    }

    @Test
    fun `local model copy uses official LiteRT wording`() {
        assertEquals(
            "高速・省電力なローカル推論に使用します",
            LocalModelSlot.NpuPreview.description,
        )
        assertEquals(
            "NPUを使用できない場合や長い処理に使用します",
            LocalModelSlot.GenericFallback.description,
        )
        assertEquals("LiteRTモデルが選択されていません", localModelSlotStatusLabel(null))
    }

    @Test
    fun `focus highlights both cards when both models are unconfigured`() {
        SettingsLocalModelFocus.entries.forEach { focus ->
            assertEquals(
                setOf(LocalModelSlot.NpuPreview, LocalModelSlot.GenericFallback),
                resolveLocalModelHighlightSlots(
                    focus = focus,
                    isNpuConfigured = false,
                    isGenericConfigured = false,
                ),
            )
        }
    }

    @Test
    fun `focus highlights only the unconfigured card when one model is configured`() {
        assertEquals(
            setOf(LocalModelSlot.NpuPreview),
            resolveLocalModelHighlightSlots(
                focus = SettingsLocalModelFocus.GENERIC,
                isNpuConfigured = false,
                isGenericConfigured = true,
            ),
        )
        assertEquals(
            setOf(LocalModelSlot.GenericFallback),
            resolveLocalModelHighlightSlots(
                focus = SettingsLocalModelFocus.NPU,
                isNpuConfigured = true,
                isGenericConfigured = false,
            ),
        )
    }

    @Test
    fun `focus highlights nothing when both models are configured or no focus was requested`() {
        assertEquals(
            emptySet<LocalModelSlot>(),
            resolveLocalModelHighlightSlots(
                focus = SettingsLocalModelFocus.SECTION,
                isNpuConfigured = true,
                isGenericConfigured = true,
            ),
        )
        assertEquals(
            emptySet<LocalModelSlot>(),
            resolveLocalModelHighlightSlots(
                focus = null,
                isNpuConfigured = false,
                isGenericConfigured = false,
            ),
        )
    }

    @Test
    fun `local model highlight starts off pulses border three times and ends off`() {
        assertEquals(false, LocalModelHighlightVisualContract.changesContainerColor)
        assertEquals(1, LocalModelHighlightVisualContract.restingBorderWidthDp)
        assertEquals(2, LocalModelHighlightVisualContract.highlightBorderWidthDp)
        assertEquals(false, LocalModelHighlightVisualContract.startsHighlighted)
        assertEquals(3, LocalModelHighlightVisualContract.pulseCount)
        assertEquals(400, LocalModelHighlightVisualContract.offPhaseMillis)
        assertEquals(400, LocalModelHighlightVisualContract.onPhaseMillis)
        assertEquals(2400, LocalModelHighlightVisualContract.totalDurationMillis)
        assertEquals(false, LocalModelHighlightVisualContract.endsHighlighted)
    }

    @Test
    fun `local model cards reserve configured height to prevent layout shift`() {
        assertEquals(120, LocalModelCardLayoutContract.minHeightDp)
        assertEquals(true, LocalModelCardLayoutContract.alwaysReservesClearActionSlot)
    }

    @Test
    fun `local model focus scroll uses section item and offset without spacer anchor`() {
        assertEquals("local-model-section", SettingsLocalModelScrollTarget.itemKey)
        assertEquals(3, SettingsLocalModelScrollTarget.itemIndex)
        assertEquals(-52, SettingsLocalModelScrollTarget.scrollOffsetDp)
    }
}
