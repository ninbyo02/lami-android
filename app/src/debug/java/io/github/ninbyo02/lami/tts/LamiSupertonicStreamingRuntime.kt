package io.github.ninbyo02.lami.tts

/** Upstream sherpa ignores unknown options; require the patched native capability. */
internal object LamiSupertonicStreamingRuntime {
    val available: Boolean by lazy {
        runCatching {
            System.loadLibrary("sherpa-onnx-jni")
            version() == 1
        }.getOrDefault(false)
    }

    private external fun version(): Int
}
