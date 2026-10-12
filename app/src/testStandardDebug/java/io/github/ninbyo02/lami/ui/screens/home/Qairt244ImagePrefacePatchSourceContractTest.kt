package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244ImagePrefacePatchSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test fun nativePatchQueuesInitialImagesOnlyOnTheFirstDeferredSend() {
        val patch = File(root, "patches/qairt244_litertlm_image_preface.patch").readText()
        assertTrue(patch.contains("bool include_image_preface = false"))
        assertTrue(patch.contains("history_.empty() && !config_.prefill_preface_on_init()"))
        assertTrue(patch.contains("preface_messages.is_array()"))
        assertTrue(patch.contains("item.value(\"type\", \"\") == \"image\""))
        assertTrue(patch.contains("if (include_image_preface)"))
        assertTrue(patch.contains("source_messages = preface_messages"))
        assertTrue(patch.contains("single_turn_text, source_messages"))
        assertFalse(patch.contains("SetPrefillPrefaceOnInit(true)"))
        assertFalse(patch.contains("SetMaxNumTokens"))
        assertFalse(patch.contains("SetMaxNumImages"))
        assertFalse(patch.contains("overwrite_prompt_template"))
    }

    @Test fun rebuildRecordsAndVerifiesTheNativeFix() {
        val script = File(root, "scripts/rebuild_qairt244_standard_debug_native_stack.sh").readText()
        assertTrue(script.contains("apply_patch \"\$IMAGE_PREFACE_PATCH\" image_preface"))
        assertTrue(script.contains("image_preface_patch_sha256"))
        assertTrue(script.contains("qairt244_image_preface_queue_v2"))
    }
}
