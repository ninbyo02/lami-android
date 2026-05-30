package io.github.ninbyo02.lami.npu

typealias DevOnlyNpuRouteDisplayStatus = DevOnlyNpuRouteDisplayModel.Status

data class DevOnlyNpuTransientUiState(
    val visible: Boolean,
    val title: String,
    val message: String,
    val status: DevOnlyNpuRouteDisplayStatus,
    val outputPreview: String?,
    val reasonCode: String,
    val debugDetails: String,
    val shouldPersistToDb: Boolean = false,
    val shouldSpeakTts: Boolean = false,
    val shouldRenderMarkdown: Boolean = false,
    val shouldStream: Boolean = false,
)

object DevOnlyNpuTransientPresenter {
    fun present(model: DevOnlyNpuRouteDisplayModel): DevOnlyNpuTransientUiState =
        DevOnlyNpuTransientUiState(
            visible = true,
            title = model.title,
            message = model.message,
            status = model.status,
            outputPreview = model.output,
            reasonCode = model.reasonCode,
            debugDetails = buildDebugDetails(model),
            shouldPersistToDb = false,
            shouldSpeakTts = false,
            shouldRenderMarkdown = false,
            shouldStream = false,
        )

    private fun buildDebugDetails(model: DevOnlyNpuRouteDisplayModel): String =
        listOf(
            "reasonCode=${model.reasonCode}",
            model.elapsedText,
            model.backendEvidenceText,
            model.artifactText,
        ).joinToString("\n")
}
