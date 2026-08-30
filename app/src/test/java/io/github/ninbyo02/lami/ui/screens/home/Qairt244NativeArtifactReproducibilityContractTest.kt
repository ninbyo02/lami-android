package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244NativeArtifactReproducibilityContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `native rebuild runner is executable and documents its safe entry point`() {
        val runner = File(root, "scripts/rebuild_qairt244_standard_debug_native_stack.sh")
        assertTrue("reproducible QAIRT runner must exist", runner.isFile)
        assertTrue("reproducible QAIRT runner must be executable", runner.canExecute())

        val process = ProcessBuilder(runner.absolutePath, "--help")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
        assertTrue(output, output.contains("--preflight-only"))
        assertTrue(output, output.contains("--keep-bazel-output-base"))
        assertTrue(output, output.contains("--require-persistent-probe"))
        assertTrue(output, output.contains("--conversation-patch"))
        assertTrue(output, output.contains("never reset or cleaned"))
    }

    @Test
    fun `conversation probe delegates prompt ownership to model metadata`() {
        val patch = File(root, "patches/qairt244_litertlm_conversation_api_probe.patch").readText()

        assertTrue(patch.contains("ConversationConfig::Builder()"))
        assertTrue(patch.contains("Conversation::Create"))
        assertTrue(patch.contains("conversation_ptr->SendMessage"))
        assertTrue(patch.contains("conversation_api_used=true"))
        assertTrue(patch.contains("direct_session_api_used=false"))
        assertTrue(patch.contains("direct_run_prefill_used=false"))
        assertTrue(patch.contains("direct_run_decode_used=false"))
        assertTrue(patch.contains("overwrite_prompt_template_used=false"))
        assertTrue(patch.contains("model_template_source=model_metadata"))
        assertFalse(patch.contains("SetPromptTemplate"))
    }

    @Test
    fun `native rebuild uses a pinned isolated worktree and verifies the staged artifact`() {
        val runner = File(root, "scripts/rebuild_qairt244_standard_debug_native_stack.sh").readText()
        val forcedCommands = File(root, "scripts/lami_build_qairt244_forced_commands.sh").readText()

        assertTrue(runner.contains("c87189528a758db32ead241f4fc9c64836398ee7"))
        assertTrue(runner.contains("worktree add --detach"))
        assertTrue(runner.contains("GIT_LFS_SKIP_SMUDGE=1"))
        assertTrue(runner.contains("source_worktree_unchanged"))
        assertTrue(runner.contains("source_status_hash_before"))
        assertTrue(runner.contains("SOURCE_STATUS_HASH_BEFORE"))
        assertTrue(runner.contains("source_worktree_fingerprint_before"))
        assertTrue(runner.contains("git -C \"\$checkout\" diff --binary"))
        assertTrue(runner.contains("ls-files --others --exclude-standard"))
        assertTrue(runner.contains("liblami_qairt244_npu_jni.so"))
        assertTrue(runner.contains("Android SDK is missing"))
        assertTrue(runner.contains("selected ref is missing the Gemma provider"))
        assertTrue(runner.contains("patchelf --print-soname"))
        assertTrue(runner.contains("nativeRunEditablePrompt"))
        assertTrue(runner.contains("nativeRunPersistentProbe"))
        assertTrue(runner.contains("nativeRunConversationApiProbe"))
        assertTrue(runner.contains("qairt244_litertlm_conversation_api_probe.patch"))
        assertTrue(runner.contains("conversation_api_used=true"))
        assertTrue(runner.contains("model_template_source=model_metadata"))
        assertTrue(runner.contains("REQUIRE_PERSISTENT_PROBE"))
        assertTrue(runner.contains("staged separated JNI SHA does not match artifact"))
        assertTrue(runner.contains("bazel_output_base_removed"))
        assertTrue(runner.contains("chmod -R u+rwX"))
        assertTrue(runner.contains("rm -rf -- \"\$REPRO_BAZEL_OUTPUT_BASE\""))
        assertFalse(runner.contains("reset --hard"))
        assertFalse(runner.contains("clean -fdx"))

        assertTrue(forcedCommands.contains("rebuild_qairt244_standard_debug_native_stack.sh"))
        assertTrue(forcedCommands.contains("isolated_worktree=true"))
        assertTrue(forcedCommands.contains("--require-persistent-probe"))
        assertTrue(forcedCommands.contains("--artifact-dir"))
        assertFalse(forcedCommands.contains("reset --hard"))
        assertFalse(forcedCommands.contains("clean -fdx"))
    }
}
