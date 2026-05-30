package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS3MarkdownBridge(
    private val mapper: NpuStandardRouteS3MarkdownMapper = NpuStandardRouteS3MarkdownMapper,
) {
    fun prepareMarkdownCandidate(
        s1Result: NpuStandardRouteS1Result,
        finalizeMarkdown: (String) -> String = { it.trim() },
    ): NpuStandardRouteS3MarkdownMapping =
        mapper.map(
            s1Result = s1Result,
            finalizeMarkdown = finalizeMarkdown,
        )
}
