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

        // Standard keeps the separated legacy bridge isolated, while the Kotlin Conversation
        // NPU route intentionally overlays the patched stock-name LiteRT-LM JNI.
        assertTrue(text.contains("exclude(\"liblami_qairt244_smoke.so\")"))
        assertTrue(text.contains("liblami_qairt244_npu_jni.so"))
        assertTrue(text.contains("nativeRunEditablePrompt"))
        assertTrue(text.contains("File(outputDir, \"liblitertlm_jni.so\")"))
        assertTrue(text.contains("qairt244_kotlin_npu_conversation_sampler_v1"))
        assertFalse(text.contains("exclude(\"liblitertlm_jni.so\")"))

        // A clean hosted runner may opt into a deliberately non-NPU smoke APK, but the
        // default and every staged-but-invalid JNI remain strict.
        assertTrue(text.contains("lami.allowMissingQairt244Jni"))
        assertTrue(text.contains("if (!litertLmJni.isFile && allowMissingQairt244Jni.get())"))
        assertTrue(text.contains("Do not use this APK as NPU promotion evidence"))
        assertTrue(text.contains("require(litertLmJni.isFile)"))

        assertTrue(text.contains("dependsOn(\"overlayQairt244StandardDebugNativeLibs\")"))
        assertTrue(text.contains("dependsOn(\"overlayQairt244StandardDebugStrippedNativeLibs\")"))
        val standardDebugBlockDirectCustomJni = Regex(
            pattern = """(?:maybeCreate|getByName)\("standardDebug"\)[^{]*\{[^}]*src/customBuildExperimentDebug/jniLibs""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
        assertFalse(standardDebugBlockDirectCustomJni.containsMatchIn(text))
    }
}
