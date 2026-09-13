package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

internal object GpuIdlePrewarmDiagnostic {
    @Suppress("UNUSED_PARAMETER")
    suspend fun run(
        context: Context,
        holder: LocalInferenceEngineHolder,
        request: GpuIdlePrewarmRequest,
    ) = Unit
}
