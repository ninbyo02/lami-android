package io.github.ninbyo02.lami.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePrivacySourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `automatic backup and device transfer are disabled for private app data`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val legacyRules = File(root, "app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = File(root, "app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue("release manifest must disable automatic backup", "android:allowBackup=\"false\"" in manifest)
        listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue("legacy backup must exclude $domain", "domain=\"$domain\" path=\".\"" in legacyRules)
            assertTrue("cloud and device transfer must exclude $domain", extractionRules.countOccurrences("domain=\"$domain\" path=\".\"") == 2)
        }
        assertTrue("device transfer rules must be explicit", "<device-transfer>" in extractionRules)
    }

    @Test
    fun `local reflection trace is debug only and bounded`() {
        val writer = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/util/DebugTraceFile.kt",
        ).readText()
        val chatScreen = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()

        assertTrue("release builds must not persist reflection traces", "if (!BuildConfig.DEBUG) return" in writer)
        assertTrue("trace must have a hard size cap", "MAX_TRACE_BYTES" in writer)
        assertTrue("trace must rotate rather than grow forever", "PREVIOUS_TRACE_FILE" in writer)
        val streamTraceBlock = chatScreen
            .substringAfter("fun logStreamTrace")
            .substringBefore("fun debugLocalUiTrace")
        assertTrue("stream diagnostics must be disabled in release builds", "if (!BuildConfig.DEBUG) return" in streamTraceBlock)
        assertTrue("ChatScreen must delegate trace persistence", "DebugTraceFile.append" in chatScreen)
        assertFalse("ChatScreen must not append the trace file directly", "traceFile.appendText" in chatScreen)
    }

    @Test
    fun `production source does not ship a private home network event endpoint`() {
        val viewModel = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt",
        ).readText()

        assertFalse("a private deployment address must not be embedded", "192.168.52." in viewModel)
        assertTrue(
            "the optional Lemonade bridge must be disabled until explicitly configured",
            "DEFAULT_LEMONADE_UNLOAD_EVENT_URL = \"\"" in viewModel,
        )
        assertTrue("explicit event URLs must use shared URL validation", "validateUrlFormat(eventUrl)" in viewModel)
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length, 1).count { it == needle }
}
