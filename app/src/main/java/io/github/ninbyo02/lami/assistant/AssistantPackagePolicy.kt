package io.github.ninbyo02.lami.assistant

/** Combined voice delivery keeps the existing explicit headset opt-ins. */
internal object AssistantPackagePolicy {
    fun enabled(packageName: String, debug: Boolean): Boolean = debug &&
        (packageName.endsWith(".assistantprobe") ||
            packageName == "io.github.ninbyo02.lami.voiceintegrationtrial")
}
