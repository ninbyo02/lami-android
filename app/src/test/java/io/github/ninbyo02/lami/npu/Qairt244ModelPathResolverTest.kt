package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Qairt244ModelPathResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `preferred managed model wins when duplicate candidates exist`() {
        val modelsDir = temporaryFolder.newFolder("local_models")
        val first = createModel(modelsDir, "100_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")
        val selected = createModel(modelsDir, "200_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")

        val resolution = Qairt244ModelPathResolver.resolve(modelsDir, selected.absolutePath)

        assertTrue(resolution.resolved)
        assertEquals(selected.canonicalPath, resolution.path)
        assertEquals(2, resolution.candidates.size)
        assertTrue(first.exists())
    }
    @Test
    fun `duplicate candidates remain ambiguous without saved preference`() {
        val modelsDir = temporaryFolder.newFolder("local_models")
        createModel(modelsDir, "100_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")
        createModel(modelsDir, "200_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")

        val resolution = Qairt244ModelPathResolver.resolve(modelsDir)

        assertFalse(resolution.resolved)
        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_AMBIGUOUS, resolution.reasonCode)
        assertNull(resolution.path)
    }

    @Test
    fun `preferred model outside managed directory is rejected`() {
        val modelsDir = temporaryFolder.newFolder("local_models")
        createModel(modelsDir, "100_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")
        val outside = createModel(
            temporaryFolder.root,
            "200_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}",
        )

        val resolution = Qairt244ModelPathResolver.resolve(modelsDir, outside.absolutePath)

        assertFalse(resolution.resolved)
        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_INVALID, resolution.reasonCode)
        assertTrue(outside.exists())
    }
    @Test
    fun `cleanup removes only unselected compatible copies`() {
        val modelsDir = temporaryFolder.newFolder("local_models")
        val orphan = createModel(modelsDir, "100_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")
        val selected = createModel(modelsDir, "200_${Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME}")
        val generic = createModel(modelsDir, "300_gemma-4-E2B-it.litertlm")

        val cleanup = Qairt244ModelPathResolver.cleanupOrphanedCompatibleCopies(
            localModelsDir = modelsDir,
            selectedModelPath = selected.absolutePath,
        )

        assertTrue(cleanup.selectedPathValid)
        assertEquals(listOf(orphan.absolutePath), cleanup.deletedPaths)
        assertTrue(cleanup.failedPaths.isEmpty())
        assertFalse(orphan.exists())
        assertTrue(selected.exists())
        assertTrue(generic.exists())
    }

    private fun createModel(directory: File, name: String): File =
        File(directory, name).apply { writeText("model") }
}
