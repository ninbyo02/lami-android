package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuReleaseValidationSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `FastRPC visibility is scoped away from normal Standard Release`() {
        val standard = File(root, "app/src/standard/AndroidManifest.xml").readText()
        val standardDebug = File(root, "app/src/standardDebug/AndroidManifest.xml").readText()
        val validation = File(root, "app/src/standardNpuRuntime/AndroidManifest.xml").readText()
        val build = File(root, "app/build.gradle.kts").readText()
        val nativePatch = File(
            root,
            "patches/qairt244_litertlm_utf8_128token_persistent_probe.patch",
        ).readText()

        assertFalse("normal Standard must not request vendor namespace", "libcdsprpc.so" in standard)
        assertTrue("Standard Debug keeps device validation visibility", "libcdsprpc.so" in standardDebug)
        assertTrue("enabled Release validation requests FastRPC visibility", "libcdsprpc.so" in validation)
        assertTrue("validation source is property-gated", "java.srcDir(\"src/standardNpuRuntime/java\")" in build)
        assertTrue(
            "enabled validation runtime must be extracted for nativeLibraryDir",
            "variant.packaging.jniLibs.useLegacyPackaging.set(true)" in build,
        )
        assertTrue(
            "valid pre-1_0 Dispatch API versions must not be rejected",
            "api.version.minor > 0" in nativePatch,
        )
    }

    @Test
    fun `Release preflight is isolated permission guarded and externally collectible`() {
        val validation = File(root, "app/src/standardNpuRuntime/AndroidManifest.xml").readText()
        val smoke = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt",
        ).readText()
        val runner = File(root, "scripts/run_standard_npu_release_preflight.sh").readText()
        val conversationRunner = File(
            root,
            "scripts/run_standard_npu_release_conversation_validation.sh",
        ).readText()

        assertTrue("preflight must run outside the product process", "android:process=\":npu_preflight\"" in validation)
        assertTrue("only shell or privileged callers may trigger it", "android:permission=\"android.permission.DUMP\"" in validation)
        assertTrue("Release validation diagnostics must survive an isolated crash", "getExternalFilesDir(null)" in smoke)
        assertTrue("cold installed packages must be explicitly included", "--include-stopped-packages" in runner)
        assertTrue("post-install stopped state must be cleared before cold receiver start", "shell monkey -p \"\$app_id\"" in runner)
        assertTrue("conversation validation must clear the same stopped state", "shell monkey -p \"\$app_id\"" in conversationRunner)
        assertTrue("runner must require GetApi success", "dispatch_preflight_get_api_status=0" in runner)
        assertTrue("Release validation must use the actual first-turn output", "turn1_output=\$turn_output" in conversationRunner)
        assertTrue("Release validation must assert NPU evidence", "QNN_HTP_V79_FastRPC_native_diag" in conversationRunner)
        assertTrue("Release validation must enforce the input bound", "-le 128" in conversationRunner)
    }

    @Test
    fun `normal route owns no chat template and native NPU is an explicit exception`() {
        val policy = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalConversationPolicy.kt",
        ).readText()
        val history = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalConversationHistoryPolicy.kt",
        ).readText()
        val runner = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt",
        ).readText()
        val npuContract = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Contract.kt",
        ).readText()
        val normalRouteSources = listOf(policy, history, runner).joinToString("\n")

        listOf(
            "<start_of_turn>",
            "<end_of_turn>",
            "<|im_start|>",
            "<|im_end|>",
            "raw_dialog_tail_variant_a",
            "simple_ja_chat",
            "gemma_it_like",
        ).forEach { marker ->
            assertFalse("normal route must not own template marker $marker", marker in normalRouteSources)
        }
        assertTrue(
            "normal route must configure roles through ConversationConfig",
            "LocalConversationPolicy.conversationConfig(initialTurns)" in runner,
        )
        assertTrue(
            "normal route must create a Conversation API conversation",
            Regex(
                """engine\.createConversation\(\s*LocalConversationPolicy\.conversationConfig\(initialTurns\)\s*\)""",
            ).containsMatchIn(runner),
        )
        assertTrue(
            "normal route must send unwrapped user content",
            Regex("""conversation\.sendMessageAsync\(\s*prompt,""").containsMatchIn(runner),
        )
        assertTrue(
            "normal route diagnostics must report model-owned template evaluation",
            "PROMPT_TEMPLATE_OWNER = \"model_metadata\"" in policy &&
                "PROMPT_TEMPLATE_EVALUATOR = \"litert_lm_conversation_api\"" in policy &&
                "APP_TEMPLATE_USED = false" in policy &&
                "TEMPLATE_OWNERSHIP_UNIFIED = true" in policy,
        )
        assertTrue(
            "native NPU serialization must use the verified model-owned template profile",
            "PROMPT_TEMPLATE_OWNER = LocalConversationPolicy.PROMPT_TEMPLATE_OWNER" in npuContract &&
                "PROMPT_TEMPLATE_EVALUATOR = ModelOwnedChatTemplate.EVALUATOR" in npuContract &&
                "CONVERSATION_API_USED = false" in npuContract &&
                "APP_TEMPLATE_USED = false" in npuContract &&
                "TEMPLATE_OWNERSHIP_UNIFIED = true" in npuContract,
        )
    }
}
