package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NativeFileDiagnosticCacheTest {
    @Test fun unchangedFileIsReadOnceAndReplacementIsRecomputed() {
        val file = File.createTempFile("native-cache", ".so")
        try {
            val cache = NativeFileDiagnosticCache<String>()
            var reads = 0
            fun read() = cache.read(file) { reads++; file.readText() }
            file.writeText("one")
            assertEquals("one", read()); assertEquals("one", read()); assertEquals(1, reads)
            file.writeText("replacement")
            assertEquals("replacement", read()); assertEquals(2, reads)
        } finally { file.delete() }
    }
    @Test fun unavailableResultsAreRetried() {
        val file = File.createTempFile("native-cache", ".so")
        try {
            val cache = NativeFileDiagnosticCache<String> { it != "unavailable" }
            assertEquals("unavailable", cache.read(file) { "unavailable" })
            assertEquals("hash", cache.read(file) { "hash" })
            assertEquals("hash", cache.read(file) { error("must use cached hash") })
        } finally { file.delete() }
    }
    @Test fun TimestampChangeInvalidatesSameLengthFile() {
        val file = File.createTempFile("native-cache", ".so")
        try {
            val cache = NativeFileDiagnosticCache<String>()
            file.writeText("one"); val old = file.lastModified()
            assertEquals("one", cache.read(file) { file.readText() })
            file.writeText("two"); check(file.setLastModified(old + 2000))
            assertEquals("two", cache.read(file) { file.readText() })
        } finally { file.delete() }
    }
}
