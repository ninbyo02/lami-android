package io.github.ninbyo02.lami.asr

import io.github.ninbyo02.lami.asr.foundation.vad.FinalizeReason

/** One utterance owns one gate; a queued send must still claim it on the UI thread. */
internal class VoiceInputSubmissionGate(private val automatic: Boolean) {
    private var stopped = false
    private var cancelled = false
    private var claimed = false
    private var pending: String? = null
    @Synchronized fun stop() { stopped = true }
    @Synchronized fun cancel() { cancelled = true; pending = null }
    @Synchronized fun wasStopped(): Boolean = stopped
    @Synchronized fun complete(text: String, reason: FinalizeReason?): Boolean {
        if (cancelled || claimed || pending != null || text.isBlank()) return false
        if (automatic && !stopped && reason == FinalizeReason.END_SILENCE) pending = text
        return pending != null
    }
    @Synchronized fun claim(text: String): Boolean {
        if (cancelled || stopped || claimed || text.isBlank() || pending != text) return false
        claimed = true; pending = null
        return true
    }
}
