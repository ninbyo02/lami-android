package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpSamplingTest {
    @Test fun fixedSeedMatchesJavaCompatibleLcg() {
        val rng = ResidentCpGreedyFeedback.SharedDraw(42L)
        assertEquals(0.7275636800328681, rng.draw(), 0.0)
        assertEquals(0.6832234717598454, rng.draw(), 0.0)
        assertEquals(0.30871945533265976, rng.draw(), 0.0)
    }

    @Test fun sameSeedReproducesDrawSequence() {
        val a = ResidentCpGreedyFeedback.SharedDraw(42L)
        val b = ResidentCpGreedyFeedback.SharedDraw(42L)
        repeat(30) {
            val draw = a.draw()
            assertTrue(draw >= 0.0 && draw < 1.0)
            assertEquals(draw, b.draw(), 0.0)
        }
    }

    @Test fun codecTablePreflightFailsClosedForMissingFiles() {
        val directory = kotlin.io.path.createTempDirectory("lami-cp-missing-").toFile()
        try {
            assertTrue(runCatching {
                ResidentCpGreedyFeedback.verifyTables(directory)
            }.isFailure)
        } finally {
            directory.deleteRecursively()
        }
    }
}
