package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject

internal fun putLocalImageRuntimeBuildMetadata(result: JSONObject) {
    // A debug-compiled test APK also targets Release; do not inline its constants.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val config = context.classLoader.loadClass(BuildConfig::class.java.name)
    result.put("commit", config.getField("GIT_SHA").get(null))
        .put("litertlm_api_version", config.getField("LITERTLM_ANDROID_VERSION").get(null))
        .put("debug_build", config.getField("DEBUG").getBoolean(null))
        .put("application_id", context.packageName)
        .put("version_name", context.packageManager.getPackageInfo(
            context.packageName, PackageManager.PackageInfoFlags.of(0),
        ).versionName)
}
