package io.github.ninbyo02.lami.npu

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244StandardJniIsolationContractTest {
    @Test
    fun `standard debug uses isolated generated qairt jni overlay`() {
        val buildFile = File("build.gradle.kts")
        assertTrue("app/build.gradle.kts must be readable from the app test working directory", buildFile.isFile)
        val text = buildFile.readText()

        assertTrue(text.contains("getByName(\"standardDebug\")"))
        assertTrue(text.contains("generated/qairt244StandardDebugJniLibs"))
        assertTrue(text.contains("tasks.register(\"stageQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("tasks.register(\"overlayQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("tasks.register(\"overlayQairt244StandardDebugStrippedNativeLibs\")"))

        // Standard must consume the separated NPU JNI bridge rather than the old smoke or
        // monolithic LiteRT-LM JNI artifacts from the custom-build experiment source tree.
        assertTrue(text.contains("exclude(\"liblami_qairt244_smoke.so\")"))
        assertTrue(text.contains("exclude(\"liblitertlm_jni.so\")"))
        assertTrue(text.contains("liblami_qairt244_npu_jni.so"))
        assertTrue(text.contains("nativeRunEditablePrompt"))

        // Packaging must depend on the isolated overlay path; Standard must not point its
        // source set directly at the custom-build experiment jniLibs directory.
        assertTrue(text.contains("dependsOn(\"overlayQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("dependsOn(\"overlayQairt244StandardDebugStrippedNativeLibs\")"))
        assertFalse(
            text.contains(
                "getByName(\"standardDebug\") {\n            jniLibs.srcDir(\"src/customBuildExperimentDebug/jniLibs\")",
            ),
        )
    }
}
