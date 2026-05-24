package io.github.ninbyo02.lami.ui.text

import io.github.ninbyo02.lami.ui.screens.home.buildFinalizedStreamingResponseForPersist
import io.github.ninbyo02.lami.ui.screens.home.normalizeStreamingPartialForRender
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownStreamingModeTest {
    @Test
    fun defaultMode_isLamiRecoveryV1() {
        assertEquals(MarkdownStreamingMode.LAMI_RECOVERY_V1, MarkdownStreamingMode.DEFAULT)
        assertEquals(MarkdownStreamingMode.LAMI_RECOVERY_V1, MarkdownStreamingMode.fromStorage(null))
        assertEquals(MarkdownStreamingMode.LAMI_RECOVERY_V1, MarkdownStreamingMode.fromStorage("unknown"))
    }

    @Test
    fun releaseBuildForcesDefaultMode() {
        assertEquals(
            MarkdownStreamingMode.LAMI_RECOVERY_V1,
            resolveEffectiveMarkdownStreamingMode(
                storedMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
                isDebugBuild = false,
            ),
        )
    }

    @Test
    fun edgeGalleryCompatibleMarkdown_replacesEscapedNewlinesOnly() {
        assertEquals(
            "line1\nline2",
            processEdgeGalleryCompatibleMarkdown("line1\\nline2"),
        )
    }

    @Test
    fun edgeGalleryCompatibleMode_bypassesMarkdownCodeRepair() {
        val input = """
            ```python
            score + = 10
            ```
        """.trimIndent()

        assertEquals(
            input,
            buildFinalizedStreamingResponseForPersist(
                response = input,
                markdownStreamingMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
            ),
        )
    }

    @Test
    fun lamiRecoveryV1_keepsPythonFenceRepair() {
        val input = """
            ```python
            score + = 10
            ```
        """.trimIndent()

        assertEquals(
            """
                ```python
                score += 10
                ```
            """.trimIndent(),
            buildFinalizedStreamingResponseForPersist(
                response = input,
                markdownStreamingMode = MarkdownStreamingMode.LAMI_RECOVERY_V1,
            ),
        )
    }

    @Test
    fun edgeGalleryCompatibleStreamingPartial_replacesEscapedNewlines() {
        assertEquals(
            " alpha\nbeta ",
            normalizeStreamingPartialForRender(
                partial = " alpha\\nbeta ",
                markdownStreamingMode = MarkdownStreamingMode.EDGE_GALLERY_COMPAT,
            ),
        )
    }
}
