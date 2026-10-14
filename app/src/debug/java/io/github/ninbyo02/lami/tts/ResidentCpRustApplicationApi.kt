package io.github.ninbyo02.lami.tts

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/** Explicit debug-only factory for the installed app, not an app_process loader. */
internal object ResidentCpRustApplicationApi {
    fun create(context: Context, spec: ResidentCpContextSpec): ResidentCpRustNativeApi {
        val app = context.applicationContext
        check(app.classLoader === ResidentCpRustNativeApi::class.java.classLoader) {
            "Use the installed application's ClassLoader"
        }
        val info = app.applicationInfo
        require(info.flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS != 0) {
            "Resident CP diagnostics require an explicitly extracted native runtime"
        }
        val path = info.nativeLibraryDir
        require(!path.isNullOrBlank()) { "Missing application nativeLibraryDir" }
        val runtime = File(path)
        require(runtime.isDirectory) { "Missing packaged diagnostic runtime" }
        return ResidentCpRustNativeApi(runtime, spec)
    }
}
