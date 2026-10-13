package io.github.ninbyo02.lami

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageBenchmarkReleaseIsolationSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").isFile }
    private val releaseBlock = File(root, "app/build.gradle.kts").readText()
        .substringAfter("        release {\n").substringBefore("    compileOptions {")

    @Test fun releaseBenchmarkUsesOnlyOptInIsolatedPackage() {
        assertTrue(releaseBlock.contains("if (imageBenchmark.get())"))
        assertTrue(releaseBlock.contains("applicationIdSuffix = \".imagebench\""))
        assertTrue(releaseBlock.contains("versionNameSuffix = \"-imageBenchmarkRelease\""))
    }

    @Test fun releaseBenchmarkRetainsReleaseApiAndDoesNotBecomeDebuggable() {
        assertTrue(releaseBlock.contains("liteRtLmAndroidReleaseVersion"))
        assertFalse(releaseBlock.contains("isDebuggable = true"))
    }
}
