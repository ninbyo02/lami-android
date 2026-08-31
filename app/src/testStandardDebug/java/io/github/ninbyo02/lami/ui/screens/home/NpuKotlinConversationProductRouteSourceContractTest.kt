package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuKotlinConversationProductRouteSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `product candidate uses model owned Kotlin Conversation API on NPU`() {
        val source = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuKotlinConversationProductRoute.kt",
        ).readText()

        assertTrue(source.contains("Backend.NPU(context.applicationInfo.nativeLibraryDir)"))
        assertTrue(source.contains("activeEngine.createConversation("))
        assertTrue(source.contains("activeConversation.sendMessage("))
        assertTrue(source.contains("LocalConversationPolicy.conversationConfig(initialTurns)"))
        assertTrue(source.contains("conversationApiUsed = true"))
        assertTrue(source.contains("appTemplateUsed = false"))
        assertFalse(source.contains("renderForNativeAdapter"))
        assertFalse(source.contains("<|turn>"))
    }

    @Test
    fun `ChatScreen does not mix Kotlin Conversation and legacy NPU adapter in one request`() {
        val chat = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val kotlinRouteIndex = chat.indexOf("NpuKotlinConversationProductRoute.run(")
        assertTrue(kotlinRouteIndex >= 0)
        assertTrue(chat.contains("if (NpuKotlinConversationProductRoute.enabled)"))
        assertTrue(chat.contains("npu_product_route=KOTLIN_CONVERSATION_API"))
        assertTrue(chat.contains("KOTLIN_CONVERSATION_API_FALLBACK_LOCAL"))
        assertFalse(chat.contains("KOTLIN_CONVERSATION_API_FALLBACK_NATIVE"))
        assertTrue(chat.contains("adapter_failure:kotlin_conversation_product_route:"))
    }

    @Test
    fun `Standard NPU packaging requires sampler aligned stock JNI`() {
        val build = File(root, "app/build.gradle.kts").readText()

        assertTrue(build.contains("qairt244_kotlin_npu_conversation_sampler_v1"))
        assertTrue(build.contains("File(outputDir, \"liblitertlm_jni.so\")"))
        assertTrue(build.contains("standardDebug NPU Conversation route requires patched stock-name liblitertlm_jni.so"))
        assertTrue(build.contains("Enabled Standard Release NPU Conversation runtime requires patched liblitertlm_jni.so"))
    }
}
