package io.github.ninbyo02.lami.tts

/**
 * sherpa 1.13.6 JNI looks up invoke([F)Ljava/lang/Integer; on the concrete class.
 * A Kotlin 2.3 invokedynamic lambda only exposes the erased method and aborts JNI.
 */
internal class LamiNativePcmCallback(private val accept: (FloatArray) -> Unit) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int {
        accept(samples)
        return 1
    }
}
