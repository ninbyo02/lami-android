package com.sonusid.ollama

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonusid.ollama.ui.screens.settings.ErrorCause
import com.sonusid.ollama.ui.screens.settings.resolveErrorKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteSettingsErrorKeyResolutionTest {

    @Test
    fun resolveErrorKey_prioritizesStoredKeyOverRecommended() {
        assertEquals("ErrorHeavy", resolveErrorKey("ErrorHeavy", ErrorCause.UNKNOWN))
        assertEquals("ErrorLight", resolveErrorKey("ErrorLight", ErrorCause.NETWORK))
        assertEquals("ErrorLight", resolveErrorKey("Other", ErrorCause.NETWORK))
    }

    @Test
    fun resolveErrorKey_usesRecommendedWhenStoredKeyIsBlank() {
        assertEquals("ErrorHeavy", resolveErrorKey("", ErrorCause.NETWORK))
        assertEquals("ErrorLight", resolveErrorKey(null, ErrorCause.UNKNOWN))
        assertEquals("ErrorLight", resolveErrorKey(null, null))
    }
}
