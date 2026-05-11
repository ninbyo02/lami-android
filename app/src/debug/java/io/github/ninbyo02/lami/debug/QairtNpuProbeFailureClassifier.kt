package io.github.ninbyo02.lami.debug

internal object QairtNpuProbeFailureClassifier {
    fun classify(
        throwable: Throwable,
        stage: String,
    ): String {
        val haystack = buildString {
            append(stage)
            append(' ')
            append(throwable.javaClass.name)
            append(' ')
            append(throwable.message.orEmpty())
            throwable.cause?.let { cause ->
                append(' ')
                append(cause.javaClass.name)
                append(' ')
                append(cause.message.orEmpty())
            }
        }.lowercase()

        return when {
            haystack.contains("permission denied") ||
                haystack.contains("eacces") ||
                haystack.contains("missing file") ||
                haystack.contains("unreadable file") ||
                haystack.contains("stage dir missing") ||
                haystack.contains("canread=false") ||
                haystack.contains("canexecute=false") -> "file permission"

            haystack.contains("nosuchmethod") ||
                haystack.contains("noclassdeffound") ||
                haystack.contains("classnotfound") ||
                haystack.contains("abstractmethod") ||
                haystack.contains("backend.npu") ||
                haystack.contains("engineconfig") -> "LiteRT-LM API mismatch"

            haystack.contains("litertdispatch_qualcomm") ||
                haystack.contains("dispatch") -> "dispatch load"

            haystack.contains("skel") ||
                haystack.contains("adsp") ||
                haystack.contains("libqnnhtpv79skel") ||
                haystack.contains("libqnnhtpv79stub") -> "skel / ADSP path"

            haystack.contains("libqnnhtp.so") ||
                haystack.contains("libqnnsystem.so") ||
                haystack.contains("qnn runtime") ||
                haystack.contains("qnnruntime") -> "QNN runtime load"

            haystack.contains("model") && (
                haystack.contains("signature") ||
                    haystack.contains("incompatible") ||
                    haystack.contains("invalid") ||
                    haystack.contains("flatbuffer") ||
                    haystack.contains("schema")
                ) -> "model signature"

            haystack.contains("namespace") ||
                haystack.contains("dlopen failed") ||
                haystack.contains("not accessible for the namespace") -> "linker namespace"

            else -> "unknown"
        }
    }
}
