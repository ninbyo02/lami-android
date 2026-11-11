package io.github.ninbyo02.lami.tts

/** The separately packaged debug trial uses streaming without changing app preferences. */
internal object LamiVoiceTrialPolicy {
    fun streamingEnabledForSelection(explicitSelection: Boolean, packageName: String, validationOptIn: () -> Boolean): Boolean =
        explicitSelection || streamingEnabled(packageName, validationOptIn)

    fun streamingEnabled(packageName: String, validationOptIn: () -> Boolean): Boolean =
        packageName == "io.github.ninbyo02.lami.voiceintegrationtrial" ||
            (packageName.endsWith(".npuvalidation") && validationOptIn())
}
