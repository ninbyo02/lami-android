package io.github.ninbyo02.lami.asr.platform.model

import io.github.ninbyo02.lami.asr.foundation.model.DistributionReview
import io.github.ninbyo02.lami.asr.foundation.model.ModelFileRole
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReazonSpeechModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun productionManifestPinsRuntimeLicenseAndEveryModelFile() {
        val manifest = ReazonSpeechModel.manifest

        assertEquals("reazonspeech-k2-v2", manifest.id)
        assertEquals("2024-08-01", manifest.version)
        assertEquals("1.13.6", manifest.runtimeVersion)
        assertEquals(listOf("ja"), manifest.languages)
        assertEquals(DistributionReview.APPROVED, manifest.license.distributionReview)
        assertEquals(
            "2576f6e72ec5ff83a06f9fdee5d6a525748a282e65b3ef7728ae128db0a3d6bc",
            manifest.approvalFingerprint,
        )
        assertEquals(
            setOf(
                ModelFileRole.ENCODER,
                ModelFileRole.DECODER,
                ModelFileRole.JOINER,
                ModelFileRole.TOKENS,
            ),
            manifest.filesByRole.keys,
        )
    }

    @Test
    fun exactApprovedFileSelectionIsAcceptedInAnyOrder() {
        ReazonSpeechModel.requireExactSelection(
            ReazonSpeechModel.requiredFileNames.reversed(),
        )
    }

    @Test
    fun missingUnexpectedAndDuplicateSelectionsAreRejected() {
        val required = ReazonSpeechModel.requiredFileNames.toList()

        assertThrows(IllegalArgumentException::class.java) {
            ReazonSpeechModel.requireExactSelection(required.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReazonSpeechModel.requireExactSelection(required.dropLast(1) + "unexpected.onnx")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReazonSpeechModel.requireExactSelection(required.dropLast(1) + required.first())
        }
    }

    @Test
    fun inaccessibleStagingRootReportsFilesMissingWithoutCrashing() {
        val blockingFile = temporaryFolder.newFile("model-import").toPath()
        val stagingRoot = blockingFile.resolve(ReazonSpeechModel.ID)
        val required = ReazonSpeechModel.requiredFileNames.toList()

        val missing = missingModelFiles(stagingRoot, required)

        assertEquals(required, missing)
        assertTrue(Files.isRegularFile(blockingFile))
    }
}
