package io.github.ninbyo02.lami.ui.screens.home

internal fun summarizeWhitespaceForUi(text: String?): String {
    if (text == null) return "len=null\nspaces=0\nnewlines=0\ntabs=0\ntext=\"null\""
    val spaces = text.count { it == ' ' }
    val newlines = text.count { it == '\n' }
    val tabs = text.count { it == '\t' }
    val visualized = text
        .replace(" ", "␠")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    val limited = if (visualized.length > 1200) {
        visualized.take(1200) + "…(truncated)"
    } else {
        visualized
    }
    return "len=${text.length}\nspaces=$spaces\nnewlines=$newlines\ntabs=$tabs\ntext=\"$limited\""
}

internal fun safeAppendTrace(
    appendTrace: (String) -> Unit,
    message: String,
) {
    runCatching { appendTrace(message) }
}
