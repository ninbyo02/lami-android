package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrBuildPcRemoteScriptContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `ASR remote build runner remains directly executable`() {
        val script = File(root, "scripts/lami_asr_buildpc_remote.sh")
        assertTrue("ASR remote build runner must exist", script.isFile)
        assertTrue("ASR remote build runner must keep the git executable bit", script.canExecute())

        val process = ProcessBuilder(script.absolutePath, "--help")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        assertEquals(output, 0, exitCode)
        assertTrue(output, output.contains("Usage: scripts/lami_asr_buildpc_remote.sh"))
    }
}
