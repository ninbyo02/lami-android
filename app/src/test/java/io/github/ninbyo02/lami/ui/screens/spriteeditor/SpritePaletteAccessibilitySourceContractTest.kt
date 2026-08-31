package io.github.ninbyo02.lami.ui.screens.spriteeditor

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SpritePaletteAccessibilitySourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun paletteSubheadings_areExposedAsAccessibilityHeadings() {
        val source = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/spriteeditor/SpriteEditorScreen.kt",
        ).readText()

        val baseBlock = source.substringAfter("text = \"Base Color\"").substringBefore("item {")
        assertTrue("Base Color must be an accessibility heading", ".semantics { heading() }" in baseBlock)

        val groupBlock = source.substringAfter("text = group.label").substringBefore("items(group.colors.size)")
        assertTrue("Muted/Normal/Vivid must be accessibility headings", ".semantics { heading() }" in groupBlock)
    }
}
