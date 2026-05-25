package io.github.ninbyo02.lami.npu

object DevOnlyNpuPhaseH1PreviewRenderer {
    fun renderLines(model: DevOnlyNpuPhaseH1CardViewModel): List<String> {
        if (!model.visible) return emptyList()

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
                model.detailLines.forEach { detail -> add("- $detail") }
            }
            if (model.warningLines.isNotEmpty()) {
                add("Warnings:")
                model.warningLines.forEach { warning -> add("- $warning") }
            }
        }
    }

    fun renderContractText(model: DevOnlyNpuPhaseH1CardViewModel): String =
        renderLines(model).joinToString(separator = "\n")
}
