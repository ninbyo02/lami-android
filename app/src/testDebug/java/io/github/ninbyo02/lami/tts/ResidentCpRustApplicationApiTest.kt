package io.github.ninbyo02.lami.tts

import android.content.pm.ApplicationInfo
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResidentCpRustApplicationApiTest {
    private val spec = ResidentCpContextSpec("0".repeat(64), 1024, 16, 32, "app-loader-test")

    @Test fun packagedFactoryDoesNotAutomaticallyLoadOrExecuteNativeCode() {
        val app = RuntimeEnvironment.getApplication()
        val old = app.applicationInfo.nativeLibraryDir
        val flags = app.applicationInfo.flags
        val directory = Files.createTempDirectory("cp-app-runtime").toFile()
        try {
            app.applicationInfo.nativeLibraryDir = directory.absolutePath
            app.applicationInfo.flags = flags or ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS
            ResidentCpRustApplicationApi.create(app, spec).close()
        } finally {
            app.applicationInfo.nativeLibraryDir = old
            app.applicationInfo.flags = flags
            directory.deleteRecursively()
        }
    }

    @Test fun missingPackagedRuntimeFailsClosed() {
        val app = RuntimeEnvironment.getApplication()
        val old = app.applicationInfo.nativeLibraryDir
        val flags = app.applicationInfo.flags
        try {
            app.applicationInfo.nativeLibraryDir = "/missing/lami-resident-cp-runtime"
            app.applicationInfo.flags = flags or ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS
            try { ResidentCpRustApplicationApi.create(app, spec); fail("Expected missing packaged runtime") }
            catch (_: IllegalArgumentException) {}
        } finally { app.applicationInfo.nativeLibraryDir = old; app.applicationInfo.flags = flags }
    }

    @Test fun unextractedPackagingRejectedEvenWhenDirectoryExists() {
        val app = RuntimeEnvironment.getApplication()
        val old = app.applicationInfo.nativeLibraryDir
        val flags = app.applicationInfo.flags
        val directory = Files.createTempDirectory("cp-unextracted-runtime").toFile()
        try {
            app.applicationInfo.nativeLibraryDir = directory.absolutePath
            app.applicationInfo.flags = flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS.inv()
            try { ResidentCpRustApplicationApi.create(app, spec); fail("Expected unextracted runtime rejection") }
            catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("extracted")) }
        } finally {
            app.applicationInfo.nativeLibraryDir = old
            app.applicationInfo.flags = flags
            directory.deleteRecursively()
        }
    }
}
