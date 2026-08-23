package io.github.ninbyo02.lami.npu

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244StandardJniIsolationContractTest {
    @Test
    fun `standard debug uses isolated generated qairt jni overlay`() {
        val buildFile = sequenceOf(
            File("app/build.gradle.kts"),
            File("build.gradle.kts"),
        ).firstOrNull { candidate ->
            candidate.isFile && candidate.readText().contains("qairt244StandardDebugJniLibs")
        } ?: error("could not locate app/build.gradle.kts containing the qairt244 Standard Debug overlay")
        val text = buildFile.readText()

        val standardDebugGeneratedSourceSet = Regex(
            pattern = """(?:maybeCreate|getByName)\("standardDebug"\)[^{]*\{[^}]*generated/qairt244StandardDebugJniLibs""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(standardDebugGeneratedSourceSet.containsMatchIn(text))
        assertTrue(text.contains("tasks.register(\"stageQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("tasks.register(\"overlayQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("tasks.register(\"overlayQairt244StandardDebugStrippedNativeLibs\")"))

        // Standard must consume the separated NPU JNI bridge rather than the old smoke or
        // monolithic LiteRT-LM JNI artifacts from the custom-build experiment source tree.
        assertTrue(text.contains("exclude(\"liblami_qairt244_smoke.so\")"))
        assertTrue(text.contains("exclude(\"liblitertlm_jni.so\")"))
        assertTrue(text.contains("liblami_qairt244_npu_jni.so"))
        assertTrue(text.contains("nativeRunEditablePrompt"))

        assertTrue(text.contains("dependsOn(\"overlayQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("dependsOn(\"overlayQairt244StandardDebugStrippedNativeLibs\")"))
        val standardDebugBlockDirectCustomJni = Regex(
            pattern = """(?:maybeCreate|getByName)\("standardDebug"\)[^{]*\{[^}]*src/customBuildExperimentDebug/jniLibs""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
        assertFalse(standardDebugBlockDirectCustomJni.containsMatchIn(text))
    }
}
