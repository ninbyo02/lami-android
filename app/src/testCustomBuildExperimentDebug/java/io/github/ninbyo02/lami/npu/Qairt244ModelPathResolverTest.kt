package io.github.ninbyo02.lami.npu

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Qairt244ModelPathResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `no litertlm file is not found`() {
        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
    }

    @Test
    fun `single valid litertlm file resolves`() {
        val model = temporaryFolder.newFile("1779578208133_gemma-4-E2B-it.litertlm")
        model.writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_OK, resolution.reasonCode)
        assertEquals(model.absolutePath, resolution.path)
        assertTrue(resolution.resolved)
    }

    @Test
    fun `multiple files select unique preferred candidate`() {
        temporaryFolder.newFile("other.litertlm").writeText("model")
        val preferred = temporaryFolder.newFile("gemma_qualcomm_sm8750.litertlm")
        preferred.writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_OK, resolution.reasonCode)
        assertEquals(preferred.absolutePath, resolution.path)
    }

    @Test
    fun `multiple equally preferred files are ambiguous`() {
        temporaryFolder.newFile("gemma_a.litertlm").writeText("model")
        temporaryFolder.newFile("gemma_b.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_AMBIGUOUS, resolution.reasonCode)
        assertNull(resolution.path)
    }

    @Test
    fun `empty selected file is invalid`() {
        val model = File(temporaryFolder.root, "gemma.litertlm")
        model.createNewFile()

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_INVALID, resolution.reasonCode)
        assertNull(resolution.path)
    }
}
