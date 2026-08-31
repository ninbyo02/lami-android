package io.github.ninbyo02.lami.npu

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuKotlinConversationDurabilitySourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `debug durability harness exercises product NPU lifecycle`() {
        val source = File(
            root,
            "app/src/debug/java/io/github/ninbyo02/lami/npu/NpuKotlinConversationDurabilityReceiver.kt",
        ).readText()
        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()

        assertTrue(source.contains("NpuKotlinConversationProductRoute.run("))
        assertTrue(source.contains("notifyAppBackgrounded"))
        assertTrue(source.contains("notifyAppForegrounded"))
        assertTrue(source.contains("notifyLowMemory"))
        assertTrue(source.contains("DEFAULT_TURNS = 20"))
        assertTrue(source.contains("background_timeout_recreate_pass"))
        assertTrue(source.contains("chat_switch_engine_reuse_pass"))
        assertTrue(manifest.contains("NPU_KOTLIN_CONVERSATION_DURABILITY"))
    }
}
