package io.github.ninbyo02.lami.assistant

/** A new hardware invocation invalidates buffered transcripts and delayed sends immediately. */
internal class AssistantSubmissionOwner {
    private var generation = 0L
    private var current = 0L
    private var offered = false
    private var consumed = false
    private var claimed = false
    private var stopped = false
    private var automatic = false
    private var transcript: String? = null

    @Synchronized fun begin(): Long {
        current = ++generation
        offered = false; consumed = false; claimed = false; stopped = false
        automatic = false; transcript = null
        return current
    }
    @Synchronized fun isCurrent(id: Long) = id > 0 && current == id
    @Synchronized fun cancel(id: Long) { if (isCurrent(id)) { current = 0; transcript = null } }
    @Synchronized fun stop() { stopped = true }
    @Synchronized fun automaticAllowed(id: Long) = isCurrent(id) && automatic && !stopped
    @Synchronized fun offer(id: Long, text: String, autoSend: Boolean): Boolean {
        if (!isCurrent(id) || offered || text.isBlank()) return false
        offered = true; transcript = text.trim(); automatic = autoSend && !stopped
        return true
    }
    @Synchronized fun consume(id: Long, text: String): Boolean {
        if (!isCurrent(id) || !offered || consumed || transcript != text.trim()) return false
        consumed = true
        return true
    }
    @Synchronized fun claim(id: Long, text: String, settingEnabled: Boolean): Boolean {
        if (!isCurrent(id) || !consumed || claimed || stopped || !automatic || !settingEnabled ||
            text.isBlank() || transcript != text.trim()) return false
        claimed = true
        return true
    }
}
