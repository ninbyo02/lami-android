package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinNpuConversationCandidateSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `candidate uses official Kotlin Conversation API with NPU backend`() {
        val source = File(
            root,
            "app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/KotlinNpuConversationCandidate.kt",
        ).readText()

        assertTrue(source.contains("Backend.NPU(appContext.applicationInfo.nativeLibraryDir)"))
        assertTrue(source.contains("engine.createConversation("))
        assertTrue(source.contains("conversation.sendMessage("))
        assertTrue(source.contains("LocalConversationPolicy.conversationConfig(preface.initialTurns)"))
        assertTrue(source.contains("suspend fun runSequence("))
        assertTrue(source.contains("conversation = engine.createConversation(LocalConversationPolicy.conversationConfig())"))
        assertTrue(source.contains("normalizedPrompts.forEachIndexed"))
        assertFalse(source.contains("renderForNativeAdapter"))
        assertFalse(source.contains("<|turn>"))
    }

    @Test
    fun `candidate remains isolated and JNI patch only aligns NPU sampler backend`() {
        val manifest = File(root, "app/src/customBuildExperimentDebug/AndroidManifest.xml").readText()
        val receiver = File(
            root,
            "app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/KotlinNpuConversationCandidateReceiver.kt",
        ).readText()
        val patch = File(root, "patches/qairt244_litertlm_kotlin_npu_conversation_sampler.patch").readText()
        val normalMain = File(root, "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(manifest.contains("KotlinNpuConversationCandidateReceiver"))
        assertTrue(manifest.contains("KOTLIN_NPU_CONVERSATION_CANDIDATE"))
        assertTrue(receiver.contains("EXTRA_PROMPT_2"))
        assertTrue(receiver.contains("EXTRA_PROMPT_3"))
        assertTrue(receiver.contains("KotlinNpuConversationCandidate.runSequence("))
        assertTrue(receiver.contains("SCENARIO_COLOR_CORRECTION_JA"))
        assertTrue(receiver.contains("好きな色を青に訂正します。"))
        assertTrue(patch.contains("session_config.SetSamplerBackend(Backend::NPU)"))
        assertTrue(patch.contains("GetMainExecutorSettings().GetBackend()"))
        assertTrue(patch.contains("qairt244_kotlin_npu_conversation_sampler_v1"))
        assertFalse(patch.contains("SetPromptTemplate"))
        assertFalse(patch.contains("overwrite_prompt_template"))
        assertFalse(normalMain.contains("KotlinNpuConversationCandidateReceiver"))
    }
}
