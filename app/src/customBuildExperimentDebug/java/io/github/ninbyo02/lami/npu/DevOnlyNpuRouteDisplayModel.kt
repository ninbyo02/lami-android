package io.github.ninbyo02.lami.npu

data class DevOnlyNpuRouteDisplayModel(
    val title: String,
    val message: String,
    val status: Status,
    val output: String?,
    val reasonCode: String,
    val elapsedText: String,
    val backendEvidenceText: String,
    val artifactText: String,
) {
    enum class Status {
        SUCCESS,
        BLOCKED,
        TIMEOUT,
        CRASH,
        ERROR,
    }
}

object DevOnlyNpuRouteDisplayModelMapper {
    fun from(result: DevOnlyNpuRouteResult): DevOnlyNpuRouteDisplayModel {
        val status = when {
            result.success -> DevOnlyNpuRouteDisplayModel.Status.SUCCESS
            result.timeout -> DevOnlyNpuRouteDisplayModel.Status.TIMEOUT
            result.freshCrash -> DevOnlyNpuRouteDisplayModel.Status.CRASH
            result.reasonCode.startsWith("gate_blocked:") -> DevOnlyNpuRouteDisplayModel.Status.BLOCKED
            result.reasonCode == BlockedDevOnlyNpuRouteAdapter.REASON_ADAPTER_NOT_CONNECTED ->
                DevOnlyNpuRouteDisplayModel.Status.BLOCKED
            else -> DevOnlyNpuRouteDisplayModel.Status.ERROR
        }

        return DevOnlyNpuRouteDisplayModel(
            title = titleFor(status),
            message = messageFor(status, result),
            status = status,
            output = result.output,
            reasonCode = result.reasonCode,
            elapsedText = buildElapsedText(result),
            backendEvidenceText = result.backendEvidence ?: "backendEvidence=none",
            artifactText = result.artifactPath ?: "artifactPath=none",
        )
    }

    private fun titleFor(status: DevOnlyNpuRouteDisplayModel.Status): String =
        when (status) {
            DevOnlyNpuRouteDisplayModel.Status.SUCCESS -> "DEV NPU route success"
            DevOnlyNpuRouteDisplayModel.Status.BLOCKED -> "DEV NPU route blocked"
            DevOnlyNpuRouteDisplayModel.Status.TIMEOUT -> "DEV NPU route timeout"
            DevOnlyNpuRouteDisplayModel.Status.CRASH -> "DEV NPU route crash evidence"
            DevOnlyNpuRouteDisplayModel.Status.ERROR -> "DEV NPU route error"
        }

    private fun messageFor(
        status: DevOnlyNpuRouteDisplayModel.Status,
        result: DevOnlyNpuRouteResult,
    ): String =
        when (status) {
            DevOnlyNpuRouteDisplayModel.Status.SUCCESS ->
                "output=${result.output ?: "none"}"
            DevOnlyNpuRouteDisplayModel.Status.BLOCKED ->
                if (result.reasonCode == BlockedDevOnlyNpuRouteAdapter.REASON_ADAPTER_NOT_CONNECTED) {
                    "NPU route adapter is not connected"
                } else {
                    "blocked reason=${result.reasonCode}"
                }
            DevOnlyNpuRouteDisplayModel.Status.TIMEOUT ->
                "timeout reason=${result.reasonCode}"
            DevOnlyNpuRouteDisplayModel.Status.CRASH ->
                "fresh crash evidence reason=${result.reasonCode}"
            DevOnlyNpuRouteDisplayModel.Status.ERROR ->
                "error reason=${result.reasonCode}"
        }

    private fun buildElapsedText(result: DevOnlyNpuRouteResult): String =
        listOf(
            "elapsed_ms=${result.elapsedMs?.toString() ?: "unknown"}",
            "decode_elapsed_ms=${result.decodeElapsedMs?.toString() ?: "unknown"}",
        ).joinToString(" ")
}
