package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImageInputTest {
    @Test
    fun knownGemmaVisionFingerprintRequiresExactSizeAndSha256() {
        assertTrue(
            isKnownGemmaVisionModelFingerprint(
                sizeBytes = 3_016_294_400L,
                sha256 = "41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e",
            ),
        )
        assertTrue(
            isKnownGemmaVisionModelFingerprint(
                sizeBytes = 2_588_147_712L,
                sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            ),
        )
    }

    @Test
    fun knownGemmaVisionFingerprintRejectsNameOnlyOrSameSizeDifferentContent() {
        assertFalse(
            isKnownGemmaVisionModelFingerprint(
                sizeBytes = 3_016_294_400L,
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            ),
        )
        assertFalse(
            isKnownGemmaVisionModelFingerprint(
                sizeBytes = 3_016_294_399L,
                sha256 = "41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e",
            ),
        )
    }
}
