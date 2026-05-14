package io.github.ninbyo02.lami.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QnnDelegateProbeTest {
    @Test
    fun `collectSocHints includes available build fields`() {
        val hints = QnnDelegateProbe.collectSocHints(
            QnnDeviceBuildInfo(
                hardware = "qcom",
                board = "board-x",
                device = "device-x",
                product = "product-x",
                model = "Nubia Z70S Ultra",
                socModel = "SM8750",
                socManufacturer = "Qualcomm",
            ),
        )

        assertEquals(
            listOf(
                "HARDWARE=qcom",
                "BOARD=board-x",
                "DEVICE=device-x",
                "PRODUCT=product-x",
                "MODEL=Nubia Z70S Ultra",
                "SOC_MODEL=SM8750",
                "SOC_MANUFACTURER=Qualcomm",
            ),
            hints,
        )
    }

    @Test
    fun `sm8750 model is treated as likely`() {
        assertTrue(QnnDelegateProbe.isSm8750LikelyFromHints(listOf("SOC_MODEL=SM8750")))
    }

    @Test
    fun `snapdragon 8 elite string is treated as likely`() {
        assertTrue(QnnDelegateProbe.isSm8750LikelyFromHints(listOf("MODEL=Snapdragon 8 Elite reference device")))
    }

    @Test
    fun `kalama alone is not treated as sm8750 likely`() {
        assertFalse(QnnDelegateProbe.isSm8750LikelyFromHints(listOf("BOARD=kalama")))
    }
}
