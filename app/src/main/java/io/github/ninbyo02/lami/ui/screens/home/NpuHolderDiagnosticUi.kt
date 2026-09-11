package io.github.ninbyo02.lami.ui.screens.home

/** A current display snapshot; diagnostic state and jobs remain owned by the caller. */
internal data class NpuHolderDiagnosticUi<T>(
    val state: T,
    val running: Boolean = false,
    val blockedByGeneration: Boolean = false,
)

/** Actions supplied by the caller, preserving its execution and copy notification behavior. */
internal data class NpuHolderDiagnosticActions(
    val onStart: () -> Unit,
    val onCopySummary: (() -> Unit)? = null,
    val onCopyFullDump: (() -> Unit)? = null,
)
