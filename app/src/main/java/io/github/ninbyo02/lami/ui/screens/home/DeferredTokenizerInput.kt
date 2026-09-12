package io.github.ninbyo02.lami.ui.screens.home

/** Transient original strings; never persist these or retain a native engine for statistics. */
internal data class DeferredTokenizerInput(val prompt: String, val response: String)

internal fun resolveDeferredTokenizerInput(
    snapshot: LocalInferenceMeasuredTokenSnapshot?,
    fallbackPrompt: String,
    fallbackResponse: String,
): DeferredTokenizerInput = snapshot?.deferredTokenizerInput
    ?: DeferredTokenizerInput(fallbackPrompt, fallbackResponse)
