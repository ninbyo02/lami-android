package io.github.ninbyo02.lami.npu

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Qairt244ModelPathResolverCustomBuildTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `no litertlm file is not found`() {
        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
    }

    @Test
    fun `generic gemma file is not a runnable candidate`() {
        temporaryFolder.newFile("1779578208133_gemma-4-E2B-it.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
    }

    @Test
    fun `plain gemma E2B and E4B files are not runnable candidates`() {
        temporaryFolder.newFile("gemma-4-E2B-it.litertlm").writeText("model")
        temporaryFolder.newFile("gemma-4-E4B-it.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
        assertEquals(emptyList<String>(), resolution.candidates)
    }

    @Test
    fun `generic named litertlm file is not a runnable candidate`() {
        temporaryFolder.newFile("generic.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
        assertEquals(emptyList<String>(), resolution.candidates)
    }

    @Test
    fun `qcs8275 model file is not a runnable candidate`() {
        temporaryFolder.newFile("gemma-4-E2B-it_qualcomm_qcs8275.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
    }

    @Test
    fun `single exact sm8750 model resolves`() {
        val model = temporaryFolder.newFile("gemma-4-E2B-it_qualcomm_sm8750.litertlm")
        model.writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_OK, resolution.reasonCode)
        assertEquals(model.absolutePath, resolution.path)
        assertTrue(resolution.resolved)
        assertEquals(true, resolution.checkedExists)
        assertEquals(true, resolution.checkedCanRead)
        assertEquals(5L, resolution.checkedLength)
        assertEquals("gemma-4-E2B-it_qualcomm_sm8750.litertlm", resolution.modelInfo?.resolvedModelBasename)
        assertEquals(Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME, resolution.modelInfo?.canonicalModelBasename)
        assertEquals(false, resolution.modelInfo?.timestampPrefixStripped)
    }

    @Test
    fun `timestamped sm8750 model resolves`() {
        val model = temporaryFolder.newFile("1779612108800_gemma-4-E2B-it_qualcomm_sm8750.litertlm")
        model.writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_OK, resolution.reasonCode)
        assertEquals(model.absolutePath, resolution.path)
        assertTrue(resolution.resolved)
        assertEquals("1779612108800_gemma-4-E2B-it_qualcomm_sm8750.litertlm", resolution.modelInfo?.resolvedModelBasename)
        assertEquals(Qairt244ModelPathResolver.CANONICAL_MODEL_BASENAME, resolution.modelInfo?.canonicalModelBasename)
        assertEquals(true, resolution.modelInfo?.timestampPrefixStripped)
    }

    @Test
    fun `exact sm8750 model is selected over generic qcs8275 and non numeric prefix files`() {
        temporaryFolder.newFile("gemma-4-E2B-it.litertlm").writeText("model")
        temporaryFolder.newFile("gemma-4-E4B-it.litertlm").writeText("model")
        temporaryFolder.newFile("gemma-4-E2B-it_qualcomm_qcs8275.litertlm").writeText("model")
        temporaryFolder.newFile("abc_gemma-4-E2B-it_qualcomm_sm8750.litertlm").writeText("model")
        val sm8750 = temporaryFolder.newFile("gemma-4-E2B-it_qualcomm_sm8750.litertlm")
        sm8750.writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_OK, resolution.reasonCode)
        assertEquals(sm8750.absolutePath, resolution.path)
        assertEquals(listOf(sm8750.absolutePath), resolution.candidates)
    }

    @Test
    fun `exact plus timestamped sm8750 model is ambiguous`() {
        temporaryFolder.newFile("gemma-4-E2B-it_qualcomm_sm8750.litertlm").writeText("model")
        temporaryFolder.newFile("1779612108800_gemma-4-E2B-it_qualcomm_sm8750.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_AMBIGUOUS, resolution.reasonCode)
        assertNull(resolution.path)
        assertEquals(2, resolution.candidates.size)
    }

    @Test
    fun `multiple timestamped sm8750 models are ambiguous`() {
        temporaryFolder.newFile("1779612108800_gemma-4-E2B-it_qualcomm_sm8750.litertlm").writeText("model")
        temporaryFolder.newFile("1779612108801_gemma-4-E2B-it_qualcomm_sm8750.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_AMBIGUOUS, resolution.reasonCode)
        assertNull(resolution.path)
        assertEquals(2, resolution.candidates.size)
    }

    @Test
    fun `non canonical sm8750 compatible files are not runnable candidates`() {
        temporaryFolder.newFile("a_qualcomm_sm8750.litertlm").writeText("model")
        temporaryFolder.newFile("b_qualcomm_sm8750.litertlm").writeText("model")

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_NOT_FOUND, resolution.reasonCode)
        assertNull(resolution.path)
        assertEquals(0, resolution.candidates.size)
    }

    @Test
    fun `empty selected sm8750 file is invalid`() {
        val model = File(temporaryFolder.root, "gemma-4-E2B-it_qualcomm_sm8750.litertlm")
        model.createNewFile()

        val resolution = Qairt244ModelPathResolver.resolve(temporaryFolder.root)

        assertEquals(Qairt244ModelPathResolver.REASON_MODEL_FILE_INVALID, resolution.reasonCode)
        assertNull(resolution.path)
        assertEquals(model.absolutePath, resolution.checkedPath)
        assertEquals(0L, resolution.checkedLength)
    }
    @Test
    fun `required sm8750 path check allows exact and numeric timestamped basenames only`() {
        assertTrue(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertTrue(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/1779612108800_gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/abc_gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm.bak"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/gemma-4-E2B-it_qualcomm_qcs8275.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/1779612108800_gemma-4-E2B-it_qualcomm_qcs8275.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/gemma-4-E2B-it.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/1779612108800_gemma-4-E2B-it.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/generic.litertlm"))
        assertFalse(Qairt244ModelPathResolver.isRequiredSm8750ModelPath("/tmp/1779612108800_generic.litertlm"))
    }
}
