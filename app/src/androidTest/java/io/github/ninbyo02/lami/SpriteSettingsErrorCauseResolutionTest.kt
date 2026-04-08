package io.github.ninbyo02.lami

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ninbyo02.lami.ui.screens.settings.ErrorCause
import io.github.ninbyo02.lami.viewmodels.resolveErrorKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteSettingsErrorCauseResolutionTest {

    @Test
    fun resolveErrorKey_usesCauseWhenNoUserSelection() {
        assertEquals("ErrorHeavy", resolveErrorKey(null, ErrorCause.HEAVY))
        assertEquals("ErrorLight", resolveErrorKey(null, ErrorCause.LIGHT))
    }
}
