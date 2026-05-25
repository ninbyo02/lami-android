package io.github.ninbyo02.lami.npu

object DevOnlyNpuPhaseH1XmlCardContract {
    private const val RENDERER_HEADER = "DEV ONLY - DEV NPU transient preview"

    fun renderLines(model: DevOnlyNpuPhaseH1ComposeModel): List<String> {
        if (!model.shouldShowSurface) return emptyList()
        return buildList {
            add(model.devBadge)
            add(model.title)
            add("Status: ${model.statusLabel}")
            add(model.subtitle)
            model.body?.let { body ->
                add("Output:")
                add(body)
            }
            add("Reason: ${model.reasonLabel}")
            if (model.detailLines.isNotEmpty()) {
                add("Details:")
                model.detailLines.forEach { line -> add("- $line") }
            }
        }
    }

    fun renderLines(
        visible: Boolean,
        renderedLines: List<String>,
    ): List<String> {
        if (!visible) return emptyList()
        if (
            renderedLines.getOrNull(0) == "DEV ONLY" &&
            renderedLines.getOrNull(1) == "DEV NPU transient preview"
        ) {
            return renderedLines
        }
        return buildList {
            add("DEV ONLY")
            add("DEV NPU transient preview")
            addAll(renderedLines.dropWhile { line -> line == RENDERER_HEADER })
        }
    }

    fun renderText(model: DevOnlyNpuPhaseH1ComposeModel): String =
        renderLines(model).joinToString(separator = "\n")

    fun renderText(
        visible: Boolean,
        renderedLines: List<String>,
    ): String =
        renderLines(
            visible = visible,
            renderedLines = renderedLines,
        ).joinToString(separator = "\n")
}
