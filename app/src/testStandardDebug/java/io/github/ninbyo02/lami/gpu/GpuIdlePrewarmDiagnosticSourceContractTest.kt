package io.github.ninbyo02.lami.gpu

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuIdlePrewarmDiagnosticSourceContractTest {
    private val gradle = File("build.gradle.kts").readText()
    private val manifest = File("src/standardDebug/AndroidManifest.xml").readText()
    private val source = File(
        "src/debug/java/io/github/ninbyo02/lami/gpu/" +
            "GpuIdlePrewarmDiagnosticReceiver.kt",
    ).readText()

    @Test
    fun `diagnostic requires explicit debug-only isolated build property`() {
        assertTrue(
            gradle.contains(
                "providers.gradleProperty(\"lami.gpuIdlePrewarmDiagnostic\")",
            ),
        )
        assertTrue(gradle.contains("applicationIdSuffix = \".gpuidleprewarm\""))
        assertTrue(
            gradle.contains(
                "flavor == \"standard\" && variant.buildType == \"debug\" && " +
                    "gpuIdlePrewarmDiagnostic.get()",
            ),
        )
        assertTrue(
            source.contains(
                "!BuildConfig.DEBUG || !BuildConfig.GPU_IDLE_PREWARM_DIAGNOSTIC",
            ),
        )
        assertTrue(
            source.contains(
                "BuildConfig.APPLICATION_ID.endsWith(\".gpuidleprewarm\")",
            ),
        )
    }

    @Test
    fun `normal build defaults stay disabled and release cannot opt in`() {
        assertTrue(
            gradle.contains(
                "buildConfigField(\"Boolean\", " +
                    "\"GPU_IDLE_PREWARM_DIAGNOSTIC\", \"false\")",
            ),
        )
        assertFalse(
            gradle.contains(
                "variant.buildType == \"release\" && gpuIdlePrewarmDiagnostic",
            ),
        )
    }

    @Test
    fun `activity retains the long diagnostic and cancel receiver shares its process`() {
        assertTrue(manifest.contains("GpuIdlePrewarmDiagnosticActivity"))
        assertTrue(manifest.contains("GpuIdlePrewarmDiagnosticReceiver"))
        assertTrue(manifest.contains("android:process=\":gpu_idle_prewarm\""))
        assertTrue(manifest.contains("GPU_IDLE_PREWARM_DIAGNOSTIC"))
        assertTrue(manifest.contains("CANCEL_GPU_IDLE_PREWARM_DIAGNOSTIC"))
        assertTrue(source.contains("class GpuIdlePrewarmDiagnosticActivity : Activity()"))
        assertTrue(source.contains("override fun onStop()"))
        assertFalse(source.contains("goAsync()"))
    }
    @Test
    fun `engine constraints match the normal GPU product route`() {
        assertTrue(source.contains("private const val GPU_CONTEXT_MAX_TOKENS = 512"))
        assertTrue(source.contains("backend = Backend.GPU()"))
        assertTrue(source.contains("visionBackend = Backend.GPU()"))
        assertTrue(source.contains("audioBackend = Backend.CPU()"))
        assertTrue(source.contains("LocalConversationPolicy.conversationConfig()"))
        assertTrue(source.contains("LocalConversationPolicy.generationExtraContext"))
        assertTrue(source.contains("Conversation.sendMessageAsync"))
        assertTrue(source.contains("sendMessageAsync("))
        assertTrue(source.contains("\"sampler_profile\", \"lami_stable_v1\""))
        assertTrue(
            source.contains(
                "\"prompt_template_owner\", \"model_metadata\"",
            ),
        )
    }

    @Test
    fun `all lifecycle cancellation causes are allowlisted`() {
        listOf(
            "navigation",
            "model_changed",
            "backend_changed",
            "low_memory",
            "background_confirmed",
            "user_stop",
            "timeout",
        ).forEach { reason ->
            assertTrue(source.contains("\"" + reason + "\""))
        }
        assertTrue(source.contains("override fun onLowMemory()"))
        assertTrue(source.contains("override fun onTrimMemory(level: Int)"))
    }
    @Test
    fun `cancellation always releases conversation before engine`() {
        val releaseBody = source.substringAfter("private fun releaseResources() {")
            .substringBefore("private fun ensureNotCancelled()")
        val conversationClose =
            releaseBody.indexOf("activeConversation.getAndSet(null)?.close()")
        val engineClose =
            releaseBody.indexOf("activeEngine.getAndSet(null)?.close()")
        assertTrue(conversationClose >= 0)
        assertTrue(engineClose > conversationClose)
    }

    @Test
    fun `result captures latency resource thermal and energy evidence`() {
        listOf(
            "engine_create_ms",
            "conversation_create_ms",
            "send_ms",
            "user_visible_latency_ms",
            "prewarm_lead_ms",
            "ttft_ms",
            "prefill_tokens_per_second",
            "decode_tokens_per_second",
            "main_thread_max_latency_ms",
            "rss_kb",
            "pss_kb",
            "battery_temp_tenths_c",
            "energy_counter_nwh",
            "thermal_status",
            "resources_closed",
        ).forEach { field ->
            assertTrue(source.contains("\"" + field + "\""))
        }
    }
}
