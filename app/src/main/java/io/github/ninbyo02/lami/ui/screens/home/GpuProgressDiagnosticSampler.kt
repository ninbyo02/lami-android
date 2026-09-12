package io.github.ninbyo02.lami.ui.screens.home

/** Only repeated progress snapshots are sampled; lifecycle, first-token and failures are kept. */
internal class GpuProgressDiagnosticSampler(private val intervalMs: Long = 1000L) {
    private val lastEmitted = mutableMapOf<String, Long>()
    fun shouldEmit(stage: String, nowMs: Long): Boolean {
        if (stage !in SAMPLED_STAGES) return true
        val previous = lastEmitted[stage]
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        lastEmitted[stage] = nowMs
        return true
    }
    private companion object {
        val SAMPLED_STAGES = setOf("generate_callback_invoked", "generate_ui_append_started", "generate_ui_append_finished")
    }
}
