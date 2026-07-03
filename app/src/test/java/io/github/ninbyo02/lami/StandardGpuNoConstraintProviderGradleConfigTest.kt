package io.github.ninbyo02.lami

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardGpuNoConstraintProviderGradleConfigTest {
    @Test
    fun `standard gpu no constraint provider flavor excludes only constraint provider`() {
        val gradle = appBuildGradleText()
        val flavorBlock = gradle.substringAfter("create(\"standardGpuNoConstraintProvider\")")
            .substringBefore("create(\"galleryAlignedNpuProbe\")")
        val packagingBlock = gradle.substringAfter("flavor == \"standardGpuNoConstraintProvider\"")
            .substringBefore("val liteRtLmVersion = when")

        assertTrue(flavorBlock.contains("applicationIdSuffix = \".gpunoconstraint\""))
        assertTrue(flavorBlock.contains("CURRENT_FLAVOR\", \"\\\"standardGpuNoConstraintProvider\\\"\""))
        assertTrue(flavorBlock.contains("STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR\", \"true\""))
        assertTrue(packagingBlock.contains("**/libGemmaModelConstraintProvider.so"))
        assertFalse(packagingBlock.contains("libLiteRtDispatch_Qualcomm.so"))
        assertFalse(packagingBlock.contains("libLiteRtCompilerPlugin_Qualcomm.so"))
        assertFalse(packagingBlock.contains("libQnn*.so"))
    }

    private fun appBuildGradleText(): String {
        return sequenceOf(
            File("app/build.gradle.kts"),
            File("build.gradle.kts"),
        ).firstOrNull { it.isFile }?.readText()
            ?: error("app/build.gradle.kts not found from ${File(".").absolutePath}")
    }
}
