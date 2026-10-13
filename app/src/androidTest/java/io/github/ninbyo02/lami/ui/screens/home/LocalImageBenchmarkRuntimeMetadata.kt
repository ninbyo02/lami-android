package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import org.json.JSONObject

internal fun putLocalImageRuntimeBuildMetadata(result: JSONObject) {
    // A debug-compiled test APK also targets Release; do not inline its constants.
    val config = BuildConfig::class.java
    result.put("commit", config.getField("GIT_SHA").get(null))
        .put("litertlm_api_version", config.getField("LITERTLM_ANDROID_VERSION").get(null))
        .put("debug_build", config.getField("DEBUG").getBoolean(null))
}
